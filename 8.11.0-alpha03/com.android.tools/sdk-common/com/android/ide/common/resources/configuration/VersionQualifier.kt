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

import java.util.regex.Pattern

/** Resource qualifier for Platform version. */
class VersionQualifier(val version: Int = DEFAULT_VERSION) : ResourceQualifier() {

  override fun getName() = NAME

  override fun getShortName() = "Version"

  override fun since() = 1

  override fun isValid() = version != DEFAULT_VERSION

  override fun hasFakeValue() = false

  override fun checkAndSet(value: String, config: FolderConfiguration): Boolean {
    val qualifier = getQualifier(value) ?: return false

    config.setVersionQualifier(qualifier)
    return true
  }

  override fun equals(qualifier: Any?): Boolean {
    return qualifier is VersionQualifier && this.version == qualifier.version
  }

  override fun isMatchFor(qualifier: ResourceQualifier): Boolean {
    if (qualifier !is VersionQualifier) return false

    // It is considered a match if our API level is equal or lower to the given qualifier,
    // or the given qualifier doesn't specify an API Level.
    return this.version <= qualifier.version || qualifier.version == DEFAULT_VERSION
  }

  override fun isBetterMatchThan(
    compareTo: ResourceQualifier?,
    reference: ResourceQualifier,
  ): Boolean {
    if (compareTo == null) return true

    val compareQ = compareTo as VersionQualifier
    val referenceQ = reference as VersionQualifier

    return when {
      // what we have is already the best possible match (exact match)
      compareQ.version == referenceQ.version -> false
      // got new exact value, this is the best!
      this.version == referenceQ.version -> true
      // In all case we're going to prefer the higher version (since they have been filtered to not
      // be too high.)
      else -> this.version > compareQ.version
    }
  }

  override fun hashCode() = version

  /** Returns the string used to represent this qualifier in the folder name. */
  override fun getFolderSegment() = getFolderSegment(version)

  override fun getShortDisplayValue(): String {
    return if (version == DEFAULT_VERSION) "" else "API $version"
  }

  override fun getLongDisplayValue(): String {
    return if (version == DEFAULT_VERSION) "" else "API Level $version"
  }

  companion object {
    /** Default version. This means the property is not set. */
    const val DEFAULT_VERSION = -1

    private val sVersionPattern: Pattern = Pattern.compile("^v(\\d+)$")

    const val NAME: String = "Platform Version"

    /**
     * Creates and returns a qualifier from the given folder segment. If the segment is incorrect,
     * `null` is returned.
     *
     * @param segment the folder segment from which to create a qualifier
     * @return a new VersionQualifier object or `null`
     */
    @JvmStatic
    fun getQualifier(segment: String): VersionQualifier? {
      val m = sVersionPattern.matcher(segment)
      if (!m.matches()) return null

      val version = m.group(1).toIntOrNull() ?: return null
      return VersionQualifier(version)
    }

    /**
     * Returns the folder name segment for the given version value. This is equivalent to calling
     * `new VersionQualifier(version).toString()`.
     *
     * @param version the value of the qualifier, as returned by [.getVersion].
     */
    fun getFolderSegment(version: Int): String {
      return if (version == DEFAULT_VERSION) "" else "v$version"
    }
  }
}
