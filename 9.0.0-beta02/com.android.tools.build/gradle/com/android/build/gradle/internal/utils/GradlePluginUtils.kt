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

import com.android.build.gradle.internal.plugins.AppPlugin
import com.android.build.gradle.internal.plugins.DynamicFeaturePlugin
import com.android.build.gradle.internal.plugins.FusedLibraryPlugin
import com.android.build.gradle.internal.plugins.KotlinMultiplatformAndroidPlugin
import com.android.build.gradle.internal.plugins.LibraryPlugin
import com.android.build.gradle.internal.plugins.PrivacySandboxSdkPlugin
import com.android.build.gradle.internal.plugins.TestPlugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.initialization.dsl.ScriptHandler.CLASSPATH_CONFIGURATION
import java.net.JarURLConnection
import java.util.regex.Pattern

/**
 * Fails the build if the given [requiredPlugin] has not been applied after this project's
 * evaluation.
 *
 * [mainPlugin] is the plugin that requires the [requiredPlugin].
 */
internal fun Project.requirePlugin(mainPlugin: String, requiredPlugin: String) {
    afterEvaluate {
        check(pluginManager.hasPlugin(requiredPlugin)) {
            """
            The '$mainPlugin' plugin requires the '$requiredPlugin' plugin to be applied.
            Apply the '$requiredPlugin' plugin in this project's build file: $buildFile.
            """.trimIndent()
        }
    }
}

/**
 * Fails the build if the given [incompatiblePlugin] has been applied.
 *
 * If the [incompatiblePlugin] has not been applied but will be applied later, then this will make
 * the build fail later at the point when the [incompatiblePlugin] has been applied (unless that
 * plugin fails the build before that point -- see b/397373580).
 *
 * [mainPlugin] is the plugin that the [incompatiblePlugin] is incompatible with.
 */
internal fun Project.disallowPlugin(mainPlugin: String, incompatiblePlugin: String) {
    pluginManager.withPlugin(incompatiblePlugin) {
        error(
            """
            The '$incompatiblePlugin' plugin is not compatible with the '$mainPlugin' plugin.
            Remove the '$incompatiblePlugin' plugin from this project's build file: $buildFile.
            """.trimIndent()
        )
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

/** Android Gradle plugins where no two plugins can be applied together in the same project. */
val MUTUALLY_EXCLUSIVE_ANDROID_GRADLE_PLUGINS = mapOf(
    AppPlugin::class.java to "com.android.application",
    LibraryPlugin::class.java to "com.android.library",
    DynamicFeaturePlugin::class.java to "com.android.dynamic-feature",
    TestPlugin::class.java to "com.android.test",
    KotlinMultiplatformAndroidPlugin::class.java to "com.android.kotlin.multiplatform.library",
    FusedLibraryPlugin::class.java to "com.android.fused-library",
)
