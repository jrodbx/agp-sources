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

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.process.GradleJavaProcessExecutor
import com.android.build.gradle.internal.testing.utp.UtpDependency
import com.android.ide.common.process.LoggedProcessOutputHandler
import com.android.ide.common.process.ProcessInfoBuilder
import javax.inject.Inject
import org.gradle.api.logging.Logging
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkAction

/**
 * A work action that runs UTP in an external java process.
 */
abstract class RunUtpWorkAction : WorkAction<RunUtpWorkParameters> {

    private val logger = Logging.getLogger(RunUtpWorkAction::class.java)

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun execute() {
        val javaProcessExecutor = GradleJavaProcessExecutor {
            execOperations.javaexec(it)
        }
        val javaProcessInfo = ProcessInfoBuilder().apply {
            setClasspath(parameters.launcherJar.asFile.get().absolutePath)
            setMain(UtpDependency.LAUNCHER.mainClass)
            addArgs(parameters.coreJar.asFile.get().absolutePath)
            addArgs("--proto_config=${parameters.runnerConfig.asFile.get().absolutePath}")
            addArgs("--proto_server_config=${parameters.serverConfig.asFile.get().absolutePath}")
            addJvmArg("-Djava.util.logging.config.file=${
                parameters.loggingProperties.asFile.get().absolutePath}")
        }.createJavaProcess()

        javaProcessExecutor.execute(
            javaProcessInfo,
            LoggedProcessOutputHandler(LoggerWrapper(logger))).apply {
            rethrowFailure()
        }
    }
}
