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
import com.android.build.gradle.internal.api.DefaultAndroidSourceDirectorySet
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.LibraryCreationConfig
import com.android.build.gradle.internal.profile.AnalyticsConfiguratorService
import com.android.build.gradle.internal.services.getBuildService
import com.android.utils.appendCapitalized
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.HasConvention
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

const val KOTLIN_ANDROID_PLUGIN_ID = "org.jetbrains.kotlin.android"
const val KOTLIN_KAPT_PLUGIN_ID = "org.jetbrains.kotlin.kapt"
private val KOTLIN_MPP_PLUGIN_IDS = listOf("kotlin-multiplatform", "org.jetbrains.kotlin.multiplatform")

/**
 * Returns `true` if any of the Kotlin plugins is applied (there are many Kotlin plugins). If we
 * want to check a specific Kotlin plugin, use another method (e.g.,
 * [isKotlinAndroidPluginApplied]).
 */
fun isKotlinPluginApplied(project: Project): Boolean {
    return try {
        project.plugins.any { it is KotlinBasePluginWrapper }
    } catch (ignored: Throwable) {
        // This may fail if Kotlin plugin is not applied, as KotlinBasePluginWrapper
        // will not be present at runtime. This means that the Kotlin plugin is not applied.
        false
    }
}

fun isKotlinAndroidPluginApplied(project: Project) =
        project.pluginManager.hasPlugin(KOTLIN_ANDROID_PLUGIN_ID)

fun isKotlinKaptPluginApplied(project: Project) =
        project.pluginManager.hasPlugin(KOTLIN_KAPT_PLUGIN_ID)

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
            ?.setKotlinOptions(GradleBuildVariant.KotlinOptions.newBuilder().setUseIr(true))
}

/** Add compose compiler extension args to Kotlin compile task. */
fun addComposeArgsToKotlinCompile(
        task: Task,
        creationConfig: ComponentCreationConfig,
        compilerExtension: FileCollection,
        useLiveLiterals: Boolean) {
    task as KotlinCompile
    // Add as input
    task.inputs.files(compilerExtension)
            .withPropertyName("composeCompilerExtension")
            .withNormalizer(ClasspathNormalizer::class.java)

    // Add useLiveLiterals as an input
    task.inputs.property("useLiveLiterals", useLiveLiterals)

    val debuggable = if (creationConfig is ApkCreationConfig || creationConfig is LibraryCreationConfig) {
        creationConfig.debuggable
    } else {
        false
    }

    task.doFirst {
        it as KotlinCompile
        it.kotlinOptions.useIR = true
        val extraFreeCompilerArgs = mutableListOf(
                "-Xplugin=${compilerExtension.files.first().absolutePath}",
                "-XXLanguage:+NonParenthesizedAnnotationsOnFunctionalTypes",
                "-P", "plugin:androidx.compose.plugins.idea:enabled=true",
                "-Xallow-unstable-dependencies"
        )
        if (debuggable) {
            extraFreeCompilerArgs += listOf(
                    "-P",
                    "plugin:androidx.compose.compiler.plugins.kotlin:sourceInformation=true")

            if (useLiveLiterals) {
                extraFreeCompilerArgs += listOf(
                        "-P",
                        "plugin:androidx.compose.compiler.plugins.kotlin:liveLiterals=true")
            }
        }
        it.kotlinOptions.freeCompilerArgs += extraFreeCompilerArgs
    }
}

/**
 * Get information about Kotlin sources from KGP, until there is a KGP version that can work
 * with AGP which supports Kotlin source directories.
 */
fun syncAgpAndKgpSources(project: Project, sourceSets: NamedDomainObjectContainer<com.android.build.gradle.api.AndroidSourceSet>) {
    val hasMpp = KOTLIN_MPP_PLUGIN_IDS.any { project.pluginManager.hasPlugin(it) }
    sourceSets.all {
        val kotlinConvention = (it as HasConvention).convention.plugins["kotlin"]
        if (kotlinConvention!=null) {
            val sourceDir =
                    kotlinConvention::class.java.getMethod("getKotlin")
                            .invoke(kotlinConvention) as SourceDirectorySet

            if (!hasMpp) {
                sourceDir.srcDirs((it.kotlin as DefaultAndroidSourceDirectorySet).srcDirs)
            }
            it.kotlin.setSrcDirs(listOf(sourceDir.sourceDirectories))
        }
    }
}

/**
 * Attempts to find the corresponding `kapt` configurations for the source sets of the given
 * variant. The returned list may be incomplete or empty if unsuccessful.
 */
fun findKaptConfigurationsForVariant(
    project: Project,
    creationConfig: ComponentCreationConfig
): List<Configuration> {
    return creationConfig.variantSources.sortedSourceProviders.mapNotNull { sourceSet ->
        val kaptConfigurationName = if (sourceSet.name != SourceSet.MAIN_SOURCE_SET_NAME)
            "kapt".appendCapitalized(sourceSet.name)
        else
            "kapt"
        project.configurations.findByName(kaptConfigurationName)
    }
}
