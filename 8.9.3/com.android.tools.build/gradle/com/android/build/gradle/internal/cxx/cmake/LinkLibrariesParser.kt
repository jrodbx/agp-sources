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

package com.android.build.gradle.internal.cxx.cmake

/**
 * Parses the linkLibraries field of the CMake server response into a list of individual items.
 *
 * The linkLibraries field in the CMake server response is a whitespace delimited string rather
 * than a list of items. Elements may be paths, and if the path contains spaces the item will be
 * quoted.
 *
 * @property linkLibraries The linkLibraries field in the CMake server response.
 */
private class LinkLibrariesParser(private val linkLibraries: String) {
    private val items = mutableListOf<String>()
    private val stringBuilder = StringBuilder()
    private val iterator = linkLibraries.iterator()

    private fun parseQuoted() {
        require(stringBuilder.isEmpty()) { "expected quoted string to be the start of a new item" }
        while (true) {
            val c = try {
                iterator.next()
            } catch (ex: StringIndexOutOfBoundsException) {
                throw IllegalArgumentException(ex)
            }
            when (c) {
                '"' -> {
                    finishItem()
                    return
                }
                else -> stringBuilder.append(c)
            }
        }
    }

    private fun finishItem() {
        if (stringBuilder.isNotEmpty()) {
            items.add(stringBuilder.toString())
            stringBuilder.setLength(0)
        }
    }

    fun parse(): List<String> {
        while (iterator.hasNext()) {
            when (val c = iterator.next()) {
                '"' -> parseQuoted()
                ' ' -> finishItem()
                else -> stringBuilder.append(c)
            }
        }

        finishItem()
        return items
    }
}

/**
 * Parses the linkLibraries field of the CMake server response into a list of individual items.
 *
 * The linkLibraries field in the CMake server response is a whitespace delimited string rather
 * than a list of items. Elements may be paths, and if the path contains spaces the item will be
 * quoted.
 *
 * @param linkLibraries The linkLibraries field in the CMake server response.
 */
fun parseLinkLibraries(linkLibraries: String): List<String> =
    LinkLibrariesParser(linkLibraries).parse()