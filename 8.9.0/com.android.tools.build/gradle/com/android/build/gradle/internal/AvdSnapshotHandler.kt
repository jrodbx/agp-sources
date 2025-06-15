/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.build.gradle.internal.testing.getEmulatorMetadata
import com.android.build.gradle.internal.testing.AdbHelper
import com.android.build.gradle.internal.testing.EmulatorVersionMetadata
import com.android.build.gradle.internal.testing.QemuExecutor
import com.android.sdklib.internal.avd.AvdManager
import com.android.testing.utils.createSetupDeviceId
import com.android.utils.FileUtils
import com.android.utils.GrabProcessOutput
import com.android.utils.ILogger
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import java.io.IOException

private const val EMULATOR_EXECUTABLE = "emulator"
private const val DEFAULT_DEVICE_BOOT_AND_SNAPSHOT_CHECK_TIMEOUT_SEC = 600L
private const val TARGET_SNAPSHOT_NAME = "default_boot"

// This is an extra wait time after the AVD boot completed before taking system snapshot image
// for stability.
private const val WAIT_AFTER_BOOT_MS = 5000L

/**
 * @param deviceBootAndSnapshotCheckTimeoutSec a timeout duration in minute for AVD device to boot
 * and for snapshot to be created. If null, the default value (10 minutes) is used. If zero or
 * negative value is passed, it waits infinitely.
 */
class AvdSnapshotHandler(
    private val showFullEmulatorKernelLogging: Boolean,
    private val deviceBootAndSnapshotCheckTimeoutSec: Long?,
    private val adbHelper: AdbHelper,
    private val emulatorDir: Provider<Directory>,
    private val qemuExecutor: QemuExecutor,
    private val extraWaitAfterBootCompleteMs: Long = WAIT_AFTER_BOOT_MS,
    private val executor: Executor = Executors.newSingleThreadExecutor(),
    private val metadataFactory: (File) -> EmulatorVersionMetadata = ::getEmulatorMetadata,
    private val processFactory: (List<String>) -> ProcessBuilder = { ProcessBuilder(it) }) {

    private val emulatorMetadata: EmulatorVersionMetadata by lazy {
        val emulatorDirectory =
            emulatorDir.orNull?.asFile ?: error("Emulator dir does not exist")
        metadataFactory(emulatorDirectory)
    }

    /**
     * Checks whether the emulator directory contains a valid emulator executable, and returns it.
     *
     * @param emulatorDirectoryProvider provider for the directory containing the emulator and related
     * files.
     *
     * @return the emulator executable from the given directory.
     */
    val emulatorExecutable: File by lazy {
        emulatorMetadata // force evalutation of emulator metadata
        val emulatorDirectory =
            emulatorDir.orNull?.asFile ?: error("Emulator dir does not exist")
        emulatorDirectory.resolve(EMULATOR_EXECUTABLE)
    }

    private fun getEmulatorCommand(
        avdName: String,
        emulatorGpuFlag: String,
        additionalParams: List<String>,
    ): List<String> {
        return listOfNotNull(
            emulatorExecutable.absolutePath,
            "@$avdName",
            "-no-window",
            "-no-boot-anim",
            "-no-audio",
            "-delay-adb",
            "-verbose".takeIf { showFullEmulatorKernelLogging },
            "-show-kernel",
            "-gpu", emulatorGpuFlag,
            *additionalParams.toTypedArray(),
        )
    }

    /**
     * Checks whether the given snapshot on a device is loadable with the emulator.
     *
     * Uses a command of the form:
     * ./emulator @[avdName] -no-window -no-boot-anim -check-snapshot-loadable [snapshotName]
     * Which does not open an instance of the emulator and instead returns "Loadable" or
     * "Not loadable" depending on if the snapshot is compatible with current emulator.
     * Note: -no-window and -no-boot-anim affect which snapshots may be loadable, but
     * otherwise have no effect.
     *
     * @param avdName The name of the device to check if the snapshot is loadable on.
     * @param snapshotName The name of the snapshot to check if loadable.
     *
     * @return true if and only if the snapshot is loadable with the current version of
     * the emulator.
     */
    fun checkSnapshotLoadable(
        avdName: String,
        avdLocation: File,
        emulatorGpuFlag: String,
        logger: ILogger,
        snapshotName: String = "default_boot"
    ): Boolean {
        logger.info("Checking $snapshotName on device $avdName is loadable.")
        val processBuilder = processFactory(
            getEmulatorCommand(
                avdName,
                emulatorGpuFlag,
                listOf(
                    "-read-only",
                    "-no-snapshot-save",
                    "-check-snapshot-loadable", snapshotName
                )
            )
        )
        processBuilder.environment()["ANDROID_AVD_HOME"] = avdLocation.absolutePath
        val process = processBuilder.start()

        var success = AtomicBoolean(false)
        var timeout = false
        var outputProcessed = CountDownLatch(1)
        try {
            GrabProcessOutput.grabProcessOutput(
                process,
                GrabProcessOutput.Wait.ASYNC,
                object : GrabProcessOutput.IProcessOutput {
                    override fun out(line: String?) {
                        if (line == null) {
                            outputProcessed.countDown()
                            return
                        }
                        logger.verbose(line)
                        // If it fails, the line will contain "Not loadable"
                        // so checking for the capitalized text should be fine.
                        if (line.contains("Loadable")) {
                            success.set(true)
                            outputProcessed.countDown()
                        } else if (line.contains("Not loadable")) {
                            outputProcessed.countDown()
                        }
                    }

                    override fun err(line: String?) {}
                }
            )
        } catch (e: Exception) {
            process.destroy()
            throw RuntimeException(e)
        }
        process.waitUntilTimeout(logger) {
            timeout = true
            logger.warning("Timed out trying to check $snapshotName for $avdName is loadable.")
        }
        if (!timeout) {
            val timeoutSec =
                deviceBootAndSnapshotCheckTimeoutSec ?:
                DEFAULT_DEVICE_BOOT_AND_SNAPSHOT_CHECK_TIMEOUT_SEC
            outputProcessed.await(timeoutSec, TimeUnit.SECONDS)
        }
        return success.get()
    }

    private fun Process.waitUntilTimeout(logger: ILogger, onTimeout: () -> Unit) {
        val timeoutSec =
            deviceBootAndSnapshotCheckTimeoutSec ?:
            DEFAULT_DEVICE_BOOT_AND_SNAPSHOT_CHECK_TIMEOUT_SEC
        if (timeoutSec > 0) {
            logger.verbose("Waiting for a process to complete (timeout $timeoutSec seconds)")
            if (!waitFor(timeoutSec, TimeUnit.SECONDS)) {
                onTimeout()
            }
        } else {
            logger.verbose("Waiting for a process to complete (no timeout)")
            waitFor()
        }
    }

    /**
     * Generates a snapshot for the given device.
     *
     * Temporarily opens the emulator to load the device in a state where a
     * snapshot can be saved. Then the emulator is closed to force a snapshot
     * write.
     *
     * @param avdName name of the device for a snapshot to be created.
     */
    fun generateSnapshot(
        avdName: String,
        avdLocation: File,
        emulatorGpuFlag: String,
        avdManager: AvdManager,
        logger: ILogger
    ) {
        logger.verbose("Creating snapshot for $avdName")

        val maxRetryAttempt = 5
        lateinit var lastException: EmulatorSnapshotCannotCreatedException
        repeat(maxRetryAttempt) { attempt ->
            try {
                // Create a new snapshot image by starting emulator then stop immediately after
                // the boot is completed and system server is ready. Emulator takes snapshot when
                // it's closing.
                logger.verbose(
                        "Starting Emulator to create a snapshot for $avdName " +
                        "(Attempt ${attempt + 1}/$maxRetryAttempt)")
                startEmulatorThenStop(
                        createSnapshot = true,
                        avdName,
                        avdLocation,
                        emulatorGpuFlag,
                        logger)

                if (!checkSnapshotLoadable(
                        avdName,
                        avdLocation,
                        emulatorGpuFlag,
                        logger)) {

                    throw EmulatorSnapshotCannotCreatedException(
                        "Snapshot setup for $avdName ran successfully, but the snapshot failed " +
                        "to be created. This is likely to a lack of disk space for the snapshot. " +
                        "Try the cleanManagedDevices task with the --unused-only flag to remove " +
                        "any unused devices for this project.")
                }

                // Validate the newly created snapshot if that's really loadable and usable.
                // Emulator occasionally (about 1% of the time) generates a corrupted snapshot
                // image. If this happens, the emulator process crashed immediately after it
                // loads such snapshot image. See b/314022353.
                logger.verbose(
                        "Starting Emulator to validate a snapshot for $avdName " +
                        "(Attempt ${attempt + 1}/$maxRetryAttempt)")
                startEmulatorThenStop(
                        createSnapshot = false,
                        avdName,
                        avdLocation,
                        emulatorGpuFlag,
                        logger)

                logger.info("Successfully created snapshot for: $avdName")
                return
            } catch (e: EmulatorSnapshotCannotCreatedException) {
                logger.warning(
                    "Failed to create Emulator snapshot image (${attempt + 1}/$maxRetryAttempt). "
                    + "Error: $e")
                lastException = e
            }
        }

        deleteSnapshotForDevice(avdName, avdManager, logger)

        throw lastException
    }

    class EmulatorSnapshotCannotCreatedException(message: String) : RuntimeException(message)

    private fun deleteSnapshotForDevice(
        deviceName: String,
        avdManager: AvdManager,
        logger: ILogger,
    ) {
        val avdDir = avdManager.getAvd(deviceName, /* validAvdOnly = */false)
            ?.dataFolderPath?.toFile() ?: return
        try {
            logger.warning("Deleting unbootable snapshot for device: $deviceName")
            qemuExecutor.deleteSnapshot(deviceName, avdDir, TARGET_SNAPSHOT_NAME, logger)
        } catch (ioException: IOException) {
            logger.error(
                ioException,
                "Could not delete snapshot $TARGET_SNAPSHOT_NAME for device $deviceName."
            )
        }
    }

    private fun startEmulatorThenStop(
        createSnapshot: Boolean,
        avdName: String,
        avdLocation: File,
        emulatorGpuFlag: String,
        logger: ILogger
    ) {
        val deviceId = createSetupDeviceId(avdName)

        val processBuilder = processFactory(
            getEmulatorCommand(
                avdName,
                emulatorGpuFlag,
                listOfNotNull(
                    "-no-snapshot-load".takeIf { createSnapshot },
                    "-force-snapshot-load".takeIf {
                        !createSnapshot && emulatorMetadata.canUseForceSnapshotLoad },
                    "-read-only".takeIf { !createSnapshot },
                    "-no-snapshot-save".takeIf { !createSnapshot },
                    "-id", deviceId)
            )
        )
        processBuilder.environment()["ANDROID_AVD_HOME"] = avdLocation.absolutePath
        processBuilder.environment()["ANDROID_EMULATOR_WAIT_TIME_BEFORE_KILL"] = (
                deviceBootAndSnapshotCheckTimeoutSec ?:
                DEFAULT_DEVICE_BOOT_AND_SNAPSHOT_CHECK_TIMEOUT_SEC
                ).toString()
        val emulatorProcess = processBuilder.start()
        val bootCompleted = AtomicBoolean(false)
        // need to process both stderr and stdout
        val outputProcessed = CountDownLatch(2)
        val emulatorErrorList = mutableListOf<String>()
        try {
            executor.execute {
                var emulatorSerial: String? = null
                while(emulatorProcess.isAlive) {
                    try {
                        emulatorSerial = adbHelper.findDeviceSerialWithId(deviceId, logger)
                        break
                    } catch (e: Exception) {
                        logger.verbose(
                            "Waiting for $avdName to be attached to adb. Last attempt: $e")
                    }
                    Thread.sleep(5000)
                }
                if (emulatorSerial == null) {
                    // It is possible for the emulator process to return unexpectedly
                    // and the emulatorSerial to not be set.
                    return@execute
                }
                logger.verbose("$avdName is attached to adb ($emulatorSerial).")

                while(emulatorProcess.isAlive) {
                    if (adbHelper.isBootCompleted(emulatorSerial, logger)) {
                        break
                    }
                    logger.verbose("Waiting for $avdName to boot up.")
                    Thread.sleep(5000)
                }
                logger.verbose("Booting $avdName is completed.")

                while(emulatorProcess.isAlive) {
                    if (adbHelper.isPackageManagerStarted(emulatorSerial, logger)) {
                        break
                    }
                    logger.verbose("Waiting for PackageManager to be ready on $avdName.")
                    Thread.sleep(5000)
                }
                logger.verbose("PackageManager is ready on $avdName.")

                // Emulator process may crash soon after the boot is completed.
                // Wait a few extra seconds to make sure Emulator is really ready.
                if (extraWaitAfterBootCompleteMs > 0) {
                    Thread.sleep(extraWaitAfterBootCompleteMs)
                }

                if (emulatorProcess.isAlive) {
                    logger.verbose("$avdName is ready to take a snapshot.")
                    bootCompleted.set(true)
                    adbHelper.killDevice(emulatorSerial)
                } else {
                    logger.warning(
                        "Emulator process exited unexpectedly with the return code " +
                        "${emulatorProcess.exitValue()}.")
                }
            }

            GrabProcessOutput.grabProcessOutput(
                emulatorProcess,
                GrabProcessOutput.Wait.ASYNC,
                object : GrabProcessOutput.IProcessOutput {

                    override fun out(line: String?) = processLine(line)

                    override fun err(line: String?) = processLine(line)

                    fun processLine(line: String?) {
                        if (line == null) {
                            outputProcessed.countDown()
                            return
                        }

                        if (line.contains("ERROR")) {
                            emulatorErrorList.add(line)
                        }
                        logger.verbose(line)
                    }
                }
            )

            emulatorProcess.waitUntilTimeout(logger) {
                logger.warning("Snapshot creation timed out. Closing emulator.")
                throw EmulatorSnapshotCannotCreatedException("""
                    Gradle was not able to complete device setup for: $avdName
                    This could be due to having insufficient resources to provision the number of
                    devices requested. Try running the test again and request fewer devices or
                    fewer shards.
                """.trimIndent())
            }
        } finally {
            emulatorProcess.destroy()
        }



        if (!bootCompleted.get()) {
            // wait for processing to complete for output of process

            val timeoutSec =
                deviceBootAndSnapshotCheckTimeoutSec ?:
                DEFAULT_DEVICE_BOOT_AND_SNAPSHOT_CHECK_TIMEOUT_SEC
            outputProcessed.await(timeoutSec, TimeUnit.SECONDS)

            throw EmulatorSnapshotCannotCreatedException(
                """
                        Gradle was not able to complete device setup for: $avdName
                        The emulator failed to open the managed device to generate the snapshot.
                        This is because the emulator closed unexpectedly (exit value = ${emulatorProcess.exitValue()}).
                        The errors recorded from emulator:
                    """.trimIndent() + emulatorErrorList.joinToString(separator = "\n"))
        }
    }
}
