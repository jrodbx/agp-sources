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
package com.android.build.gradle.internal.tasks

import com.android.build.api.artifact.impl.InternalScopedArtifacts
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.internal.cxx.configure.trySymlinkNdk
import com.android.build.gradle.internal.packaging.ParsedPackagingOptions
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.builder.files.KeyedFileCache
import com.android.builder.files.SerializableInputChanges
import com.android.builder.merge.IncrementalFileMergerInput
import com.android.utils.FileUtils
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import java.io.File
import java.io.Serializable

/**
 * [ProfileAwareWorkAction] to merge java resources
 */
abstract class MergeJavaResWorkAction : ProfileAwareWorkAction<MergeJavaResWorkAction.Params>() {
    override fun run() {
        run(isIncremental = parameters.incremental.get(), changedInputs = parameters.changedInputs.orNull)
    }
    private fun run(isIncremental: Boolean, changedInputs: SerializableInputChanges?) {
        val outputFile = parameters.outputFile.get().asFile
        val incrementalStateFile = parameters.incrementalStateFile.asFile.get()
        if (!isIncremental) {
            FileUtils.deleteIfExists(outputFile)
            FileUtils.deleteIfExists(incrementalStateFile)
        }
        val cacheDir = parameters.cacheDir.asFile.get().also { FileUtils.mkdirs(it) }

        val zipCache = KeyedFileCache(cacheDir, KeyedFileCache::fileNameKey)
        val cacheUpdates = mutableListOf<Runnable>()
        val inputDataList = mutableListOf<InputData>()

        inputDataList.addAll(parameters.projectJavaRes.map {
            InputData(
                it,
                determinePriority(ScopedArtifacts.Scope.PROJECT),
                "project(\"${parameters.projectPath.get()}\") - This project"
            )
        })

        inputDataList.addAll(parameters.subProjectJavaRes.get().map {
            InputData(
                it.input,
                determinePriority(InternalScopedArtifacts.InternalScope.SUB_PROJECTS),
                it.source
            )
        })
        inputDataList.addAll(parameters.externalLibJavaRes.get().map {
            InputData(
                it.input,
                determinePriority(InternalScopedArtifacts.InternalScope.EXTERNAL_LIBS),
                it.source
            )
        })
        inputDataList.addAll(parameters.featureJavaRes.get().map {
            InputData(
                it.input,
                determinePriority(InternalScopedArtifacts.InternalScope.FEATURES),
                it.source
            )
        })

        val inputs = try {
            toInputs(
                inputDataList,
                changedInputs,
                zipCache,
                cacheUpdates,
                !isIncremental
            )
        } catch (e: IllegalStateException) {
            if (isIncremental) {
                Logging.getLogger(MergeJavaResWorkAction::class.java)
                        .debug("Incremental loading failed, fall back to non-incremental ", e)
                return run(isIncremental = false, changedInputs = null)
            }
            throw IllegalStateException("Failed to convert inputs to the task non-incremental run ", e)
        }

        val mergeJavaResDelegate =
            MergeJavaResourcesDelegate(
                inputs,
                outputFile,
                ParsedPackagingOptions(
                    parameters.excludes.get(),
                    parameters.pickFirsts.get(),
                    parameters.merges.get()
                ),
                incrementalStateFile,
                isIncremental,
                parameters.noCompress.get()
            )
        mergeJavaResDelegate.run()
        cacheUpdates.forEach(Runnable::run)
    }

    /**
     * Data Structure to associate a file and it's provenance to the worker.
     */
    data class SourcedInput(
        val input: File,
        val source: String,
    ): Serializable

    abstract class Params : Parameters() {
        abstract val projectJavaRes: ConfigurableFileCollection
        abstract val subProjectJavaRes: ListProperty<SourcedInput>
        abstract val externalLibJavaRes: ListProperty<SourcedInput>
        abstract val featureJavaRes: ListProperty<SourcedInput>
        abstract val outputFile: RegularFileProperty
        abstract val incrementalStateFile: RegularFileProperty
        abstract val incremental: Property<Boolean>
        abstract val cacheDir: DirectoryProperty
        abstract val changedInputs: Property<SerializableInputChanges>
        abstract val noCompress: ListProperty<String>
        abstract val excludes: SetProperty<String>
        abstract val pickFirsts: SetProperty<String>
        abstract val merges: SetProperty<String>
    }
}
