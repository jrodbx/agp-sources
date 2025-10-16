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
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import java.io.File
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Launches multiple Unified Test Platform (UTP) test suites in parallel as external Java processes.
 *
 * @param javaExecFile The [File] pointing to the `java` executable.
 * @param loggingPropertiesFileList A list of [File]s for `java.util.logging` configurations.
 * Each file corresponds to a single UTP process.
 * @param utpLauncherJars The list of JARs to be used as the classpath for the UTP launcher.
 * @param utpCoreJars The list of UTP core JARs, passed as an argument to the launcher.
 * @param utpRunnerConfigFileList A list of [File]s for the UTP runner protobuf configurations.
 * Each file corresponds to a single UTP test suite execution.
 * @param logger The [Logger] instance to which UTP process output will be streamed.
 * @param processBuilderFactory A factory function to create the [ProcessBuilder], primarily for
 * testing.
 * @param executorServiceFactory A factory function to create the [ExecutorService] for running
 * tests in parallel, primarily for testing.
 */
class UtpRunner(
    private val javaExecFile: File,
    private val loggingPropertiesFileList: List<File>,
    private val utpLauncherJars: List<File>,
    private val utpCoreJars: List<File>,
    private val utpRunnerConfigFileList: List<File>,
    private val logger: Logger = Logging.getLogger(UtpRunner::class.java),
    private val processBuilderFactory: (List<String>) -> ProcessBuilder = { ProcessBuilder(it) },
    private val executorServiceFactory: () -> ExecutorService = Executors::newCachedThreadPool,
) {
    /**
     * Executes all the configured UTP test suites in parallel.
     *
     * This method submits each test suite to an [ExecutorService] and waits for all of them to
     * complete. If any of the test executions fail, it logs the failures and throws a final
     * [GradleException] to fail the build. If the Gradle build is cancelled, the executor is
     * shut down immediately.
     */
    fun execute() {
        val executorService = executorServiceFactory()
        try {
            val futures = utpRunnerConfigFileList.zip(loggingPropertiesFileList)
                .map { (runConfig, loggingPropertiesFile) ->
                    executorService.submit {
                        execute(runConfig, loggingPropertiesFile)
                    }
                }.toList()

            var hasExecutionFailures = false
            futures.forEach {
                try {
                    it.get()
                } catch (e: ExecutionException) {
                    logger.warn("Test Execution failed", e)
                    hasExecutionFailures = true
                }
            }

            if (hasExecutionFailures) {
                throw GradleException("Test Execution failed")
            }
        } finally {
            executorService.shutdownNow()
        }
    }

    /**
     * Executes a single UTP process for a given configuration.
     *
     * This method blocks until the external UTP process terminates. It streams the process output
     * to the logger. If the Gradle build is cancelled, the [InterruptedException] is caught,
     * the process is forcibly destroyed, and the exception is re-thrown to signal cancellation.
     *
     * @param utpRunnerConfigFile The runner configuration proto file for this specific UTP execution.
     * @param loggingPropertiesFile The logging properties file for this specific UTP execution.
     */
    private fun execute(utpRunnerConfigFile: File, loggingPropertiesFile: File) {
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
