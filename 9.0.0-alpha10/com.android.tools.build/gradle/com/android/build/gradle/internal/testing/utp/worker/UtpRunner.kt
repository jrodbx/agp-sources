/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.gradle.internal.testing.utp.worker

import com.android.build.gradle.internal.testing.utp.UtpDependency
import com.android.utils.GrabProcessOutput
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import java.io.File

/**
 * Launches the Unified Test Platform (UTP) as an external Java process.
 *
 * @param javaExecFile The [File] pointing to the `java` executable.
 * @param loggingPropertiesFile A [File] pointing to the `java.util.logging` configuration file.
 * @param utpLauncherJars The list of JARs to be used as the classpath for the UTP launcher.
 * @param utpCoreJars The list of UTP core JARs, passed as an argument to the launcher.
 * @param utpRunnerConfigFile The [File] for the UTP runner protobuf configuration.
 * @param utpServerConfigFile The [File] for the UTP server protobuf configuration.
 * @param logger The [Logger] instance to which UTP process output will be streamed.
 * @param processBuilderFactory A factory function to create the [ProcessBuilder], primarily for
 * testing.
 */
class UtpRunner(
    private val javaExecFile: File,
    private val loggingPropertiesFile: File,
    private val utpLauncherJars: List<File>,
    private val utpCoreJars: List<File>,
    private val utpRunnerConfigFile: File,
    private val utpServerConfigFile: File,
    private val logger: Logger = Logging.getLogger(UtpRunner::class.java),
    private val processBuilderFactory: (List<String>) -> ProcessBuilder = { ProcessBuilder(it) },
) {
    /**
     * Executes the UTP process.
     *
     * This method blocks until the external UTP process terminates. If the Gradle build
     * is cancelled, the [InterruptedException] is caught, the process is forcibly
     * destroyed, and the exception is re-thrown to signal cancellation to Gradle.
     */
    fun execute() {
        val processBuilder = processBuilderFactory(
            listOf(
                javaExecFile.absolutePath,
                "-Djava.awt.headless=true",
                "-Djava.util.logging.config.file=${loggingPropertiesFile.absolutePath}",
                "-Dfile.encoding=UTF-8",
                "-cp",
                utpLauncherJars.joinToString(File.pathSeparator) { it.absolutePath },
                UtpDependency.LAUNCHER.mainClass,
                utpCoreJars.joinToString(File.pathSeparator) { it.absolutePath },
                "--proto_config=${utpRunnerConfigFile.absolutePath}",
                "--proto_server_config=${utpServerConfigFile.absolutePath}",
            )
        )
        val process = processBuilder.start()

        // Shutdown hook to close the process if gradle is closed/cancelled.
        val shutdownHook = Thread(process::destroyForcibly)

        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook)

            try {
                GrabProcessOutput.grabProcessOutput(
                    process,
                    GrabProcessOutput.Wait.WAIT_FOR_READERS,
                    object : GrabProcessOutput.IProcessOutput {
                        override fun out(line: String?) {
                            if (!line.isNullOrBlank()) {
                                logger.info(line)
                            }
                        }

                        override fun err(line: String?) {
                            if (!line.isNullOrBlank()) {
                                logger.info(line)
                            }
                        }
                    }, null, null
                )
            } catch (e: InterruptedException) {
                process.destroyForcibly()
                process.waitFor()

                // Re-throw the exception so Gradle knows the work was cancelled.
                throw e
            }
        } finally {
            Runtime.getRuntime().removeShutdownHook(shutdownHook)
        }
    }
}
