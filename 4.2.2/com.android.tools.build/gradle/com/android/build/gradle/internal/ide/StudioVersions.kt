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

@file:JvmName("StudioVersions")

package com.android.build.gradle.internal.ide

import com.android.build.gradle.options.BooleanOption
import com.google.common.annotations.VisibleForTesting
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.StringOption
import com.android.Version
import com.google.common.base.CharMatcher
import com.google.common.base.Splitter
import org.gradle.api.InvalidUserDataException

/** Throws if the Intellij Android Support plugin version used has a lower major/minor version than the current Android Gradle plugin */
fun verifyIDEIsNotOld(projectOptions: ProjectOptions) {
    if (!projectOptions[BooleanOption.ENABLE_STUDIO_VERSION_CHECK]) {
        return
    }
    verifyIDEIsNotOld(
        projectOptions.get(StringOption.IDE_ANDROID_STUDIO_VERSION),
        ANDROID_GRADLE_PLUGIN_VERSION
    )
}

@VisibleForTesting
internal fun verifyIDEIsNotOld(
    injectedVersion: String?,
    androidGradlePluginVersion: MajorMinorVersion
) {
    if (injectedVersion == null) {
        // Be lenient when the version is not injected.
        return
    }

    val parsedInjected = parseVersion(injectedVersion)
        ?: throw InvalidUserDataException("Invalid injected android support version '$injectedVersion', expected to be of the form 'w.x.y.z'")

    // for AGP 7.0 and above, the minimum version of Studio that is required to open
    // the project is different from the plugin version itself.
    // For now, maintain a table, but this will be changed in the future.
    val minRequiredVersion = when (androidGradlePluginVersion) {
        MajorMinorVersion(majorVersion = 7, minorVersion = 0) -> MajorMinorVersion(2020, 3, 1)
        else -> androidGradlePluginVersion
    }

    // the injected version is something like 203.7148.57.2031.SNAPSHOT in the tests, which is not the case
    // in product. so just ignore it.
    if (parsedInjected < minRequiredVersion && !injectedVersion.endsWith("SNAPSHOT")) {
        throw RuntimeException(
            "This version of the Android Support plugin for IntelliJ IDEA (or Android Studio) cannot open this project, please retry with version $androidGradlePluginVersion or newer."
        )
    }
}

/** This will accept some things that are not valid versions as it ignores everything after the
 * second.
 *
 * There are two possible types of format that we can obtain from the IDE, in versions before
 * 4.0, the application ID is send. This will be either eg - "2020.1.4535" if run from IDEA or
 * eg 0 "3.6.3" if send from Android Studio.
 *
 * On and after 4.0 this version will be the version of the Android Support Plugin which will
 * be the same between Android Studio and Intellij IDEA. This will be in the form 10.x.y.* where
 * x and y are the major and minor versions respectively.
 *
 * On and after 4.3, Android Studio moved to a year-based system that is more closely aligned with
 * IntelliJ IDEA, so the new format is YYYY.x.y where YYYY is the year, x and y are the major and
 * minor versions respectively
 *
 * This is also called with the raw version of AGP and also need to handle that case. This will
 * be in the form x.y.*.
 *
 */
@VisibleForTesting
internal fun parseVersion(version: String): MajorMinorVersion? {
    val segments = SPLITTER.split(version).iterator()
    if (!segments.hasNext()) {
        return null
    }
    var yearVersion = 0
    var majorVersion = segments.next().toIntOrNull() ?: return null
    if (majorVersion == 10 || majorVersion > 2000) {
        if (!segments.hasNext()) {
            return null
        }
        yearVersion = majorVersion
        majorVersion = segments.next().toIntOrNull() ?: return null
    }

    if (!segments.hasNext()) {
        return null
    }
    val minorVersion = segments.next().toIntOrNull() ?: return null
    if (majorVersion < 0 || minorVersion < 0) {
        return null
    }
    if (yearVersion > 2000) {
        return MajorMinorVersion(yearVersion, majorVersion, minorVersion)
    }
    return MajorMinorVersion(majorVersion = majorVersion, minorVersion = minorVersion)
}

@VisibleForTesting
internal data class MajorMinorVersion(
    val yearVersion: Int = 0,
    val majorVersion: Int,
    val minorVersion: Int
) : Comparable<MajorMinorVersion> {
    override fun compareTo(other: MajorMinorVersion): Int {
        var diff = this.yearVersion - other.yearVersion
        if (diff != 0) {
            return diff
        }
        diff = this.majorVersion - other.majorVersion
        if (diff != 0) {
            return diff
        }
        return minorVersion - other.minorVersion
    }

    override fun toString(): String {
        if (yearVersion == 0) {
            return "$majorVersion.$minorVersion"
        }
        return "$yearVersion.$majorVersion.$minorVersion"
    }
}

private val SPLITTER = Splitter.on(CharMatcher.anyOf(". "))

private val ANDROID_GRADLE_PLUGIN_VERSION =
    parseVersion(Version.ANDROID_GRADLE_PLUGIN_VERSION)!!
