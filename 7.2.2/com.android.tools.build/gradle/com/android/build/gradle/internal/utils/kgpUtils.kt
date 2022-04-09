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

private val irBackendByDefault = KotlinVersion(1, 5)
private val irBackendIntroduced = KotlinVersion(1, 3, 70)

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

/**
 * returns the kotlin plugin version, or null if plugin is not applied to this project or if plugin
 * is applied but version can't be determined.
 */
fun getProjectKotlinPluginKotlinVersion(project: Project): KotlinVersion? {
    val currVersion = getKotlinPluginVersion(project)
    if (currVersion == null || currVersion == "unknown")
        return null
    return parseKotlinVersion(currVersion)
}

fun parseKotlinVersion(currVersion: String): KotlinVersion? {
    return try {
        val parts = currVersion.split(".")
        val major = parts[0]
        val minor = parts[1]
        // We ignore the extensions, eg. "-RC".
        val patch = parts[2].substringBefore('-')
        return KotlinVersion(
                major.toInt(),
                minor.toInt(),
                patch.toInt()
        )
    } catch (e: Throwable) {
        null
    }
}

/**
 * returns the kotlin plugin version as string, or null if plugin is not applied to this project, or
 * "unknown" if plugin is applied but version can't be determined.
 */
fun getKotlinPluginVersion(project: Project): String? {
    val plugin = project.plugins.findPlugin("kotlin-android") ?: return null
    return try {
        // No null checks below because we're catching all exceptions.
        val method = plugin.javaClass.getMethod("getKotlinPluginVersion")
        method.isAccessible = true
        method.invoke(plugin).toString()
    } catch (e: Throwable) {
        // Defensively catch all exceptions because we don't want it to crash
        // if kotlin plugin code changes unexpectedly.
        "unknown"
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

                    val kotlinVersion = getProjectKotlinPluginKotlinVersion(project)
                    val irBackendEnabled = when {
                        kotlinVersion == null -> return@configure
                        kotlinVersion >= irBackendByDefault -> {
                            !getKotlinOptionsValueIfSet(task, extension, "getUseOldBackend", false)
                        }
                        kotlinVersion >= irBackendIntroduced -> {
                            getKotlinOptionsValueIfSet(task, extension, "getUseIR", false)
                        }
                        else -> null
                    }
                    irBackendEnabled?.let {
                        setIrUsedInAnalytics(creationConfig, project)
                    }
                } catch (ignored: Throwable) {
                }
            }
        } catch (ignored: Throwable) {
        }
    }
}

private fun getKotlinOptionsValueIfSet(task: Task, extension: BaseExtension, methodName: String, defaultValue: Boolean): Boolean {
    // We need reflection because AGP and KGP can be in different class loaders.
    val getKotlinOptions = task.javaClass.getMethod("getKotlinOptions")
    val taskOptions = getKotlinOptions.invoke(task)
    val method = taskOptions.javaClass.getMethod(methodName)
    val taskValue = method.invoke(taskOptions) as Boolean
    if (defaultValue != taskValue) return taskValue

    // If not specified on the task, check global DSL extension
    val kotlinDslOptions = (extension as ExtensionAware).extensions.getByName("kotlinOptions")
    val globalValue = method.invoke(kotlinDslOptions) as Boolean
    if (defaultValue != globalValue) return globalValue

    return defaultValue
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

    val kotlinVersion = getProjectKotlinPluginKotlinVersion(task.project)
    task.doFirst {
        it as KotlinCompile
        kotlinVersion?.let { version ->
            when {
                version >= irBackendByDefault -> return@let // IR is enabled by default
                version >= irBackendIntroduced -> it.kotlinOptions.useIR = true
            }
        }
        val extraFreeCompilerArgs = mutableListOf(
                "-Xplugin=${compilerExtension.files.first().absolutePath}",
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
fun syncAgpAndKgpSources(project: Project, sourceSets: NamedDomainObjectContainer<out com.android.build.gradle.api.AndroidSourceSet>) {
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
