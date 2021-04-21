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
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.ide.common.process.JavaProcessExecutor
import com.android.ide.common.process.LoggedProcessOutputHandler
import com.android.ide.common.process.ProcessInfoBuilder
import com.android.utils.ILogger
import com.google.common.io.Files
import com.google.protobuf.TextFormat
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader

/**
 * Runs the given runner config on the Unified Test Platform server.
 *
 * Returns the result of the UTP execution from the given output directory.
 */
internal fun runUtpTestSuite(
    runnerConfigFile: File,
    utpOutputDir: File,
    configFactory: UtpConfigFactory,
    utpDependencies: UtpDependencies,
    javaProcessExecutor: JavaProcessExecutor,
    logger: ILogger
): TestSuiteResultProto.TestSuiteResult {
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
    val javaProcessInfo = ProcessInfoBuilder().apply {
        setClasspath(utpDependencies.launcher.singleFile.absolutePath)
        setMain(UtpDependency.LAUNCHER.mainClass)
        addArgs(utpDependencies.core.singleFile.absolutePath)
        addArgs("--proto_config=${runnerConfigFile.absolutePath}")
        addArgs("--proto_server_config=${serverConfigProtoFile.absolutePath}")
        addJvmArg("-Djava.util.logging.config.file=${loggingPropertiesFile.absolutePath}")
    }.createJavaProcess()

    javaProcessExecutor.execute(javaProcessInfo, LoggedProcessOutputHandler(logger)).apply {
        rethrowFailure()
    }

    return getResultsProto(utpOutputDir)
}

/**
 * Retrieves a test suite result proto from the Unified Test Platform's output directory.
 */
internal fun getResultsProto(outputDir: File): TestSuiteResultProto.TestSuiteResult {
    val testResultInAsciiProto = File(outputDir, TEST_RESULT_OUTPUT_FILE_NAME)
    check(testResultInAsciiProto.exists())
    return TestSuiteResultProto.TestSuiteResult.newBuilder().apply {
        TextFormat.merge(
            InputStreamReader(FileInputStream(testResultInAsciiProto)),
            this)
    }.build()
}

fun shouldEnableUtp(
    projectOptions: ProjectOptions,
    testOptions: TestOptions?
): Boolean {
    return (projectOptions[BooleanOption.ANDROID_TEST_USES_UNIFIED_TEST_PLATFORM]
            || (testOptions != null && testOptions.emulatorSnapshots.enableForTestFailures))
}
