/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.build.gradle.internal.caching.DisabledCachingReason.SIMPLE_MERGING_TASK
import com.android.buildanalyzer.common.TaskCategory
import com.android.utils.FileUtils
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputFile
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.IOException

/**
 * Task to merge files. This appends all the files together into an output file.
 */
@DisableCachingByDefault(because = SIMPLE_MERGING_TASK)
@BuildAnalyzer(primaryTaskCategory = TaskCategory.MISC, secondaryTaskCategories = [TaskCategory.MERGING])
abstract class MergeFileTask : NonIncrementalTask() {

    @get:Classpath // The order of `inputFiles` is important
    abstract val inputFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    override fun doTaskAction() {
        mergeFiles(inputFiles.files.filter { it.isFile }, outputFile.get().asFile)
    }

    companion object {

        fun mergeFiles(inputFiles: Collection<File>, outputFile: File) {
            FileUtils.deleteIfExists(outputFile)

            // If there are no input files, we can either (1) not write the output file, or (2)
            // write an empty output file. Let's go with option (1).
            if (inputFiles.isEmpty()) {
                return
            }

            outputFile.printWriter().buffered().use { writer ->
                inputFiles.joinTo(writer, "\n") { it.readText() }
            }
        }
    }
}
