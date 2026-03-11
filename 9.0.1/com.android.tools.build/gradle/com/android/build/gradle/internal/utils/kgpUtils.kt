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

import com.android.build.api.dsl.AndroidSourceSet
import com.android.build.gradle.internal.api.DefaultAndroidSourceDirectorySet
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.profile.AnalyticsConfiguratorService
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.options.BooleanOption
import com.android.builder.errors.IssueReporter.Type
import com.android.utils.appendCapitalized
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.SourceSet
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.plugin.CompilerPluginConfig
import org.jetbrains.kotlin.gradle.plugin.KotlinBaseApiPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.util.Locale

const val KOTLIN_ANDROID_PLUGIN_ID = "org.jetbrains.kotlin.android"
const val ANDROID_BUILT_IN_KOTLIN_PLUGIN_ID = "com.android.built-in-kotlin"
const val KOTLIN_KAPT_PLUGIN_ID = "org.jetbrains.kotlin.kapt"
const val ANDROID_BUILT_IN_KAPT_PLUGIN_ID = "com.android.legacy-kapt"
const val COMPOSE_COMPILER_PLUGIN_ID = "org.jetbrains.kotlin.plugin.compose"
const val KSP_PLUGIN_ID = "com.google.devtools.ksp"
const val KOTLIN_MPP_PLUGIN_ID = "org.jetbrains.kotlin.multiplatform"
internal const val ANDROID_KOTLIN_MPP_LIBRARY_PLUGIN_ID = "com.android.kotlin.multiplatform.library"

/**
 * Returns `true` if any of the Kotlin plugins (e.g., [KOTLIN_ANDROID_PLUGIN_ID] or
 * [KOTLIN_KAPT_PLUGIN_ID]) is applied *AND* it is loaded in the same classloader as AGP.
 *
 * Here is a likely scenario that causes AGP and KGP to be loaded in different classloaders:
 *   - The project uses buildSrc and the buildSrc project has an `implementation` dependency on AGP,
 *     but not KGP. Because AGP has a `compileOnly` dependency on KGP, at runtime the buildSrc
 *     classloader loads AGP only, not KGP.
 *   - The main project applies KGP. Gradle loads KGP in a child classloader of the buildSrc
 *     classloader.
 *   - Classes in the buildSrc classloader (AGP) can't access classes in the child classloader
 *     (KGP).
 *
 * If we want to check whether a specific Kotlin plugin is applied and check it across classloaders,
 * use a different method instead (e.g., [isKotlinAndroidPluginApplied] or
 * [isKotlinKaptPluginApplied]).
 */
fun isKotlinPluginAppliedInTheSameClassloader(project: Project): Boolean {
    return try {
        project.plugins.any { it is KotlinBasePluginWrapper }
    } catch (e: Throwable) {
        if (e is ClassNotFoundException || e is NoClassDefFoundError) {
            // KGP is either not applied or loaded in a different classloader than AGP
            false
        } else throw e
    }
}

/**
 * returns the kotlin plugin version, or null if plugin is not applied to this project or if plugin
 * is applied but version can't be determined.
 */
fun getProjectKotlinPluginKotlinVersion(project: Project): KotlinVersion? {
    val currVersion = getKotlinAndroidPluginVersion(project) ?: return null
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
fun getKotlinAndroidPluginVersion(project: Project): String? {
    val kotlinBaseApiPlugin =
        try {
            project.plugins.findPlugin(KotlinBaseApiPlugin::class.java)
        } catch (e: Throwable) {
            if (e is ClassNotFoundException || e is NoClassDefFoundError) null else throw e
        }
    val plugin =
        project.plugins.findPlugin("kotlin-android")
            ?: kotlinBaseApiPlugin
            ?: return null
    return getKotlinPluginVersionFromPlugin(plugin)
}

fun getKotlinPluginVersionFromPlugin(plugin: Plugin<*>): String? {
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
        null
    }
}

fun isKotlinAndroidPluginApplied(project: Project) =
        project.pluginManager.hasPlugin(KOTLIN_ANDROID_PLUGIN_ID)

fun isKotlinKaptPluginApplied(project: Project) =
        project.pluginManager.hasPlugin(KOTLIN_KAPT_PLUGIN_ID)

fun isComposeCompilerPluginApplied(project: Project) =
    project.pluginManager.hasPlugin(COMPOSE_COMPILER_PLUGIN_ID)

fun isKspPluginApplied(project: Project) =
    project.pluginManager.hasPlugin(KSP_PLUGIN_ID)

/** Configures Kotlin compile tasks corresponding to the given variants. */
fun configureKotlinCompileTasks(
   project: Project,
   creationConfigs: List<ComponentCreationConfig>,
   action: (KotlinCompile, ComponentCreationConfig) -> Unit
) {
    // Map tasks' name prefixes to their corresponding variants (e.g., `compileDebugKotlin` to
    // `debug`). Note that the keys are name prefixes because KMP projects have task names such as
    // `compileDebugKotlinAndroid` instead of `compileDebugKotlin`.
    val taskNamePrefixToVariant: Map<String, ComponentCreationConfig> =
        creationConfigs.associateBy { it.computeTaskNameInternal("compile", "Kotlin") }

    project.tasks.withType(KotlinCompile::class.java).configureEach { kotlinCompile ->
        // Note: We won't run `action` if we can't find a matching variant for the task (e.g.,
        // "compileKotlinJvm" is a KotlinCompile task for JVM in a KMP project, which does
        // not have a matching variant, and we don't need to handle it).
        taskNamePrefixToVariant
            .filterKeys { kotlinCompile.name.startsWith(it) }
            .entries.singleOrNull()?.value
            ?.let {
                action(kotlinCompile, it)
            }
    }
}

/** Records information from KGP for analytics. */
fun recordKgpPropertiesForAnalytics(project: Project, creationConfigs: List<ComponentCreationConfig>) {
    configureKotlinCompileTasks(project, creationConfigs) { kotlinCompile, creationConfig ->
        recordKotlinCompilePropertiesForAnalytics(kotlinCompile, creationConfig)
    }
}

fun recordKotlinCompilePropertiesForAnalytics(
    kotlinCompile: KotlinCompile,
    creationConfig: ComponentCreationConfig
) {
    getLanguageVersionUnsafe(kotlinCompile)?.let { languageVersion ->
        getBuildService(
            creationConfig.services.buildServiceRegistry,
            AnalyticsConfiguratorService::class.java
        ).get().getVariantBuilder(creationConfig.services.projectInfo.path, creationConfig.name)?.apply {
            setKotlinOptions(
                GradleBuildVariant.KotlinOptions.newBuilder().setLanguageVersion(languageVersion)
            )
        }
    }
}

/**
 * Attempts to return the language version of [kotlinCompile]. If we fail to access the property
 * (e.g., because the compile and runtime versions of KGP differ), this method will return `null`.
 */
private fun getLanguageVersionUnsafe(kotlinCompile: KotlinCompile): String? {
    return runCatching { kotlinCompile.compilerOptions.languageVersion.orNull?.version }.getOrNull()
        ?: runCatching { org.jetbrains.kotlin.gradle.dsl.KotlinVersion.DEFAULT.version }.getOrNull()
}

/** Add compose compiler extension args to Kotlin compile task. */
fun addComposeArgsToKotlinCompile(
    task: KotlinCompile,
    compilerExtension: FileCollection,
) {
    val kotlinVersion = getProjectKotlinPluginKotlinVersion(task.project)

    task.pluginClasspath.from(compilerExtension)

    task.maybeAddSourceInformationOption(kotlinVersion)

    task.compilerOptions.freeCompilerArgs.add("-Xallow-unstable-dependencies")
}

/**
 * Adds Kotlin compiler argument `-Xuse-inline-scopes-numbers` to improve the debugging experience
 * in Android Studio (b/372264148).
 *
 * Note: When the Kotlin compiler enables this option by default (KT-79401), we can remove this
 * method.
 */
fun maybeUseInlineScopesNumbers(
    task: KotlinCompile,
    creationConfig: ComponentCreationConfig,
    logger: Logger
) {
    // Only use -Xuse-inline-scopes-numbers for APKs. If it's used for an AAR (or any kind of
    // dependency), consumers wouldn't be able to use Kotlin < 2.0.
    if (!creationConfig.componentType.isApk || !creationConfig.debuggable) {
        return
    }

    logger.info("Adding -Xuse-inline-scopes-numbers Kotlin compiler flag for task ${task.path}")
    task.compilerOptions.freeCompilerArgs.add("-Xuse-inline-scopes-numbers")
}

private fun KotlinCompile.addPluginOption(
    pluginId: String,
    key: String,
    value: String
) {
    val freeCompilerArgs = compilerOptions.freeCompilerArgs.getOrElse(emptyList())
    val pluginOption = "plugin:$pluginId:$key"
    // Only add the plugin option if it was not previously added by the user (see b/318384658)
    if (freeCompilerArgs.any { it.startsWith("$pluginOption=") }) {
        return
    }
    pluginOptions.add(
        CompilerPluginConfig().apply {
            addPluginArgument(pluginId, SubpluginOption(key, value))
        }
    )
}

private fun KotlinVersion?.isVersionAtLeast(major: Int, minor: Int, patch: Int? = null): Boolean =
    when {
        // If this == null, it's likely a newer Kotlin version
        this == null -> true
        patch == null -> this.isAtLeast(major, minor)
        else -> this.isAtLeast(major, minor, patch)
    }

/**
 * Add the Compose Compiler Gradle Plugin's sourceInformation flag only if the kotlin version is
 * below 2.1.20-Beta2. Starting at that version, the Compose Compiler Gradle Plugin adds the flag
 * itself (see https://youtrack.jetbrains.com/issue/KT-74415).
 */
private fun KotlinCompile.maybeAddSourceInformationOption(kotlinVersion: KotlinVersion?) {
    if (kotlinVersion.isVersionAtLeast(2, 1, 20)) {
        return
    }
    addPluginOption(
        "androidx.compose.compiler.plugins.kotlin",
        "sourceInformation",
        "true"
    )
}

/**
 * Handles [KotlinSourceSet]s depending on whether built-in Kotlin is enabled (b/386221070).
 */
fun handleKotlinSourceSets(
    project: Project,
    projectServices: ProjectServices,
    androidSourceSets: NamedDomainObjectContainer<out AndroidSourceSet>,
    builtInKotlin: Boolean
) {
    // Skip this work if the `kotlin-multiplatform` plugin is applied
    if (project.pluginManager.hasPlugin(KOTLIN_MPP_PLUGIN_ID)) return

    fun reportErrorIfKotlinSourceSetsAreUsed(kotlinSourceSets: NamedDomainObjectContainer<KotlinSourceSet>) {
        androidSourceSets.forEach { androidSourceSet ->
            val kotlinSourceSet = kotlinSourceSets.findByName(androidSourceSet.name) ?: return@forEach

            if (kotlinSourceSet.kotlin.srcDirs.isNotEmpty()) {
                // Filter out default directories added by KGP
                val nonDefaultKotlinSrcDirs = kotlinSourceSet.kotlin.srcDirs.filterNot {
                    it.invariantSeparatorsPath.endsWith("${kotlinSourceSet.name}/kotlin")
                }

                if (nonDefaultKotlinSrcDirs.isNotEmpty()) {
                    projectServices.issueReporter.reportError(
                        Type.GENERIC,
                        "Using kotlin.sourceSets DSL to add Kotlin sources is not allowed with built-in Kotlin.\n" +
                                "Kotlin source set '${androidSourceSet.name}' contains: ${kotlinSourceSet.kotlin.srcDirs}\n" +
                                "Solution: Use android.sourceSets DSL instead.\n" +
                                "For more information, see https://developer.android.com/r/tools/built-in-kotlin\n" +
                                "To suppress this error, set ${BooleanOption.DISALLOW_KOTLIN_SOURCE_SETS.propertyName}=false in gradle.properties."
                    )
                }

                // Clear the Kotlin source set to indicate that it should not be used
                kotlinSourceSet.kotlin.setSrcDirs(emptySet<File>())
            }
        }
    }

    fun createKotlinSourceSetsPerAndroidSourceSet(kotlinSourceSets: NamedDomainObjectContainer<KotlinSourceSet>) {
        androidSourceSets.forEach {
            // Note: The source set may have been created by the user, so we call `maybeCreate`
            // instead of `create`
            kotlinSourceSets.maybeCreate(it.name)
        }
    }

    fun syncAndroidAndKotlinSourceSets() {
        val kotlinExtension = project.extensions.findByType(KotlinAndroidProjectExtension::class.java) ?: return
        val kotlinSourceSets = kotlinExtension.sourceSets

        androidSourceSets.forEach { androidSourceSet ->
            val kotlinSourceSet = kotlinSourceSets.findByName(androidSourceSet.name) ?: return@forEach

            kotlinSourceSet.kotlin.srcDirs((androidSourceSet.java as DefaultAndroidSourceDirectorySet).srcDirs)
            kotlinSourceSet.kotlin.srcDirs((androidSourceSet.kotlin as DefaultAndroidSourceDirectorySet).srcDirs)
            @Suppress("DEPRECATION")
            androidSourceSet.kotlin.setSrcDirs(kotlinSourceSet.kotlin.srcDirs)
        }
    }

    // When built-in Kotlin is enabled, Kotlin source sets should not be used (b/386221070).
    // However, to give plugins time to migrate, we still partially allow it when the user sets
    // `android.disallowKotlinSourceSets=false`.
    if (builtInKotlin) {
        val kotlinSourceSets = projectServices.builtInKotlinServices.kotlinAndroidProjectExtension.sourceSets
        if (projectServices.projectOptions.get(BooleanOption.DISALLOW_KOTLIN_SOURCE_SETS)) {
            // When built-in Kotlin is enabled and `android.disallowKotlinSourceSets=true`:
            //   1. DO NOT create one Kotlin source set for each Android source set (b/461767350)
            //   2. DO NOT sync Kotlin source sets with Android source sets
            //   3. REPORT AN ERROR if Kotlin source sets are used
            reportErrorIfKotlinSourceSetsAreUsed(kotlinSourceSets)
        } else {
            // When built-in Kotlin is enabled and `android.disallowKotlinSourceSets=false`:
            //   1. Create one Kotlin source set for each Android source set (b/461767350)
            //   2. DO NOT sync Kotlin source sets with Android source sets
            //   3. SILENTLY IGNORE Kotlin source sets if they are used
            createKotlinSourceSetsPerAndroidSourceSet(kotlinSourceSets)
        }
    } else {
        // When built-in Kotlin is disabled:
        //    1. Create one Kotlin source set for each Android source set (this is done by KGP)
        //    2. Sync Kotlin source sets with Android source sets
        //    3. Allow Kotlin source sets to be used
        syncAndroidAndKotlinSourceSets()
    }
}

/**
 * Handles the case where the user adds a Kotlin dependency without a version
 * (b/443037365, b/471410336). In that case, we will set a default version.
 *
 * This is similar to the `kotlin-android` plugin's behavior:
 * https://github.com/JetBrains/kotlin/blob/1cfbb801b77eaf00a7806631d29415dfd40f2fcd/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/internal/KotlinDependenciesManagement.kt#L60-L82
 */
internal fun handleKotlinDependenciesWithoutVersion(
    project: Project,
    projectServices: ProjectServices,
    androidSourceSets: NamedDomainObjectContainer<out AndroidSourceSet>,
) {
    val defaultKotlinVersion =
        projectServices.builtInKotlinServices.kotlinAndroidProjectExtension.coreLibrariesVersion
    val sourceSetsConfigurations = androidSourceSets.flatMap { it.getConfigurations(project) }

    sourceSetsConfigurations.forEach { configuration ->
        configuration.dependencies.forEach {
            if (it is ExternalDependency && it.group == KOTLIN_GROUP && it.version == null) {
                configuration.dependencyConstraints.add(
                    project.dependencies.constraints.create("$KOTLIN_GROUP:${it.name}") { constraint ->
                        constraint.version { versionConstraint ->
                            // Note: We shouldn't use "versionConstraint.prefer()" because it does
                            // not work well with Maven publishing (b/450851465).
                            versionConstraint.require(defaultKotlinVersion)
                        }
                    }
                )
            }
        }
    }
}

/**
 * Adds kotlin-stdlib dependency to the `api` configurations if it's not yet added by the user, and
 * they haven't set `kotlin.stdlib.default.dependency=false` (b/452246814).
 *
 * This is similar to the `kotlin-android` plugin's behavior:
 *   - https://youtrack.jetbrains.com/issue/KT-38221
     - https://github.com/JetBrains/kotlin/blob/fd1d3d967df9eab306bdc9707229bd22a2d5d1c2/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/internal/stdlibDependencyManagement.kt#L105-L111
 */
internal fun maybeAddKotlinStdlibDependency(
    project: Project,
    projectServices: ProjectServices,
    androidSourceSets: NamedDomainObjectContainer<out AndroidSourceSet>,
) {

    fun Configuration.hasKotlinStdlib(): Boolean = dependencies.any {
        it is ExternalDependency && it.group == KOTLIN_GROUP && it.name == KOTLIN_STDLIB
    }

    fun kotlinStdlibDefaultDependencyProperty(): Boolean? =
        project.providers.gradleProperty("kotlin.stdlib.default.dependency")
            .orNull?.lowercase(Locale.US)?.toBooleanStrictOrNull()

    // If the user has added kotlin-stdlib to one of the configurations of the main source set
    // or if they set `kotlin.stdlib.default.dependency=false`, then do not add kotlin-stdlib
    // automatically
    val mainSourceSet = androidSourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
    if (mainSourceSet.getConfigurations(project).any { it.hasKotlinStdlib() }
        || kotlinStdlibDefaultDependencyProperty() == false
    ) {
        return
    }

    // Otherwise, add kotlin-stdlib to the `api` configuration of the main source set
    val apiConfiguration = project.configurations.getByName(mainSourceSet.apiConfigurationName)
    val defaultKotlinVersion =
        projectServices.builtInKotlinServices.kotlinAndroidProjectExtension.coreLibrariesVersion
    apiConfiguration.dependencies.add(
        project.dependencies.create("$KOTLIN_GROUP:$KOTLIN_STDLIB:$defaultKotlinVersion")
    )
}

/**
 * Returns all [Configuration]s associated with this [AndroidSourceSet] where the user can declare
 * dependencies.
 */
private fun AndroidSourceSet.getConfigurations(project: Project): List<Configuration> {
    return listOf(
        apiConfigurationName,
        implementationConfigurationName,
        compileOnlyConfigurationName,
        compileOnlyApiConfigurationName,
        runtimeOnlyConfigurationName,
        annotationProcessorConfigurationName,
        kaptConfigurationName
    ).mapNotNull {
        project.configurations.findByName(it)
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

private const val KOTLIN_GROUP = "org.jetbrains.kotlin"
private const val KOTLIN_STDLIB = "kotlin-stdlib"
