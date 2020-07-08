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

@file:JvmName("KgpUtils")

package com.android.build.gradle.internal.utils

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.profile.AnalyticsConfiguratorService
import com.android.build.gradle.internal.services.getBuildService
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.TaskProvider

fun getKotlinCompile(project: Project, creationConfig: ComponentCreationConfig): TaskProvider<Task> =
        project.tasks.named(creationConfig.computeTaskName("compile", "Kotlin"))

/* Record information if IR backend is enabled. */
fun recordIrBackendForAnalytics(allPropertiesList: List<ComponentCreationConfig>, extension: BaseExtension, project: Project, composeIsEnabled: Boolean) {
    for (creationConfig in allPropertiesList) {
        try {
            val compileKotlin = getKotlinCompile(project, creationConfig)
            compileKotlin.configure { task: Task ->
                try {
                    // Enabling compose forces IR, so handle that case.
                    if (composeIsEnabled) {
                        setIrUsedInAnalytics(creationConfig, project)
                        return@configure
                    }

                    // We need reflection because AGP and KGP can be in different class loaders.
                    val getKotlinOptions = task.javaClass.getMethod("getKotlinOptions")
                    val taskOptions = getKotlinOptions.invoke(task)
                    val getUseIR = taskOptions.javaClass.getMethod("getUseIR")
                    if (getUseIR.invoke(taskOptions) as Boolean) {
                        setIrUsedInAnalytics(creationConfig, project)
                        return@configure
                    }

                    val kotlinDslOptions =
                            (extension as ExtensionAware).extensions.getByName("kotlinOptions")
                    if (getUseIR.invoke(kotlinDslOptions) as Boolean) {
                        setIrUsedInAnalytics(creationConfig, project)
                        return@configure
                    }
                } catch (ignored: Throwable) {
                }
            }
        } catch (ignored: Throwable) {
        }
    }
}

private fun setIrUsedInAnalytics(creationConfig: ComponentCreationConfig, project: Project) {
    val buildService: AnalyticsConfiguratorService =
            getBuildService(
                    creationConfig.services.buildServiceRegistry,
                    AnalyticsConfiguratorService::class.java)
                    .get()

    buildService.getVariantBuilder(project.path, creationConfig.name)
            .setKotlinOptions(GradleBuildVariant.KotlinOptions.newBuilder().setUseIr(true))
}
