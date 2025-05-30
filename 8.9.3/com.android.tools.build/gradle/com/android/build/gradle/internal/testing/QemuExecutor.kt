/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.utils.FileUtils
import com.android.utils.ILogger
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Timeout for qemu-img delete command.
 *
 * This is well above the time it takes to delete a snapshot entry. So if we manage to
 * hit this timeout it will clearly be the result of a failure in qemu-img
 */
private const val QEMU_TIMEOUT_SEC = 10L
private const val SNAPSHOTS_DIRECTORY = "snapshots"
private const val QEMU_IMG_EXE = "qemu-img"

/**
 * Executor for qemu commands.
 */
class QemuExecutor(
    private val emulatorDir: Provider<Directory>,
    private val processFactory: (List<String>) -> ProcessBuilder = { ProcessBuilder(it) }
) {

    val qemuImageExecutable: File
        get() = emulatorDir.get().asFile.resolve(QEMU_IMG_EXE)

    /**
     * Deletes the given snapshot for the given avd.
     *
     * Runs the qemu command:
     * `qemu-img snapshot -d $deviceDir <qcow-file>`
     * on every qcow file in the device directory, then deletes the snapshot folder.
     *
     * This is required to ensure the emulator remains in a valid state after snapshot deletiion.
     *
     * @param avdName name of the device
     * @param deviceDir the directory specifically for the given avd device.
     * @param snapshotName the name of the snapshot to be deleted
     */
    fun deleteSnapshot(
        avdName: String,
        deviceDir: File,
        snapshotName: String,
        logger: ILogger
    ) {

        logger.verbose("Deleting snapshot $snapshotName for $avdName.")

        var failedToDelete = false

        // Delete the snapshot metadata from qcow2 files.
        for (qcowFile in deviceDir.listFiles { pathname ->
                pathname.isFile() && pathname.name.endsWith(".qcow2")
        }) {

            val process = processFactory(
                listOf(
                    qemuImageExecutable.absolutePath,
                    "snapshot",
                    "-d",
                    snapshotName,
                    qcowFile.absolutePath
                )
            ).start()

            if (!process.waitFor(QEMU_TIMEOUT_SEC, TimeUnit.SECONDS) || process.exitValue() != 0 ) {

                process.destroyForcibly()

                // In the case of failure to update the qemu file, it will have to be overwritten
                // on the next snapshot write.
                logger.warning(
                    """
                        Failed to delete snapshot $snapshotName for device $avdName in qemu
                        snapshot file $qcowFile. qemu-img exit code: ${process.exitValue()}
                    """.trimIndent()
                )
                failedToDelete = true
            }
        }

        val snapshotDir = FileUtils.join(deviceDir, SNAPSHOTS_DIRECTORY, snapshotName)

        // Finally, delete snapshot files on machine.
        if (snapshotDir.exists() && snapshotDir.isDirectory) {
            try {
                logger.warning("Deleting unbootable snapshot for device: $avdName")
                FileUtils.deleteRecursivelyIfExists(snapshotDir)
            } catch (ioException: IOException) {
                logger.error(
                    ioException,
                    "Could not delete snapshot folder at location: ${snapshotDir.absolutePath}."
                )
                failedToDelete = true
            }
        }

        if (!failedToDelete) {
            logger.verbose("Successfully deleted snapshot $snapshotName for $avdName.")
        }
    }
}
