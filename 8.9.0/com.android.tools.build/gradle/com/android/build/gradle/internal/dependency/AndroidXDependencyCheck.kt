/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency

import com.android.build.gradle.internal.utils.getModuleComponents
import com.android.build.gradle.internal.utils.getPathToComponent
import com.android.build.gradle.options.BooleanOption
import com.android.builder.errors.IssueReporter
import com.android.builder.errors.IssueReporter.Type.ANDROID_X_PROPERTY_NOT_ENABLED
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvableDependencies

/**
 * Checks whether a configuration contains AndroidX or legacy support library dependencies, and
 * issues an error/warning where appropriate based on the current settings of the
 * `android.useAndroidX` and `android.enableJetifier` properties.
 */
object AndroidXDependencyCheck {

    /**
     * Check to run when `android.useAndroidX=false` and `android.enabledJetifier=false`.
     *
     * NOTE: The caller must invoke this check only under the above condition.
     */
    class AndroidXDisabledJetifierDisabled(
            private val project: Project,
            private val configurationName: String,
            private val issueReporter: IssueReporter
    ) : Action<ResolvableDependencies> {

        override fun execute(resolvableDependencies: ResolvableDependencies) {
            // Report only once
            if (project.extensions.extraProperties.has(issueReported)) {
                return
            }

            val result = resolvableDependencies.resolutionResult
            val androidXDependencies = result.getModuleComponents {
                AndroidXDependencySubstitution.isAndroidXDependency("${it.group}:${it.module}:${it.version}")
            }
            val configurationDisplayPath = project.getConfigurationDisplayPath(configurationName)
            val pathsToAndroidXDependencies = androidXDependencies.map {
                result.getPathToComponent(it).getPathString(configurationDisplayPath)
            }.filterNot {
                // Ignore databinding-compiler (see bug 179377689)
                it.contains("androidx.databinding:databinding-compiler:")
            }
            if (pathsToAndroidXDependencies.isNotEmpty()) {
                project.extensions.extraProperties.set(issueReported, true)
                val message =
                    "Configuration `$configurationDisplayPath` contains AndroidX dependencies," +
                            " but the `${BooleanOption.USE_ANDROID_X.propertyName}` property is not enabled," +
                            " which may cause runtime issues.\n" +
                            "Set `${BooleanOption.USE_ANDROID_X.propertyName}=true` in the `gradle.properties` file and retry.\n" +
                            "The following AndroidX dependencies are detected:\n" +
                            pathsToAndroidXDependencies.joinToString("\n")
                issueReporter.reportError(
                        ANDROID_X_PROPERTY_NOT_ENABLED,
                        message,
                        pathsToAndroidXDependencies.joinToString(",")
                )
            }
        }
    }

    private fun Project.getConfigurationDisplayPath(configurationName: String): String {
        return if (project.path == ":") {
            ":$configurationName"
        } else {
            "${project.path}:$configurationName"
        }
    }
}

private val issueReported =
    "${AndroidXDependencyCheck.AndroidXDisabledJetifierDisabled::class.java.name}_issue_reported"
