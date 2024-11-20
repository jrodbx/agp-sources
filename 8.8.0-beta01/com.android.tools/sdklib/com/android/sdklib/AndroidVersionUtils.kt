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

/**
 * A formatted name for the Android version
 *
 * - [name] is the main name as returned by the various methods. This can be release focused
 *   (e.g. "Android 12.0"), or API focused ("API 33").
 * - [details] is an optional string that shows the other side of the name. Release focused
 *   name can have the API level in the details, and API focused name can have the release name
 *   in the details.
 */
data class NameDetails(
    val name: String,
    val details: String?
)

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
 * Returns the API name with extension and the marketing version + codename detail as two strings
 *
 * This will support platform previews by returning the codename only (since
 * no API level or release name has been set at that time).
 * The string will look like:
 * - API Tiramisu Preview
 *
 * See [computeApiNameAndDetails] for non-preview string rendering
 *
 * The highest number (inclusive) that is supported
 * is [SdkVersionInfo.HIGHEST_KNOWN_API].
 *
 * For a release name focused string, see [getApiNameAndDetails]
 *
 * @param includeReleaseName whether to include the release name in the string
 * @param includeCodeName whether to include the codename in the string
 * @return a suitable version display name
 */
fun AndroidVersion.getApiNameAndDetails(
    includeReleaseName: Boolean = false,
    includeCodeName: Boolean = false,
): NameDetails {
    // See http://source.android.com/source/build-numbers.html

    if (codename != null) {
        return NameDetails("API $codename Preview", null)
    }

    return computeApiNameAndDetails(
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
 * Returns the Android release name and the API level/extension + codename as 2 strings
 *
 * This will support platform previews by returning the codename only (since
 * no API level or release name has been set at that time).
 * The string will look like:
 * - Android Tiramisu Preview
 *
 * See [computeReleaseNameAndDetails] for non-preview string rendering
 *
 * For an API level focused string, see [getApiNameAndDetails]
 *
 * The highest number (inclusive) that is supported
 * is [SdkVersionInfo.HIGHEST_KNOWN_API].
 *
 * @param includeApiLevel whether to include the API Level in the string
 * @param includeCodeName whether to include the codename in the string
 * @return a suitable version display name
 */
fun AndroidVersion.getReleaseNameAndDetails(
    includeApiLevel: Boolean = false,
    includeCodeName: Boolean = false,
): NameDetails {
    // See http://source.android.com/source/build-numbers.html

    if (codename != null) {
        return NameDetails("Android $codename Preview", null)
    }

    return computeReleaseNameAndDetails(
        apiLevel,
        if (isBaseExtension) null else extensionLevel,
        includeApiLevel,
        includeCodeName
    )
}


/**
 * Computes and returns the API name + extension + marketing version and codename as a single string
 *
 * See format options in [computeApiNameAndDetails].
 *
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
    val nameDetails = computeApiNameAndDetails(apiLevel, extensionLevel, includeReleaseName, includeCodeName)

    if (nameDetails.details != null) {
        return "${nameDetails.name} (${nameDetails.details})"
    }

    return nameDetails.name
}

/**
 * Computes and returns the API name + extension, as well as the marketing version and codename
 * as 2 separate strings.
 *
 * This does not support platform previews since it requires an integer API Level
 *
 * The rendering will look like this:
 * - API 33        / null
 * - API 33 ext. 4 / null
 * - API 33        / Android 13.0
 * - API 33        / "Tiramisu"
 * - API 33        / "Tiramisu"; Android 13.0
 *
 * If the release name or codename are unknown, they will be omitted.
 *
 * For a release name focused string, see [computeFullReleaseName]
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
fun computeApiNameAndDetails(
    apiLevel: Int,
    extensionLevel: Int?,
    includeReleaseName: Boolean = false,
    includeCodeName: Boolean = false,
): NameDetails {
    // See http://source.android.com/source/build-numbers.html

    val name = StringBuilder("API $apiLevel")
    if (extensionLevel != null) {
        name.append(" ext. $extensionLevel")
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

    if (codeName == null && releaseName == null) {
        return NameDetails(name.toString(), null)
    }

    val details = StringBuilder()

    if (codeName != null) {
        details.append("\"$codeName\"")
        if (releaseName != null) {
            details.append("; ")
        }
    }

    if (releaseName != null) {
        details.append("Android $releaseName")
    }

    return NameDetails(name.toString(), details.toString())
}

/**
 * Computes and returns the Android release name + API level/extension + codename
 *
 * This does not support platform previews since it requires an integer API Level
 *
 * The rendering will look like this:
 * - Android 12.0
 * - Android 12.0 (API 33)
 * - Android 12.0 (API 33 ext. 4)
 * - Android 12.0 ("Tiramisu")
 * - Android 12.0 ("Tiramisu"; API 33 ext. 4)
 *
 * If a release name is unknown, the API level will be used instead:
 * - Android API 314
 * - Android API 314, extension 34
 *
 * For an API level focused string, see [computeFullApiName]
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
    val nameDetails = computeReleaseNameAndDetails(apiLevel, extensionLevel, includeApiLevel, includeCodeName)

    if (nameDetails.details != null) {
        return "${nameDetails.name} (${nameDetails.details})"
    }

    return nameDetails.name
}

/**
 * Computes and returns the Android release name, as well as the API level/extension & codename
 * as two separate strings
 *
 * This does not support platform previews since it requires an integer API Level
 *
 * The rendering will look like this:
 * - Android 12.0 / null
 * - Android 12.0 / API 33
 * - Android 12.0 / API 33 ext. 4
 * - Android 12.0 / "Tiramisu"
 * - Android 12.0 / "Tiramisu"; API 33 ext. 4
 *
 * If a release name is unknown, the API level will be used instead:
 * - Android API 314
 * - Android API 314, extension 34
 *
 * For an API level focused string, see [computeFullApiName]
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
fun computeReleaseNameAndDetails(
    apiLevel: Int,
    extensionLevel: Int?,
    includeApiLevel: Boolean = false,
    includeCodeName: Boolean = false,
): NameDetails {
    val name = StringBuilder()

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
            name.append("Android $knownCodeName")
            if (includeApiLevel) {
                name.append(" (API \"$apiLevel\"")
                if (extensionLevel != null) {
                    name.append(" ext. $extensionLevel")
                }
                name.append(")")
            }
        } else {
            // only use the API level
            name.append("Android API $apiLevel")
            if (includeApiLevel && extensionLevel != null) {
                name.append(" ext. $extensionLevel")
            }
        }

        // we already handled either the API and/or the codename to circumvent the lack
        // of release name, so we stop here.
        return NameDetails(name.toString(), null)
    }

    name.append("Android $releaseName")

    val resolvedCodeName = if (includeCodeName) SdkVersionInfo.getCodeName(apiLevel) else null

    if (resolvedCodeName == null && !includeApiLevel) {
        return NameDetails(name.toString(), null)
    }

    val details = StringBuilder()

    if (resolvedCodeName != null) {
        details.append("\"$resolvedCodeName\"")

        if (includeApiLevel) {
            details.append("; ")
        }
    }

    if (includeApiLevel) {
        details.append("API $apiLevel")
        if (extensionLevel != null) {
            details.append(" ext. $extensionLevel")
        }
    }

    return NameDetails(name.toString(), details.toString())
}
