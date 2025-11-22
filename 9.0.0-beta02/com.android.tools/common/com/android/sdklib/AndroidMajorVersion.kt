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

/**
 * A representation of an Android major version that explicitly doesn't include minor versions. This
 * should be used instead of AndroidVersion to avoid false precision when we don't know or don't
 * care about the minor version.
 */
data class AndroidMajorVersion
@JvmOverloads
constructor(val apiLevel: Int, val codename: String? = null) : Comparable<AndroidMajorVersion> {

  @JvmOverloads
  constructor(apiLevel: AndroidApiLevel, codename: String? = null): this(apiLevel.majorVersion, codename)

  val apiString
    get() = codename ?: apiLevel.toString()

  val isPreview: Boolean
    get() = codename != null

  val featureLevel: Int
    get() = if (isPreview) apiLevel + 1 else apiLevel

  override fun compareTo(other: AndroidMajorVersion): Int = comparator.compare(this, other)

  companion object {
    private val comparator: Comparator<AndroidMajorVersion> =
      compareBy<AndroidMajorVersion> { it.apiLevel }.thenBy(nullsFirst()) { it.codename }
  }
}
