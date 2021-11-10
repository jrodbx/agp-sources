/*
 * Copyright (C) 2021 The Android Open Source Project
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

import java.util.regex.Pattern


data class CompileData(
    val apiLevel: Int? = null,
    val codeName: String? = null,
    val sdkExtension: Int? = null,
    val vendorName: String? = null,
    val addonName: String? = null
) {
    fun isAddon() = vendorName != null && addonName != null
}

fun parseTargetHash(targetHash : String): CompileData  {
    val m = API_PATTERN.matcher(targetHash)
    if (m.matches()) {
        val api = m.group(1)

        val apiLevel = api.toIntOrNull()
        if (apiLevel != null) {
            return CompileData(
                apiLevel = apiLevel,
                sdkExtension = m.group(3)?.toIntOrNull()
            )
        } else {
            return CompileData(
                codeName = api
            )
        }
    } else {
        val m2 = ADDON_PATTERN.matcher(targetHash)
        if (m2.matches()) {
            return CompileData(
                vendorName = m2.group(1),
                addonName = m2.group(2),
                apiLevel = m2.group(3).toInt()
            )
        } else {
            throw RuntimeException(
                """
                            Unsupported value: $targetHash. Format must be one of:
                            - android-31
                            - android-31-ext2
                            - android-T
                            - vendorName:addonName:31
                            """.trimIndent()
            )

        }
    }

}

private val API_PATTERN: Pattern = Pattern.compile("^android-([0-9A-Z]+)(-ext(\\d+))?$")
private val ADDON_PATTERN: Pattern = Pattern.compile("^(.+):(.+):(\\d+)$")
