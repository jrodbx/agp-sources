/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.sdklib.internal.avd

import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.Device

object AvdNames {
  private const val ALLOWED_CHARS = "0-9a-zA-Z-_. ()"
  private const val ALLOWED_CHARS_READABLE = "a-z A-Z 0-9 . _ - ( )"

  @JvmStatic
  fun isValid(candidateName: String): Boolean {
    // The name is valid if it has one or more allowed characters
    // and only allowed characters
    return candidateName.matches("^[$ALLOWED_CHARS]+$".toRegex())
  }

  @JvmStatic
  internal fun stripBadCharacters(candidateName: String): String {
    // Remove any invalid characters.
    return candidateName.replace("[^$ALLOWED_CHARS]".toRegex(), " ")
  }

  @JvmStatic fun humanReadableAllowedCharacters(): String = ALLOWED_CHARS_READABLE

  /**
   * Get a version of `avdName` modified such that it is an allowed AVD filename. (This may be more
   * restrictive than what the underlying filesystem requires.) Remove leading and trailing spaces.
   * Replace consecutive disallowed characters, spaces, parentheses, and underscores by a single
   * underscore. If the result is empty, "myavd" will be returned.
   */
  @JvmStatic
  fun cleanAvdName(avdName: String): String {
    return avdName
      .replace("[^$ALLOWED_CHARS]".toRegex(), " ")
      .trim()
      .replace("[ ()_]+".toRegex(), "_")
      .ifBlank { "myavd" }
  }

  /**
   * Computes a reasonable display name for a newly-created AVD with the given device and version.
   */
  @JvmStatic
  fun getDefaultDeviceDisplayName(device: Device, version: AndroidVersion): String {
    // A device name might include the device's screen size as, e.g., 7". The " is not allowed in
    // a display name. Ensure that the display name does not include any forbidden characters.
    return stripBadCharacters(device.displayName) + " API " + version.apiStringWithExtension
  }

  fun uniquify(name: String, separator: String, isPresent: (String) -> Boolean): String {
    var suffix = 1
    var candidate = name
    while (isPresent(candidate)) {
      candidate = "$name$separator${++suffix}"
    }
    return candidate
  }
}

/**
 * Appends _n to the name if necessary to make the name unique, where n is the first number that
 * makes the filename unique (starting with 2).
 */
fun AvdManager.uniquifyAvdName(avdName: String): String =
  AvdNames.uniquify(avdName, "_") { getAvd(it, false) != null }

/**
 * If the given display name is already present on an AVD, appends the first number that makes it
 * unique (starting with 2).
 */
fun AvdManager.uniquifyDisplayName(displayName: String): String =
  AvdNames.uniquify(displayName, " ") { findAvdWithDisplayName(it) != null }
