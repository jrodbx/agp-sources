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

package com.android.build.gradle.internal.attribution

import com.android.ide.common.attribution.AndroidGradlePluginAttributionData
import org.gradle.BuildAdapter
import org.gradle.BuildResult
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.tasks.TaskState
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * A listener used for fetching the data that is not available through the tooling API to be used
 * in showing the build attribution on the IDE side.
 */
class AttributionBuildListener internal constructor(private val outputDirPath: String) :
    TaskExecutionListener, BuildAdapter() {
    private val taskNameToClassNameMap: MutableMap<String, String> = ConcurrentHashMap()
    private val outputFileToTasksMap: MutableMap<String, MutableList<String>> = ConcurrentHashMap()

    override fun buildFinished(buildResult: BuildResult) {
        AndroidGradlePluginAttributionData.save(
            File(outputDirPath),
            AndroidGradlePluginAttributionData(
                taskNameToClassNameMap = taskNameToClassNameMap,
                tasksSharingOutput = outputFileToTasksMap.filter { it.value.size > 1 }
            )
        )
        AttributionListenerInitializer.unregister(buildResult.gradle)
    }

    override fun beforeExecute(task: Task) {
        taskNameToClassNameMap[task.name] = getTaskClassName(task.javaClass.name)

        task.outputs.files.forEach { outputFile ->
            outputFileToTasksMap.computeIfAbsent(outputFile.absolutePath) {
                ArrayList()
            }.add(task.path)
        }
    }

    private fun getTaskClassName(className: String): String {
        if (className.endsWith("_Decorated")) {
            return className.substring(0, className.length - "_Decorated".length)
        }
        return className
    }

    override fun afterExecute(task: Task, taskState: TaskState) {
        // nothing to do
    }
}