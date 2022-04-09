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

import com.android.SdkConstants
import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.ArtifactKind
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType.MERGED_AAR_METADATA
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType.MERGED_AIDL
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType.MERGED_ASSETS
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType.MERGED_JNI
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType.MERGED_NAVIGATION_JSON
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType.MERGED_PREFAB_PACKAGE
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType.MERGED_PREFAB_PACKAGE_CONFIGURATION
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType.MERGED_RENDERSCRIPT_HEADERS
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryVariantScope
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.configureVariantProperties
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.utils.usLocaleCapitalize
import org.gradle.api.attributes.Usage
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File

/**
 * Responsible for merging multiple library artifacts of the same artifact type into a single
 * artifact in the fused library aar, for future consumption. The task is intended to be configured
 * for each artifact type. Eventually these operations can be migrated to artifact transforms
 * once this feature is supported in the fused library plugin.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.MISC, secondaryTaskCategories = [TaskCategory.MERGING, TaskCategory.FUSING])
abstract class FusedLibraryMergeArtifactTask : NonIncrementalTask() {

    @get:Input
    abstract val artifactType: Property<ArtifactType>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val artifactFiles: ConfigurableFileCollection

    @get:OutputDirectory
    @get:Optional
    abstract val outputDir: DirectoryProperty

    @get:OutputFile
    @get:Optional
    abstract val outputFile: RegularFileProperty

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(FusedLibraryMergeArtifactWorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.artifactType.set(artifactType)
            it.input.setFrom(artifactFiles)
            it.output.setDisallowChanges(
                    if (outputDir.isPresent) outputDir else outputFile
            )
        }
    }

    abstract class FusedLibraryMergeArtifactParams : ProfileAwareWorkAction.Parameters() {
        abstract val artifactType: Property<ArtifactType>
        abstract val input: ConfigurableFileCollection
        abstract val output: Property<FileSystemLocation>
    }

    abstract class FusedLibraryMergeArtifactWorkAction
        : ProfileAwareWorkAction<FusedLibraryMergeArtifactParams>() {

        override fun run() {
            with(parameters!!) {
                val inputFiles = input.files.toList()
                when (val currentArtifactType = artifactType.get()) {
                    ArtifactType.AAR_METADATA -> {
                        writeMergedMetadata(inputFiles, output.get().asFile)
                    }
                    ArtifactType.ASSETS -> {
                        val aarOutputAssetsOutputDir = output.get().asFile
                        for (dir in inputFiles.reversed()) {
                            dir.copyRecursively(aarOutputAssetsOutputDir, overwrite = true)
                        }
                    }
                    ArtifactType.JNI -> {
                        val aarOutputJniOutputDir =
                                File(output.get().asFile, SdkConstants.FD_JNI)
                        copyFilesToDirRecursivelyWithOverriding(inputFiles, aarOutputJniOutputDir) {
                            it.toString().substringAfterLast("${File.separator}jni${File.separator}")
                        }
                    }
                    else -> {
                        val supportedArtifacts = mergeArtifactMap.map { it.first }
                        if (currentArtifactType !in supportedArtifacts) {
                            error("${currentArtifactType.type} is not a supported artifact type for " +
                                    "fused library artifact republishing.")
                        }
                    }
                }
            }
        }
    }

    class CreateActionFusedLibrary(val creationConfig: FusedLibraryVariantScope,
            private val androidArtifactType: ArtifactType,
            private val internalArtifactType: Artifact.Single<*>) :
        TaskCreationAction<FusedLibraryMergeArtifactTask>() {

        override val name: String
            get() = "mergingArtifact${androidArtifactType.name.usLocaleCapitalize()}"
        override val type: Class<FusedLibraryMergeArtifactTask>
            get() = FusedLibraryMergeArtifactTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<FusedLibraryMergeArtifactTask>) {
            super.handleProvider(taskProvider)

            when (internalArtifactType.kind) {
                ArtifactKind.DIRECTORY ->
                    creationConfig.artifacts.setInitialProvider(
                            taskProvider,
                            FusedLibraryMergeArtifactTask::outputDir
                    ).withName(androidArtifactType.name.lowercase())
                            .on(internalArtifactType as Artifact.Single<Directory>)
                ArtifactKind.FILE ->
                    creationConfig.artifacts.setInitialProvider(
                            taskProvider,
                            FusedLibraryMergeArtifactTask::outputFile
                    ).withName(androidArtifactType.name.lowercase())
                            .on(internalArtifactType as Artifact.Single<RegularFile>)
            }
        }

        override fun configure(task: FusedLibraryMergeArtifactTask) {
            task.artifactFiles.setFrom(
                    creationConfig.dependencies.getArtifactFileCollection(
                            Usage.JAVA_RUNTIME,
                            creationConfig.mergeSpec,
                            androidArtifactType
                    )
            )
            task.artifactType.setDisallowChanges(androidArtifactType)
            task.configureVariantProperties("", task.project.gradle.sharedServices)
        }

    }

    class CreateActionPrivacySandboxSdk(val creationConfig: PrivacySandboxSdkVariantScope,
            private val androidArtifactType: ArtifactType,
            private val internalArtifactType: Artifact.Single<*>) :
            TaskCreationAction<FusedLibraryMergeArtifactTask>() {

        override val name: String
            get() = "mergingArtifact${androidArtifactType.name.usLocaleCapitalize()}"
        override val type: Class<FusedLibraryMergeArtifactTask>
            get() = FusedLibraryMergeArtifactTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<FusedLibraryMergeArtifactTask>) {
            super.handleProvider(taskProvider)

            when (internalArtifactType.kind) {
                ArtifactKind.DIRECTORY ->
                    creationConfig.artifacts.setInitialProvider(
                            taskProvider,
                            FusedLibraryMergeArtifactTask::outputDir
                    ).withName(androidArtifactType.name.lowercase())
                            .on(internalArtifactType as Artifact.Single<Directory>)
                ArtifactKind.FILE ->
                    creationConfig.artifacts.setInitialProvider(
                            taskProvider,
                            FusedLibraryMergeArtifactTask::outputFile
                    ).withName(androidArtifactType.name.lowercase())
                            .on(internalArtifactType as Artifact.Single<RegularFile>)
            }
        }

        override fun configure(task: FusedLibraryMergeArtifactTask) {
            task.artifactFiles.setFrom(
                    creationConfig.dependencies.getArtifactFileCollection(
                            Usage.JAVA_RUNTIME,
                            creationConfig.mergeSpec,
                            androidArtifactType
                    )
            )
            task.artifactType.setDisallowChanges(androidArtifactType)
            task.configureVariantProperties("", task.project.gradle.sharedServices)
        }

    }

    companion object {

        private val mergeArtifactMap: List<Pair<ArtifactType, Artifact.Single<*>>> =
                listOf(
                        ArtifactType.AIDL to MERGED_AIDL,
                        ArtifactType.RENDERSCRIPT to MERGED_RENDERSCRIPT_HEADERS,
                        ArtifactType.PREFAB_PACKAGE to MERGED_PREFAB_PACKAGE,
                        ArtifactType.PREFAB_PACKAGE_CONFIGURATION to MERGED_PREFAB_PACKAGE_CONFIGURATION,
                        ArtifactType.ASSETS to MERGED_ASSETS,
                        ArtifactType.JNI to MERGED_JNI,
                        ArtifactType.NAVIGATION_JSON to MERGED_NAVIGATION_JSON,
                        ArtifactType.AAR_METADATA to MERGED_AAR_METADATA,
                )
        fun getCreationActions(creationConfig: FusedLibraryVariantScope) :
                List<CreateActionFusedLibrary> {
            return mergeArtifactMap.map { CreateActionFusedLibrary(creationConfig, it.first, it.second) }
        }
        fun getCreationActions(creationConfig: PrivacySandboxSdkVariantScope) :
                List<CreateActionPrivacySandboxSdk> {
            return mergeArtifactMap.map { CreateActionPrivacySandboxSdk(creationConfig, it.first, it.second) }
        }
    }
}
