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

import com.android.build.gradle.internal.LoggerWrapper
import com.android.builder.dexing.getR8Version
import com.google.common.annotations.VisibleForTesting
import java.io.Serializable

data class VersionedCodeShrinker(val version: String) : Serializable {
    companion object {
        @JvmStatic
        fun create() = VersionedCodeShrinker(parseVersionString(getR8Version()))

        private val versionPattern = """[^\s.]+(?:\.[^\s.]+)+""".toRegex()

        @VisibleForTesting
        internal fun parseVersionString(version: String): String {
            val matcher = versionPattern.find(version)
            return if (matcher != null) {
                LoggerWrapper.getLogger(VersionedCodeShrinker::class.java)
                    .verbose("Parsed shrinker version: ${matcher.groupValues[0]}")
                matcher.groupValues[0]
            } else {
                LoggerWrapper.getLogger(VersionedCodeShrinker::class.java)
                    .warning("Cannot parse shrinker version, assuming 0.0.0")
                "0.0.0"
            }
        }
    }
}
