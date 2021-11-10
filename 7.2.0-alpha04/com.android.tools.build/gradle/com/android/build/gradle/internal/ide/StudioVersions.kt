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
import com.google.common.base.CharMatcher
import com.google.common.base.Splitter
import org.gradle.api.InvalidUserDataException

/**
 * For AGP 7.0 and above, the minimum version of Studio that is required to open
 * the project is different from the plugin version itself.
 */
private val MINIMUM_REQUIRED_STUDIO_VERSION = MajorMinorVersion(2021, 1,1)

/** Throws if the Intellij Android Support plugin version used has a lower major/minor version than the current Android Gradle plugin */
fun verifyIDEIsNotOld(projectOptions: ProjectOptions) {
    if (!projectOptions[BooleanOption.ENABLE_STUDIO_VERSION_CHECK]) {
        return
    }
    verifyIDEIsNotOld(
        projectOptions.get(StringOption.IDE_ANDROID_STUDIO_VERSION),
        MINIMUM_REQUIRED_STUDIO_VERSION
    )
}

@VisibleForTesting
internal fun verifyIDEIsNotOld(injectedVersion: String?, minRequiredVersion: MajorMinorVersion) {
    if (injectedVersion == null) {
        // Be lenient when the version is not injected.
        return
    }
    val parsedInjected = parseVersion(injectedVersion)
        ?: throw InvalidUserDataException("Unrecognized Android Studio (or Android Support plugin for IntelliJ IDEA) version '$injectedVersion', please retry with version $minRequiredVersion or newer.")

    // the injected version is something like 203.7148.57.2031.SNAPSHOT in the tests, which is not the case
    // in product. so just ignore it.
    if (parsedInjected < minRequiredVersion && !injectedVersion.endsWith("SNAPSHOT")) {
        throw RuntimeException(
            "This version of the Android Support plugin for IntelliJ IDEA (or Android Studio) cannot open this project, please retry with version $minRequiredVersion or newer."
        )
    }
}

/**
 * This will accept some things that are not valid versions as it ignores everything after
 * extracting the Year (if applicable), Major and Minor versions.
 *
 * Before Android Studio 4.x, the platform version number is injected. e.g. "3.6.3.0" for Android
 * Studio 3.6.3 or something like "2020.1.4535" if run from the Android Plugin in IDEA
 *
 * In Android Studio 4.x this version is the version of the Android Support Plugin, e.g. "10.4.3.1",
 * which will be the same between Android Studio and Intellij IDEA.
 * This will be in the form 10.x.y.* where x and y are the major and minor versions respectively.
 *
 * Android Studio Arctic Fox moved to a year-based system that is more closely aligned with
 * IntelliJ IDEA, so the new format is YYYY.x.y where YYYY is the year, x and y are the major and
 * minor versions respectively
 *
 * This is also called with the raw version of AGP and also need to handle that case. This will
 * be in the form x.y.*.
 *
 */
@VisibleForTesting
internal fun parseVersion(version: String): MajorMinorVersion? {
    val segments = SPLITTER.split(version).map { it.toIntOrNull() ?: -1 }
    if (segments.size < 3) {
        return null
    }
    return when {
        segments[0] <= 4 -> {
            // Handle case of e.g. 3.2.1.6
            versionOf(year = 0, major = segments[0], minor = segments[1])
        }
        // 4.x versions have sometimes a 10. prefix, e.g. 10.4.1.3.6, discard it.
        segments[0] == 10 && segments[1] == 4 -> {
            versionOf(year = 0, major = segments[1], minor = segments[2])
        }
        version.startsWith("10.2020.3 ") -> {
            // Handle the missing minor version from earlier Android Studio Arctic Fox canaries.
            return MajorMinorVersion(yearVersion = 2020, majorVersion = 3, minorVersion = 1)
        }
        segments[0] >= 2020 -> {
            versionOf(year = segments[0], major = segments[1], minor = segments[2])
        }
        else -> null
    }
}

private fun versionOf(year: Int, major: Int, minor: Int): MajorMinorVersion? {
    if (major < 0 || minor < 0) {
        return null
    }
    return MajorMinorVersion(year, major, minor)
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

private val SPLITTER = Splitter.on(CharMatcher.anyOf(". ")).omitEmptyStrings()
