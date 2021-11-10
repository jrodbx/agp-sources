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

import com.android.SdkConstants
import com.android.Version
import com.android.build.gradle.internal.isConfigurationCache
import com.android.build.gradle.internal.services.ServiceRegistrationAction
import com.android.build.gradle.internal.services.getBuildServiceName
import com.android.build.gradle.internal.utils.getBuildSrcPlugins
import com.android.build.gradle.internal.utils.getBuildscriptDependencies
import com.android.builder.utils.SynchronizedFile
import com.android.ide.common.attribution.AndroidGradlePluginAttributionData
import com.android.ide.common.attribution.AndroidGradlePluginAttributionData.BuildInfo
import com.android.ide.common.attribution.AndroidGradlePluginAttributionData.JavaInfo
import com.android.tools.analytics.HostData
import com.android.utils.FileUtils
import org.gradle.api.Project
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistration
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.lang.management.ManagementFactory

abstract class BuildAttributionService : BuildService<BuildAttributionService.Parameters>,
        OperationCompletionListener,
        AutoCloseable {

    companion object {

        private var initialized = false

        @Synchronized
        fun init(project: Project,
                attributionFileLocation: String,
                listenersRegistry: BuildEventsListenerRegistry) {
            if (initialized) {
                return
            }
            val serviceRegistration = project.gradle
                    .sharedServices
                    .registrations
                    .getByName(
                            getBuildServiceName(BuildAttributionService::class.java)
                    ) as BuildServiceRegistration<BuildAttributionService, Parameters>

            initialized = true

            project.gradle.taskGraph.whenReady { taskGraph ->
                val outputFileToTasksMap = mutableMapOf<String, MutableList<String>>()
                val taskNameToClassNameMap = mutableMapOf<String, String>()

                taskGraph.allTasks.forEach { task ->
                    taskNameToClassNameMap[task.name] = getTaskClassName(task.javaClass.name)

                    task.outputs.files.forEach { outputFile ->
                        outputFileToTasksMap.computeIfAbsent(outputFile.absolutePath) {
                            ArrayList()
                        }.add(task.path)
                    }
                }

                val buildscriptDependenciesInfo = getBuildscriptDependencies(project.rootProject)
                        .map { "${it.group}:${it.module}:${it.version}" }

                serviceRegistration.parameters.attributionFileLocation.set(attributionFileLocation)
                serviceRegistration.parameters.taskNameToClassNameMap.set(taskNameToClassNameMap)
                serviceRegistration.parameters.tasksSharingOutputs.set(
                        outputFileToTasksMap.filter { it.value.size > 1 }
                )
                serviceRegistration.parameters.javaInfo.set(
                    JavaInfo(
                        version = project.providers.systemProperty("java.version")
                            .forUseAtConfigurationTime().getOrElse(""),
                        vendor = project.providers.systemProperty("java.vendor")
                            .forUseAtConfigurationTime().getOrElse(""),
                        home = project.providers.systemProperty("java.home")
                            .forUseAtConfigurationTime().getOrElse(""),
                        vmArguments = HostData.runtimeBean?.inputArguments ?: emptyList()
                    )
                )
                serviceRegistration.parameters.buildscriptDependenciesInfo.set(buildscriptDependenciesInfo)
                serviceRegistration.parameters.buildInfo.set(
                    BuildInfo(
                        agpVersion = Version.ANDROID_GRADLE_PLUGIN_VERSION,
                        configurationCacheIsOn = project.gradle.startParameter.isConfigurationCache
                    )
                )


                listenersRegistry.onTaskCompletion(serviceRegistration.service)
            }
        }

        private fun saveAttributionData(
            outputDir: File,
            attributionData: AndroidGradlePluginAttributionData
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
                            attributionData
                        )
                    )
                }
            }
        }

        private fun getTaskClassName(className: String): String {
            if (className.endsWith("_Decorated")) {
                return className.substring(0, className.length - "_Decorated".length)
            }
            return className
        }
    }

    private val initialGarbageCollectionData: Map<String, Long> =
            ManagementFactory.getGarbageCollectorMXBeans().map { it.name to it.collectionTime }
                    .toMap()

    override fun close() {
        initialized = false

        val gcData =
                ManagementFactory.getGarbageCollectorMXBeans().map {
                    it.name to it.collectionTime - initialGarbageCollectionData.getOrDefault(
                            it.name,
                            0
                    )
                }.filter { it.second > 0L }.toMap()

        saveAttributionData(
            File(parameters.attributionFileLocation.get()),
            AndroidGradlePluginAttributionData(
                taskNameToClassNameMap = parameters.taskNameToClassNameMap.get(),
                tasksSharingOutput = parameters.tasksSharingOutputs.get(),
                garbageCollectionData = gcData,
                buildSrcPlugins = getBuildSrcPlugins(this.javaClass.classLoader),
                javaInfo = parameters.javaInfo.get(),
                buildscriptDependenciesInfo = parameters.buildscriptDependenciesInfo.get(),
                buildInfo = parameters.buildInfo.get()
            )
        )
    }

    override fun onFinish(p0: FinishEvent?) {
        // Nothing to be done.
        // This is a workaround to make Gradle always start the service, specifically needed when
        // configuration caching is enabled where the service can't be started on configuration.
    }

    interface Parameters : BuildServiceParameters {

        val attributionFileLocation: Property<String>

        val tasksSharingOutputs: MapProperty<String, List<String>>

        val taskNameToClassNameMap: MapProperty<String, String>

        val javaInfo: Property<JavaInfo>

        val buildscriptDependenciesInfo: SetProperty<String>

        val buildInfo: Property<BuildInfo>
    }

    class RegistrationAction(project: Project)
        : ServiceRegistrationAction<BuildAttributionService, Parameters>(
            project,
            BuildAttributionService::class.java
    ) {

        override fun configure(parameters: Parameters) {}
    }
}
