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
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider

private const val EMULATOR_EXECUTABLE = "emulator"
private const val DEFAULT_DEVICE_BOOT_AND_SNAPSHOT_CHECK_TIMEOUT_SEC = 600L
private const val ADB_TIMEOUT_SEC = 60L

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
            listOfNotNull(
                emulatorExecutable.absolutePath,
                "@$avdName",
                "-no-window",
                "-no-boot-anim",
                "-no-audio",
                "-verbose".takeIf { showEmulatorKernelLogging },
                "-show-kernel".takeIf { showEmulatorKernelLogging },
                "-gpu",
                emulatorGpuFlag,
                "-check-snapshot-loadable",
                snapshotName
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
        logger.verbose("Creating snapshot for $avdName")
        val deviceId = "${avdName}_snapshot"

        val processBuilder = processFactory(
            listOfNotNull(
                emulatorExecutable.absolutePath,
                "@${avdName}",
                "-no-window",
                "-no-boot-anim",
                "-no-audio",
                "-verbose".takeIf { showEmulatorKernelLogging },
                "-show-kernel".takeIf { showEmulatorKernelLogging },
                "-id",
                deviceId,
                "-gpu",
                emulatorGpuFlag,
            )
        )
        processBuilder.environment()["ANDROID_AVD_HOME"] = avdLocation.absolutePath
        val process = processBuilder.start()
        val bootCompleted = AtomicBoolean(false)
        try {
            Thread {
                var emulatorSerial: String? = null
                while(process.isAlive) {
                    try {
                        emulatorSerial = adbHelper.findDeviceSerialWithId(deviceId, logger)
                        break
                    } catch (e: Exception) {
                        logger.verbose("Waiting for $avdName to be attached to adb.")
                    }
                    Thread.sleep(5000)
                }
                if (emulatorSerial == null) {
                    // It is possible for the emulator process to return unexpectly
                    // and the emulatorSerial to not be set.
                    return@Thread
                }
                logger.verbose("$avdName is attached to adb ($emulatorSerial).")

                while(process.isAlive) {
                    if (adbHelper.isBootCompleted(emulatorSerial, logger)) {
                        break
                    }
                    logger.verbose("Waiting for $avdName to boot up.")
                    Thread.sleep(5000)
                }
                logger.verbose("Booting $avdName is completed.")

                while(process.isAlive) {
                    if (adbHelper.isPackageManagerStarted(emulatorSerial)) {
                        break
                    }
                    logger.verbose("Waiting for PackageManager to be ready on $avdName.")
                    Thread.sleep(5000)
                }
                logger.verbose("PackageManager is ready on $avdName.")

                if (process.isAlive) {
                    bootCompleted.set(true)
                    Thread.sleep(WAIT_AFTER_BOOT_MS)
                    adbHelper.killDevice(emulatorSerial)
                }
            }.start()

            GrabProcessOutput.grabProcessOutput(
                process,
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
            process.waitUntilTimeout(logger) {
                logger.verbose("Snapshot creation timed out. Closing emulator.")
                closeEmulatorWithId(process, deviceId, logger)
                process.waitFor()
                error("""
                    Gradle was not able to complete device setup for: $avdName
                    This could be due to having insufficient resources to provision the number of
                    devices requested. Try running the test again and request fewer devices or
                    fewer shards.
                """.trimIndent())
            }
            if (!bootCompleted.get()) {
                error("""
                    Gradle was not able to complete device setup for: $avdName
                    The emulator failed to open the managed device to generate the snapshot.
                    This is because the emulator closed unexpectedly, try updating the emulator and
                    ensure a device can be run from Android Studio.
                """.trimIndent())
            }
            logger.info("Successfully created snapshot for: $avdName")
        } finally {
            closeEmulatorWithId(process, deviceId, logger)
            process.waitFor()
        }
    }

    /**
     * Attempts to close the emulator with the given id.
     **/
    private fun closeEmulatorWithId(
        emulatorProcess: Process,
        idValue: String,
        logger: ILogger
    ) {
        try {
            val emulatorSerial = adbHelper.findDeviceSerialWithId(idValue, logger)
            adbHelper.killDevice(emulatorSerial)
        } catch (e: Exception) {
            logger.info("Failed to close emulator properly from adb. Reason: $e")
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

