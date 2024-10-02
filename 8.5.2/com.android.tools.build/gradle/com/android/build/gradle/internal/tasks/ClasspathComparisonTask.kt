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

import com.android.buildanalyzer.common.TaskCategory
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Abstract class used to compare two configurations, and to report differences in versions of
 * artifacts. This is useful to warn user about potential issues that could arise from such
 * differences. E.g. for application, differences in runtime and compile classpath could result in
 * runtime failure.
 *
 * Caching disabled by default for this task because the task does very little work.
 * Calculating cache hit/miss and fetching results is likely more expensive than
 * simply executing the task.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.HELP)
abstract class ClasspathComparisonTask : NonIncrementalTask() {

    @get:Input
    abstract val runtimeVersionMap: MapProperty<Info, String>

    @get:Input
    abstract val compileVersionMap: MapProperty<Info, String>

    // fake output dir so that the task doesn't run unless an input has changed.
    @get:OutputDirectory
    var fakeOutputDirectory: File? = null
        protected set

    protected abstract fun onDifferentVersionsFound(
        group: String,
        module: String,
        runtimeVersion: String,
        compileVersion: String
    )

    override fun doTaskAction() {
        val runtimeMap = runtimeVersionMap.get()
        for (compileEntry in compileVersionMap.get().entries) {
            val runtimeVersion = runtimeMap[compileEntry.key] ?: continue

            if (runtimeVersion != compileEntry.value) {
                onDifferentVersionsFound(
                    compileEntry.key.group,
                    compileEntry.key.module,
                    runtimeVersion,
                    compileEntry.value
                )
            }
        }
    }
}

data class Info(
    val group: String,
    val module: String
)

fun Configuration.toVersionMap(): Map<Info, String> =
    incoming.resolutionResult.allComponents
        .asSequence()
        .filter { it.id is ModuleComponentIdentifier }
        .map { it.id as ModuleComponentIdentifier }
        .map { Info(it.group, it.module) to it.version }
        .toMap()
