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

import com.android.builder.dexing.R8Version
import com.android.ide.common.gradle.Version
import java.io.Serializable

data class ShrinkerVersion(private val version: Version) : Serializable, Comparable<ShrinkerVersion> {

    fun asString(): String = version.toString()

    override fun compareTo(other: ShrinkerVersion): Int = version.compareTo(other.version)

    companion object {

        val R8 by lazy {
            parse(R8Version.getVersionString())
        }

        // Special value representing R8 "main" version (b/374032642)
        private const val R8_MAIN_VERSION = "9999.0.0-dev"

        private val versionPattern = """[^\s.]+(?:\.[^\s.]+)+""".toRegex()

        fun parse(version: String): ShrinkerVersion =
            tryParse(version) ?: error("Unrecognized version: $version")

        fun tryParse(version: String): ShrinkerVersion? {
            // Check if this is R8 "main" version (version == "main (build 1234567)").
            return if (version.startsWith("main")) {
                ShrinkerVersion(Version.parse(R8_MAIN_VERSION))
            } else {
                versionPattern.find(version)
                    ?.let { matchResult -> matchResult.groupValues[0] }
                    ?.let { Version.parse(it) }
                    ?.let { ShrinkerVersion(it) }
            }
        }
    }
}
