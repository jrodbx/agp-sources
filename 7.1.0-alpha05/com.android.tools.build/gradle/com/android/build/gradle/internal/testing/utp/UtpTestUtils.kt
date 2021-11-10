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

package com.android.build.gradle.internal.testing.utp

import com.android.build.gradle.internal.dsl.TestOptions
import com.android.build.gradle.internal.testing.utp.worker.RunUtpWorkAction
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.google.common.io.Files
import java.io.File
import java.io.FileOutputStream
import org.gradle.workers.WorkQueue

/**
 * Runs the given runner config on the Unified Test Platform server.
 */
fun runUtpTestSuite(
    runnerConfigFile: File,
    configFactory: UtpConfigFactory,
    utpDependencies: UtpDependencies,
    workQueue: WorkQueue
) {
    val serverConfigProtoFile = File.createTempFile("serverConfig", ".pb").also { file ->
        FileOutputStream(file).use { writer ->
            configFactory.createServerConfigProto().writeTo(writer)
        }
    }
    val loggingPropertiesFile = File.createTempFile("logging", "properties").also { file ->
        Files.asCharSink(file, Charsets.UTF_8).write("""
                .level=WARNING
                .handlers=java.util.logging.ConsoleHandler
                java.util.logging.ConsoleHandler.level=WARNING
            """.trimIndent())
    }
    workQueue.submit(RunUtpWorkAction::class.java) { params ->
        params.launcherJar.set(utpDependencies.launcher.singleFile)
        params.coreJar.set(utpDependencies.core.singleFile)
        params.runnerConfig.set(runnerConfigFile)
        params.serverConfig.set(serverConfigProtoFile)
        params.loggingProperties.set(loggingPropertiesFile)
    }
}

fun shouldEnableUtp(
    projectOptions: ProjectOptions,
    testOptions: TestOptions?
): Boolean {
    return (projectOptions[BooleanOption.ANDROID_TEST_USES_UNIFIED_TEST_PLATFORM]
            || (testOptions != null && testOptions.emulatorSnapshots.enableForTestFailures))
}
