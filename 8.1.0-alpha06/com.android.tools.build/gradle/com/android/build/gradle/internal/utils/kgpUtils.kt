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
import com.android.build.gradle.api.AndroidSourceSet
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
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.SourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

const val KOTLIN_ANDROID_PLUGIN_ID = "org.jetbrains.kotlin.android"
const val KOTLIN_KAPT_PLUGIN_ID = "org.jetbrains.kotlin.kapt"
const val KSP_PLUGIN_ID = "com.google.devtools.ksp"
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
        // KGP 1.7.0+ has getPluginVersion and older version have getKotlinPluginVersion
        val method = plugin.javaClass.methods.first {
            it.name == "getKotlinPluginVersion" || it.name == "getPluginVersion"
        }
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

fun isKspPluginApplied(project: Project) =
    project.pluginManager.hasPlugin(KSP_PLUGIN_ID)

/** Configure Kotlin compile tasks for the current project and the current variant. */
fun configureKotlinCompileForProject(
    project: Project,
    creationConfig: ComponentCreationConfig,
    action: (KotlinCompile) -> Unit
) {
    // KGP has names like compileDebugKotlin but KMP may create compileDebugKotlinAndroid
    // so make sure to match both.
    val expectedTaskNameOrPrefix = creationConfig.computeTaskName("compile", "Kotlin")
    project.tasks.withType(KotlinCompile::class.java).configureEach {
        if (it.project == project && it.name.startsWith(expectedTaskNameOrPrefix)) {
            action(it)
        }
    }
}

/* Record information if IR backend is enabled. */
fun recordIrBackendForAnalytics(allPropertiesList: List<ComponentCreationConfig>, extension: BaseExtension, project: Project, composeIsEnabled: Boolean) {
    for (creationConfig in allPropertiesList) {
        try {
            configureKotlinCompileForProject(project, creationConfig) { task: KotlinCompile ->
                try {
                    // Enabling compose forces IR, so handle that case.
                    if (composeIsEnabled) {
                        setIrUsedInAnalytics(creationConfig, project, useIr = true)
                        return@configureKotlinCompileForProject
                    }

                    val kotlinVersion = getProjectKotlinPluginKotlinVersion(project)
                    if (kotlinVersion != null) {
                        val irBackendEnabled = !getKotlinOptionsValueIfSet(task, extension, "getUseOldBackend", false)
                        setIrUsedInAnalytics(creationConfig, project, irBackendEnabled)
                    }
                } catch (ignored: Throwable) {
                }
            }
        } catch (ignored: Throwable) {
        }
    }
}

@Suppress("SameParameterValue")
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

private fun setIrUsedInAnalytics(
    creationConfig: ComponentCreationConfig,
    project: Project,
    useIr: Boolean
) {
    val buildService: AnalyticsConfiguratorService =
            getBuildService(
                    creationConfig.services.buildServiceRegistry,
                    AnalyticsConfiguratorService::class.java)
                    .get()

    buildService.getVariantBuilder(project.path, creationConfig.name)
            ?.setKotlinOptions(GradleBuildVariant.KotlinOptions.newBuilder().setUseIr(useIr))
}

/** Add compose compiler extension args to Kotlin compile task. */
fun addComposeArgsToKotlinCompile(
    task: KotlinCompile,
    creationConfig: ComponentCreationConfig,
    compilerExtension: FileCollection,
    useLiveLiterals: Boolean
) {
    val debuggable = if (creationConfig is ApkCreationConfig || creationConfig is LibraryCreationConfig) {
        creationConfig.debuggable
    } else {
        false
    }

    val kotlinVersion = getProjectKotlinPluginKotlinVersion(task.project)

    task.addPluginClasspath(kotlinVersion, compilerExtension)

    task.addPluginOption("androidx.compose.plugins.idea", "enabled", "true")
    if (debuggable) {
        task.addPluginOption("androidx.compose.compiler.plugins.kotlin", "sourceInformation", "true")
        if (useLiveLiterals) {
            task.addPluginOption("androidx.compose.compiler.plugins.kotlin", "liveLiterals", "true")
        }
    }

    task.kotlinOptions.freeCompilerArgs += "-Xallow-unstable-dependencies"
}

private fun KotlinCompile.addPluginClasspath(
    kotlinVersion: KotlinVersion?, compilerExtension: FileCollection
) {
    // If kotlinVersion == null, it's likely a newer Kotlin version
    if (kotlinVersion == null || kotlinVersion.isAtLeast(1, 7)) {
        pluginClasspath.from(compilerExtension)
    } else {
        inputs.files(compilerExtension)
            .withPropertyName("composeCompilerExtension")
            .withNormalizer(ClasspathNormalizer::class.java)
        doFirst {
            (it as KotlinCompile).kotlinOptions.freeCompilerArgs +=
                "-Xplugin=${compilerExtension.files.single().path}"
        }
    }
}

private fun KotlinCompile.addPluginOption(pluginId: String, key: String, value: String) {
    // Once https://youtrack.jetbrains.com/issue/KT-54160 is fixed, we will be able to use the new
    // API to add plugin options as follows:
    //     // If kotlinVersion == null, it's likely a newer Kotlin version
    //     if (kotlinVersion == null || kotlinVersion.isAtLeast(X, Y)) {
    //         pluginOptions.add(CompilerPluginConfig().apply {
    //             addPluginArgument(pluginId, SubpluginOption(key, value))
    //         })
    //     } else { ... }
    // For now, continue to use the old way to add plugin options.
    kotlinOptions.freeCompilerArgs += listOf("-P", "plugin:$pluginId:$key=$value")
}

/**
 * Get information about Kotlin sources from KGP, until there is a KGP version that can work
 * with AGP which supports Kotlin source directories.
 */
@Suppress("UNCHECKED_CAST")
fun syncAgpAndKgpSources(
    project: Project, sourceSets: NamedDomainObjectContainer<out AndroidSourceSet>
) {
    val hasMpp = KOTLIN_MPP_PLUGIN_IDS.any { project.pluginManager.hasPlugin(it) }
    // TODO(b/246910305): Remove once it is gone from Gradle
    val hasConventionSupport = try {
        Class.forName("org.gradle.api.internal.HasConvention")
        true
    } catch (ignored: Throwable) {
        false
    }

    val kotlinSourceSets by lazy {
        val kotlinExtension = project.extensions.findByName("kotlin") ?: return@lazy null

        kotlinExtension::class.java.getMethod("getSourceSets")
            .invoke(kotlinExtension) as NamedDomainObjectContainer<Any>
    }

    fun AndroidSourceSet.findKotlinSourceSet(): SourceDirectorySet? {
        if (hasMpp) {
            if (!hasConventionSupport) {
                // Newer versions of MPP will invoke AGP APIs to add the kotlin src dirs,
                // so we can skip doing that.
                return null
            }
            val convention = this::class.java.getMethod("getConvention").invoke(this)
            val plugins =
                convention::class.java.getMethod("getPlugins")
                    .invoke(convention) as Map<String, Any>
            val kotlinConvention = plugins["kotlin"] ?: return null

            return kotlinConvention::class.java.getMethod("getKotlin")
                .invoke(kotlinConvention) as SourceDirectorySet
        } else {
            val kotlinSourceSet: Any = kotlinSourceSets?.findByName(this.name) ?: return null

            return kotlinSourceSet::class.java.getMethod("getKotlin")
                .invoke(kotlinSourceSet) as SourceDirectorySet
        }
    }

    sourceSets.all {
        val kotlinSourceSet = it.findKotlinSourceSet()
        if (kotlinSourceSet != null) {
            if (!hasMpp) {
                kotlinSourceSet.srcDirs((it.kotlin as DefaultAndroidSourceDirectorySet).srcDirs)
            }
            it.kotlin.setSrcDirs(kotlinSourceSet.srcDirs)
        }
    }
}

/**
 * Attempts to find the corresponding `kapt` or `ksp` configurations for the source sets of the
 * given variant. The returned list may be incomplete or empty if unsuccessful.
 */
fun findKaptOrKspConfigurationsForVariant(
    creationConfig: ComponentCreationConfig,
    kaptOrKsp: String
): List<Configuration> {
    return creationConfig.sources.sourceProviderNames.mapNotNull { sourceSetName ->
        val configurationName = if (sourceSetName != SourceSet.MAIN_SOURCE_SET_NAME)
            kaptOrKsp.appendCapitalized(sourceSetName)
        else
            kaptOrKsp
        creationConfig.services.configurations.findByName(configurationName)
    }
}
