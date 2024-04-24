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

import com.android.build.gradle.internal.testing.AdbHelper
import com.android.utils.GrabProcessOutput
import com.android.utils.ILogger
import java.io.File
import java.nio.file.Files.readAllLines
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider

private const val EMULATOR_EXECUTABLE = "emulator"
private const val DEFAULT_DEVICE_BOOT_AND_SNAPSHOT_CHECK_TIMEOUT_SEC = 600L

// This is an extra wait time after the AVD boot completed before taking system snapshot image
// for stability.
private const val WAIT_AFTER_BOOT_MS = 5000L

private const val MINIMUM_MAJOR_VERSION = 30
private const val MINIMUM_MINOR_VERSION = 6
private const val MINIMUM_MICRO_VERSION = 4

/**
 * @param deviceBootAndSnapshotCheckTimeoutSec a timeout duration in minute for AVD device to boot
 * and for snapshot to be created. If null, the default value (10 minutes) is used. If zero or
 * negative value is passed, it waits infinitely.
 */
class AvdSnapshotHandler(
    private val showEmulatorKernelLogging: Boolean,
    private val deviceBootAndSnapshotCheckTimeoutSec: Long?,
    private val adbHelper: AdbHelper,
    private val extraWaitAfterBootCompleteMs: Long = WAIT_AFTER_BOOT_MS,
    private val executor: Executor = Executors.newSingleThreadExecutor(),
    private val processFactory: (List<String>) -> ProcessBuilder = { ProcessBuilder(it) }) {
    /**
     * Checks whether the emulator directory contains a valid emulator executable, and returns it.
     *
     * @param emulatorDirectoryProvider provider for the directory containing the emulator and related
     * files.
     *
     * @return the emulator executable from the given directory.
     */
    fun getEmulatorExecutable(emulatorDirectoryProvider: Provider<Directory>): File {
        val emulatorDir =
            emulatorDirectoryProvider.orNull?.asFile ?: error("Emulator dir does not exist")
        ensureEmulatorVersionRequirement(emulatorDir)
        return emulatorDir.resolve(EMULATOR_EXECUTABLE)
    }

    private fun getEmulatorCommand(
        avdName: String,
        emulatorExecutable: File,
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
            "-verbose".takeIf { showEmulatorKernelLogging },
            "-show-kernel".takeIf { showEmulatorKernelLogging },
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
        emulatorExecutable: File,
        avdLocation: File,
        emulatorGpuFlag: String,
        logger: ILogger,
        snapshotName: String = "default_boot"
    ): Boolean {
        logger.info("Checking $snapshotName on device $avdName is loadable.")
        val processBuilder = processFactory(
            getEmulatorCommand(
                avdName,
                emulatorExecutable,
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

        var success = false
        try {
            GrabProcessOutput.grabProcessOutput(
                process,
                GrabProcessOutput.Wait.WAIT_FOR_READERS,
                object : GrabProcessOutput.IProcessOutput {
                    override fun out(line: String?) {
                        line ?: return
                        logger.verbose(line)
                        // If it fails, the line will contain "Not loadable"
                        // so checking for the capitalized text should be fine.
                        if (line.contains("Loadable")) {
                            success = true
                        }
                    }

                    override fun err(line: String?) {}
                }
            )
        } catch (e: Exception) {
            process.destroy()
            throw RuntimeException(e)
        }
        return success
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
        emulatorExecutable: File,
        avdLocation: File,
        emulatorGpuFlag: String,
        logger: ILogger
    ) {
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
                        emulatorExecutable,
                        avdLocation,
                        emulatorGpuFlag,
                        logger)

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
                        emulatorExecutable,
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
        throw lastException
    }

    class EmulatorSnapshotCannotCreatedException(message: String) : RuntimeException(message)

    private fun startEmulatorThenStop(
        createSnapshot: Boolean,
        avdName: String,
        emulatorExecutable: File,
        avdLocation: File,
        emulatorGpuFlag: String,
        logger: ILogger
    ) {
        val deviceId = "${avdName}_snapshot"

        val processBuilder = processFactory(
            getEmulatorCommand(
                avdName,
                emulatorExecutable,
                emulatorGpuFlag,
                listOfNotNull(
                    "-no-snapshot-load".takeIf { createSnapshot },
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
                    override fun out(line: String?) {
                        line ?: return
                        logger.verbose(line)
                    }

                    override fun err(line: String?) {
                        line ?: return
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
            if (!bootCompleted.get()) {
                throw EmulatorSnapshotCannotCreatedException("""
                    Gradle was not able to complete device setup for: $avdName
                    The emulator failed to open the managed device to generate the snapshot.
                    This is because the emulator closed unexpectedly (${emulatorProcess.exitValue()}),
                    try updating the emulator and ensure a device can be run from Android Studio.
                """.trimIndent())
            }
        } finally {
            emulatorProcess.destroy()
        }
    }

    /**
     * Ensures that all required features of the emulator are present.
     *
     * Checks the emulator version to ensure the emulator executable supports the
     * "--check-snapshot-loadable" flag. Errors on failure.
     */
    private fun ensureEmulatorVersionRequirement(emulatorDir: File) {
        val packageFile = emulatorDir.resolve("package.xml")
        val versionPattern =
            Pattern.compile("<major>(\\d+)</major><minor>(\\d+)</minor><micro>(\\d+)</micro>")
        for (line in readAllLines(packageFile.toPath())) {
            val matcher = versionPattern.matcher(line)
            if (matcher.find()) {
                val majorVersion = matcher.group(1).toInt()
                val minorVersion = matcher.group(2).toInt()
                val microVersion = matcher.group(3).toInt()
                when {
                    majorVersion > MINIMUM_MAJOR_VERSION -> return
                    majorVersion == MINIMUM_MAJOR_VERSION &&
                            minorVersion > MINIMUM_MINOR_VERSION -> return
                    majorVersion == MINIMUM_MAJOR_VERSION &&
                            minorVersion == MINIMUM_MINOR_VERSION &&
                            microVersion >= MINIMUM_MICRO_VERSION -> return
                    else ->
                        error(
                            "Emulator needs to be updated in order to use managed devices. Minimum " +
                                    "version required: $MINIMUM_MAJOR_VERSION.$MINIMUM_MINOR_VERSION" +
                                    ".$MINIMUM_MICRO_VERSION. Version found: $majorVersion.$minorVersion" +
                                    ".$microVersion."
                        )
                }
            }
        }
        error(
            "Could not determine version of Emulator in ${emulatorDir.absolutePath}. Update " +
                    "emulator in order to use Managed Devices."
        )
    }
}
