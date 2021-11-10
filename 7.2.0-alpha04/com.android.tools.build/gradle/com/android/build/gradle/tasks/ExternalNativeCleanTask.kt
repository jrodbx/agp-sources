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
import com.android.build.gradle.internal.cxx.model.jsonFile
import com.android.build.gradle.internal.cxx.model.logNativeCleanToLifecycle
import com.android.build.gradle.internal.cxx.process.createProcessOutputJunction
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.builder.errors.DefaultIssueReporter
import com.android.ide.common.process.ProcessInfoBuilder
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject

/**
 * Task that takes set of JSON files of type NativeBuildConfigValue and does clean steps with them.
 *
 * It declares no inputs or outputs, as it's supposed to always run when invoked. Incrementality
 * is left to the underlying build system.
 */
@DisableCachingByDefault
abstract class ExternalNativeCleanTask @Inject constructor(private val ops: ExecOperations) : NonIncrementalTask() {
    @get:Internal
    abstract val sdkComponents: Property<SdkComponentsBuildService>
    @get:Internal
    internal lateinit var configurationModel: CxxConfigurationModel

    override fun doTaskAction() {
        IssueReporterLoggingEnvironment(
            DefaultIssueReporter(LoggerWrapper(logger)),
            analyticsService.get(),
            configurationModel.variant
        ).use {
            infoln("starting clean")
            infoln("finding existing JSONs")
            val existingJsonAbis = mutableListOf<CxxAbiModel>()
            val allAbis = configurationModel.activeAbis + configurationModel.unusedAbis
            // Include unused ABIs since changes to build.gradle may have changed the set of
            // ABIs that are built. We want to clean the old ones too.
            for (abi in allAbis) {
                if (abi.jsonFile.isFile) {
                    existingJsonAbis.add(abi)
                } else {
                    // This is infoln instead of warnln because clean considers all possible
                    // ABIs while cleaning
                    infoln("Json file not found so contents couldn't be cleaned ${abi.jsonFile}")
                }
            }
            val configValueList = getNativeBuildMiniConfigs(existingJsonAbis,null)
            val abiToLogFolder = allAbis
                    .associate { it.abi.tag to it.intermediatesParentFolder }
            val batch = configValueList.map { config ->
                val abiName = config.libraries.values.mapNotNull { it.abi }.distinct().single()
                CleanProcessInfo(
                    commands = config.cleanCommandsComponents,
                    logFolder = abiToLogFolder.getValue(abiName),
                    targets = config.libraries.values.joinToString { library ->
                        "${library.artifactName}-${library.abi}"
                    }
                )
            }
            infoln("about to execute %s clean command batches", batch.size)
            executeProcessBatch(batch)
            infoln("clean complete")
        }
    }

    /**
     * Information about a batch of clean commands to execute. There is one command for each
     * element of [commands] outer list. The inner list first contains the executable to run
     * then each subsequent command-line parameter for it.
     */
    private data class CleanProcessInfo(
        val logFolder : File,
        val commands : List<List<String>>,
        val targets : String
    )

    /**
     * Given a list of clean commands, execute each. If there is a failure, processing is stopped at
     * that point.
     */
    private fun executeProcessBatch(cleanProcessInfos: List<CleanProcessInfo>) {
        val logFullStdout = configurationModel.variant.logNativeCleanToLifecycle
        for (cleanProcessInfo in cleanProcessInfos) with(cleanProcessInfo) {
            for (commandIndex in commands.indices) {
                val number = if (commandIndex == 0) "" else " [$commandIndex]"
                val tokens = commands[commandIndex]
                logger.lifecycle("Clean $targets$number")
                val processBuilder = ProcessInfoBuilder()
                processBuilder.setExecutable(tokens[0])
                processBuilder.addArgs(tokens.drop(1))
                infoln("$processBuilder")
                createProcessOutputJunction(
                    logFolder.resolve("android_gradle_clean_command_$commandIndex.txt"),
                    logFolder.resolve("android_gradle_clean_stdout_$commandIndex.txt"),
                    logFolder.resolve("android_gradle_clean_stderr_$commandIndex.txt"),
                    processBuilder,
                    ""
                )
                    .logStderr()
                    .logStdout()
                    .logFullStdout(logFullStdout)
                    .execute(ops::exec)
            }
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
