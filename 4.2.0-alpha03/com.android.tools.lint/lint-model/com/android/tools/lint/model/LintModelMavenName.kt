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

package com.android.tools.lint.model

interface LintModelMavenName : Comparable<LintModelMavenName> {
    val groupId: String
    val artifactId: String
    val version: String

    // Support destructuring
    operator fun component1(): String = groupId
    operator fun component2(): String = artifactId
    operator fun component3(): String = version

    override fun compareTo(other: LintModelMavenName): Int {
        val group = groupId.compareTo(other.groupId)
        if (group != 0) {
            return group
        }
        val artifact = artifactId.compareTo(other.artifactId)
        if (artifact != 0) {
            return artifact
        }

        return version.compareTo(other.version)
    }

    companion object {
        // Reverse operation from toString
        fun parse(string: String): LintModelMavenName? {
            val index1 = string.indexOf(':')
            val index2 = string.indexOf(':', index1 + 1)
            return if (index2 != -1) {
                DefaultLintModelMavenName(
                    string.substring(0, index1),
                    string.substring(index1 + 1, index2),
                    string.substring(index2 + 1)
                )
            } else {
                null
            }
        }

        val NONE = DefaultLintModelMavenName("", "")

        @Suppress("SpellCheckingInspection")
        const val LOCAL_AARS = "__local_aars__"
    }
}

data class DefaultLintModelMavenName(
    override val groupId: String,
    override val artifactId: String,
    override val version: String = ""
) : LintModelMavenName {
    override fun toString(): String = "$groupId:$artifactId:$version"
}
