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
package com.android.build.gradle.tasks

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.cxx.configure.NativeLocationsBuildService
import com.android.build.gradle.internal.cxx.configure.rewriteWithLocations
import com.android.build.gradle.internal.cxx.gradle.generator.CxxMetadataGenerator
import com.android.build.gradle.internal.cxx.gradle.generator.createCxxMetadataGenerator
import com.android.build.gradle.internal.cxx.logging.IssueReporterLoggingEnvironment
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.metadataGenerationTimingFolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.UnsafeOutputsTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.errors.DefaultIssueReporter
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/** Task wrapper around [CxxMetadataGenerator].  */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.NATIVE, secondaryTaskCategories = [TaskCategory.METADATA])
abstract class ExternalNativeBuildJsonTask @Inject constructor(
        @get:Internal val ops: ExecOperations) :
        UnsafeOutputsTask("C/C++ Configuration is always run.") {

    @get:Internal
    abstract val sdkComponents: Property<SdkComponentsBuildService>

    @get:Internal
    abstract val nativeLocationsBuildService: Property<NativeLocationsBuildService>

    @get:Internal
    internal lateinit var abi: CxxAbiModel

    /**
     * Specify at least one output in order to avoid having the clean task run in parallel with
     * this task. See http://b/262059864 for more details.
     */
    @Optional
    @OutputDirectory
    fun getMetadataGenerationTimingFolder()  = abi.metadataGenerationTimingFolder

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    @get:InputFiles
    abstract val renderscriptSources: DirectoryProperty

    override fun doTaskAction() {
        IssueReporterLoggingEnvironment(
            DefaultIssueReporter(LoggerWrapper(logger)),
            analyticsService.get(),
            abi.variant
        ).use {
            val generator: CxxMetadataGenerator =
                createCxxMetadataGenerator(
                    abi = abi.rewriteWithLocations(nativeLocationsBuildService.get()),
                    analyticsService = analyticsService.get()
                )
            generator.configure(ops, false)
        }
    }
}

/**
 * Create a C/C++ configure task.
 */
fun createCxxConfigureTask(
    project: Project,
    globalConfig: GlobalTaskCreationConfig,
    creationConfig: VariantCreationConfig,
    abi: CxxAbiModel,
    name: String
) = object : GlobalTaskCreationAction<ExternalNativeBuildJsonTask>(globalConfig) {
    override val name = name
    override val type = ExternalNativeBuildJsonTask::class.java
    override fun configure(task: ExternalNativeBuildJsonTask) {
        super.configure(task)
        task.abi = abi
        task.variantName = abi.variant.variantName
        task.nativeLocationsBuildService.setDisallowChanges(getBuildService(project.gradle.sharedServices))
        if (creationConfig.renderscriptCreationConfig?.dslRenderscriptNdkModeEnabled == true) {
            creationConfig
                .artifacts
                .setTaskInputToFinalProduct(
                    InternalArtifactType.RENDERSCRIPT_SOURCE_OUTPUT_DIR,
                    task.renderscriptSources
                )
        }
    }
}
