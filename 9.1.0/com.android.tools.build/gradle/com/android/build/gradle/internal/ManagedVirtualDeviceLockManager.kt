/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.internal

import com.android.builder.utils.SynchronizedFile
import com.android.prefs.AndroidLocationsProvider
import com.google.common.annotations.VisibleForTesting
import java.io.File
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

private const val LOCK_COUNT_PREFIX = "MDLockCount"

private const val TRACKING_FILE_NAME = "active_gradle_devices"

private val DEFAULT_RETRY_WAIT_MS = 1000L

/**
 * Manages concurrent access to Gradle Managed Devices across multiple Gradle processes to enforce a system-wide limit.
 *
 * This class solves the problem of limiting the total number of concurrently running Gradle Managed Devices (GMDs) to avoid overloading the
 * host machine. Since Gradle can run tasks in parallel, including across different projects, a process-safe locking mechanism is required.
 *
 * The manager uses a shared lock file (`tracking_file.lock`) stored in the Gradle AVD directory. This file contains a simple count of all
 * currently "locked" or active devices across the entire system. All operations on this file are performed atomically to ensure safety
 * between competing processes.
 *
 * A JVM shutdown hook is registered to attempt to release any acquired locks if the process terminates unexpectedly, reducing the chance of
 * stale locks. If a stale lock file is encountered, it can be cleared by running the `cleanManagedDevices` task.
 *
 * The primary entry point is the [lockAndExecute] method, which handles the lifecycle of acquiring and releasing locks.
 *
 * @param androidLocationsProvider Service to locate the shared `.android/avd` directory where the lock file is stored.
 * @param maxGMDs The maximum number of concurrent Gradle Managed Devices allowed to run system-wide.
 * @param deviceLockWaitTimeoutSeconds The maximum time in seconds to wait for a device lock to become available before throwing a
 *   [TimeoutException]. A value of 0 or less disables the timeout.
 * @param retryWaitAction A lambda executed between failed attempts to acquire a lock. Defaults to sleeping the thread and is mainly used
 *   for testing.
 */
class ManagedVirtualDeviceLockManager(
  androidLocationsProvider: AndroidLocationsProvider,
  private val maxGMDs: Int,
  private val deviceLockWaitTimeoutSeconds: Int,
  private val retryWaitAction: () -> Unit = { Thread.sleep(DEFAULT_RETRY_WAIT_MS) },
) {
  private val trackingFile: SynchronizedFile

  /**
   * Number of managed devices tracked by this instance of ManagedVirtualDeviceLockManager.
   *
   * This may be less than the total number of managed devices tracked by gradle as multiple lock managers may have open devices at the same
   * time.
   */
  private val trackedDevicesInProcess: AtomicInteger

  @VisibleForTesting
  val devicesInProcess: Int
    get() = trackedDevicesInProcess.get()

  init {
    val lockFile = androidLocationsProvider.gradleAvdLocation.toFile().resolve(TRACKING_FILE_NAME)
    if (!lockFile.parentFile.exists()) {
      lockFile.parentFile.mkdirs()
    }
    trackedDevicesInProcess = AtomicInteger()
    trackingFile = SynchronizedFile.getInstanceWithMultiProcessLocking(lockFile)

    /*
     * Registers a shutdown hook to release any acquired device locks if the process
     * terminates unexpectedly. This ensures the device tracking file is not left
     * in an inconsistent state.
     */
    Runtime.getRuntime().addShutdownHook(Thread { releaseLocks(trackedDevicesInProcess.get()) })
  }

  private val logger: Logger = Logging.getLogger(this.javaClass)

  /**
   * Acquires up to [lockCount] device locks, executes the [onLockAcquired] block, and returns its result.
   *
   * This function blocks until at least one device lock is available. It repeatedly attempts to acquire the requested number of locks. Once
   * one or more locks are secured, it creates a [DeviceLock], passes it to the [onLockAcquired] lambda, and returns the value produced by
   * the lambda.
   *
   * The number of acquired locks may be less than the requested [lockCount]. This prioritizes making progress with available devices over
   * waiting for the full count. The actual number of acquired locks can be checked via `DeviceLock.lockCount`.
   *
   * The locks are released after the [onLockAcquired] block finishes execution, even if an exception occurs. In case of JVM crashes, we
   * attempt to release locks via the shutdown hook. However, its execution is not guaranteed. If that happens, a user has to delete the
   * lock file manually by running [com.android.build.gradle.internal.tasks.ManagedDeviceCleanTask], which invokes [deleteLockFile] to
   * forcefully delete the tracking file.
   *
   * @param T The type of the value returned by the [onLockAcquired] block.
   * @param lockCount The desired number of device locks to acquire. Defaults to 1.
   * @param onLockAcquired The block of code to execute while holding the device locks. It receives a [DeviceLock] instance, and its result
   *   is returned by this function.
   * @return The result of the [onLockAcquired] lambda.
   */
  fun <T> lockAndExecute(lockCount: Int = 1, onLockAcquired: (DeviceLock) -> T): T {
    val startSystemTime = System.currentTimeMillis()
    var locksAcquired = AcquireLocksResult(0, 0)
    while (true) {
      locksAcquired = tryToAcquireLocks(lockCount)
      if (locksAcquired.locksToClaim != 0) {
        break
      }

      if (deviceLockWaitTimeoutSeconds > 0 && System.currentTimeMillis() - startSystemTime > deviceLockWaitTimeoutSeconds * 1000) {
        throw TimeoutException(
          """
                    Could not acquire device lock after waiting for $deviceLockWaitTimeoutSeconds seconds.
                    The limit of $maxGMDs concurrent devices has been reached (${locksAcquired.currentTotalDevicesCount} are active).

                    If this seems incorrect, you may have a stale lock file.
                    Run the `./gradlew cleanManagedDevices` task to resolve the issue.
                    """
            .trimIndent()
        )
      }

      // Rest for a bit before trying again.
      retryWaitAction()
    }

    try {
      return onLockAcquired(DeviceLock(locksAcquired.locksToClaim))
    } finally {
      releaseLocks(locksAcquired.locksToClaim)
    }
  }

  private fun releaseLocks(locksToRelease: Int) {
    if (locksToRelease <= 0) {
      if (locksToRelease < 0) {
        logger.log(
          LogLevel.WARN,
          """
                        Attempting to free a negative number of locks.
                        Locks to release: $locksToRelease
                    """
            .trimIndent(),
        )
      }
      return
    }
    trackingFile.write { file ->
      val currentTotalDeviceCount = getCurrentLockCount(file)

      val newTotalDevicesCount =
        if (devicesInProcess < locksToRelease) {
          logger.log(
            LogLevel.WARN,
            """
                        Attempting to free more locks than have been claimed by this lock manager.
                        Locks to release: $locksToRelease Locks available In Process: $devicesInProcess
                    """
              .trimIndent(),
          )
          currentTotalDeviceCount - trackedDevicesInProcess.getAndSet(0)
        } else {
          trackedDevicesInProcess.addAndGet(-locksToRelease)
          currentTotalDeviceCount - locksToRelease
        }
      writeLockCount(file, newTotalDevicesCount)
    }
  }

  private fun tryToAcquireLocks(locksRequested: Int): AcquireLocksResult {
    trackingFile.createIfAbsent { file -> createDefaultLockFile(file) }
    return trackingFile.write { file ->
      // Get the current lock count.
      val currentTotalDevicesCount = getCurrentLockCount(file)
      // Find out how many locks we can claim, and adjust the file if necessary.
      val locksToClaim = max(min(locksRequested, maxGMDs - currentTotalDevicesCount), 0)
      if (locksToClaim != 0) {
        writeLockCount(file, currentTotalDevicesCount + locksToClaim)
        trackedDevicesInProcess.addAndGet(locksToClaim)
      }
      AcquireLocksResult(locksToClaim, currentTotalDevicesCount)
    }
  }

  private data class AcquireLocksResult(val locksToClaim: Int, val currentTotalDevicesCount: Int)

  /**
   * Deletes the lock file
   *
   * Should be used if the lock file contains stale locks.
   */
  fun deleteLockFile() {
    trackingFile.write(File::delete)
  }

  private fun createDefaultLockFile(file: File) {
    logger.info("Creating default GMD lock tracking file at ${file.absolutePath}")
    // Since the nature of synchronized files are supposed to work across versions,
    // we are adding a prefix of "GMDLockCount" just in case we decide to track other
    // relavent information in the future.
    file.writeText("$LOCK_COUNT_PREFIX 0")
  }

  private fun getCurrentLockCount(file: File): Int {
    if (file.exists()) {
      for (line in file.readLines()) {
        if (line.startsWith(LOCK_COUNT_PREFIX)) {
          return line.substring(LOCK_COUNT_PREFIX.length).trim().toInt()
        }
      }
      logger.error(" Failed to find $LOCK_COUNT_PREFIX in gmd lock file. File Contents:\n " + "${file.readLines()}")
      // Reset the GMD lock here.
      writeLockCount(file, 0)
      error("Failed to find the number of active Gradle Managed Devices.")
    } else {
      return 0
    }
  }

  private fun writeLockCount(file: File, newLockCount: Int) {
    val lines = file.readLines()
    // Clear the file so we can overwrite it.
    file.writeText("")
    file.appendText("$LOCK_COUNT_PREFIX $newLockCount")
    lines.forEach { line ->
      if (!line.startsWith(LOCK_COUNT_PREFIX)) {
        file.appendText("\n$line")
      }
    }
  }

  /** A lock tracking a number of devices equal to [lockCount]. */
  data class DeviceLock(val lockCount: Int)
}
