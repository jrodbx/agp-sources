/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.ide.common.gradle

import java.io.Serializable

data class Module(
    val group: String,
    val name: String,
): Serializable {
    override fun toString() = when(val id = toIdentifier()) {
        is String -> id
        else -> "Module(group=$group, name=$name)"
    }
    /**
     * Return a string that will produce the same [Module] when parsed, or `null`
     * if no such identifier exists.
     */
    fun toIdentifier() = when {
        !group.contains(':') && !name.contains(':') -> "${group}:${name}"
        else -> null
    }

    companion object {
        /**
         * Parse a string with exactly 1 colon (':') character as a [Module], where the colon
         * separates the group from the name.  If the string has zero, or more than one, colon,
         * return `null`.
         */
        @JvmStatic
        fun tryParse(string: String) = string
            .takeIf { s -> s.count { it == ':' } == 1 }
            ?.run { Module(substring(0, indexOf(':')), substring(indexOf(':') + 1)) }

        /**
         * Attempt to parse a string as a [Module] using [tryParse].  If parsing fails, throw
         * an [IllegalArgumentException].
         *
         * @throws IllegalArgumentException
         */
        @JvmStatic
        fun parse(string: String): Module =
            tryParse(string) ?: throw IllegalArgumentException("Invalid module: `$string`")
    }
}

