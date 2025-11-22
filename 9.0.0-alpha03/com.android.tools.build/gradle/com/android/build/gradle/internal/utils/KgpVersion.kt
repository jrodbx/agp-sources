/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.gradle.internal.utils

import com.android.ide.common.gradle.Version

/** Represents a Kotlin Gradle plugin version. */
@Suppress("UnstableApiUsage")
class KgpVersion(private val version: Version) : Comparable<KgpVersion> {

    override fun compareTo(other: KgpVersion) = version.compareTo(other.version)

    override fun toString() = version.toString()

    companion object {

        fun parse(version: String): KgpVersion {
            return KgpVersion(Version.parse(version))
        }

        val KGP_2_1_0: KgpVersion = parse("2.1.0")

        /**
         * The minimum version of KGP required to be on the buildscript classpath for built-in
         * Kotlin support in AGP.
         */
        val MINIMUM_BUILT_IN_KOTLIN_VERSION: KgpVersion = parse("1.9.20")
    }
}
