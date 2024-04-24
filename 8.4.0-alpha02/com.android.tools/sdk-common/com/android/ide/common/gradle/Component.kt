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

data class Component(
    val module: Module,
    val version: Version,
): Serializable {
    constructor(group: String, name: String, version: Version): this(Module(group, name), version)

    val group get() = module.group
    val name get() = module.name

    override fun toString() = when(val id = toIdentifier()) {
        is String -> id
        else -> "Component(module=$module, version=$version)"
    }
    /**
     * Return a string that will produce the same [Component] when parsed, or `null`
     * if no such identifier exists.
     */
    fun toIdentifier() = module.toIdentifier()?.let { moduleIdentifier ->
        when {
            !version.isPrefixInfimum -> "$moduleIdentifier:$version"
            else -> null
        }
    }

    companion object {
        /**
         * Parse a string with two or more colon (':') characters as a [Component], where the
         * first colon terminates the group of a [Module]; the second colon terminates the name
         * of the [Module]; and the remainder of the string is parsed as a [Version].  If the
         * string has fewer than two colons, return `null`.
         */
        fun tryParse(string: String): Component? = string
            .takeIf { s -> s.count { it == ':' } > 1 }
            ?.run {
                val firstColonIndex = indexOf(':')
                val secondColonIndex = indexOf(':', startIndex = 1 + firstColonIndex)
                Component(
                    Module.parse(substring(0, secondColonIndex)),
                    Version.parse(substring(1 + secondColonIndex))
                )
            }

        /**
         * Attempt to parse a string as a [Component] using [tryParse]; if parsing fails, throw
         * an [IllegalArgumentException].
         *
         * @throws IllegalArgumentException
         */
        fun parse(string: String): Component =
            tryParse(string) ?: throw IllegalArgumentException("Invalid component: `$string`")
    }
}
