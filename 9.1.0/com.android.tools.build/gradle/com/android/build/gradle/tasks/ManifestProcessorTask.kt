/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalGlobalTask
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.buildanalyzer.common.TaskCategory
import com.android.manifmerger.MergingReport
import com.android.utils.FileUtils
import com.google.common.base.Charsets
import com.google.common.io.Files
import java.io.File
import java.io.IOException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.work.DisableCachingByDefault

interface ManifestProcessor {

  @get:Optional @get:OutputFile abstract val reportFile: RegularFileProperty

  @get:Optional @get:OutputFile abstract val mergeBlameFile: RegularFileProperty
}

/** A task that processes the manifest */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.MANIFEST)
abstract class ManifestProcessorTask : NonIncrementalTask(), ManifestProcessor

/** A task that processes the manifest */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.MANIFEST)
abstract class ManifestProcessorGlobalTask : NonIncrementalGlobalTask(), ManifestProcessor

@Throws(IOException::class)
internal fun outputMergeBlameContents(mergingReport: MergingReport, mergeBlameFile: File?) {
  if (mergeBlameFile == null) {
    return
  }
  val output = mergingReport.getMergedDocument(MergingReport.MergedManifestKind.BLAME) ?: return
  FileUtils.mkdirs(mergeBlameFile.parentFile)
  Files.newWriter(mergeBlameFile, Charsets.UTF_8).use { writer -> writer.write(output) }
}
