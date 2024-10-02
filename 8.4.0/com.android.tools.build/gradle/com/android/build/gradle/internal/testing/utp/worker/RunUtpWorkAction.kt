/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.logging.Logging
import org.gradle.workers.WorkAction
import java.io.File
import javax.inject.Inject

/**
 * A work action that runs UTP in an external java process.
 */
abstract class RunUtpWorkAction @Inject constructor(
): WorkAction<RunUtpWorkParameters> {

    private val logger = Logging.getLogger(RunUtpWorkAction::class.java)

    @VisibleForTesting
    fun processFactory(): (List<String>) -> ProcessBuilder = { ProcessBuilder(it) }

    override fun execute() {
        val processBuilder = processFactory().invoke(
            listOfNotNull(
                "${parameters.jvm.asFile.get().absolutePath}",
                "-Djava.awt.headless=true",
                "-Djava.util.logging.config.file=${
                    parameters.loggingProperties.asFile.get().absolutePath}",
                "-Dfile.encoding=UTF-8",
                "-cp",
                parameters.launcherJar.joinToString(File.pathSeparator) { it.absolutePath },
                UtpDependency.LAUNCHER.mainClass,
                parameters.coreJar.joinToString(File.pathSeparator) { it.absolutePath },
                "--proto_config=${parameters.runnerConfig.asFile.get().absolutePath}",
                "--proto_server_config=${parameters.serverConfig.asFile.get().absolutePath}"
            )
        )
        val process = processBuilder.start()

        // Shutdown hook to close the process if gradle is closed/cancelled.
        val shutdownHook = Thread(process::destroyForcibly)

        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook)

            GrabProcessOutput.grabProcessOutput(
                process,
                GrabProcessOutput.Wait.ASYNC,
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
                }
            )

            process.waitFor()
        } finally {
            Runtime.getRuntime().removeShutdownHook(shutdownHook)
        }
    }
}
