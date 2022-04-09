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

import com.android.build.gradle.internal.dependency.AndroidXDependencySubstitution.COM_ANDROID_DATABINDING_BASELIBRARY
import com.android.build.gradle.internal.utils.getModuleComponents
import com.android.build.gradle.internal.utils.getPathFromRoot
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.Version
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

        private val issueReported =
                "${AndroidXDisabledJetifierDisabled::class.java.name}_issue_reported"

        override fun execute(resolvableDependencies: ResolvableDependencies) {
            // Report only once
            if (project.extensions.extraProperties.has(issueReported)) {
                return
            }

            val androidXDependencies =
                    resolvableDependencies.resolutionResult.getModuleComponents {
                        AndroidXDependencySubstitution.isAndroidXDependency("${it.group}:${it.module}:${it.version}")
                    }
            val pathsToAndroidXDependencies = androidXDependencies.map {
                it.getPathFromRoot().getPathString(configurationName)
            }.filterNot {
                // Ignore databinding-compiler (see bug 179377689)
                it.contains("androidx.databinding:databinding-compiler:")
            }
            if (pathsToAndroidXDependencies.isNotEmpty()) {
                project.extensions.extraProperties.set(issueReported, true)
                val message =
                        "Configuration `$configurationName` contains AndroidX dependencies, but the `${BooleanOption.USE_ANDROID_X.propertyName}` property is not enabled, which may cause runtime issues.\n" +
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

    /**
     * Check to run when `android.useAndroidX=true` and `android.enabledJetifier=false`.
     *
     * NOTE: The caller must invoke this check only under the above condition.
     */
    class AndroidXEnabledJetifierDisabled(
            private val project: Project,
            private val configurationName: String,
            private val issueReporter: IssueReporter
    ) : Action<ResolvableDependencies> {

        private val issueReported =
                "${AndroidXEnabledJetifierDisabled::class.java.name}_issue_reported"

        override fun execute(resolvableDependencies: ResolvableDependencies) {
            // Report only once
            if (project.extensions.extraProperties.has(issueReported)) {
                return
            }

            val supportLibDependencies =
                    resolvableDependencies.resolutionResult.getModuleComponents {
                        val componentId = "${it.group}:${it.module}:${it.version}"
                        AndroidXDependencySubstitution.isLegacySupportLibDependency(componentId)
                                // com.android.databinding:baseLibrary is an exception (see bug 187448822)
                                && !componentId.startsWith(COM_ANDROID_DATABINDING_BASELIBRARY)
                    }
            if (supportLibDependencies.isNotEmpty()) {
                project.extensions.extraProperties.set(issueReported, true)
                val pathsToSupportLibDependencies = supportLibDependencies.map {
                    it.getPathFromRoot().getPathString(configurationName)
                }
                val message =
                        "Your project has set `${BooleanOption.USE_ANDROID_X.propertyName}=true`, but configuration `$configurationName` still contains legacy support libraries, which may cause runtime issues.\n" +
                                "This behavior will not be allowed in Android Gradle plugin ${Version.VERSION_8_0.versionString}.\n" +
                                "Please use only AndroidX dependencies or set `${BooleanOption.ENABLE_JETIFIER.propertyName}=true` in the `gradle.properties` file to migrate your project to AndroidX (see https://developer.android.com/jetpack/androidx/migrate for more info).\n" +
                                "The following legacy support libraries are detected:\n" +
                                pathsToSupportLibDependencies.joinToString("\n")
                issueReporter.reportWarning(
                        ANDROID_X_PROPERTY_NOT_ENABLED,
                        message,
                        pathsToSupportLibDependencies.joinToString(",")
                )
            }
        }
    }
}
