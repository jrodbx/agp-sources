/*
 * Copyright (C) 2009 The Android Open Source Project
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
package com.android.ide.common.resources.configuration

import com.android.sdklib.AndroidApiLevel
import java.util.Objects

/** Resource qualifier for Platform version. */
class VersionQualifier(val androidApiLevel: AndroidApiLevel?, val includeMinorVersion: Boolean) : ResourceQualifier() {

  constructor() : this(null, false)

  constructor(androidApiLevel: AndroidApiLevel) : this(androidApiLevel, androidApiLevel.majorVersion >= 36)

  constructor(majorVersion: Int) : this(if (majorVersion != DEFAULT_API_LEVEL.majorVersion) AndroidApiLevel(majorVersion) else null, false)

  init {
    val minorVersion = androidApiLevel?.minorVersion ?: 0
    require(minorVersion == 0 || includeMinorVersion) { "Minor version must be included unless it is 0." }
  }

  @Deprecated("Use androidApiLevel instead.", ReplaceWith("androidApiLevel?.majorVersion"))
  val version: Int
    get() = androidApiLevel?.majorVersion ?: DEFAULT_API_LEVEL.majorVersion

  override fun getName() = NAME

  override fun getShortName() = "Version"

  override fun since() = 1

  override fun isValid() = androidApiLevel != null

  override fun hasFakeValue() = false

  override fun checkAndSet(value: String, config: FolderConfiguration): Boolean {
    val qualifier = getQualifier(value) ?: return false

    config.setVersionQualifier(qualifier)
    return true
  }

  override fun equals(qualifier: Any?): Boolean {
    return qualifier is VersionQualifier &&
      this.androidApiLevel == qualifier.androidApiLevel &&
      this.includeMinorVersion == qualifier.includeMinorVersion
  }

  override fun isMatchFor(qualifier: ResourceQualifier): Boolean {
    if (qualifier !is VersionQualifier) return false

    // It is considered a match if our API level is equal or lower to the given qualifier,
    // or the given qualifier doesn't specify an API Level.
    val thisApiLevel = this.androidApiLevel ?: DEFAULT_API_LEVEL
    val qualifierApiLevel = qualifier.androidApiLevel

    return qualifierApiLevel == null || thisApiLevel <= qualifierApiLevel
  }

  override fun isBetterMatchThan(compareTo: ResourceQualifier?, reference: ResourceQualifier): Boolean {
    if (compareTo == null) return true

    val thisApiLevel = androidApiLevel ?: DEFAULT_API_LEVEL
    val compareApiLevel = (compareTo as VersionQualifier).androidApiLevel ?: DEFAULT_API_LEVEL
    val referenceApiLevel = (reference as VersionQualifier).androidApiLevel ?: DEFAULT_API_LEVEL

    return when {
      // what we have is already the best possible match (exact match)
      compareApiLevel == referenceApiLevel && compareTo.includeMinorVersion == reference.includeMinorVersion -> false
      // What we have already matches the API level, but the included minor version doesn't. Only
      // use this qualifier if it's an exact match.
      compareApiLevel == referenceApiLevel ->
        this.androidApiLevel == referenceApiLevel && this.includeMinorVersion == reference.includeMinorVersion
      // got new exact value, this is the best!
      thisApiLevel == referenceApiLevel -> true
      // In all case we're going to prefer the higher version (since they have been filtered to not
      // be too high.)
      else -> thisApiLevel > compareApiLevel
    }
  }

  override fun hashCode() = Objects.hash(androidApiLevel, includeMinorVersion)

  /** Returns the string used to represent this qualifier in the folder name. */
  override fun getFolderSegment() = getDisplayValueVersion()?.let { "v$it" } ?: ""

  override fun getShortDisplayValue() = getDisplayValueVersion()?.let { "API $it" } ?: ""

  override fun getLongDisplayValue() = getDisplayValueVersion()?.let { "API Level $it" } ?: ""

  private fun getDisplayValueVersion(): String? =
    when {
      androidApiLevel == null -> null
      includeMinorVersion -> "${androidApiLevel.majorVersion}.${androidApiLevel.minorVersion}"
      else -> androidApiLevel.majorVersion.toString()
    }

  companion object {

    /**
     * Default version. This means the property is not set. Using -1 allows comparisons within this class to be done numerically, rather
     * than dealing with nulls.
     */
    private val DEFAULT_API_LEVEL = AndroidApiLevel(-1)

    @JvmField val DEFAULT = VersionQualifier()

    private val versionPattern = Regex("^v(\\d+)(\\.(\\d+))?$")

    const val NAME = "Platform Version"

    /**
     * Creates and returns a qualifier from the given folder segment. If the segment is incorrect, `null` is returned.
     *
     * @param segment the folder segment from which to create a qualifier
     * @return a new VersionQualifier object or `null`
     */
    @JvmStatic
    fun getQualifier(segment: String): VersionQualifier? {
      val match = versionPattern.matchEntire(segment) ?: return null

      val majorVersion = match.groupValues[1].toIntOrNull() ?: return null
      val minorVersion = match.groupValues[3].toIntOrNull()
      return VersionQualifier(AndroidApiLevel(majorVersion, minorVersion ?: 0), minorVersion != null)
    }
  }
}
