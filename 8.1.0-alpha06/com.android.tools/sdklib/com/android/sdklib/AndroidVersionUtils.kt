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
@file:JvmName("AndroidVersionUtils")
package com.android.sdklib

import java.lang.RuntimeException

/**
 * Returns the API name + extension + marketing version and codename
 *
 * This will support platform preview by returning the codename only (since
 * no API level or release name has been set at that time).
 * The string will look like:
 * - API Tiramisu Preview
 *
 * See [computeFullApiName] for non-preview string rendering
 *
 * The highest number (inclusive) that is supported
 * is [SdkVersionInfo.HIGHEST_KNOWN_API].
 *
 * For a Release name focused string, see [getFullReleaseName]
 *
 * @param includeReleaseName whether to include the release name in the string
 * @param includeCodeName whether to include the codename in the string
 * @return a suitable version display name
 */
@JvmOverloads
fun AndroidVersion.getFullApiName(
    includeReleaseName: Boolean = false,
    includeCodeName: Boolean = false,
): String {
    // See http://source.android.com/source/build-numbers.html

    if (codename != null) {
        return "API $codename Preview"
    }

    return computeFullApiName(
        apiLevel,
        if (isBaseExtension) null else extensionLevel,
        includeReleaseName,
        includeCodeName)
}

/**
 * Returns the Android release name + API level/extension + codename
 *
 * This will support platform preview by returning the codename only (since
 * no API level or release name has been set at that time).
 * The string will look like:
 * - Android Tiramisu Preview
 *
 * See [computeFullReleaseName] for non-preview string rendering
 *
 * For a API level focused string, see [getFullApiName]
 *
 * The highest number (inclusive) that is supported
 * is [SdkVersionInfo.HIGHEST_KNOWN_API].
 *
 * @param includeApiLevel whether to include the API Level in the string
 * @param includeCodeName whether to include the codename in the string
 * @return a suitable version display name
 */
fun AndroidVersion.getFullReleaseName(
    includeApiLevel: Boolean = false,
    includeCodeName: Boolean = false,
): String {
    // See http://source.android.com/source/build-numbers.html

    if (codename != null) {
        return "Android $codename Preview"
    }

    return computeFullReleaseName(
        apiLevel,
        if (isBaseExtension) null else extensionLevel,
        includeApiLevel,
        includeCodeName
    )
}

/**
 * Computes and returns the API name + extension + marketing version and codename
 *
 * This does not support preview platform since it requires an integer API Level
 *
 * The rendering will look like this:
 * - API 33
 * - API 33 ext. 4
 * - API 33 (Android 13.0)
 * - API 33 ("Tiramisu")
 * - API 33 ("Tiramisu"; Android 13.0)
 *
 * If the release name or codename are unknown, they will be omitted.
 *
 * For a Release name focused string, see [computeFullReleaseName]
 *
 * The highest number (inclusive) that is supported
 * is [SdkVersionInfo.HIGHEST_KNOWN_API].
 *
 * @param apiLevel the API level
 * @param extensionLevel the extension level or null to represent the base extension of the given API (or unknown)
 * @param includeReleaseName whether to include the release name in the string
 * @param includeCodeName whether to include the codename in the string
 * @return a suitable version display name
 */
fun computeFullApiName(
    apiLevel: Int,
    extensionLevel: Int?,
    includeReleaseName: Boolean = false,
    includeCodeName: Boolean = false,
): String {
    // See http://source.android.com/source/build-numbers.html

    val sb = StringBuilder()
    sb.append("API $apiLevel")
    if (extensionLevel != null) {
        sb.append(" ext. $extensionLevel")
    }

    var useCodeName = includeCodeName

    val releaseName = if (includeReleaseName) {
        // It is possible that Studio does not know the name of the release if the platform is more
        // recent.
        // In this case, because this method is supposed to display the release name, we will look
        // for alternative options.
        // It is possible that Studio knows the name if it's currently a preview. This is normally caught
        // in [getFullReleaseName] but in case of direct call here with [AndroidVersion.featureLevel]
        // it is possible that we are called with a preview API. In this case we will attempt to
        // display the codename. However, we cannot be certain that this is in fact a preview
        // so we will not use that term.
        // TODO(267390396): add testing for this. It's currently untestable because it relies on static data that cannot be injected.
        val relName = SdkVersionInfo.getReleaseVersionString(apiLevel)
        val codeName = if (relName == null) {
            SdkVersionInfo.getCodeName(apiLevel)?.also {
                // since we are using the codename here directly, disable adding it
                // later
                useCodeName = false
            }
        } else null
        relName ?: codeName

    } else null

    // get a codename from the API level
    val codeName = if (useCodeName) SdkVersionInfo.getCodeName(apiLevel) else null

    val hasDetails = codeName != null || releaseName != null
    if (hasDetails) {
        sb.append(" (")
    }

    if (codeName != null) {
        sb.append("\"$codeName\"")
        if (releaseName != null) {
            sb.append("; ")
        }
    }

    if (releaseName != null) {
        sb.append("Android $releaseName")
    }

    if (hasDetails) {
        sb.append(")")
    }

    return sb.toString()
}

/**
 * Computes and returns the Android release name + API level/extension + codename
 *
 * This does not support preview platform since it requires an integer API Level
 *
 * The rendering will look like this:
 * - Android 12.0
 * - Android 12.0 (API 33)
 * - Android 12.0 (API 33 ext. 4)
 * - Android 12.0 ("Tiramisu")
 * - Android 12.0 ("Tiramisu"; API 33 ext. 4)
 *
 * IF a release name is unknown, the API level will be used instead:
 * - Android API 314
 * - Android API 314, extension 34
 *
 * For a API level focused string, see [computeFullApiName]
 *
 * The highest number (inclusive) that is supported
 * is [SdkVersionInfo.HIGHEST_KNOWN_API].
 *
 * @param apiLevel the API level
 * @param extensionLevel the extension level or null to represent the base extension of the given API (or unknown)
 * @param includeApiLevel whether to include the API Level in the string
 * @param includeCodeName whether to include the codename in the string
 * @return a suitable version display name
 */
fun computeFullReleaseName(
    apiLevel: Int,
    extensionLevel: Int?,
    includeApiLevel: Boolean = false,
    includeCodeName: Boolean = false,
): String {
    val sb = StringBuilder()

    val releaseName = SdkVersionInfo.getReleaseVersionString(apiLevel)

    // It is possible that Studio does not know the name of the release if the platform is more
    // recent.
    // In this case, because this method is supposed to display the release name, we will look
    // for alternative options.
    // It is possible that Studio knows the name if it's currently a preview. This is normally caught
    // in [getFullReleaseName] but in case of direct call here with [AndroidVersion.featureLevel]
    // it is possible that we are called with a preview API. In this case we will attempt to
    // display the codename. However, we cannot be certain that this is in fact a preview
    // so we will not use that term.
    if (releaseName == null) {
        val knownCodeName = SdkVersionInfo.getCodeName(apiLevel)

        if (knownCodeName != null) {
            // TODO(267390396): add testing for this. It's currently untestable because it relies on static data that cannot be injected.
            sb.append("Android $knownCodeName")
            if (includeApiLevel) {
                sb.append(" (API \"$apiLevel\"")
                if (extensionLevel != null) {
                    sb.append(" ext. $extensionLevel")
                }
                sb.append(")")
            }
        } else {
            // only use the API level
            sb.append("Android API $apiLevel")
            if (includeApiLevel && extensionLevel != null) {
                sb.append(" ext. $extensionLevel")
            }
        }

        // we already handled either the API and/or the codename to circumvent the lack
        // of release name, so we stop here.
        return sb.toString()
    }

    sb.append("Android $releaseName")

    val resolvedCodeName = if (includeCodeName) SdkVersionInfo.getCodeName(apiLevel) else null

    val hasDetails = resolvedCodeName != null || includeApiLevel
    if (hasDetails) {
        sb.append(" (")
    }

    if (resolvedCodeName != null) {
        sb.append("\"$resolvedCodeName\"")

        if (includeApiLevel) {
            sb.append("; ")
        }
    }

    if (includeApiLevel) {
        sb.append("API $apiLevel")
        if (extensionLevel != null) {
            sb.append(" ext. $extensionLevel")
        }
    }


    if (hasDetails) {
        sb.append(")")
    }

    return sb.toString()
}
