/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.dependency.ShrinkerVersion
import com.android.builder.dexing.R8Version
import com.android.builder.errors.IssueReporter

fun checkIfR8VersionMatches(issueReporter: IssueReporter) {
    try {
        val versionInClasspath =
            ShrinkerVersion.parse(R8Version.getVersionString()) ?: return
        // Compiler inlines constants, so this retrieves R8 version at compile time (from AGP).
        // This may differ from the R8 version available at runtime.
        val versionAgpWasShippedWith = ShrinkerVersion.parse(R8Version.VERSION_AGP_WAS_SHIPPED_WITH) ?: return
        if (versionInClasspath < versionAgpWasShippedWith) {
            throw R8VersionCheckException(versionAgpWasShippedWith, versionInClasspath)
        }
    } catch (e: NoSuchMethodError) {
        throw R8VersionCheckException()
    } catch (e: R8VersionCheckException) {
        issueReporter.reportWarning(IssueReporter.Type.GENERIC, e)
    }
}

class R8VersionCheckException(
    minimumRequired: ShrinkerVersion? = null,
    foundVersion: ShrinkerVersion? = null
) :
    Exception(
        "Your project includes ${foundVersion?.asString()?.let { "version $it" } ?: "an old version"} of R8, " +
                "while Android Gradle Plugin was shipped with ${minimumRequired?.asString() ?: "a newer one"}. " +
                "This can lead to unexpected issues."
    )
