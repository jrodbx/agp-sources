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
import com.android.build.gradle.internal.utils.getPathFromRoot
import com.android.build.gradle.options.BooleanOption
import com.android.builder.errors.IssueReporter
import com.android.builder.errors.IssueReporter.Type.ANDROID_X_PROPERTY_NOT_ENABLED
import org.gradle.api.Action
import org.gradle.api.artifacts.ResolvableDependencies

/**
 * Checks whether a configuration uses AndroidX dependencies but the project does not have the
 * `android.useAndroidX` property enabled.
 *
 * This check assumes that the property is currently disabled. (It is the responsibility of the
 * caller to ensure that that is the case.)
 *
 * @see [com.android.builder.model.SyncIssue.TYPE_ANDROID_X_PROPERTY_NOT_ENABLED]
 */
class AndroidXDependencyCheck(
        private val configurationName: String, private val issueReporter: IssueReporter
) : Action<ResolvableDependencies> {

    override fun execute(resolvableDependencies: ResolvableDependencies) {
        // Report only once
        if (issueReporter.hasIssue(ANDROID_X_PROPERTY_NOT_ENABLED)) {
            return
        }

        val androidXDependencies = resolvableDependencies.resolutionResult.getModuleComponents() {
                AndroidXDependencySubstitution.isAndroidXDependency("${it.group}:${it.module}:${it.version}")
        }
        val pathsToAndroidXDependencies = androidXDependencies.map {
            it.getPathFromRoot().getPathString(configurationName)
        }.filterNot {
            // Ignore databinding-compiler (see bug 179377689)
            it.contains("androidx.databinding:databinding-compiler:")
        }
        if (pathsToAndroidXDependencies.isNotEmpty()) {
            val message = "Configuration `$configurationName` uses AndroidX dependencies, but the" +
                    " `${BooleanOption.USE_ANDROID_X.propertyName}` property is not enabled.\n" +
                    "Set `${BooleanOption.USE_ANDROID_X.propertyName}=true` in the `gradle.properties` file and retry.\n" +
                    "The following AndroidX dependencies are detected:\n" +
                    pathsToAndroidXDependencies.joinToString("\n")
            issueReporter.reportError(
                    ANDROID_X_PROPERTY_NOT_ENABLED, message, pathsToAndroidXDependencies.joinToString(",")
            )
        }
    }
}