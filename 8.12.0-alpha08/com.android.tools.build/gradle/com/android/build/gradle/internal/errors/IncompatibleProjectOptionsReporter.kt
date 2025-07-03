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

package com.android.build.gradle.internal.errors

import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.errors.IssueReporter

/** Reports issues on project options that are incompatible. */
object IncompatibleProjectOptionsReporter {

    @JvmStatic
    fun check(projectOptions: ProjectOptions, issueReporter: SyncIssueReporter) {
        if (!projectOptions.get(BooleanOption.USE_ANDROID_X)
                && projectOptions.get(BooleanOption.ENABLE_JETIFIER)) {
            issueReporter.reportError(
                type = IssueReporter.Type.ANDROID_X_PROPERTY_NOT_ENABLED,
                msg = "AndroidX must be enabled when Jetifier is enabled. To resolve, set" +
                        " ${BooleanOption.USE_ANDROID_X.propertyName}=true" +
                        " in your gradle.properties file."
            )
        }
    }
}