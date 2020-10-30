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
import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.api.variant.impl.ApplicationVariantPropertiesImpl
import com.android.build.gradle.internal.dsl.NdkOptions.DebugSymbolLevel
import com.android.build.gradle.internal.packaging.JarCreatorFactory
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.util.PatternSet
import java.io.File
import java.io.Serializable
import javax.inject.Inject

/**
 * Task that merges the .so.dbg or .so.sym native debug metadata files into a zip to be published in
 * the outputs folder.
 */
@CacheableTask
abstract class MergeNativeDebugMetadataTask : NonIncrementalTask() {

    @get:SkipWhenEmpty
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    override fun doTaskAction() {
        getWorkerFacadeWithWorkers().use {
            it.submit(
                MergeNativeDebugMetadataRunnable::class.java,
                MergeNativeDebugMetadataRunnable.Params(
                    inputFiles.files,
                    outputFile.get().asFile
                )
            )
        }
    }

    private class MergeNativeDebugMetadataRunnable @Inject constructor(
        val params: Params
    ) : Runnable {

        override fun run() {
            mergeFiles(params.inputFiles, params.outputFile)
        }

        class Params(val inputFiles: Collection<File>, val outputFile: File) : Serializable
    }

    companion object {

        @VisibleForTesting
        internal fun mergeFiles(inputFiles: Collection<File>, outputFile: File) {
            FileUtils.deleteIfExists(outputFile)
            JarCreatorFactory.make(jarFile = outputFile.toPath()).use { zipCreator ->
                inputFiles.forEach {
                    zipCreator.addFile("${it.parentFile.name}/${it.name}", it.toPath())
                }
            }
        }

        fun getNativeDebugMetadataFiles(
            componentProperties: ComponentPropertiesImpl
        ): FileCollection {
            val nativeDebugMetadataDirs = componentProperties.services.fileCollection()
            when (componentProperties.variantDslInfo.ndkConfig.debugSymbolLevelEnum) {
                DebugSymbolLevel.FULL -> {
                    nativeDebugMetadataDirs.from(
                        componentProperties.artifacts.get(
                            InternalArtifactType.NATIVE_DEBUG_METADATA
                        )
                    )
                    nativeDebugMetadataDirs.from(
                        componentProperties.variantDependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
                            AndroidArtifacts.ArtifactScope.PROJECT,
                            AndroidArtifacts.ArtifactType.REVERSE_METADATA_NATIVE_DEBUG_METADATA
                        )
                    )
                }
                DebugSymbolLevel.SYMBOL_TABLE -> {
                    nativeDebugMetadataDirs.from(
                        componentProperties.artifacts.get(
                            InternalArtifactType.NATIVE_SYMBOL_TABLES
                        )
                    )
                    nativeDebugMetadataDirs.from(
                        componentProperties.variantDependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
                            AndroidArtifacts.ArtifactScope.PROJECT,
                            AndroidArtifacts.ArtifactType.REVERSE_METADATA_NATIVE_SYMBOL_TABLES
                        )
                    )
                }
                DebugSymbolLevel.NONE -> { }
            }
            nativeDebugMetadataDirs.disallowChanges()
            return nativeDebugMetadataDirs.asFileTree.matching(patternSet)
        }

        private val patternSet = PatternSet().include("**/*$DOT_DBG").include("**/*$DOT_SYM")
    }

    class CreationAction(componentProperties: ApplicationVariantPropertiesImpl) :
        VariantTaskCreationAction<MergeNativeDebugMetadataTask, ApplicationVariantPropertiesImpl>(
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
