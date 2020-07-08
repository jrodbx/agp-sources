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

import com.google.common.annotations.VisibleForTesting
import com.android.builder.errors.IssueReporter
import com.android.ide.common.repository.GradleVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.initialization.dsl.ScriptHandler.CLASSPATH_CONFIGURATION

private const val INTERNAL__CHECKED_MINIMUM_PLUGIN_VERSIONS =
    "INTERNAL__CHECKED_MINIMUM_PLUGIN_VERSIONS"

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
    // We're going to check all projects at the end of the configuration phase, so make sure to do
    // this check only once by marking a custom property of the root project. The access doesn't
    // need to be thread-safe as configuration is single-threaded.
    val alreadyChecked =
        project.rootProject.extensions.extraProperties.has(
            INTERNAL__CHECKED_MINIMUM_PLUGIN_VERSIONS
        )
    if (alreadyChecked) {
        return
    }
    project.rootProject.extensions.extraProperties.set(
        INTERNAL__CHECKED_MINIMUM_PLUGIN_VERSIONS,
        true
    )

    project.gradle.projectsEvaluated { gradle ->
        gradle.allprojects {
            for (plugin in pluginList) {
                enforceMinimumVersionOfPlugin(it, plugin, issueReporter)
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
    val dependency = (dependencyResult as ResolvedDependencyResult).selected
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