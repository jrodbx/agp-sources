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

package com.android.build.gradle.internal.attribution

import com.android.build.gradle.internal.services.ServiceRegistrationAction
import com.android.build.gradle.internal.utils.getBuildSrcPlugins
import com.android.buildanalyzer.common.AndroidGradlePluginAttributionData
import com.android.buildanalyzer.common.AndroidGradlePluginAttributionData.BuildInfo
import com.android.buildanalyzer.common.AndroidGradlePluginAttributionData.JavaInfo
import com.android.buildanalyzer.common.AndroidGradlePluginAttributionData.TaskInfo
import com.android.buildanalyzer.common.TaskCategoryIssue
import com.android.builder.utils.SynchronizedFile
import org.gradle.api.Project
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.lang.management.ManagementFactory
import java.util.Collections

/**
 * Collects information for Build Analyzer in the IDE.
 *
 * DO NOT instantiate eagerly, this build service relies on
 * [org.gradle.api.execution.TaskExecutionGraph.whenReady] to configure itself.
 */
abstract class BuildAnalyzerService : BuildService<BuildAnalyzerService.Parameters>,
        OperationCompletionListener,
        AutoCloseable {

    companion object {

        private fun saveAttributionData(
            outputDir: File,
            attributionData: () -> AndroidGradlePluginAttributionData
        ) {
            val file = AndroidGradlePluginAttributionData.getAttributionFile(outputDir)
            file.parentFile.mkdirs()
            // In case of having different classloaders for different projects when the classpaths
            // are different (b/154388196), multiple instances of BuildAttributionService will try
            // to save the attribution data to the same output file. The data produced by the
            // different services should be identical.
            // Here we try to acquire an exclusive lock on the output file and write the attribution
            // data to it. If another BuildAttributionService did already write to the file and
            // released the lock, we will rewrite the data again which should be identical to the
            // previously written.
            SynchronizedFile.getInstanceWithMultiProcessLocking(file).write {
                BufferedWriter(FileWriter(file)).use {
                    it.write(
                        AndroidGradlePluginAttributionData.AttributionDataAdapter.toJson(
                            attributionData.invoke()
                        )
                    )
                }
            }
        }


    }

    private val initialGarbageCollectionData: Map<String, Long> =
        ManagementFactory.getGarbageCollectorMXBeans().associate { it.name to it.collectionTime }

    private val executionTimeTaskCategoryIssues = Collections.synchronizedSet(mutableSetOf<TaskCategoryIssue>())

    fun reportBuildAnalyzerIssue(issue: TaskCategoryIssue) {
        executionTimeTaskCategoryIssues.add(issue)
    }

    override fun close() {
        if (!parameters.attributionFileLocation.isPresent) {
            // There were no tasks in this build, so avoid recording info
            return
        }

        val gcData =
                ManagementFactory.getGarbageCollectorMXBeans().map {
                    it.name to it.collectionTime - initialGarbageCollectionData.getOrDefault(
                            it.name,
                            0
                    )
                }.filter { it.second > 0L }.toMap()

        saveAttributionData(
            File(parameters.attributionFileLocation.get()),
        ) {
            val taskCategoryIssues =
                executionTimeTaskCategoryIssues + parameters.taskCategoryIssues.get()
            val partialResults = BuildAnalyzerPartialResult(taskCategoryIssues)

            val partialResultsOutputDir = AndroidGradlePluginAttributionData.getPartialResultsDir(
                File(parameters.attributionFileLocation.get())
            )

            // This will be invoked under a file lock, and so it's safe to read the output of other
            // build services at this point
            BuildAnalyzerPartialResult.getAllPartialResults(
                partialResultsOutputDir
            ).forEach { partialResults.combineWith(it) }

            partialResults.saveToDir(partialResultsOutputDir)

            AndroidGradlePluginAttributionData(
                tasksSharingOutput = parameters.tasksSharingOutputs.get(),
                garbageCollectionData = gcData,
                buildSrcPlugins = getBuildSrcPlugins(this.javaClass.classLoader),
                javaInfo = parameters.javaInfo.get(),
                buildscriptDependenciesInfo = parameters.buildscriptDependenciesInfo.get(),
                buildInfo = parameters.buildInfo.get(),
                taskNameToTaskInfoMap = parameters.taskNameToTaskInfoMap.get(),
                taskCategoryIssues = partialResults.issues.toList()
            )
        }
    }

    override fun onFinish(p0: FinishEvent?) {
        // Nothing to be done.
        // This is a workaround to make Gradle always start the service, specifically needed when
        // configuration caching is enabled where the service can't be started on configuration.
    }

    interface Parameters : BuildServiceParameters {

        val attributionFileLocation: Property<String>

        val tasksSharingOutputs: MapProperty<String, List<String>>

        val javaInfo: Property<JavaInfo>

        val buildscriptDependenciesInfo: SetProperty<String>

        val buildInfo: Property<BuildInfo>

        val taskNameToTaskInfoMap: MapProperty<String, TaskInfo>

        val taskCategoryIssues: SetProperty<TaskCategoryIssue>
    }

    @Suppress("UnstableApiUsage")
    class RegistrationAction(
        project: Project,
        private val attributionFileLocation: String,
        private val listenersRegistry: BuildEventsListenerRegistry,
        private val buildAnalyzerConfiguratorService: BuildAnalyzerConfiguratorService
    ) : ServiceRegistrationAction<BuildAnalyzerService, Parameters>(
        project,
        BuildAnalyzerService::class.java
    ) {

        override fun configure(parameters: Parameters) {
            buildAnalyzerConfiguratorService.initBuildAnalyzerService(
                project,
                attributionFileLocation,
                parameters
            )
        }

        override fun execute(): Provider<BuildAnalyzerService> {
            return super.execute().also {
                listenersRegistry.onTaskCompletion(it)
            }
        }
    }
}
