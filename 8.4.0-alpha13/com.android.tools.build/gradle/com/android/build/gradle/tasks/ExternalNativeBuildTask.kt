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
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.cxx.build.CxxBuilder
import com.android.build.gradle.internal.cxx.build.CxxRegularBuilder
import com.android.build.gradle.internal.cxx.build.CxxRepublishBuilder
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel
import com.android.build.gradle.internal.cxx.logging.IssueReporterLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxVariantModel
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.UnsafeOutputsTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.errors.DefaultIssueReporter
import com.android.utils.cxx.CxxDiagnosticCode.CONFIGURE_MORE_THAN_ONE_SO_FOLDER
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.process.ExecOperations
import javax.inject.Inject
import org.gradle.work.DisableCachingByDefault
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.TaskProvider

/**
 * Task that performs a C/C++ build action or refers to a build from a different task.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.NATIVE)
abstract class ExternalNativeBuildTask :
        UnsafeOutputsTask("External Native Build task is always run as incrementality is left to the external build system.") {

    @get:Internal
    internal lateinit var builder: CxxBuilder


    @get:Internal
    internal lateinit var variant: CxxVariantModel

    @get:OutputDirectory
    abstract val soFolder : DirectoryProperty

    @Inject
    protected abstract fun getExecOperations(): ExecOperations

    override fun doTaskAction() {
        recordTaskAction(analyticsService.get()) {
            IssueReporterLoggingEnvironment(
                DefaultIssueReporter(LoggerWrapper(logger)),
                analyticsService.get(),
                variant
            ).use {
                builder.build(getExecOperations())
            }
        }
    }
}

/**
 * Create a C/C++ build task just republishes build outputs from a prior build task.
 * This is used to publish build outputs to their legacy locations (from before configuration
 * folding).
 */
fun createRepublishCxxBuildTask(
    configurationModel : CxxConfigurationModel,
    creationConfig: VariantCreationConfig,
    name : String
) = object : VariantTaskCreationAction<ExternalNativeBuildTask, VariantCreationConfig>(creationConfig) {
    override val name = name
    override val type = ExternalNativeBuildTask::class.java
    override fun configure(task: ExternalNativeBuildTask) {
        super.configure(task)
        task.builder = CxxRepublishBuilder(configurationModel)
        task.variant = configurationModel.variant
        // We use all ABIs not just active ABIs to cover the case where active ABIs is
        // empty. This is a corner case but it does happen in legitimate scenarios.
        // See b/65323727
        val allAbis = (configurationModel.activeAbis + configurationModel.unusedAbis)
        val soParentFolders = allAbis
            .map { it.soRepublishFolder.parentFile }
            .distinct()
        if (soParentFolders.size != 1) {
            errorln(CONFIGURE_MORE_THAN_ONE_SO_FOLDER, "More than one SO folder: ${soParentFolders.joinToString { it.path }}")
        }
        task.soFolder.set(soParentFolders.single())
        task.soFolder.disallowChanges()
    }
}

/**
 * Create a C/C++ build task does actual build work. It may be referred to by build tasks created
 * by [createRepublishCxxBuildTask].
 */
fun createWorkingCxxBuildTask(
    coveredVariantConfigurations: List<VariantCreationConfig>,
    creationConfig: GlobalTaskCreationConfig,
    abi: CxxAbiModel,
    name: String
) = object : GlobalTaskCreationAction<ExternalNativeBuildTask>(creationConfig) {
    override val name = name
    override val type = ExternalNativeBuildTask::class.java
    override fun handleProvider(
        taskProvider: TaskProvider<ExternalNativeBuildTask>
    ) {
        super.handleProvider(taskProvider)
        for(variant in coveredVariantConfigurations) {
            val container = variant.artifacts.getArtifactContainer(InternalMultipleArtifactType.EXTERNAL_NATIVE_BUILD_LIBS)
            container.addInitialProvider(taskProvider, taskProvider.flatMap { it.soFolder })
        }
    }

    override fun configure(task: ExternalNativeBuildTask) {
        super.configure(task)
        task.builder = CxxRegularBuilder(abi)
        task.variantName = abi.variant.variantName
        task.variant = abi.variant
        task.soFolder.set(abi.soFolder)
    }
}
