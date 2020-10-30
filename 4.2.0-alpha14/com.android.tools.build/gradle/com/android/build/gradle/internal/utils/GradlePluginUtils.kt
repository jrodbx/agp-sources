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
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.initialization.dsl.ScriptHandler.CLASSPATH_CONFIGURATION
import java.net.JarURLConnection
import java.util.Properties
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

    // https://youtrack.jetbrains.net/issue/KT-27160 (b/118644940)
    DependencyInfo(
        "Kotlin",
        "org.jetbrains.kotlin",
        "kotlin-gradle-plugin",
        GradleVersion.parse("1.3.10")
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
    // Apply this check only to current subproject, other subprojects that do not apply the Android
    // Gradle plugin should not be impacted by this check (see bug 148776286).
    project.afterEvaluate {
        for (plugin in pluginList) {
            enforceMinimumVersionOfPlugin(it, plugin, issueReporter)
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
    val pathsToViolatingPlugins = mutableListOf<String>()
    for (dependency in buildScriptClasspath.incoming.resolutionResult.root.dependencies) {
        visitDependency(
            dependency,
            project.displayName,
            pluginInfo,
            pathsToViolatingPlugins,
            mutableSetOf()
        )
    }

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
internal fun visitDependency(
    dependencyResult: DependencyResult,
    parentPath: String,
    dependencyInfo: DependencyInfo,
    pathsToViolatingDeps: MutableList<String>,
    visitedDependencies: MutableSet<String>
) {
    // The dependency must have been resolved
    check(dependencyResult is ResolvedDependencyResult) {
        "Expected ${ResolvedDependencyResult::class.java.name}" +
                " but found ${dependencyResult.javaClass.name}"
    }

    // The selected dependency may be different from the requested dependency, but we are interested
    // in only the selected dependency
    val dependency = dependencyResult.selected
    val moduleVersion = dependency.moduleVersion!!
    val group = moduleVersion.group
    val name = moduleVersion.name
    val version = moduleVersion.version

    // Compute the path to the dependency
    val currentPath = "$parentPath -> $group:$name:$version"

    // Detect violating dependencies
    if (group == dependencyInfo.dependencyGroup && name == dependencyInfo.dependencyName) {
        // Use GradleVersion to parse the version since the format accepted by GradleVersion is
        // general enough. In the unlikely event that the version cannot be parsed (the return
        // result is null), let's be lenient and ignore the error.
        val parsedVersion = GradleVersion.tryParse(version)
        if (parsedVersion != null && parsedVersion < dependencyInfo.minimumVersion) {
            pathsToViolatingDeps.add(currentPath)
        }
    }

    // Don't visit a dependency twice (except for the dependency being searched, that's why this
    // check should be after the detection above)
    val dependencyFullName = "$group:$name:$version"
    if (visitedDependencies.contains(dependencyFullName)) {
        return
    }
    visitedDependencies.add(dependencyFullName)

    for (childDependency in dependency.dependencies) {
        visitDependency(
            childDependency,
            currentPath,
            dependencyInfo,
            pathsToViolatingDeps,
            visitedDependencies
        )
    }
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