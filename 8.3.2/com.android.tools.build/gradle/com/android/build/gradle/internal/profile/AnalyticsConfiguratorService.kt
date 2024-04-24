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

package com.android.build.gradle.internal.profile

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.projectIsolationRequested
import com.android.build.gradle.internal.services.ServiceRegistrationAction
import com.android.build.gradle.internal.services.getBuildService
import com.android.builder.profile.Recorder
import com.google.wireless.android.sdk.stats.GradleBuildProfile
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import com.google.wireless.android.sdk.stats.GradleBuildProject
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.build.event.BuildEventsListenerRegistry
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * A build service used to configure [AnalyticsService].
 *
 * [AnalyticsConfiguratorService] is paired with [AnalyticsService] which means there will always be
 * one and only one [AnalyticsConfiguratorService] instance for each [AnalyticsService] instance.
 */
abstract class AnalyticsConfiguratorService : BuildService<AnalyticsConfiguratorService.Params> {

    @get:Inject
    abstract val objectFactory: ObjectFactory

    private val resourcesManager = AnalyticsResourceManager(
        GradleBuildProfile.newBuilder(),
        ConcurrentHashMap(),
        false,
        null,
        ConcurrentHashMap(),
        null,
        objectFactory.setProperty(String::class.java),
    )

    private enum class State {
        COLLECTING_DATA,
        CALLBACK_REGISTERED,
        ANALYTICS_SERVICE_CREATED
    }

    private var state = State.COLLECTING_DATA

    open fun getProjectBuilder(projectPath: String) : GradleBuildProject.Builder? {
        if (state == State.ANALYTICS_SERVICE_CREATED && configureOnDemandDisabled()) {
            //TODO Consider turn this into warnings. See b/286859043 for more details.
            LoggerWrapper.getLogger(this::class.java).info(
                "GradleBuildProject.Builder should not be accessed through " +
                        "AnalyticsConfiguratorService after AnalyticsService is created." +
                        " Analytics information of this build might be incomplete."
            )
        }
        return resourcesManager.getProjectBuilder(projectPath)
    }

    open fun getVariantBuilder(projectPath: String, variantName: String) : GradleBuildVariant.Builder? {
        if (state == State.ANALYTICS_SERVICE_CREATED && configureOnDemandDisabled()) {
            LoggerWrapper.getLogger(this::class.java).info(
                "GradleBuildProject.Builder should not be accessed through " +
                        "AnalyticsConfiguratorService after AnalyticsService is created." +
                        " Analytics information of this build might be incomplete."
            )
        }
        return resourcesManager.getVariantBuilder(projectPath, variantName)
    }

    /**
     * When configure on demand is enabled, undeclared dependency resolution would cause projects
     * to be configured during execution phase. In this case, we don't throw error for accessing
     * [getVariantBuilder] during execution time.
     *
     * Side note: Undeclared dependency resolution at execution time would be deprecated by
     * Gradle in 8.X.
     */
    private fun configureOnDemandDisabled(): Boolean {
        return !parameters.configureOnDemand.get()
    }

    /**
     * Records the time elapsed while executing a void block and saves the resulting
     * [GradleBuildProfileSpan].
     */
    open fun recordBlock(
        executionType: GradleBuildProfileSpan.ExecutionType,
        projectPath: String,
        variant: String?,
        block: Recorder.VoidBlock
    ) {
        resourcesManager.recordBlockAtConfiguration(executionType, projectPath, variant, block)
    }

    open fun recordApplicationId(applicationId: Provider<String>) {
        resourcesManager.recordApplicationId(applicationId)
    }

    open fun createAnalyticsService(
        project: Project, registry: BuildEventsListenerRegistry, parameters: AnalyticsService.Params
    ) {
        if (state == State.CALLBACK_REGISTERED) {
            return
        }
        state = State.CALLBACK_REGISTERED

        if (project.gradle.startParameter.taskNames.isEmpty()) {
            project.gradle.projectsEvaluated {
                resourcesManager.recordGlobalProperties(project)
                resourcesManager.configureAnalyticsService(parameters)
                instantiateAnalyticsService(project)
            }
        } else {
            project.gradle.taskGraph.whenReady {
                resourcesManager.recordGlobalProperties(project)
                if (!projectIsolationRequested(project.providers)) {
                    // Accessing all tasks is not supported in project isolation mode
                    resourcesManager.collectTaskMetadata(it)
                    resourcesManager.recordTaskNames(it)
                }

                resourcesManager.configureAnalyticsService(parameters)
                instantiateAnalyticsService(project)
                registry.onTaskCompletion(
                    getBuildService(project.gradle.sharedServices, AnalyticsService::class.java))
            }
        }
    }

    private fun instantiateAnalyticsService(project: Project) {
        if (state == State.ANALYTICS_SERVICE_CREATED) {
            return
        }
        val analyticsService =
            getBuildService(project.gradle.sharedServices, AnalyticsService::class.java)
        analyticsService.get()
            .setInitialMemorySampleForConfiguration(resourcesManager.initialMemorySample)
        analyticsService.get().setConfigurationSpans(resourcesManager.configurationSpans)
        state = State.ANALYTICS_SERVICE_CREATED
    }

    interface Params: BuildServiceParameters {
        val configureOnDemand: Property<Boolean>
    }

    class RegistrationAction(project: Project)
        : ServiceRegistrationAction<AnalyticsConfiguratorService, Params>(
        project,
        AnalyticsConfiguratorService::class.java
    ) {
        override fun configure(parameters: Params) {
            parameters.configureOnDemand.set(project.gradle.startParameter.isConfigureOnDemand)
        }
    }
}
