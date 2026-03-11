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
package com.android.ide.common.repository

/** Workaround for firebase performance plugin b/428189815 */
@Suppress("DEPRECATION_ERROR")
@Deprecated("", level = DeprecationLevel.HIDDEN)
class GradleVersion private constructor(private val agpVersion: AgpVersion) : Comparable<GradleVersion> {

  @Deprecated("", level = DeprecationLevel.HIDDEN)
  @JvmSynthetic
  override fun compareTo(other: GradleVersion): Int {
    return this.agpVersion.compareTo(other.agpVersion)
  }

  @Deprecated("", level = DeprecationLevel.HIDDEN)
  companion object {

    @Deprecated("", level = DeprecationLevel.HIDDEN)
    @JvmSynthetic
    @JvmStatic
    fun parseAndroidGradlePluginVersion(version: String): GradleVersion = GradleVersion(AgpVersion.parse(version))
  }
}
