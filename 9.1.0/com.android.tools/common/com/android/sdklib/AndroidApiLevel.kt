/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.sdklib

import java.io.Serializable

/**
 * If the apiLevel is at least MIN_MAJOR_WITH_EXPLICIT_MINOR, the version string will always include the minor version, even if it is zero.
 *
 * <p>In other words, the expected sequence of versions currently encoded is:
 * <ul>
 * <li>35
 * <li>36.0 (minor version is always included in the version string for 36 and above)
 * <li>36.1
 * </ul>
 *
 * See AndroidVersionTest.testMinorVersionNormalization
 */
private const val MIN_API_FOR_EXPLICIT_MINOR: Int = 36

data class AndroidApiLevel @JvmOverloads constructor(val majorVersion: Int, val minorVersion: Int = 0) :
  Comparable<AndroidApiLevel>, Serializable {

  override fun compareTo(other: AndroidApiLevel): Int {
    if (majorVersion != other.majorVersion) return majorVersion.compareTo(other.majorVersion)
    return minorVersion.compareTo(other.minorVersion)
  }

  override fun toString(): String {
    return if (majorVersion >= MIN_API_FOR_EXPLICIT_MINOR || minorVersion > 0) "$majorVersion.$minorVersion" else majorVersion.toString()
  }

  /** Returns a text representation of the API level without the minor version if it is zero. */
  fun toShortString(): String {
    return if (minorVersion > 0) "$majorVersion.$minorVersion" else majorVersion.toString()
  }

  companion object {
    @JvmStatic
    fun fromString(s: String): AndroidApiLevel? =
      API_LEVEL_REGEX.matchEntire(s)?.let { AndroidApiLevel(it.groupValues[1].toInt(), it.groups[3]?.value?.toInt() ?: 0) }

    private val API_LEVEL_REGEX = Regex("(\\d+)(\\.(\\d+))?")
  }
}
