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

import com.android.build.gradle.internal.services.ServiceRegistrationAction
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.services.getBuildServiceName
import com.android.builder.profile.Recorder
import com.google.wireless.android.sdk.stats.GradleBuildProfile
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import com.google.wireless.android.sdk.stats.GradleBuildProject
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistration
import org.gradle.build.event.BuildEventsListenerRegistry
import java.util.concurrent.ConcurrentHashMap

/**
 * A build service used to configure [AnalyticsService].
 *
 * [AnalyticsConfiguratorService] is paired with [AnalyticsService] which means there will always be
 * one and only one [AnalyticsConfiguratorService] instance for each [AnalyticsService] instance.
 */
abstract class AnalyticsConfiguratorService : BuildService<BuildServiceParameters.None> {

    private val resourcesManager = AnalyticsResourceManager(
        GradleBuildProfile.newBuilder(),
        ConcurrentHashMap(),
        false,
        null,
        ConcurrentHashMap(),
        null
    )

    private enum class State {
        COLLECTING_DATA,
        CALLBACK_REGISTERED,
        ANALYTICS_SERVICE_CREATED
    }

    private var state = State.COLLECTING_DATA

    open fun getProjectBuilder(projectPath: String) : GradleBuildProject.Builder? {
        if (state == State.ANALYTICS_SERVICE_CREATED) {
            error("Accessing GradleBuildProject.Builder through AnalyticsConfiguratorService " +
                    "is not allowed after AnalyticsService is created.")
        }
        return resourcesManager.getProjectBuilder(projectPath)
    }

    open fun getVariantBuilder(projectPath: String, variantName: String) : GradleBuildVariant.Builder? {
        if (state == State.ANALYTICS_SERVICE_CREATED) {
            error("Accessing GradleBuildVariant.Builder through AnalyticsConfiguratorService " +
                    "is not allowed after AnalyticsService is created.")
        }
        return resourcesManager.getVariantBuilder(projectPath, variantName)
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

    @Synchronized
    open fun createAnalyticsService(project: Project, registry: BuildEventsListenerRegistry) {
        if (state == State.CALLBACK_REGISTERED) {
            return
        }
        state = State.CALLBACK_REGISTERED

        val serviceRegistration = project.gradle
            .sharedServices
            .registrations
            .getByName(
                getBuildServiceName(AnalyticsService::class.java)
            ) as BuildServiceRegistration<AnalyticsService, AnalyticsService.Params>

        // In composite build, in order to tell if it is a model query or not, we need to check
        // the existence of task requests from the main build, not from included build. The
        // reason is that the existence of task requests from the included build is not
        // finalized at this point can could change when the main build gets evaluated.
        var rootBuild = project.gradle
        while (rootBuild.parent != null) {
            rootBuild = rootBuild.parent!!
        }
        if (rootBuild.startParameter.taskNames.isEmpty()) {
            project.gradle.projectsEvaluated {
                resourcesManager.recordGlobalProperties(project)
                resourcesManager.configureAnalyticsService(serviceRegistration.parameters)
                instantiateAnalyticsService(project)
            }
        } else {
            project.gradle.taskGraph.whenReady {
                resourcesManager.recordGlobalProperties(project)
                resourcesManager.collectTaskMetadata(it)
                resourcesManager.recordTaskNames(it)
                resourcesManager.configureAnalyticsService(serviceRegistration.parameters)
                instantiateAnalyticsService(project)
                registry.onTaskCompletion(
                    getBuildService<AnalyticsService>(project.gradle.sharedServices))
            }
        }
    }

    private fun instantiateAnalyticsService(project: Project) {
        if (state == State.ANALYTICS_SERVICE_CREATED) {
            return
        }
        val analyticsService = getBuildService<AnalyticsService>(project.gradle.sharedServices)
        analyticsService.get()
            .setInitialMemorySampleForConfiguration(resourcesManager.initialMemorySample)
        analyticsService.get().setConfigurationSpans(resourcesManager.configurationSpans)
        state = State.ANALYTICS_SERVICE_CREATED
    }

    class RegistrationAction(project: Project)
        : ServiceRegistrationAction<AnalyticsConfiguratorService, BuildServiceParameters.None>(
        project,
        AnalyticsConfiguratorService::class.java
    ) {
        override fun configure(parameters: BuildServiceParameters.None) {}
    }
}
