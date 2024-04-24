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

import com.android.build.api.variant.impl.AndroidVersionImpl
import com.android.builder.core.DefaultApiVersion
import com.android.sdklib.AndroidVersion
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
    val apiMatcher = API_PATTERN.matcher(targetHash)
    if (apiMatcher.matches()) {
        return CompileData(
            apiLevel = apiMatcher.group(1).toInt(),
            sdkExtension = apiMatcher.group(3)?.toIntOrNull()
        )
    }

    val previewMatcher = FULL_PREVIEW_PATTERN.matcher(targetHash)
    if (previewMatcher.matches()) {
        return CompileData(codeName = previewMatcher.group(1))
    }

    val addonMatcher = ADDON_PATTERN.matcher(targetHash)
    if (addonMatcher.matches()) {
        return CompileData(
            vendorName = addonMatcher.group(1),
            addonName = addonMatcher.group(2),
            apiLevel = addonMatcher.group(3).toInt()
        )
    }

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

fun validatePreviewTargetValue(value: String): String? =
    if (AndroidVersion.PREVIEW_PATTERN.matcher(value).matches()) {
        value
    } else null

internal fun createTargetSdkVersion(targetSdk: Int?, targetSdkPreview: String?) =
    if (targetSdk != null || targetSdkPreview != null) {
        val apiVersion =
            targetSdk?.let { DefaultApiVersion(it) } ?: DefaultApiVersion(targetSdkPreview!!)
        apiVersion.run { AndroidVersionImpl(apiLevel, codename) }
    } else null

private val API_PATTERN: Pattern = Pattern.compile("^android-([0-9]+)(-ext(\\d+))?$")
private val FULL_PREVIEW_PATTERN: Pattern = Pattern.compile("^android-([A-Z][0-9A-Za-z_]*)$")
private val ADDON_PATTERN: Pattern = Pattern.compile("^(.+):(.+):(\\d+)$")
