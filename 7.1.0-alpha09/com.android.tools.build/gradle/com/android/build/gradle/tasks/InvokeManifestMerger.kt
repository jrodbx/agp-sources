/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.databinding.tool.util.Preconditions
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.manifmerger.ManifestMerger2
import com.android.manifmerger.MergingReport
import com.google.common.base.Supplier
import java.io.File
import java.io.FileWriter
import org.apache.tools.ant.BuildException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.DisableCachingByDefault

/**
 * Simple task to invoke the new Manifest Merger without any injection, features, system properties
 * or overlay manifests
 */
@DisableCachingByDefault
abstract class InvokeManifestMerger : NonIncrementalTask(), Supplier<File> {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    var mainManifestFile: File? = null

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    var secondaryManifestFiles: List<File>? = null

    @get:OutputFile
    var outputFile: File? = null

    override fun doTaskAction() {
        Preconditions.checkNotNull(mainManifestFile, "mainManifestFile must not be null")
        Preconditions.checkNotNull(secondaryManifestFiles, "secondaryManifestFiles must not be null")
        Preconditions.checkNotNull(outputFile, "outputFile must not be null")

        val iLogger = LoggerWrapper(logger)
        val mergerInvoker = ManifestMerger2.newMerger(
            mainManifestFile!!,
            iLogger,
            ManifestMerger2.MergeType.APPLICATION
        )
        mergerInvoker.addLibraryManifests(*secondaryManifestFiles!!.toTypedArray())
        val mergingReport = mergerInvoker.merge()
        if (mergingReport.result.isError) {
            logger.error(mergingReport.reportString)
            mergingReport.log(iLogger)
            throw BuildException(mergingReport.reportString)
        }
        FileWriter(outputFile!!).use { fileWriter ->
            fileWriter.append(
                mergingReport
                    .getMergedDocument(MergingReport.MergedManifestKind.MERGED)
            )
        }
    }

    override fun get(): File? {
        return outputFile
    }
}
