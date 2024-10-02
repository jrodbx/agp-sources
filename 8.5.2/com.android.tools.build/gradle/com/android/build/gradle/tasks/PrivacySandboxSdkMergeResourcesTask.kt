/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.build.gradle.internal.aapt.WorkerExecutorResourceCompilationService
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.res.Aapt2FromMaven
import com.android.build.gradle.internal.services.Aapt2Input
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.internal.tasks.configureVariantProperties
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.ide.common.workers.WorkerExecutorFacade
import org.gradle.api.attributes.Usage
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider

/**
 * Manages Android resource merging for libraries dependencies of the fused library.
 *
 * This task only merges resources and does not handle more complex resource operations such as
 * png generation/crunching, compilation, pseudolocalization etc., as these operations are just
 * handled by the AGP MergeResources task.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.ANDROID_RESOURCES, secondaryTaskCategories = [TaskCategory.MERGING])
abstract class PrivacySandboxSdkMergeResourcesTask : NonIncrementalTask() {

    @get:OutputDirectory
    abstract val mergedResources: DirectoryProperty

    @get:OutputDirectory
    abstract val blameLogOutputFolder: DirectoryProperty

    // Not yet consumed, as incremental resource merging is not yet supported for fused libraries.
    @get:OutputDirectory
    @get:Optional
    abstract val incrementalMergedResources: DirectoryProperty

    @get:Internal
    abstract val projectFilepath: Property<String>

    @get:Input
    abstract val minSdk: Property<Int>

    @get:Internal
    abstract val analytics: Property<AnalyticsService>

    @get:Nested
    abstract val aapt2: Aapt2Input

    @get:Internal
    val aaptWorkerFacade: WorkerExecutorFacade
        get() = Workers.withGradleWorkers(
                projectFilepath.get(),
                path,
                workerExecutor,
                analyticsService
        )

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val resourceSets: ConfigurableFileCollection

    override fun doTaskAction() {
        mergeResourcesWithCompilationService(
                resCompilerService = WorkerExecutorResourceCompilationService(
                        projectPath,
                        path,
                        workerExecutor,
                        analyticsService,
                        aapt2
                ),
                incrementalMergedResources = incrementalMergedResources.get().asFile,
                mergedResources = mergedResources.get().asFile,
                resourceSets = resourceSets.files.toList(),
                minSdk = minSdk.get(),
                aaptWorkerFacade = aaptWorkerFacade,
                blameLogOutputFolder = blameLogOutputFolder.get().asFile,
                logger = logger)
    }

    class CreationAction(val creationConfig: PrivacySandboxSdkVariantScope) :
            TaskCreationAction<PrivacySandboxSdkMergeResourcesTask>() {

        override val name: String
            get() = "mergeAndCompileResources"
        override val type: Class<PrivacySandboxSdkMergeResourcesTask>
            get() = PrivacySandboxSdkMergeResourcesTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<PrivacySandboxSdkMergeResourcesTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    PrivacySandboxSdkMergeResourcesTask::mergedResources
            ).on(PrivacySandboxSdkInternalArtifactType.MERGED_RES)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    PrivacySandboxSdkMergeResourcesTask::incrementalMergedResources
            ).on(PrivacySandboxSdkInternalArtifactType.INCREMENTAL_MERGED_RES)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    PrivacySandboxSdkMergeResourcesTask::blameLogOutputFolder
            ).on(PrivacySandboxSdkInternalArtifactType.MERGED_RES_BLAME_LOG)
        }

        override fun configure(task: PrivacySandboxSdkMergeResourcesTask) {
            task.configureVariantProperties("", task.project.gradle.sharedServices)
            task.aapt2.let { aapt2Input ->
                aapt2Input.buildService.setDisallowChanges(
                        getBuildService(task.project.gradle.sharedServices)
                )
                aapt2Input.threadPoolBuildService.setDisallowChanges(
                        getBuildService(task.project.gradle.sharedServices)
                )
                val aapt2Bin =
                        Aapt2FromMaven.create(task.project) { System.getenv(it.propertyName) }
                aapt2Input.binaryDirectory.setFrom(aapt2Bin.aapt2Directory)
                aapt2Input.version.setDisallowChanges(aapt2Bin.version)
                aapt2Input.maxWorkerCount.setDisallowChanges(
                        task.project.gradle.startParameter.maxWorkerCount
                )
                aapt2Input.maxAapt2Daemons.setDisallowChanges(8)
            }
            task.projectFilepath.set(creationConfig.layout.projectDirectory.asFile.absolutePath)
            task.minSdk.setDisallowChanges(creationConfig.minSdkVersion.apiLevel)
            task.resourceSets.setFrom(
                    creationConfig.dependencies.getArtifactFileCollection(
                            Usage.JAVA_RUNTIME,
                            creationConfig.mergeSpec,
                            AndroidArtifacts.ArtifactType.ANDROID_RES
                    )
            )
        }
    }
}
