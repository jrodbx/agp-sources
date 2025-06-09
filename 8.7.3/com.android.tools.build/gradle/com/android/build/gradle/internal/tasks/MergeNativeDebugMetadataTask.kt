/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.SdkConstants.DOT_DBG
import com.android.SdkConstants.DOT_SYM
import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.MultipleArtifact
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.dsl.NdkOptions.DebugSymbolLevel
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.packaging.JarFlinger
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.util.PatternSet
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Task that merges the .so.dbg or .so.sym native debug metadata files into a zip to be published in
 * the outputs folder.
 *
 * Caching disabled by default for this task because the task does very little work.
 * The task moves files from Inputs, unchanged, into a Zip file.
 * Calculating cache hit/miss and fetching results is likely more expensive than
 * simply executing the task.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.NATIVE, secondaryTaskCategories = [TaskCategory.METADATA, TaskCategory.MERGING])
abstract class MergeNativeDebugMetadataTask : NonIncrementalTask() {

    @get:SkipWhenEmpty
    @get:IgnoreEmptyDirectories
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(
            MergeNativeDebugMetadataWorkAction::class.java
        ) {
            it.initializeFromAndroidVariantTask(this)
            it.inputFiles.from(inputFiles)
            it.outputFile.set(outputFile)
        }
    }

    abstract class MergeNativeDebugMetadataWorkAction :
        ProfileAwareWorkAction<MergeNativeDebugMetadataWorkAction.Parameters>() {

        override fun run() {
            mergeFiles(parameters.inputFiles.files, parameters.outputFile.asFile.get())
        }

        abstract class Parameters : ProfileAwareWorkAction.Parameters() {
            abstract val inputFiles: ConfigurableFileCollection
            abstract val outputFile: RegularFileProperty
        }
    }

    companion object {

        @VisibleForTesting
        internal fun mergeFiles(inputFiles: Collection<File>, outputFile: File) {
            FileUtils.deleteIfExists(outputFile)
            JarFlinger(outputFile.toPath(), null).use { zipCreator ->
                inputFiles.forEach {
                    zipCreator.addFile("${it.parentFile.name}/${it.name}", it.toPath())
                }
            }
        }

        private fun collectDebugSymbols(
                variant: ApplicationCreationConfig,
                nativeDebugMetadataDirs: ConfigurableFileCollection,
                extractedDebugSymbolsArtifact: Artifact.Single<Directory>,
                externalDebugSymbolArtifacts: Artifact.Multiple<Directory>,
                reverseMetadataDebugSymbolArtifactType: AndroidArtifacts.ArtifactType
        ) {
            nativeDebugMetadataDirs.from(
                    variant.artifacts.get(
                            extractedDebugSymbolsArtifact
                    )
            )
            nativeDebugMetadataDirs.from(
                    variant.artifacts
                            .getAll(externalDebugSymbolArtifacts)
            )
            nativeDebugMetadataDirs.from(
                    variant.variantDependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
                            AndroidArtifacts.ArtifactScope.PROJECT,
                            reverseMetadataDebugSymbolArtifactType
                    )
            )
        }

        fun getNativeDebugMetadataFiles(
            variant: ApplicationCreationConfig
        ): FileCollection {
            val nativeDebugMetadataDirs = variant.services.fileCollection()
            when (variant.nativeBuildCreationConfig?.nativeDebugSymbolLevel) {
                DebugSymbolLevel.FULL -> {
                    collectDebugSymbols(
                            variant,
                            nativeDebugMetadataDirs,
                            InternalArtifactType.NATIVE_DEBUG_METADATA,
                            MultipleArtifact.NATIVE_DEBUG_METADATA,
                            AndroidArtifacts.ArtifactType.REVERSE_METADATA_NATIVE_DEBUG_METADATA
                        )
                }
                DebugSymbolLevel.SYMBOL_TABLE -> {
                    collectDebugSymbols(
                            variant,
                            nativeDebugMetadataDirs,
                            InternalArtifactType.NATIVE_SYMBOL_TABLES,
                            MultipleArtifact.NATIVE_SYMBOL_TABLES,
                            AndroidArtifacts.ArtifactType.REVERSE_METADATA_NATIVE_SYMBOL_TABLES
                    )
                }
                DebugSymbolLevel.NONE -> { }
                null -> { }
            }
            nativeDebugMetadataDirs.disallowChanges()
            return nativeDebugMetadataDirs.asFileTree.matching(patternSet)
        }

        private val patternSet = PatternSet().include("**/*$DOT_DBG").include("**/*$DOT_SYM")
    }

    class CreationAction(componentProperties: ApplicationCreationConfig) :
        VariantTaskCreationAction<MergeNativeDebugMetadataTask, ApplicationCreationConfig>(
            componentProperties
        ) {
        override val name: String
            get() = computeTaskName("merge", "NativeDebugMetadata")

        override val type: Class<MergeNativeDebugMetadataTask>
            get() = MergeNativeDebugMetadataTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<MergeNativeDebugMetadataTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                MergeNativeDebugMetadataTask::outputFile
            ).withName("native-debug-symbols.zip")
                .on(InternalArtifactType.MERGED_NATIVE_DEBUG_METADATA)
        }

        override fun configure(
            task: MergeNativeDebugMetadataTask
        ) {
            super.configure(task)
            task.inputFiles.fromDisallowChanges(getNativeDebugMetadataFiles(creationConfig))
        }
    }
}
