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

import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryVariantScope
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalGlobalTask
import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.ide.common.resources.CopyToOutputDirectoryResourceCompilationService
import com.android.ide.common.workers.WorkerExecutorFacade
import org.gradle.api.attributes.Usage
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
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
@BuildAnalyzer(primaryTaskCategory = TaskCategory.ANDROID_RESOURCES, secondaryTaskCategories = [TaskCategory.MERGING, TaskCategory.FUSING])
abstract class FusedLibraryMergeResourcesTask : NonIncrementalGlobalTask() {

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
                resCompilerService = CopyToOutputDirectoryResourceCompilationService,
                incrementalMergedResources = incrementalMergedResources.get().asFile,
                mergedResources = mergedResources.get().asFile,
                resourceSets = resourceSets.files.toList(),
                minSdk = minSdk.get(),
                aaptWorkerFacade = aaptWorkerFacade,
                blameLogOutputFolder = blameLogOutputFolder.get().asFile,
                logger = logger)
    }

    class CreationAction(val creationConfig: FusedLibraryVariantScope) :
            TaskCreationAction<FusedLibraryMergeResourcesTask>() {

        override val name: String
            get() = "mergeResources"
        override val type: Class<FusedLibraryMergeResourcesTask>
            get() = FusedLibraryMergeResourcesTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<FusedLibraryMergeResourcesTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    FusedLibraryMergeResourcesTask::mergedResources
            ).on(FusedLibraryInternalArtifactType.MERGED_RES)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    FusedLibraryMergeResourcesTask::incrementalMergedResources
            ).on(FusedLibraryInternalArtifactType.INCREMENTAL_MERGED_RES)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    FusedLibraryMergeResourcesTask::blameLogOutputFolder
            ).on(FusedLibraryInternalArtifactType.MERGED_RES_BLAME_LOG)
        }

        override fun configure(task: FusedLibraryMergeResourcesTask) {

            task.projectFilepath.set(creationConfig.layout.projectDirectory.asFile.absolutePath)
            task.analyticsService.setDisallowChanges(
                    getBuildService(task.project.gradle.sharedServices)
            )
            task.minSdk.setDisallowChanges(creationConfig.extension.minSdk)
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
