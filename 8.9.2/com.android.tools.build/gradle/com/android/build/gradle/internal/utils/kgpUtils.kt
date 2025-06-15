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
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.LibraryCreationConfig
import com.android.build.gradle.internal.profile.AnalyticsConfiguratorService
import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.internal.services.getBuildService
import com.android.ide.common.gradle.Version
import com.android.utils.appendCapitalized
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.SourceSet
import org.jetbrains.kotlin.gradle.plugin.CompilerPluginConfig
import org.jetbrains.kotlin.gradle.plugin.KotlinBaseApiPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Locale

const val KOTLIN_ANDROID_PLUGIN_ID = "org.jetbrains.kotlin.android"
const val ANDROID_BUILT_IN_KOTLIN_PLUGIN_ID = "com.android.experimental.built-in-kotlin"
const val KOTLIN_KAPT_PLUGIN_ID = "org.jetbrains.kotlin.kapt"
const val ANDROID_BUILT_IN_KAPT_PLUGIN_ID = "com.android.legacy-kapt"
const val COMPOSE_COMPILER_PLUGIN_ID = "org.jetbrains.kotlin.plugin.compose"
const val KSP_PLUGIN_ID = "com.google.devtools.ksp"
const val KOTLIN_MPP_PLUGIN_ID = "org.jetbrains.kotlin.multiplatform"
private val KOTLIN_MPP_PLUGIN_IDS = listOf("kotlin-multiplatform", KOTLIN_MPP_PLUGIN_ID)

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
    return runCatching { kotlinCompile.kotlinOptions.languageVersion }.getOrNull()
        ?: runCatching { kotlinCompile.compilerOptions.languageVersion.orNull?.version }.getOrNull()
        ?: runCatching { org.jetbrains.kotlin.gradle.dsl.KotlinVersion.DEFAULT.version }.getOrNull()
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

    if (debuggable) {
        task.addPluginOption(
            kotlinVersion,
            "androidx.compose.compiler.plugins.kotlin",
            "sourceInformation",
            "true"
        )
        if (useLiveLiterals) {
            task.addPluginOption(
                kotlinVersion,
                "androidx.compose.compiler.plugins.kotlin",
                "liveLiterals",
                "true"
            )
        }
    }

    if (kotlinVersion.isVersionAtLeast(1, 8)) {
        task.compilerOptions.freeCompilerArgs.add("-Xallow-unstable-dependencies")
    } else {
        task.kotlinOptions.freeCompilerArgs += "-Xallow-unstable-dependencies"
    }
}

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

    val kotlinVersion = getProjectKotlinPluginKotlinVersion(task.project)
    if (kotlinVersion == null || !kotlinVersion.isVersionAtLeast(2, 0)) {
        return
    }

    logger.info("Adding -Xuse-inline-scopes-numbers Kotlin compiler flag for task ${task.path}")
    task.compilerOptions.freeCompilerArgs.add("-Xuse-inline-scopes-numbers")
}

private fun KotlinCompile.addPluginClasspath(
    kotlinVersion: KotlinVersion?, compilerExtension: FileCollection
) {
    if (kotlinVersion.isVersionAtLeast(1, 7)) {
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

private fun KotlinCompile.addPluginOption(
    kotlinVersion: KotlinVersion?,
    pluginId: String,
    key: String,
    value: String
) {
    val freeCompilerArgs =
        if (kotlinVersion.isVersionAtLeast(1, 8)) {
            compilerOptions.freeCompilerArgs.getOrElse(emptyList())
        } else {
            kotlinOptions.freeCompilerArgs
        }
    val pluginOption = "plugin:$pluginId:$key"
    // Only add the plugin option if it was not previously added by the user (see b/318384658)
    if (freeCompilerArgs.any { it.startsWith("$pluginOption=") }) {
        return
    }
    if (kotlinVersion.isVersionAtLeast(1, 9, 20)) {
         pluginOptions.add(
             CompilerPluginConfig().apply {
                 addPluginArgument(pluginId, SubpluginOption(key, value))
             }
         )
    } else {
        kotlinOptions.freeCompilerArgs += listOf("-P", "$pluginOption=$value")
    }
}

private fun KotlinVersion?.isVersionAtLeast(major: Int, minor: Int, patch: Int? = null): Boolean =
    when {
        // If this == null, it's likely a newer Kotlin version
        this == null -> true
        patch == null -> this.isAtLeast(major, minor)
        else -> this.isAtLeast(major, minor, patch)
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
            val extensions = this::class.java.getMethod("getExtensions").invoke(this)
            val plugins = extensions::class.java.getMethod("getAsMap").invoke(extensions) as Map<String, Any>
            val kotlinConvention = plugins["kotlin"] ?: return null

            return kotlinConvention as SourceDirectorySet
        } else {
            val kotlinSourceSet: Any = kotlinSourceSets?.findByName(this.name) ?: return null

            return kotlinSourceSet::class.java.getMethod("getKotlin")
                .invoke(kotlinSourceSet) as SourceDirectorySet
        }
    }

    sourceSets.configureEach {
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

fun findKotlinBaseApiPlugin(projectInfo: ProjectInfo): KotlinBaseApiPlugin? =
    try {
        projectInfo.findPlugin(KotlinBaseApiPlugin::class.java)
    } catch (e: Throwable) {
        if (e is ClassNotFoundException || e is NoClassDefFoundError) null else throw e
    }

/**
 * Add the kotlin stdlib to the compile and runtime classpaths, if it's not added by the user.
 * Similar to https://youtrack.jetbrains.com/issue/KT-38221.
 *
 * TODO(b/341765853): Request API from Jetbrains to handle this.
 */
internal fun maybeAddKotlinStdlibDependency(
    project: Project,
    creationConfig: ComponentCreationConfig
) {
    // Honor "kotlin.stdlib.default.dependency=false"
    val kotlinStdlibDefaultDependencyProperty =
        project.providers.gradleProperty("kotlin.stdlib.default.dependency")
    if (kotlinStdlibDefaultDependencyProperty.orNull?.lowercase(Locale.US) == "false") {
        return
    }

    val kotlinStdlibVersion =
        creationConfig.global.kotlinAndroidProjectExtension?.coreLibrariesVersion
            ?: creationConfig.services.kotlinServices?.kgpVersion

    fun Configuration.hasKotlinStdlibDependency(): Boolean {
        val externalDependencies = this.allDependencies.matching { it !is ProjectDependency }
        return externalDependencies.any { dependency ->
            dependency.group == KOTLIN_GROUP && dependency.name in KOTLIN_STDLIB_MODULES
        }
    }

    fun Configuration.maybeAddKotlinStdlibDependency() {
        if (this.hasKotlinStdlibDependency()) {
            return
        }
        // Use kotlin-stdlib-jdk8 if < Kotlin 1.9.20 (Same behavior as KGP)
        val moduleName =
            if (kotlinStdlibVersion?.let {Version.parse(it) < Version.parse("1.9.20") } == true) {
                "kotlin-stdlib-jdk8"
            } else {
                "kotlin-stdlib"
            }
        this.dependencies
            .add(project.dependencies.kotlinDependency(moduleName, kotlinStdlibVersion))
    }

    creationConfig.variantDependencies.compileClasspath.maybeAddKotlinStdlibDependency()
    creationConfig.variantDependencies.runtimeClasspath.maybeAddKotlinStdlibDependency()
}

private fun DependencyHandler.kotlinDependency(moduleName: String, versionOrNull: String?) =
    create("$KOTLIN_GROUP:$moduleName${versionOrNull?.let { ":$it" }.orEmpty()}")

private const val KOTLIN_GROUP = "org.jetbrains.kotlin"
private val KOTLIN_STDLIB_MODULES =
    setOf("kotlin-stdlib", "kotlin-stdlib-jdk7", "kotlin-stdlib-jdk8")
// The minimum version of KGP required to be on the buildscript classpath for built-in kotlin
// support in AGP
const val MINIMUM_BUILT_IN_KOTLIN_VERSION = "1.9.20"
