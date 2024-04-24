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
import java.io.Closeable
import java.io.File
import kotlin.math.min
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

private const val LOCK_COUNT_PREFIX = "MDLockCount"

private const val TRACKING_FILE_NAME = "active_gradle_devices"

private val DEFAULT_RETRY_WAIT_MS = 1000L

/**
 * Class for tracking the number of active Virtual Managed Devices being spawned
 * by the Android Plugin for gradle.
 *
 * This class does not create managed devices, but instead acts as the source of truth
 * for how many concurrent gradle managed devices are being run. Keeping the number capped
 * at [maxGMDs]. The way to request a number of managed devices is with the [lock] method.
 *
 * Example:
 *     val lockManager = ManagedVirtualDeviceLockManager(locations, 3)
 *
 *     lockManager.lock(locksRequested).use { lock ->
 *         val locksAcquired = lock.lockCount
 *         // Do stuff with a number of devices equal to the locks acquired...
 *     }
 */
class ManagedVirtualDeviceLockManager(
    androidLocationsProvider: AndroidLocationsProvider,
    private val maxGMDs: Int,
    private val lockRetryWaitMs: Long = DEFAULT_RETRY_WAIT_MS
) {
    private val trackingFile: SynchronizedFile

    init {
        val lockFile =
            androidLocationsProvider.gradleAvdLocation.toFile().resolve(TRACKING_FILE_NAME)
        if (!lockFile.parentFile.exists()) {
            lockFile.parentFile.mkdirs()
        }
        trackingFile = SynchronizedFile.getInstanceWithMultiProcessLocking(lockFile)
    }

    private val logger: Logger = Logging.getLogger(this.javaClass)

    /**
     * Attempts to retrieve a [DeviceLock] for a number of devices equal to [lockCount]
     *
     * This is a method that blocks if no devices are available. Then, once devices are available,
     * a closable [DeviceLock] will be returned with a number of locks up to [lockCount]. This is
     * not guaranteed to be equal to [lockCount], because making some devices available for testing
     * is better than waiting for all the requested devices.
     *
     * @param lockCount the number of device locks requests.
     * @return a [DeviceLock] with a number of locks up to [lockCount]. To find the number of locks
     * acquired, check [DeviceLock.lockCount].
     */
    fun lock(lockCount: Int = 1): DeviceLock {
        var locksAcquired = 0
        while (locksAcquired == 0) {
            locksAcquired = tryToAcquireLocks(lockCount)
            // Rest for a bit before trying again.
            Thread.sleep(lockRetryWaitMs)
        }
        return DeviceLock(locksAcquired)
    }

    private fun releaseLocks(locksToRelease: Int) {
        trackingFile.write { file ->
            val currentLockCount = getCurrentLockCount(file)

            val newlockCount = if (currentLockCount < locksToRelease) {
                logger.error(
                    """
                        Attempting to free more locks than have been claimed.
                        Locks to release: $locksToRelease Locks available: $currentLockCount
                    """.trimIndent()
                )
                0
            } else {
                currentLockCount - locksToRelease
            }
            writeLockCount(file, newlockCount)
        }
    }

    private fun tryToAcquireLocks(locksRequested: Int): Int {
        trackingFile.createIfAbsent { file -> createDefaultLockFile(file) }
        return trackingFile.write { file ->
            // Get the current lock count.
            val currentLockCount = getCurrentLockCount(file)
            // Find out how many locks we can claim, and adjust the file if necessary.
            val locksToClaim = min(locksRequested, maxGMDs - currentLockCount)
            if (locksToClaim != 0) {
                writeLockCount(file, currentLockCount + locksToClaim)
            }
            locksToClaim
        }
    }

    private fun createDefaultLockFile(file: File) {
        logger.info("Creating default GMD lock tracking file at ${file.absolutePath}")
        // Since the nature of synchronized files are supposed to work across versions,
        // we are adding a prefix of "GMDLockCount" just in case we decide to track other
        // relavent information in the future.
        file.writeText("$LOCK_COUNT_PREFIX 0")
    }

    private fun getCurrentLockCount(file: File): Int {
        for (line in file.readLines()) {
            if (line.startsWith(LOCK_COUNT_PREFIX)) {
                return line.substring(LOCK_COUNT_PREFIX.length).trim().toInt()
            }
        }
        logger.error(
            " Failed to find $LOCK_COUNT_PREFIX in gmd lock file. File Contents:\n " +
                    "${file.readLines()}"
        )
        // Reset the GMD lock here.
        writeLockCount(file, 0)
        error("Failed to find the number of active Gradle Managed Devices.")
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

    /**
     * A lock tracking a number of devices equal to [lockCount].
     *
     * A closable lock that tracks a number of managed devices equal to [lockCount]
     */
    inner class DeviceLock internal constructor(val lockCount: Int): Closeable {
        var closed = false
            private set

        override fun close() {
            if (!closed) {
                this@ManagedVirtualDeviceLockManager.releaseLocks(lockCount)
                closed = true
            }
        }
    }
}
