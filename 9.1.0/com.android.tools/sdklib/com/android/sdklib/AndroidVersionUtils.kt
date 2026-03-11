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
 * A pair of strings describing an Android version.
 * - [name] is the main name as returned by the various methods. This can be release-focused (e.g. "Android 12.0"), or API-focused ("API
 *   33").
 * - [details] is an optional string that shows the other side of the name. A release-focused name can have the API level in the details,
 *   and an API-focused name can have the release name in the details.
 */
data class NameDetails(val name: String, val details: String?)

/**
 * Returns the API name, and optionally includes the marketing version and codename.
 *
 * For preview versions, returns the codename only (since no API level or release name has been set at that time). The string will look
 * like:
 * - API Tiramisu Preview
 *
 * For non-preview versions, the rendering will look like:
 * - API 33 (includeReleaseName == false, includeCodeName == false)
 * - API 33 ext. 4 (includeReleaseName == false, includeCodeName == false)
 * - API 33 (Android 13.0) (includeReleaseName == true, includeCodeName == false)
 * - API 33 ("Tiramisu") (includeReleaseName == false, includeCodeName == true)
 * - API 33 ("Tiramisu"; Android 13.0) (includeReleaseName == true, includeCodeName == true)
 *
 * The highest number (inclusive) that is supported is [SdkVersionInfo.HIGHEST_KNOWN_API].
 *
 * For a release-name-focused string, see [getFullReleaseName].
 *
 * @param includeReleaseName whether to include the release name in the string
 * @param includeCodeName whether to include the codename in the string
 * @return a suitable version display name
 */
@JvmOverloads
fun AndroidVersion.getFullApiName(
  includeReleaseName: Boolean = false,
  includeCodeName: Boolean = false,
  includeMinorVersion: Boolean = true,
): String {
  val nameDetails = getApiNameAndDetails(includeReleaseName, includeCodeName, includeMinorVersion)

  if (nameDetails.details != null) {
    return "${nameDetails.name} (${nameDetails.details})"
  }

  return nameDetails.name
}

/**
 * Returns a NameDetails containing the API level and extension as "name", and the marketing version and/or the codename as "details".
 *
 * For preview versions, returns the codename only (since no API level or release name has been set at that time). The string will look
 * like:
 * - API Tiramisu Preview
 *
 * For non-preview versions, the rendering will look like this:
 * - API 33 / null (includeReleaseName == false, includeCodeName == false)
 * - API 33 ext. 4 / null (includeReleaseName == false, includeCodeName == false)
 * - API 33 / Android 13.0 (includeReleaseName == true, includeCodeName == false)
 * - API 33 / "Tiramisu" (includeReleaseName == false, includeCodeName == true)
 * - API 33 / "Tiramisu"; Android 13.0 (includeReleaseName == true, includeCodeName == true)
 * - API 36.0 / "Baklava"; Android 16.0 (includeReleaseName == true, includeCodeName == true)
 *
 * If the release name or codename are unknown, they will be omitted.
 *
 * For a release-name-focused string, see [getFullReleaseName].
 *
 * The highest number (inclusive) that is supported is [SdkVersionInfo.HIGHEST_KNOWN_API].
 *
 * @param includeReleaseName whether to include the release name in the string
 * @param includeCodeName whether to include the codename in the string
 * @return a suitable version display name
 */
fun AndroidVersion.getApiNameAndDetails(
  includeReleaseName: Boolean = false,
  includeCodeName: Boolean = false,
  includeMinorVersion: Boolean = true,
): NameDetails {
  // See http://source.android.com/source/build-numbers.html

  if (codename != null) {
    return NameDetails("API $codename Preview", null)
  }

  val name = StringBuilder("API ")
  if (includeMinorVersion) {
    name.append(getApiStringWithoutExtension())
  } else {
    name.append(androidApiLevel.majorVersion)
  }
  if (!isBaseExtension) {
    name.append(" ext. ").append(extensionLevel)
  }

  var useCodeName = includeCodeName

  val releaseName =
    if (includeReleaseName) {
      // It is possible that Studio does not know the name of the release if the platform is more
      // recent.
      // In this case, because this method is supposed to display the release name, we will look
      // for alternative options.
      // It is possible that Studio knows the name if it's currently a preview. This is normally
      // caught
      // in [getFullReleaseName] but in case of direct call here with
      // [AndroidVersion.featureLevel]
      // it is possible that we are called with a preview API. In this case we will attempt to
      // display the codename. However, we cannot be certain that this is in fact a preview
      // so we will not use that term.
      // TODO(267390396): add testing for this. It's currently untestable because it relies on
      // static data that cannot be injected.
      val relName = SdkVersionInfo.getReleaseVersionString(apiLevel)
      val codeName =
        if (relName == null) {
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
    details.append("\"").append(codeName).append("\"")
    if (releaseName != null) {
      details.append("; ")
    }
  }

  if (releaseName != null) {
    details.append("Android ").append(releaseName)
  }

  return NameDetails(name.toString(), details.toString())
}

/**
 * Returns the Android release name, and optionally the API level and/or codename.
 *
 * For preview versions, returns the codename only (since no API level or release name has been set at that time). The string will look
 * like:
 * - Android Tiramisu Preview
 *
 * For non-previews, the rendering will look like:
 * - Android 12.0
 * - Android 12.0 (API 33)
 * - Android 12.0 (API 33 ext. 4)
 * - Android 12.0 ("Tiramisu")
 * - Android 12.0 ("Tiramisu"; API 33 ext. 4)
 *
 * If a release name is unknown, the API level will be used instead:
 * - Android API 314.0
 * - Android API 314.0, extension 34
 *
 * For an API-level-focused string, see [getFullApiName].
 *
 * The highest number (inclusive) that is supported is [SdkVersionInfo.HIGHEST_KNOWN_API].
 *
 * @param includeApiLevel whether to include the API Level in the string
 * @param includeCodeName whether to include the codename in the string
 * @return a suitable version display name
 */
fun AndroidVersion.getFullReleaseName(includeApiLevel: Boolean = false, includeCodeName: Boolean = false): String {
  val nameDetails = getReleaseNameAndDetails(includeApiLevel, includeCodeName)

  if (nameDetails.details != null) {
    return "${nameDetails.name} (${nameDetails.details})"
  }

  return nameDetails.name
}

/**
 * Returns a NameDetails containing the Android release as "name", and the API level and/or codename as "details".
 *
 * For preview versions, returns the codename only (since no API level or release name has been set at that time). The returned NameDetails
 * will look like:
 * - Android Tiramisu Preview / null
 *
 * For non-preview versions, the returned NameDetails will look like:
 * - Android 12.0 / null
 * - Android 12.0 / API 33
 * - Android 12.0 / API 33 ext. 4
 * - Android 12.0 / "Tiramisu"
 * - Android 12.0 / "Tiramisu"; API 33 ext. 4
 *
 * If a release name is unknown, the API level will be used instead:
 * - Android API 314.0
 * - Android API 314.0, extension 34
 *
 * For an API-level-focused string, see [getApiNameAndDetails].
 *
 * The highest number (inclusive) that is supported is [SdkVersionInfo.HIGHEST_KNOWN_API].
 *
 * @param includeApiLevel whether to include the API Level in the string
 * @param includeCodeName whether to include the codename in the string
 * @return a suitable version display name
 */
fun AndroidVersion.getReleaseNameAndDetails(includeApiLevel: Boolean = false, includeCodeName: Boolean = false): NameDetails {
  // See http://source.android.com/source/build-numbers.html

  if (codename != null) {
    return NameDetails("Android $codename Preview", null)
  }

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
      // TODO(267390396): add testing for this. It's currently untestable because it relies on
      // static data that cannot be injected.
      name.append("Android $knownCodeName")
      if (includeApiLevel) {
        name.append(" (API \"$apiStringWithoutExtension\"")
        if (!isBaseExtension) {
          name.append(" ext. $extensionLevel")
        }
        name.append(")")
      }
    } else {
      // only use the API level
      name.append("Android API $apiStringWithoutExtension")
      if (includeApiLevel && !isBaseExtension) {
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
    details.append("API $apiStringWithoutExtension")
    if (!isBaseExtension) {
      details.append(" ext. $extensionLevel")
    }
  }

  return NameDetails(name.toString(), details.toString())
}
