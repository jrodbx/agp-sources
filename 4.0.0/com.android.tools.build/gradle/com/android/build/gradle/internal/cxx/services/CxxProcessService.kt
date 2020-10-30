/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.services

import com.android.build.gradle.internal.cxx.model.CxxModuleModel
import com.android.build.gradle.internal.cxx.process.ProcessOutputJunction
import com.android.build.gradle.internal.process.GradleJavaProcessExecutor
import com.android.build.gradle.internal.process.GradleProcessExecutor
import com.android.ide.common.process.JavaProcessInfo
import com.android.ide.common.process.ProcessInfo
import com.android.ide.common.process.ProcessInfoBuilder
import com.android.ide.common.process.ProcessOutputHandler
import org.gradle.api.Action
import org.gradle.api.logging.Logging
import org.gradle.process.BaseExecSpec
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.gradle.process.JavaExecSpec
import java.io.File

/**
 * Creates a [ProcessOutputJunction] which is a builder-style class that composes multiple output
 * streams and invoke process with stderr and stdout sent to those streams. The kinds of stream:
 *
 * - combinedOutput is the interleaved combination of stderr and stdout. This is always gathered
 *   because it is used to return a BuildCommandException if the process failed.
 *
 * - stdout is used in the case that the caller wants a string for stdout. The main scenario for
 *   this is ndk-build -nB to gather the build plan to interpret.
 *
 * - stderr is always logged to info and stdout may be logged to info if the caller requests it.
 */
fun CxxModuleModel.createProcessOutputJunction(
    outputFolder: File,
    outputBaseName: String,
    process: ProcessInfoBuilder,
    logPrefix: String) =
    services[PROCESS_SERVICE_KEY].createProcessOutputJunction(
        outputFolder,
        outputBaseName,
        process,
        logPrefix)

/**
 * Create and register a [CxxProcessService] for creating [ProcessOutputJunction].
 */
internal fun createProcessJunctionService(
    services: CxxServiceRegistryBuilder) {
    services.registerFactory(PROCESS_SERVICE_KEY) {
        object : CxxProcessService {
            override fun createProcessOutputJunction(
                outputFolder: File,
                outputBaseName: String,
                process: ProcessInfoBuilder,
                logPrefix: String
            ): ProcessOutputJunction {
                return ProcessOutputJunction(
                    process,
                    outputFolder,
                    outputBaseName,
                    logPrefix,
                    { message -> Logging.getLogger(CxxProcessService::class.java).lifecycle(message) },
                    { processInfo: ProcessInfo, outputHandler: ProcessOutputHandler, baseExecOperation: (Action<in BaseExecSpec>) -> ExecResult ->
                        if (processInfo is JavaProcessInfo) {
                            @Suppress("UNCHECKED_CAST")
                            val javaExecOperation =
                                baseExecOperation as (Action<in JavaExecSpec>) -> ExecResult
                            GradleJavaProcessExecutor(javaExecOperation).execute(
                                processInfo,
                                outputHandler
                            )
                        } else {
                            @Suppress("UNCHECKED_CAST")
                            val execOperation =
                                baseExecOperation as (Action<in ExecSpec>) -> ExecResult
                            GradleProcessExecutor(execOperation).execute(
                                processInfo,
                                outputHandler
                            )
                        }
                    })
            }
        }
    }
}

/**
 * Private service key for process service. For use in retrieving this service from
 * [CxxServiceRegistry].
 */
private val PROCESS_SERVICE_KEY = object : CxxServiceKey<CxxProcessService> {
    override val type = CxxProcessService::class.java
}

/**
 * Private interface to access the process service via [CxxServiceRegistry].
 */
private interface CxxProcessService {
    fun createProcessOutputJunction(
        outputFolder: File,
        outputBaseName: String,
        process: ProcessInfoBuilder,
        logPrefix: String): ProcessOutputJunction
}
