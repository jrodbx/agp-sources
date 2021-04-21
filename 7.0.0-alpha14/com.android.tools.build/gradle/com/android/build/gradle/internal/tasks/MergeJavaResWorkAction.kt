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

import com.android.build.api.transform.QualifiedContent.Scope.EXTERNAL_LIBRARIES
import com.android.build.api.transform.QualifiedContent.Scope.PROJECT
import com.android.build.api.transform.QualifiedContent.Scope.SUB_PROJECTS
import com.android.build.api.transform.QualifiedContent.ScopeType
import com.android.build.gradle.internal.InternalScope
import com.android.build.gradle.internal.packaging.ParsedPackagingOptions
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.builder.files.KeyedFileCache
import com.android.builder.merge.IncrementalFileMergerInput
import com.android.ide.common.resources.FileStatus
import com.android.utils.FileUtils
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import java.io.File

/**
 * [ProfileAwareWorkAction] to merge java resources
 */
abstract class MergeJavaResWorkAction : ProfileAwareWorkAction<MergeJavaResWorkAction.Params>() {
    override fun run() {
        val isIncremental = parameters.incremental.get()
        val outputFile = parameters.outputFile.get().asFile
        val incrementalStateFile = parameters.incrementalStateFile.asFile.get()
        if (!isIncremental) {
            FileUtils.deleteIfExists(outputFile)
            FileUtils.deleteIfExists(incrementalStateFile)
        }
        val cacheDir = parameters.cacheDir.asFile.get().also { FileUtils.mkdirs(it) }

        val zipCache = KeyedFileCache(cacheDir, KeyedFileCache::fileNameKey)
        val cacheUpdates = mutableListOf<Runnable>()
        val scopeMap = mutableMapOf<IncrementalFileMergerInput, ScopeType>()

        val inputMap = mutableMapOf<File, ScopeType>()
        parameters.projectJavaRes.forEach { inputMap[it] = PROJECT}
        parameters.subProjectJavaRes.forEach { inputMap[it] = SUB_PROJECTS}
        parameters.externalLibJavaRes.forEach { inputMap[it] = EXTERNAL_LIBRARIES}
        parameters.featureJavaRes.forEach { inputMap[it] = InternalScope.FEATURES}

        val inputs =
            toInputs(
                inputMap,
                parameters.changedInputs.orNull,
                zipCache,
                cacheUpdates,
                !isIncremental,
                scopeMap
            )

        val mergeJavaResDelegate =
            MergeJavaResourcesDelegate(
                inputs,
                outputFile,
                scopeMap,
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

    abstract class Params : ProfileAwareWorkAction.Parameters() {
        abstract val projectJavaRes: ConfigurableFileCollection
        abstract val subProjectJavaRes: ConfigurableFileCollection
        abstract val externalLibJavaRes: ConfigurableFileCollection
        abstract val featureJavaRes: ConfigurableFileCollection
        abstract val outputFile: RegularFileProperty
        abstract val incrementalStateFile: RegularFileProperty
        abstract val incremental: Property<Boolean>
        abstract val cacheDir: DirectoryProperty
        abstract val changedInputs: MapProperty<File, FileStatus>
        abstract val noCompress: ListProperty<String>
        abstract val excludes: SetProperty<String>
        abstract val pickFirsts: SetProperty<String>
        abstract val merges: SetProperty<String>
    }
}
