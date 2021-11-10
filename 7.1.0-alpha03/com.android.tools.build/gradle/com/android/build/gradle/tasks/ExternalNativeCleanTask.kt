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

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons.getNativeBuildMiniConfigs
import com.android.build.gradle.internal.cxx.logging.IssueReporterLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.ifLogNativeCleanToLifecycle
import com.android.build.gradle.internal.cxx.model.jsonFile
import com.android.build.gradle.internal.cxx.process.createProcessOutputJunction
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.builder.errors.DefaultIssueReporter
import com.android.ide.common.process.ProcessInfoBuilder
import com.google.common.base.Joiner
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.process.ExecOperations
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
    @get:Internal
    internal lateinit var configurationModel: CxxConfigurationModel

    override fun doTaskAction() {
        IssueReporterLoggingEnvironment(
            DefaultIssueReporter(LoggerWrapper(logger)),
            analyticsService.get(),
            configurationModel
        ).use {
            infoln("starting clean")
            infoln("finding existing JSONs")
            val existingJsonAbis = mutableListOf<CxxAbiModel>()
            // Include unused ABIs since changes to build.gradle may have changed the set of
            // ABIs that are built. We want to clean the old ones too.
            for (abi in (configurationModel.activeAbis + configurationModel.unusedAbis)) {
                if (abi.jsonFile.isFile) {
                    existingJsonAbis.add(abi)
                } else {
                    // This is infoln instead of warnln because clean considers all possible
                    // ABIs while cleaning
                    infoln("Json file not found so contents couldn't be cleaned ${abi.jsonFile}")
                }
            }
            val configValueList = getNativeBuildMiniConfigs(
                    existingJsonAbis,
                    null)
            val cleanCommands = mutableListOf<List<String>>()
            val targetNames = mutableListOf<String>()

            for (config in configValueList) {
                cleanCommands.addAll(config.cleanCommandsComponents)
                val targets = mutableSetOf<String>()
                for (library in config.libraries.values) {
                    targets.add(String.format("%s %s", library.artifactName, library.abi))
                }
                targetNames.add(Joiner.on(",").join(targets))
            }
            infoln("about to execute %s clean commands", cleanCommands.size)
            executeProcessBatch(cleanCommands, targetNames)
            infoln("clean complete")
        }
    }

    /**
     * Given a list of build commands, execute each. If there is a failure, processing is stopped at
     * that point.
     */
    private fun executeProcessBatch(
            commands: List<List<String>>,
            targetNames: List<String>
    ) {
        for (commandIndex in commands.indices) {
            val tokens = commands[commandIndex]
            val target = targetNames[commandIndex]
            logger.lifecycle(String.format("Clean %s", target))
            val processBuilder = ProcessInfoBuilder()
            processBuilder.setExecutable(tokens[0])
            for (i in 1 until tokens.size) {
                processBuilder.addArgs(tokens[i])
            }
            infoln("$processBuilder")
            createProcessOutputJunction(
                    configurationModel.variant.soFolder.resolve("android_gradle_clean_command_$commandIndex.txt"),
                    configurationModel.variant.soFolder.resolve("android_gradle_clean_stdout_$commandIndex.txt"),
                    configurationModel.variant.soFolder.resolve("android_gradle_clean_stderr_$commandIndex.txt"),
                    processBuilder,
                    ""
            )
                    .logStderr()
                    .logStdout()
                    .logFullStdout(configurationModel.variant.ifLogNativeCleanToLifecycle { true } ?: false)
                    .execute(ops::exec)
        }
    }
}

/**
 * Create a C/C++ clean task.
 */
fun createVariantCxxCleanTask(
        configurationModel : CxxConfigurationModel,
        creationConfig: VariantCreationConfig
) = object : VariantTaskCreationAction<ExternalNativeCleanTask, VariantCreationConfig>(creationConfig) {
    override val name = computeTaskName("externalNativeBuildClean")
    override val type = ExternalNativeCleanTask::class.java

    override fun configure(task: ExternalNativeCleanTask) {
        super.configure(task)
        task.configurationModel = configurationModel
        task.sdkComponents.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry)
        )
    }
}
