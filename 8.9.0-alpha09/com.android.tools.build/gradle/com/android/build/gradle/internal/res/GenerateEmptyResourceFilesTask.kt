/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.res

import com.android.SdkConstants
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.buildanalyzer.common.TaskCategory
import com.android.utils.FileUtils
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

/**
 *  Task to create an empty R.txt and an empty res/ directory.
 *
 *  This task is used when resource processing in an Android Library module is disabled. Instead of
 *  multiple tasks merging, parsing and processing resources, the user can fully disable the
 *  resource pipeline in a library module and have this task generate the empty artifacts instead.
 *
 *  The R.txt and res/ directory are required artifacts in an AAR, even when empty, so we still need
 *  to generate them. We can however skip generating the public.txt, since it's not required
 *  (missing public.txt means all resources in the R.txt in that AAR are public, but since the R.txt
 *  is empty, we can safely skip the public.txt file).
 *
 *  Caching disabled by default for this task because the task does very little work.
 *  Calculating cache hit/miss and fetching results is likely more expensive than
 *    simply executing the task.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.ANDROID_RESOURCES)
abstract class GenerateEmptyResourceFilesTask : NonIncrementalTask() {

    @get:OutputFile
    abstract val emptyRDotTxt: RegularFileProperty

    @get:OutputDirectory
    abstract val emptyMergedResources: DirectoryProperty

    override fun doTaskAction() {
        // TODO(147579629): should this contain transitive resources or is it okay to have it empty?
        // Create empty R.txt, will be used for bundling in the AAR.
        emptyRDotTxt.asFile.get().writeText("")

        // Create empty res/ directory to bundle in the AAR.
        FileUtils.mkdirs(emptyMergedResources.asFile.get())
    }

    class CreateAction(creationConfig: ComponentCreationConfig) :
        VariantTaskCreationAction<GenerateEmptyResourceFilesTask, ComponentCreationConfig>(
            creationConfig
        ) {

        override val name: String
            get() = computeTaskName("generate", "EmptyResourceFiles")
        override val type: Class<GenerateEmptyResourceFilesTask>
            get() = GenerateEmptyResourceFilesTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<GenerateEmptyResourceFilesTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                GenerateEmptyResourceFilesTask::emptyRDotTxt
            ).withName(SdkConstants.FN_RESOURCE_TEXT).on(InternalArtifactType.COMPILE_SYMBOL_LIST)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                GenerateEmptyResourceFilesTask::emptyMergedResources
            ).withName(SdkConstants.FD_RES).on(InternalArtifactType.PACKAGED_RES)
        }
    }
}
