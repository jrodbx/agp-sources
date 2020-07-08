/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.build.gradle.tasks

import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.cxx.gradle.generator.CxxMetadataGenerator
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons.getNativeBuildMiniConfigs
import com.android.build.gradle.internal.cxx.logging.IssueReporterLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel
import com.android.build.gradle.internal.cxx.gradle.generator.createCxxMetadataGenerator
import com.android.build.gradle.internal.cxx.model.jsonFile
import com.android.build.gradle.internal.cxx.process.createProcessOutputJunction
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.builder.errors.DefaultIssueReporter
import com.android.ide.common.process.ProcessInfoBuilder
import com.android.utils.tokenizeCommandLineToEscaped
import com.google.common.base.Joiner
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

/**
 * Task that takes set of JSON files of type NativeBuildConfigValue and does clean steps with them.
 *
 * It declares no inputs or outputs, as it's supposed to always run when invoked. Incrementality
 * is left to the underlying build system.
 */
abstract class ExternalNativeCleanTask @Inject constructor(private val ops: ExecOperations) : NonIncrementalTask() {
    @get:Internal
    abstract val sdkComponents: Property<SdkComponentsBuildService>
    private lateinit var configurationModel: CxxConfigurationModel

    override fun doTaskAction() {
        IssueReporterLoggingEnvironment(
            DefaultIssueReporter(LoggerWrapper(logger))
        ).use {
            infoln("starting clean")
            infoln("finding existing JSONs")
            val generator =
                createCxxMetadataGenerator(
                    sdkComponents.get(),
                    configurationModel
                )
            val existingJsons = mutableListOf<File>()
            for (abi in generator.abis) {
                if (abi.jsonFile.isFile) {
                    existingJsons.add(abi.jsonFile)
                } else {
                    // This is infoln instead of warnln because clean considers all possible
                    // ABIs while cleaning
                    infoln("Json file not found so contents couldn't be cleaned ${abi.jsonFile}")
                }
            }
            val configValueList = getNativeBuildMiniConfigs(existingJsons, null)
            val cleanCommands = mutableListOf<String>()
            val targetNames = mutableListOf<String>()

            for (config in configValueList) {
                cleanCommands.addAll(config.cleanCommands)
                val targets = mutableSetOf<String>()
                for (library in config.libraries.values) {
                    targets.add(String.format("%s %s", library.artifactName, library.abi))
                }
                targetNames.add(Joiner.on(",").join(targets))
            }
            infoln("about to execute %s clean commands", cleanCommands.size)
            executeProcessBatch(generator, cleanCommands, targetNames)
            infoln("clean complete")
        }
    }

    /**
     * Given a list of build commands, execute each. If there is a failure, processing is stopped at
     * that point.
     */
    private fun executeProcessBatch(
        generator: CxxMetadataGenerator,
        commands: List<String>,
        targetNames: List<String>) {
        for (commandIndex in commands.indices) {
            val command = commands[commandIndex]
            val target = targetNames[commandIndex]
            logger.lifecycle(String.format("Clean %s", target))
            val tokens = command.tokenizeCommandLineToEscaped()
            val processBuilder = ProcessInfoBuilder()
            processBuilder.setExecutable(tokens[0])
            for (i in 1 until tokens.size) {
                processBuilder.addArgs(tokens[i])
            }
            infoln("$processBuilder")
            createProcessOutputJunction(
                generator.variant.objFolder,
                "android_gradle_clean_$commandIndex",
                processBuilder,
                ""
            )
                .logStderrToInfo()
                .logStdoutToInfo()
                .execute(ops::exec)
        }
    }

    class CreationAction(
        private val configurationModel : CxxConfigurationModel,
        componentProperties: ComponentPropertiesImpl
    ) : VariantTaskCreationAction<ExternalNativeCleanTask, ComponentPropertiesImpl>(
        componentProperties
    ) {

        override val name
            get() = computeTaskName("externalNativeBuildClean")

        override val type
            get() = ExternalNativeCleanTask::class.java

        override fun configure(task: ExternalNativeCleanTask) {
            super.configure(task)
            task.configurationModel = configurationModel
            task.sdkComponents.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry)
            )
        }
    }
}