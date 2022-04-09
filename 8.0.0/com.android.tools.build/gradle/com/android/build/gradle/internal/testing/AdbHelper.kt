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

package com.android.build.gradle.internal.testing

import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.utils.GrabProcessOutput
import com.android.utils.ILogger
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.util.concurrent.TimeoutException

private const val ADB_TIMEOUT_SEC = 60L

/**
 * Helper class for interacting with adb for use with managed virtual devices.
 */
class AdbHelper(
    private val versionedSdkLoader: Provider<SdkComponentsBuildService.VersionedSdkLoader>,
    private val processFactory: (List<String>) -> ProcessBuilder = { ProcessBuilder(it) }
) {

    val adbExecutable: File by lazy {
        versionedSdkLoader.get().adbExecutableProvider.get().asFile
    }

    /**
     * Checks whether the emulator with the given serial has successfully booted.
     *
     * Checks the boot_completed system property and the bootcomplete device property to check
     * whether the given device has successfully booted. If the device does not exist or is not
     * connected to adb, this method returns false.
     *
     * @param emulatorSerial the serial identifier for the emulator to be checked.
     * @param logger logs the method by which the boot was successfully verified.
     */
    fun isBootCompleted(emulatorSerial: String, logger: ILogger): Boolean {
        val bootCompleted = AtomicBoolean(false)
        getDeviceProperty("sys.boot_completed", emulatorSerial) {
            if (it.toIntOrNull() == 1) {
                logger.info("sys.boot_completed=1")
                bootCompleted.set(true)
            }
        }
        if (bootCompleted.get()) {
            return true
        }

        getDeviceProperty("dev.bootcomplete", emulatorSerial) {
            if (it.toIntOrNull() == 1) {
                logger.info("dev.bootcomplete=1")
                bootCompleted.set(true)
            }
        }
        return bootCompleted.get()
    }

    /**
     * Checks whether the package manager has started for the given device.
     */
    fun isPackageManagerStarted (emulatorSerial: String): Boolean {
        val result = AtomicBoolean(false)
        runAdbShell(emulatorSerial, listOf("/system/bin/pm", "path", "android")) {
            if (it.contains("package:")) {
                result.set(true)
            }
        }
        return result.get()
    }

    /**
     * Finds the emulator serial with the given idValue.
     *
     * This is done in a two step process:
     *
     * 1. Get all serials of the devices attached to adb.
     * 2. Query each serial for the id to check against the requested idValue.
     *
     * At least one serial is expected to have the given [idValue] associated with it. An exception
     * is thrown if the serial cannot be found.
     *
     * @param idValue the id value for the emulator to look for. This is the value passed in with
     * the "-id" flag to the emulator command, or the name of the avd launched if no value is
     * passed in.
     * @return The serial of the emulator that can be used for subsequent adb commands. See
     * [isBootCompleted], [killDevice].
     */
    fun findDeviceSerialWithId(idValue: String, logger: ILogger? = null): String {
        val serials = allSerials(logger)
        for (serial in serials) {
            if (getIdForSerial(serial) == idValue) {
                return serial
            }
        }
        error("Failed to find serial for device id: $idValue")
    }

    /**
     * Returns the list of all emulator serials whose id value starts with [idPrefix]
     *
     * This is done in a two step process:
     *
     * 1. Get all serials of the devices attached to adb.
     * 2. Query each serial for the id to check if it starts with [idPrefix]
     *
     * @param idPrefix the prefix to check each emulator id against.
     * @return a list of all serials that match the [idPrefix], an empty list if none are found.
     */
    fun findAllDeviceSerialsWithIdPrefix(idPrefix: String): List<String> =
        allSerials().filter { serial ->
            val id = getIdForSerial(serial)
            id != null && id.startsWith(idPrefix)
        }

    /**
     * Closes the given device via adb using the "emu kill" command.
     */
    fun killDevice(serial: String) {
        val killProcess = processFactory(
            listOf(
                adbExecutable.absolutePath,
                "-s",
                serial,
                "emu",
                "kill"
            )
        ).start()
        killProcess.waitFor()
    }

    private fun allSerials(logger: ILogger? = null): List<String> {
        val serials = mutableListOf<String>()
        val listDevicesProcess = processFactory(
            listOf(
                adbExecutable.absolutePath,
                "devices"
            )
        ).start()

        try {
            runWithTimeout(ADB_TIMEOUT_SEC) {
                GrabProcessOutput.grabProcessOutput(
                    listDevicesProcess,
                    GrabProcessOutput.Wait.WAIT_FOR_READERS,
                    object : GrabProcessOutput.IProcessOutput {
                        override fun out(line: String?) {
                            line ?: return
                            val trimmed = line.trim()
                            val values = trimmed.split("\\s+".toRegex())
                            // Looking for "<serial>    device"
                            if (values.size == 2) {
                                if (values[1] == "device") {
                                    logger?.info("Found device: ${values[0]}")
                                    serials.add(values[0])
                                } else {
                                    logger?.info(
                                        "Found inactive device: ${values[0]} status: ${values[1]}"
                                    )
                                }
                            }
                        }

                        override fun err(line: String?) {}
                    }
                )

                listDevicesProcess.waitFor()
            }
        } catch (e: TimeoutException) {
            listDevicesProcess.destroy()
            listDevicesProcess.waitFor()
            error("Adb device retrieval timed out. Failed to destroy emulator properly")
        }

        return serials
    }

    private fun getIdForSerial(serial: String): String? {
        var id: String? = null
        val idDetectionProcess = processFactory(
            listOf(
                adbExecutable.absolutePath,
                "-s",
                serial,
                "emu",
                "avd",
                "id"
            )
        ).start()

        try {
            runWithTimeout(ADB_TIMEOUT_SEC) {
                GrabProcessOutput.grabProcessOutput(
                    idDetectionProcess,
                    GrabProcessOutput.Wait.WAIT_FOR_READERS,
                    object : GrabProcessOutput.IProcessOutput {
                        override fun out(line: String?) {
                            line ?: return
                            val trimmed = line.trim()
                            if (trimmed.isNotEmpty() && trimmed != "OK") {
                                id = trimmed
                            }
                        }

                        override fun err(line: String?) {}
                    }
                )

                idDetectionProcess.waitFor()
            }
        } catch (e: TimeoutException) {
            // If we fail to retrieve the id, simply return null. This should not be an error.
            idDetectionProcess.destroy()
            idDetectionProcess.waitFor()
        }
        return id
    }

    private fun getDeviceProperty(
        propertyName: String,
        emulatorSerial: String,
        stdoutTextProcessor: (String)->Unit) {
        runAdbShell(
            emulatorSerial,
            listOf("getprop", propertyName),
            stdoutTextProcessor
        )
    }

    private fun runAdbShell(
        emulatorSerial: String,
        shellCommandArgs: List<String>,
        stdoutTextProcessor: (String)->Unit) {
        val getPropProcess = processFactory(
            listOf(
                adbExecutable.absolutePath,
                "-s",
                emulatorSerial,
                "shell",
            ) + shellCommandArgs
        ).start()

        GrabProcessOutput.grabProcessOutput(
            getPropProcess,
            GrabProcessOutput.Wait.WAIT_FOR_READERS,
            object : GrabProcessOutput.IProcessOutput {
                override fun out(line: String?) {
                    line ?: return
                    stdoutTextProcessor(line.trim())
                }

                override fun err(line: String?) {}
            }
        )
    }

    private fun <T> runWithTimeout(timeoutSeconds: Long, function: () -> T): T {
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit(function)
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            throw e
        } finally {
            executor.shutdown()
        }
    }
}
