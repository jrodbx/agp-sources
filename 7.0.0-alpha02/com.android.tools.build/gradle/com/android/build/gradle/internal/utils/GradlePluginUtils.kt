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
import org.gradle.api.artifacts.result.ResolvedComponentResult
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
    val pathsToViolatingPlugins = mutableListOf<String>()
    val buildScriptClasspath = project.buildscript.configurations.getByName(CLASSPATH_CONFIGURATION)
    ViolatingPluginDetector(pluginInfo, project.displayName, pathsToViolatingPlugins)
            .visit(buildScriptClasspath.incoming.resolutionResult)

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
        private val pluginToSearch: DependencyInfo,
        private val projectDisplayName: String,
        private val pathsToViolatingPlugins: MutableList<String>
) : PathAwareDependencyGraphVisitor(visitOnlyOnce = true) {

    override fun visitDependency(dependency: ResolvedComponentResult, parentPath: List<ResolvedComponentResult>) {
        val moduleVersion = dependency.moduleVersion ?: return
        if (moduleVersion.group == pluginToSearch.dependencyGroup
                && moduleVersion.name == pluginToSearch.dependencyName) {
            // Use GradleVersion to parse the version since the format accepted by GradleVersion is
            // general enough. In the unlikely event that the version cannot be parsed, ignore the
            // error.
            val parsedVersion = GradleVersion.tryParse(moduleVersion.version)
            if (parsedVersion != null && parsedVersion < pluginToSearch.minimumVersion) {
                val dependencyPath = (parentPath + dependency).map { dep ->
                    dep.moduleVersion?.let { "${it.group}:${it.name}:${it.version}" }
                            ?: dep.toString()
                }
                // The root of the dependency graph (the start of the dependency path) in this case
                // is not in a user-friendly format (e.g., ":<project-name>:unspecified"), so we use
                // `project.displayName` instead (e.g., "root project '<project-name>'").
                val adjustedPath = listOf(projectDisplayName) +
                        dependencyPath.let { it.subList(1, it.size) }
                pathsToViolatingPlugins.add(adjustedPath.joinToString(" -> "))
            }
        }
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
