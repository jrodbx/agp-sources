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

import com.android.build.gradle.internal.testing.utp.UtpTestResultListenerServerRunner
import com.android.build.gradle.internal.testing.utp.createRunnerConfigProtoForLocalDevice
import com.google.common.io.Files
import org.gradle.api.provider.ProviderFactory
import org.gradle.workers.WorkAction
import javax.inject.Inject

/**
 * A work action that runs UTP in an external java process.
 */
abstract class RunUtpWorkAction : WorkAction<RunUtpWorkParameters> {

    @get:Inject
    abstract val provider: ProviderFactory

    override fun execute() {
        // This Gradle property key is hard coded in Android Studio.
        // We will remove it once Android Studio can consume test report using
        // the tooling api.
        val enableUtpTestReportingForAndroidStudio =
            provider.gradleProperty(
                "com.android.tools.utp.GradleAndroidProjectResolverExtension.enable"
            ).orNull?.toBoolean() ?: false

        UtpTestResultListenerServerRunner().use { server ->
            val utpRunConfigs = parameters.utpRunConfigs.get()

            val utpRunnerConfigFileList = utpRunConfigs.map {
                val utpRunConfigProto = createRunnerConfigProtoForLocalDevice(
                    it.deviceId.get(),
                    it.deviceSerialNumber.get(),
                    it.testData.get(),
                    it.targetApkConfigBundle.get(),
                    it.additionalInstallOptions.get(),
                    it.helperApks.toList(),
                    it.uninstallIncompatibleApks.get(),
                    parameters.utpDependencies.get(),
                    parameters.androidSdkDirectory.get().asFile.absolutePath,
                    parameters.adbExecutable.get().asFile.absolutePath,
                    parameters.aaptExecutable.get().asFile.absolutePath,
                    parameters.dexdumpExecutable.get().asFile.absolutePath,
                    it.outputDir.get().asFile,
                    createUtpTempDirectory("utpRunTemp"),
                    it.emulatorControlConfig.get(),
                    it.coverageOutputDir.get().asFile,
                    it.useOrchestrator.get(),
                    it.forceCompilation.get(),
                    it.additionalTestOutputDir.orNull?.asFile,
                    it.additionalTestOutputOnDeviceDir.orNull,
                    it.installApkTimeout.orNull,
                    it.extractedSdkApks.get().map { it.map { it.toPath() } },
                    it.uninstallApksAfterTest.get(),
                    it.reinstallIncompatibleApksBeforeTest.get(),
                    it.shardConfig.orNull,
                    server.metadata,
                )

                createUtpTempFile("runnerConfig", ".pb").also { file ->
                    file.writeBytes(utpRunConfigProto.toByteArray())
                }
            }

            val loggingPropertiesFileList = utpRunConfigs.map {
                createUtpTempFile("logging", "properties").also { file ->
                    Files.asCharSink(file, Charsets.UTF_8).write("""
                .level=INFO
                .handlers=java.util.logging.ConsoleHandler,java.util.logging.FileHandler
                java.util.logging.ConsoleHandler.level=${it.loggingLevel.get().name}
                java.util.logging.SimpleFormatter.format=%4${'$'}s: %5${'$'}s%n
                java.util.logging.FileHandler.level=INFO
                java.util.logging.FileHandler.pattern=${it.outputDir.get().asFile.invariantSeparatorsPath}/utp.%u.log
                java.util.logging.FileHandler.formatter=java.util.logging.SimpleFormatter
            """.trimIndent())
                }
            }

            val utpRunner = UtpRunner(
                parameters.jvm.asFile.get(),
                parameters.utpDependencies.get(),
                enableUtpTestReportingForAndroidStudio,
                server,
            )

            utpRunner.execute(
                utpRunnerConfigFileList,
                loggingPropertiesFileList,
                utpRunConfigs.map { it.deviceId.get() },
                utpRunConfigs.map { it.deviceName.get() },
                utpRunConfigs.map { it.deviceShardName.get() },
                parameters.projectPath.get(),
                parameters.variantName.get(),
                parameters.xmlTestReportOutputDirectory.asFile.get(),
                utpRunConfigs.map { it.utpResultProtoOutputFile.get().asFile },
            )
        }
    }
}
