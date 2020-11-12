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

@file:JvmName("AgpVersionChecker")

package com.android.build.gradle.internal.utils

import com.android.Version
import org.gradle.api.Project
import org.gradle.internal.build.IncludedBuildState

/** This is used to enforce the same version for all projects that apply AGP. */
fun enforceTheSamePluginVersions(project: Project) {
    if (project.gradle.parent != null) {
        // This is an included build, do nothing as we'll check from the build that includes it
        return
    }
    val extraProperties = project.rootProject.extensions.extraProperties
    if (extraProperties.has(CHECK_PERFORMED)) {
        return
    }

    val currentVersion = Version.ANDROID_GRADLE_PLUGIN_VERSION
    val currentProjectPath = project.projectDir.canonicalPath
    // all projects in the current build
    project.gradle.afterProject {
        compareVersions(currentProjectPath, currentVersion, it)
    }

    // all projects in the included build, these are included builds in the composite build
    project.gradle.includedBuilds.forEach { includedBuild ->
        when (includedBuild) {
            is IncludedBuildState -> includedBuild.configuredBuild.allprojects {
                compareVersions(currentProjectPath, currentVersion, it)
            }
            else -> project.logger.warn(
                "Unable to detect AGP versions for included builds. All projects in the build should use the same AGP version. " +
                        "Class name for the included build object: ${includedBuild::class.java.name}."
            )
        }
    }

    extraProperties.set(CHECK_PERFORMED, true)
}

private fun compareVersions(
    firstProjectPath: String,
    firstVersion: String,
    projectToCheck: Project
) {
    projectToCheck.plugins.withId(ANDROID_GRADLE_PLUGIN_ID) {
        val versionValue = try {
            val versionClass = try {
                it::class.java.classLoader.loadClass(com.android.Version::class.java.name)
            } catch (exception: ClassNotFoundException) {
                // Use deprecated Version class as it exists in older AGP (com.android.Version) does
                // not exist in those versions.
                @Suppress("DEPRECATION")
                it::class.java.classLoader.loadClass(com.android.builder.model.Version::class.java.name)
            }
            val field = versionClass.fields.find { it.name == "ANDROID_GRADLE_PLUGIN_VERSION" }!!
            field.get(null) as String
        } catch (ex: Throwable) {
            projectToCheck.logger.error(
                "Unable to get AGP version for project `${projectToCheck.projectDir.canonicalPath}`. All projects in the build should use the same AGP version.",
                ex
            )
            throw ex
        }

        if (versionValue != firstVersion) throw IllegalStateException(
            """
Using multiple versions of the Android Gradle plugin in the same build is not allowed.
- Project `$firstProjectPath` is using version `$firstVersion`
- Project `${projectToCheck.projectDir.canonicalPath}` is using version `$versionValue`
            """.trimIndent()
        )
    }
}

const val ANDROID_GRADLE_PLUGIN_ID = "com.android.base"
private const val CHECK_PERFORMED = "android.agp.version.check.performed"
