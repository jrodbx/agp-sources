/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks.databinding

import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.buildanalyzer.common.TaskCategory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

/**
 * Workaround for https://issuetracker.google.com/183423660.
 *
 * Caching disabled by default for this task because the task does very little work.
 * Input files are read from disk and concatenated into a single output file.
 * Calculating cache hit/miss and fetching results is likely more expensive than
 * simply executing the task.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.ANDROID_RESOURCES, secondaryTaskCategories = [TaskCategory.MERGING])
abstract class MergeRFilesForDataBindingTask : NonIncrementalTask() {

    private val NEW_LINE = "\n".toByteArray()

    // Package-aware R.txt files from our dependencies - they contain the package of the module/lib
    // and a list of resources defined in that module/lib. Used for generating non-transitive or
    // resource namespace aware R class references.
    @get:InputFiles
    @get:Classpath
    lateinit var dependenciesLocalRFiles: FileCollection
        private set

    @get:OutputFile
    abstract val mergedDependenciesSymbolList: RegularFileProperty

    override fun doTaskAction() {
        val outputFile = mergedDependenciesSymbolList.get().asFile
        outputFile.createNewFile()

        outputFile.outputStream().use { outputStream ->

            for (inputFile in dependenciesLocalRFiles.files.toList()) {
                // Copy contents as-is for faster IO, no need to re-verify everything again.
                // Each chunk will be a package aware resource list.
                inputFile.inputStream().use { it.copyTo(outputStream) }
                // Separate the contents so it's easier to parse.
                outputStream.write(NEW_LINE)
            }
        }
    }

    class CreationAction(creationConfig: ComponentCreationConfig) :
            VariantTaskCreationAction<MergeRFilesForDataBindingTask, ComponentCreationConfig>(
                    creationConfig
            ) {

        override val name: String = computeTaskName("mergeRFilesForDataBinding")

        override val type: Class<MergeRFilesForDataBindingTask> =
                MergeRFilesForDataBindingTask::class.java

        override fun handleProvider(
                taskProvider: TaskProvider<MergeRFilesForDataBindingTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    MergeRFilesForDataBindingTask::mergedDependenciesSymbolList
            ).withName("mergedR.txt").on(InternalArtifactType.MERGED_DEPENDENCIES_SYMBOL_LIST)
        }

        override fun configure(task: MergeRFilesForDataBindingTask) {
            super.configure(task)
            task.dependenciesLocalRFiles =
                    creationConfig.variantDependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME)
        }
    }
}
