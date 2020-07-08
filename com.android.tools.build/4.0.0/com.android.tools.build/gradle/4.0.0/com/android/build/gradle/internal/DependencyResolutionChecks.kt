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
@file:JvmName("DependencyResolutionChecks")

package com.android.build.gradle.internal

import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.google.common.base.Throwables
import org.gradle.BuildAdapter
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.LogLevel

private const val REGISTERED_EXTENSION_EXT_PROPERTY_NAME =
    "_internalAndroidGradlePluginDependencyCheckerRegistered"

/** Check to ensure dependencies are not resolved during configuration.
 *
 * See  https://github.com/gradle/gradle/issues/2298
 */
fun registerDependencyCheck(project: Project, projectOptions: ProjectOptions) {
    if (project.rootProject.extensions.findByName(REGISTERED_EXTENSION_EXT_PROPERTY_NAME) != null) {
        // Only register once
        return
    }
    project.rootProject.extensions.add(REGISTERED_EXTENSION_EXT_PROPERTY_NAME, true)

    val listener = EarlyDependencyResolutionListener(
        project = project,
        warn = projectOptions[BooleanOption.WARN_ABOUT_DEPENDENCY_RESOLUTION_AT_CONFIGURATION],
        fail = projectOptions[BooleanOption.DISALLOW_DEPENDENCY_RESOLUTION_AT_CONFIGURATION]
    )
    project.gradle.addListener(listener)
}

private class EarlyDependencyResolutionListener(
    val project: Project, val warn: Boolean, val fail: Boolean
) : DependencyResolutionListener, BuildAdapter() {

    override fun beforeResolve(configuration: ResolvableDependencies) {
        if (configuration.name == "classpath") {
            return
        }
        if (project.findProperty(BooleanOption.IDE_BUILD_MODEL_ONLY.propertyName)
                ?.let { BooleanOption.IDE_BUILD_MODEL_ONLY.parse(it) } == true) {
            return
        }

        val errorMessage = errorMessage(configurationName = configuration.name)
        if (fail) {
            throw RuntimeException(errorMessage)
        } else {
            if (warn) {
                project.logger.warn("$errorMessage\nRun with --info for a stacktrace.")
                // TODO b/80230357: Heuristically sanitized stacktrace to show what triggered the resolution.
            }
            if (project.logger.isEnabled(LogLevel.INFO)) {
                project.logger.info(Throwables.getStackTraceAsString(RuntimeException(errorMessage)))
            }
        }
    }

    override fun afterResolve(p0: ResolvableDependencies) {
    }

    override fun projectsEvaluated(gradle: Gradle) {
        gradle.removeListener(this)
    }

    private fun errorMessage(configurationName: String): String {
        return """Configuration '$configurationName' was resolved during configuration time.
This is a build performance and scalability issue.
See https://github.com/gradle/gradle/issues/2298"""
    }
}


