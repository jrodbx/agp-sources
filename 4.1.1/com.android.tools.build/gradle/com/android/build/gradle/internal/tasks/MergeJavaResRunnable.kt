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

import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.QualifiedContent.ContentType
import com.android.build.api.transform.QualifiedContent.Scope.EXTERNAL_LIBRARIES
import com.android.build.api.transform.QualifiedContent.Scope.PROJECT
import com.android.build.api.transform.QualifiedContent.Scope.SUB_PROJECTS
import com.android.build.api.transform.QualifiedContent.ScopeType
import com.android.build.gradle.internal.InternalScope
import com.android.build.gradle.internal.packaging.ParsedPackagingOptions
import com.android.build.gradle.internal.packaging.SerializablePackagingOptions
import com.android.builder.files.KeyedFileCache
import com.android.builder.merge.IncrementalFileMergerInput
import com.android.ide.common.resources.FileStatus
import com.android.utils.FileUtils
import java.io.File
import java.io.Serializable
import javax.inject.Inject

/**
 * Runnable to merge java resources, whether regular java resources or native libs
 */
class MergeJavaResRunnable @Inject constructor(val params: Params) : Runnable {
    override fun run() {
        if (!params.isIncremental) {
            if (params.output.isDirectory) {
                FileUtils.cleanOutputDir(params.output)
            } else {
                FileUtils.deleteIfExists(params.output)
            }
        }
        FileUtils.mkdirs(params.cacheDir)

        val zipCache = KeyedFileCache(params.cacheDir, KeyedFileCache::fileNameKey)
        val cacheUpdates = mutableListOf<Runnable>()
        val contentMap = mutableMapOf<IncrementalFileMergerInput, QualifiedContent>()

        val inputMap = mutableMapOf<File, ScopeType>()
        params.projectJavaRes.forEach { inputMap[it] = PROJECT}
        params.subProjectJavaRes?.forEach { inputMap[it] = SUB_PROJECTS}
        params.externalLibJavaRes?.forEach { inputMap[it] = EXTERNAL_LIBRARIES}
        params.featureJavaRes?.forEach { inputMap[it] = InternalScope.FEATURES}

        val inputs =
            toInputs(
                inputMap,
                params.changedInputs,
                zipCache,
                cacheUpdates,
                !params.isIncremental,
                params.contentType,
                contentMap
            )

        val mergeJavaResDelegate =
            MergeJavaResourcesDelegate(
                inputs,
                params.output,
                contentMap,
                ParsedPackagingOptions(params.packagingOptions),
                params.contentType,
                params.incrementalStateFile,
                params.isIncremental,
                params.noCompress
            )
        mergeJavaResDelegate.run()
        cacheUpdates.forEach(Runnable::run)
    }

    class Params(
        val projectJavaRes: Collection<File>,
        val subProjectJavaRes: Collection<File>?,
        val externalLibJavaRes: Collection<File>?,
        val featureJavaRes: Collection<File>?,
        val output: File,
        val packagingOptions: SerializablePackagingOptions,
        val incrementalStateFile: File,
        val isIncremental: Boolean,
        val cacheDir: File,
        val changedInputs: Map<File, FileStatus>?,
        val contentType: ContentType,
        val noCompress: Collection<String>
    ): Serializable
}
