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
import com.android.build.gradle.internal.services.getBuildServiceName
import com.android.builder.profile.Recorder
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import com.google.wireless.android.sdk.stats.GradleBuildProject
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.Project
import org.gradle.api.services.BuildServiceParameters
import org.gradle.build.event.BuildEventsListenerRegistry

/**
 * No-op implementation of [AnalyticsConfiguratorService], which is used when analytics is disabled.
 */
abstract class NoOpAnalyticsConfiguratorService : AnalyticsConfiguratorService() {

    override fun getProjectBuilder(projectPath: String): GradleBuildProject.Builder? = null

    override fun getVariantBuilder(
        projectPath: String,
        variantName: String
    ): GradleBuildVariant.Builder? {
        return null
    }

    override fun createAnalyticsService(
        project: Project, registry: BuildEventsListenerRegistry, parameters: AnalyticsService.Params
    ) {}

    override fun recordBlock(
        executionType: GradleBuildProfileSpan.ExecutionType,
        projectPath: String,
        variant: String?,
        block: Recorder.VoidBlock
    ) {
        block.call()
    }

    /**
     * Registers [NoOpAnalyticsConfiguratorService] service. The name of the service needs to match
     * the [AnalyticsConfiguratorService] ones, as we fetch them by name, and they should be
     * interchangeable.
     */
    class RegistrationAction(project: Project)
        : ServiceRegistrationAction<NoOpAnalyticsConfiguratorService, BuildServiceParameters.None>(
        project,
        NoOpAnalyticsConfiguratorService::class.java,
        name = getBuildServiceName(AnalyticsConfiguratorService::class.java)
    ) {

        override fun configure(parameters: BuildServiceParameters.None) {
            // do nothing
        }
    }
}
