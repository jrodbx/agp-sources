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

package com.android.build.gradle.internal.cxx.json

/**
 * Maps strings to an ordinal value. Used to deduplicate string values in android_build_gradle.json.
 * Constructor accepts a map of int->string so that NativeBuildConfigValue stringTable can be
 * attached.
 */
class StringTable(private val intToString : MutableMap<Int, String> = mutableMapOf()) {
    private var nextIndex = 0
    private val stringToInt = mutableMapOf<String, Int>()
    init {
        // Purpose of map passed through constructor is to attach an empty map.
        // If a pre-filled map is ever needed then this initializer needs to also initialize
        // stringToInt.
        assert (intToString.isEmpty())
    }

    /**
     * Takes a string and returns the index into the string table of it.
     */
    fun intern(string: String): Int {
        val result = stringToInt[string]
        if (result != null) {
            return result
        }
        intToString[nextIndex] = string
        stringToInt[string] = nextIndex
        ++nextIndex
        return intern(string)
    }
}