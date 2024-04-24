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

package com.android.build.gradle.internal.attribution

import com.android.Version
import com.android.build.gradle.internal.isConfigurationCache
import com.android.build.gradle.internal.services.ServiceRegistrationAction
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.utils.getBuildscriptDependencies
import com.android.buildanalyzer.common.AndroidGradlePluginAttributionData
import com.android.buildanalyzer.common.TaskCategory
import com.android.buildanalyzer.common.TaskCategoryIssue
import com.android.tools.analytics.HostData
import com.android.utils.HelpfulEnumConverter
import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.util.Collections

/**
 * A build service that is responsible for configuring the [BuildAnalyzerService] as the build
 * analyzer service requires data that is collected through [org.gradle.api.execution.TaskExecutionGraph.whenReady]
 * which is evaluated after configuration. Once configured, use the [BuildAnalyzerService] directly
 * to add any additional data at execution time.
 *
 * Use this service to send data to the [BuildAnalyzerService] at configuration time only.
 */
abstract class BuildAnalyzerConfiguratorService: BuildService<BuildServiceParameters.None> {

    private enum class State {
        NOT_INITIALIZED,
        INITIALIZED,
        MAIN_SERVICE_CONFIGURED,
    }

    private var state = State.NOT_INITIALIZED
    private val taskCategoryIssues = Collections.synchronizedSet(mutableSetOf<TaskCategoryIssue>())

    fun reportBuildAnalyzerIssue(issue: TaskCategoryIssue) {
        if (state == State.MAIN_SERVICE_CONFIGURED) {
            throw RuntimeException(
                "The configurator service has already configured the build analyzer service. To " +
                        "add execution time warnings, add them directly to the BuildAnalyzerService."
            )
        }

        taskCategoryIssues.add(issue)
    }

    fun initBuildAnalyzerService(
        project: Project,
        attributionFileLocation: String,
        parameters: BuildAnalyzerService.Parameters
    ) {
        if (state != State.NOT_INITIALIZED) {
            return
        }

        state = State.INITIALIZED

        val taskCategoryConverter = HelpfulEnumConverter(TaskCategory::class.java)
        project.gradle.taskGraph.whenReady { taskGraph ->
            val outputFileToTasksMap = mutableMapOf<String, MutableList<String>>()
            val taskNameToTaskInfoMap = mutableMapOf<String, AndroidGradlePluginAttributionData.TaskInfo>()
            taskGraph.allTasks.forEach { task ->

                task.outputs.files.forEach { outputFile ->
                    outputFileToTasksMap.computeIfAbsent(outputFile.absolutePath) {
                        ArrayList()
                    }.add(task.path)
                }

                val taskCategoryInfo =
                    if (task::class.java.isAnnotationPresent(BuildAnalyzer::class.java)) {
                        val annotation =
                            task::class.java.getAnnotation(BuildAnalyzer::class.java)
                        val primaryTaskCategory =
                            taskCategoryConverter.convert(annotation.primaryTaskCategory.toString())!!
                        val secondaryTaskCategories =
                            annotation.secondaryTaskCategories.map {
                                taskCategoryConverter.convert(
                                    it.toString()
                                )!!
                            }
                        AndroidGradlePluginAttributionData.TaskCategoryInfo(
                            primaryTaskCategory = primaryTaskCategory,
                            secondaryTaskCategories = secondaryTaskCategories
                        )
                    } else AndroidGradlePluginAttributionData.TaskCategoryInfo(
                        primaryTaskCategory = TaskCategory.UNCATEGORIZED
                    )

                taskNameToTaskInfoMap[task.name] =
                    AndroidGradlePluginAttributionData.TaskInfo(
                        className = getTaskClassName(task.javaClass.name),
                        taskCategoryInfo = taskCategoryInfo
                    )
            }

            val buildscriptDependenciesInfo = getBuildscriptDependencies(project.rootProject)
                .map { "${it.group}:${it.module}:${it.version}" }

            parameters.attributionFileLocation.set(attributionFileLocation)
            parameters.tasksSharingOutputs.set(
                outputFileToTasksMap.filter { it.value.size > 1 }
            )
            parameters.javaInfo.set(
                AndroidGradlePluginAttributionData.JavaInfo(
                    version = System.getProperty("java.version") ?: "",
                    vendor = System.getProperty("java.vendor") ?: "",
                    home = System.getProperty("java.home") ?: "",
                    vmArguments = HostData.runtimeBean?.inputArguments ?: emptyList()
                )
            )
            parameters.buildscriptDependenciesInfo.set(buildscriptDependenciesInfo)
            parameters.buildInfo.set(
                AndroidGradlePluginAttributionData.BuildInfo(
                    agpVersion = Version.ANDROID_GRADLE_PLUGIN_VERSION,
                    configurationCacheIsOn = project.gradle.startParameter.isConfigurationCache
                )
            )
            parameters.taskNameToTaskInfoMap.set(taskNameToTaskInfoMap)

            parameters.taskCategoryIssues.set(taskCategoryIssues)

            state = State.MAIN_SERVICE_CONFIGURED
        }
    }

    private fun getTaskClassName(className: String): String {
        if (className.endsWith("_Decorated")) {
            return className.substring(0, className.length - "_Decorated".length)
        }
        return className
    }

    class RegistrationAction(project: Project):
        ServiceRegistrationAction<BuildAnalyzerConfiguratorService, BuildServiceParameters.None>(
            project,
            BuildAnalyzerConfiguratorService::class.java
        ) {

        override fun configure(parameters: BuildServiceParameters.None) { }
    }
}
