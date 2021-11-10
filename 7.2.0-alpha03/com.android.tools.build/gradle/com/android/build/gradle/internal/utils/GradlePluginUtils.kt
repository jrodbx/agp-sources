/*
 * Copyright (C) 2018 The Android Open Source Project
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

@file:JvmName("GradlePluginUtils")

package com.android.build.gradle.internal.utils

import com.android.builder.errors.IssueReporter
import com.android.ide.common.repository.GradleVersion
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.initialization.dsl.ScriptHandler.CLASSPATH_CONFIGURATION
import java.net.JarURLConnection
import java.util.regex.Pattern

private val pluginList = listOf(
    /**
     *  https://issuetracker.google.com/116747159
     *  (task generateDebugR2 fails on 3.3a12 when generating separate R classes)
     */
    DependencyInfo(
        "Butterknife",
        "com.jakewharton",
        "butterknife-gradle-plugin",
        GradleVersion.parse("9.0.0-rc2")
    ),

    // https://issuetracker.google.com/79997489
    DependencyInfo(
        "Crashlytics",
        "io.fabric.tools",
        "gradle",
        GradleVersion.parse("1.28.0")
    ),

    // https://issuetracker.google.com/110564407
    DependencyInfo(
        "Protobuf",
        "com.google.protobuf",
        "protobuf-gradle-plugin",
        GradleVersion.parse("0.8.6")
    ),

    // https://youtrack.jetbrains.com/issue/KT-30344 (b/152898926)
    DependencyInfo(
        "Kotlin",
        "org.jetbrains.kotlin",
        "kotlin-gradle-plugin",
        GradleVersion.parse("1.3.40")
    )
)

@VisibleForTesting
internal data class DependencyInfo(
    val displayName: String,
    val dependencyGroup: String,
    val dependencyName: String,
    val minimumVersion: GradleVersion
)

/**
 * Enforces minimum versions of certain plugins.
 */
fun enforceMinimumVersionsOfPlugins(project: Project, issueReporter: IssueReporter) {
    // Run only once per build
    val extraProperties = project.rootProject.extensions.extraProperties
    if (extraProperties.has(AGP_INTERNAL__MIN_PLUGIN_VERSION_CHECK_STARTED)) {
        return
    }
    extraProperties.set(AGP_INTERNAL__MIN_PLUGIN_VERSION_CHECK_STARTED, true)

    project.gradle.projectsEvaluated { gradle ->
        val projectsToCheck = mutableSetOf<Project>()
        gradle.allprojects {
            // Check only projects that have AGP applied (see bug 148776286).
            // Also check their parent projects recursively because the buildscript classpath(s) of
            // parent projects are available to child projects.
            if (it.pluginManager.hasPlugin(ANDROID_GRADLE_PLUGIN_ID)) {
                var current: Project? = it
                while (current != null && projectsToCheck.add(current)) {
                    current = current.parent
                }
            }
        }
        // Calling allprojects again is needed as Gradle doesn't allow cross-project resolution of
        // buildscript classpath
        gradle.allprojects {
            if (it in projectsToCheck) {
                for (pluginToCheck in pluginList) {
                    enforceMinimumVersionOfPlugin(it, pluginToCheck, issueReporter)
                }
            }
        }
    }
}

private fun enforceMinimumVersionOfPlugin(
    project: Project,
    pluginInfo: DependencyInfo,
    issueReporter: IssueReporter
) {
    // Traverse the dependency graph to collect violating plugins
    val buildScriptClasspath = project.buildscript.configurations.getByName(CLASSPATH_CONFIGURATION)
    val pathsToViolatingPlugins = ViolatingPluginDetector(
            buildScriptClasspath.incoming.resolutionResult, pluginInfo, project.displayName
    ).detect()

    // Report violating plugins
    if (pathsToViolatingPlugins.isNotEmpty()) {
        issueReporter.reportError(
            IssueReporter.Type.THIRD_PARTY_GRADLE_PLUGIN_TOO_OLD,
            "The Android Gradle plugin supports only ${pluginInfo.displayName} Gradle plugin" +
                    " version ${pluginInfo.minimumVersion} and higher.\n" +
                    "The following dependencies do not satisfy the required version:\n" +
                    pathsToViolatingPlugins.joinToString("\n"),
            listOf(
                pluginInfo.displayName,
                pluginInfo.dependencyGroup,
                pluginInfo.dependencyName,
                pluginInfo.minimumVersion,
                pathsToViolatingPlugins.joinToString(",", "[", "]")
            ).joinToString(";"))
    }
}

@VisibleForTesting
internal class ViolatingPluginDetector(
        private val buildscriptClasspath: ResolutionResult,
        private val pluginToSearch: DependencyInfo,
        private val projectDisplayName: String
) {

    /** Returns the paths to violating plugins. */
    fun detect(): List<String> {
        val violatingPlugins = buildscriptClasspath.getModuleComponents { moduleComponentId ->
            if (moduleComponentId.group == pluginToSearch.dependencyGroup
                    && moduleComponentId.module == pluginToSearch.dependencyName) {
                // Use GradleVersion to parse the version since the format accepted by GradleVersion
                // is general enough. In the unlikely event that the version cannot be parsed,
                // ignore the error.
                val parsedVersion = GradleVersion.tryParse(moduleComponentId.version)
                (parsedVersion != null) && (parsedVersion < pluginToSearch.minimumVersion)
            } else {
                false
            }
        }
        return violatingPlugins.mapNotNull { component ->
            component.getPathFromRoot().getPathString(projectDisplayName).takeIf {
                // Ignore Safe-args plugin for now (bug 175379963).
                !it.contains("androidx.navigation:navigation-safe-args-gradle-plugin:2.3.1")
            }
        }
    }
}

/** Lists all module dependencies resolved for buildscript classpath. */
fun getBuildscriptDependencies(project: Project): List<ModuleComponentIdentifier> {
    val buildScriptClasspath = project.buildscript.configurations.getByName(CLASSPATH_CONFIGURATION)
    return buildScriptClasspath.incoming.resolutionResult.getModuleComponents()
            .map { it.id as ModuleComponentIdentifier }
}

/**
 * Enumerates through the gradle plugin jars existing in the given [classLoader], finds the
 * buildSrc jar and loads the id of each plugin from the `META-INF/gradle-plugins/${id}.properties`
 * file name.
 *
 * @return a list of plugin ids that are defined in the buildSrc
 */
fun getBuildSrcPlugins(classLoader: ClassLoader): Set<String> {
    val pattern = Pattern.compile("META-INF/gradle-plugins/(.+)\\.properties")
    val urls = classLoader.getResources("META-INF/gradle-plugins")

    val buildSrcPlugins = HashSet<String>()
    while (urls.hasMoreElements()) {
        val url = urls.nextElement()
        if (!url.toString().endsWith("buildSrc.jar!/META-INF/gradle-plugins")) {
            continue
        }
        val urlConnection = url.openConnection()
        if (urlConnection is JarURLConnection) {
            urlConnection.jarFile.use { jar ->
                val jarEntries = jar.entries()
                while (jarEntries.hasMoreElements()) {
                    val entry = jarEntries.nextElement()
                    val matcher = pattern.matcher(entry.name)
                    if (matcher.matches()) {
                        buildSrcPlugins.add(matcher.group(1))
                    }
                }
            }
        }
    }
    return buildSrcPlugins
}

const val ANDROID_GRADLE_PLUGIN_ID = "com.android.base"
private const val AGP_INTERNAL__MIN_PLUGIN_VERSION_CHECK_STARTED = "AGP_INTERNAL__MIN_PLUGIN_VERSION_CHECK_STARTED"
