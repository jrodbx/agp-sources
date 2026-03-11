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
import com.android.build.gradle.internal.dsl.CompileSdkVersionImpl
import com.android.builder.core.DefaultApiVersion
import com.android.sdklib.AndroidVersion
import com.google.common.base.Splitter
import java.util.regex.Pattern
import javax.lang.model.SourceVersion

internal fun parseTargetHash(targetHash : String): CompileSdkVersionImpl {
    val apiMatcher = API_PATTERN.matcher(targetHash)
    if (apiMatcher.matches()) {
        return CompileSdkVersionImpl(
            apiLevel = apiMatcher.group(1).toInt(),
            minorApiLevel = apiMatcher.group(2)?.toIntOrNull(),
            sdkExtension = apiMatcher.group(4)?.toIntOrNull()
        )
    }

    val previewMatcher = FULL_PREVIEW_PATTERN.matcher(targetHash)
    if (previewMatcher.matches()) {
        return CompileSdkVersionImpl(codeName = previewMatcher.group(1))
    }

    val addonMatcher = ADDON_PATTERN.matcher(targetHash)
    if (addonMatcher.matches()) {
        return CompileSdkVersionImpl(
            vendorName = addonMatcher.group(1),
            addonName = addonMatcher.group(2),
            apiLevel = addonMatcher.group(3).toInt(),
        )
    }

    throw RuntimeException(
        """
                    Unsupported value: $targetHash. Format must be one of:
                    - android-31
                    - android-36.2
                    - android-31-ext2
                    - android-36.2-ext2
                    - android-T
                    - vendorName:addonName:31
                    """.trimIndent()
    )
}

fun validateNamespaceValue(value: String?): String? {
    if (value.isNullOrEmpty() || SourceVersion.isName(value)) return null
    val msg = "Namespace '$value' is not a valid Java package name"
    for (segment in Splitter.on('.').split(value)) {
        if(!SourceVersion.isIdentifier(segment)) {
            return "$msg as '$segment' is not a valid Java identifier."
        }
        if(SourceVersion.isKeyword(segment)) {
            return "$msg as '$segment' is a Java keyword."
        }
    }
    // Shouldn't happen.
   return "$msg."
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

/**
 * This function makes calling sdk block without doing `version = xx` has not effects
 */
fun <T> updateIfChanged(oldValue: T?, newValue: T?, setter: (T?) -> Unit) {
    if (oldValue != newValue) {
        setter(newValue)
    }
}

private val API_PATTERN = Pattern.compile("android-(\\d+)(?:\\.(\\d+))?(-ext(\\d+))?")
private val FULL_PREVIEW_PATTERN = Pattern.compile("android-([A-Z]\\w*)")
private val ADDON_PATTERN = Pattern.compile("([^:]+):([^:]+):(\\d+)")
