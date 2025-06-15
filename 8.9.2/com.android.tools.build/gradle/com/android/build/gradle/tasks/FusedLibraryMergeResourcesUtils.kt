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
import com.android.build.gradle.internal.LoggerWrapper
import com.android.ide.common.blame.MergingLog
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.MergedResourceWriter
import com.android.ide.common.resources.MergedResourceWriterRequest
import com.android.ide.common.resources.ResourceCompilationService
import com.android.ide.common.resources.ResourceMerger
import com.android.ide.common.resources.ResourceSet
import com.android.ide.common.workers.WorkerExecutorFacade
import com.android.utils.FileUtils
import org.gradle.api.logging.Logger
import java.io.File

internal fun mergeResourcesWithCompilationService(
        resCompilerService: ResourceCompilationService,
        incrementalMergedResources: File,
        mergedResources: File,
        resourceSets: List<File>,
        minSdk: Int,
        aaptWorkerFacade: WorkerExecutorFacade,
        blameLogOutputFolder: File,
        logger: Logger) {
    val incrementalMergedResources = incrementalMergedResources
    val mergedResourcesDir = File(mergedResources, SdkConstants.RES_FOLDER).also {
        it.mkdirs()
    }
    val sourcesResourceSet = ResourceSet(
            null, ResourceNamespace.RES_AUTO, null, false, null
    ).apply {
        addSources(resourceSets.reversed())
    }
    val resourceMerger = ResourceMerger(minSdk).apply {
        sourcesResourceSet.loadFromFiles(LoggerWrapper(logger))
        addDataSet(sourcesResourceSet)
    }
    aaptWorkerFacade.use { workerExecutorFacade ->
        resCompilerService.use { resCompilationService ->
            val mergeResourcesWriterRequest = MergedResourceWriterRequest(
                    workerExecutor = workerExecutorFacade,
                    rootFolder = mergedResourcesDir,
                    publicFile = null,
                    blameLog = getCleanBlameLog(blameLogOutputFolder),
                    preprocessor = null,
                    resourceCompilationService = resCompilationService,
                    temporaryDirectory = incrementalMergedResources,
                    dataBindingExpressionRemover = null,
                    notCompiledOutputDirectory = null,
                    pseudoLocalesEnabled = false,
                    crunchPng = false,
                    moduleSourceSets = emptyMap()
            )
            val writer = MergedResourceWriter(mergeResourcesWriterRequest)
            resourceMerger.mergeData(writer, true)
            resourceMerger.writeBlobTo(incrementalMergedResources, writer, false)
        }
    }
}

private fun getCleanBlameLog(blameLogOutputFolder: File): MergingLog {
    FileUtils.cleanOutputDir(blameLogOutputFolder)
    return MergingLog(blameLogOutputFolder)
}
