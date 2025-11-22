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
import com.android.utils.appendCapitalized
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.SourceSet
import org.jetbrains.kotlin.gradle.plugin.CompilerPluginConfig
import org.jetbrains.kotlin.gradle.plugin.KotlinBaseApiPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Locale

const val KOTLIN_ANDROID_PLUGIN_ID = "org.jetbrains.kotlin.android"
const val ANDROID_BUILT_IN_KOTLIN_PLUGIN_ID = "com.android.built-in-kotlin"
const val KOTLIN_KAPT_PLUGIN_ID = "org.jetbrains.kotlin.kapt"
const val ANDROID_BUILT_IN_KAPT_PLUGIN_ID = "com.android.legacy-kapt"
const val COMPOSE_COMPILER_PLUGIN_ID = "org.jetbrains.kotlin.plugin.compose"
const val KSP_PLUGIN_ID = "com.google.devtools.ksp"
const val KOTLIN_MPP_PLUGIN_ID = "org.jetbrains.kotlin.multiplatform"
private val KOTLIN_MPP_PLUGIN_IDS = listOf("kotlin-multiplatform", KOTLIN_MPP_PLUGIN_ID)
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
 * Get information about Kotlin sources from KGP, until there is a KGP version that can work
 * with AGP which supports Kotlin source directories.
 */
@Suppress("UNCHECKED_CAST")
fun syncAgpAndKgpSources(
    project: Project,
    projectServices: ProjectServices,
    androidSourceSets: NamedDomainObjectContainer<out AndroidSourceSet>,
    useBuiltInKotlinSupport: Boolean
) {
    // Create Kotlin source sets if built-in Kotlin support is available
    // (similar to what `kotlin-android` plugin does at
    // org.jetbrains.kotlin.gradle.plugin.sources.android.KotlinAndroidSourceSetFactory)
    if (useBuiltInKotlinSupport) {
        val kotlinSourceSetContainer =
            projectServices.builtInKotlinServices.kotlinAndroidProjectExtension.sourceSets
        androidSourceSets.forEach {
            // The source set may have been created by the user, so we call `maybeCreate` instead of
            // `create`
            kotlinSourceSetContainer.maybeCreate(it.name)
        }
    }

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

    androidSourceSets.configureEach {
        val kotlinSourceSet = it.findKotlinSourceSet() ?: return@configureEach
        if (hasMpp) {
            it.kotlin.setSrcDirs(kotlinSourceSet.srcDirs)
        } else if (!useBuiltInKotlinSupport) {
            // Only sync AGP and KGP source sets if built-in Kotlin is disabled (b/386221070)
            kotlinSourceSet.srcDirs((it.java as DefaultAndroidSourceDirectorySet).srcDirs)
            kotlinSourceSet.srcDirs((it.kotlin as DefaultAndroidSourceDirectorySet).srcDirs)
            it.kotlin.setSrcDirs(kotlinSourceSet.srcDirs)
        }
    }
}

/**
 * Adds kotlin-stdlib dependency to the `api` configurations if it's not yet added by the user.
 *
 * This is to match the `kotlin-android` plugin's behavior
 * (see https://youtrack.jetbrains.com/issue/KT-38221).
 *
 * Note: When KGP provides an API to do this (https://youtrack.jetbrains.com/issue/KT-73997), we can
 * remove this method.
 */
internal fun maybeAddKotlinStdlibDependency(
    project: Project,
    projectServices: ProjectServices,
    androidSourceSets: NamedDomainObjectContainer<out AndroidSourceSet>,
) {
    val kotlinServices = projectServices.builtInKotlinServices
    val kotlinVersion = kotlinServices.kotlinAndroidProjectExtension.coreLibrariesVersion

    // This is similar to https://github.com/JetBrains/kotlin/blob/1cfbb801b77eaf00a7806631d29415dfd40f2fcd/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/internal/KotlinDependenciesManagement.kt#L60-L82
    fun handleKotlinStdlibWithoutVersion() {
        project.configurations.configureEach { configuration ->
            configuration.withDependencies { dependencySet ->
                dependencySet.forEach { dependency ->
                    if (dependency is ExternalDependency
                        && dependency.group == KOTLIN_GROUP
                        && dependency.name in KOTLIN_STDLIB_MODULES
                        && dependency.version.isNullOrEmpty()
                    ) {
                        configuration.dependencyConstraints.add(
                            project.dependencies.constraints.create(dependency.module.toString()) { constraint ->
                                constraint.version { it.require(kotlinVersion) }
                            }
                        )
                    }
                }
            }
        }
    }

    fun Configuration.hasKotlinStdlib(): Boolean {
        return allDependencies.any {
            it is ExternalDependency
                    && it.group == KOTLIN_GROUP
                    && it.name in KOTLIN_STDLIB_MODULES
        }
    }

    fun hasKotlinStdlibDependency(): Boolean {
        return androidSourceSets.any {
            project.configurations.getByName(it.apiConfigurationName).hasKotlinStdlib()
            project.configurations.getByName(it.implementationConfigurationName).hasKotlinStdlib()
        }
    }

    fun kotlinStdlibDefaultDependencyProperty(): Boolean? =
        project.providers.gradleProperty("kotlin.stdlib.default.dependency")
            .orNull?.lowercase(Locale.US)?.toBooleanStrictOrNull()

    fun Configuration.addKotlinStdlib() {
        dependencies.add(project.dependencies.create("$KOTLIN_GROUP:$KOTLIN_STDLIB:$kotlinVersion"))
    }

    // If the user adds kotlin-stdlib without a version, then set a version (b/443037365)
    handleKotlinStdlibWithoutVersion()

    // If the user has added kotlin-stdlib or if they set `kotlin.stdlib.default.dependency=false`,
    // then do not add kotlin-stdlib automatically
    if (hasKotlinStdlibDependency() || kotlinStdlibDefaultDependencyProperty() == false) {
        return
    }

    // Otherwise, add kotlin-stdlib to the `api` configuration of the main source set.
    // This is similar to https://github.com/JetBrains/kotlin/blob/fd1d3d967df9eab306bdc9707229bd22a2d5d1c2/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/internal/stdlibDependencyManagement.kt#L105-L111
    val mainSourceSet = androidSourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
    val apiConfiguration = project.configurations.getByName(mainSourceSet.apiConfigurationName)
    apiConfiguration.addKotlinStdlib()
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
private val KOTLIN_STDLIB_MODULES =
    setOf("kotlin-stdlib", "kotlin-stdlib-jdk7", "kotlin-stdlib-jdk8")
