/*
 * Copyright (C) 2019 The Android Open Source Project
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

@file:JvmName("ResourcesUtil")
package com.android.ide.common.resources

import com.android.SdkConstants

/**
 * Replicates the key flattening done by AAPT. If the passed key contains '.', '-' or ':', they
 * will be replaced by '_' and a a new [String] returned. If none of those characters are
 * contained, the same [String] passed as input will be returned.
 */
fun flattenResourceName(resourceName: String): String {
  var i = 0
  val n = resourceName.length
  while (i < n) {
    var c = resourceName[i]
    if (isInvalidResourceNameCharacter(c)) {
      // We found one instance that we need to replace. Allocate the buffer, copy everything up to this point and start replacing.
      val buffer = CharArray(resourceName.length)
      resourceName.toCharArray(buffer, 0, 0, i)
      buffer[i] = '_'
      for (j in i + 1 until n) {
        c = resourceName[j]
        buffer[j] = if (isInvalidResourceNameCharacter(c)) '_' else c
      }
      return String(buffer)
    }
    i++
  }

  return resourceName
}

fun isInvalidResourceNameCharacter(c: Char): Boolean {
  return c == ':' || c == '.' || c == '-'
}

/**
 * Returns the given id without an `@id/` or `@+id` prefix.
 */
fun stripPrefixFromId(id: String): String {
  return when {
    id.startsWith(SdkConstants.NEW_ID_PREFIX) -> id.substring(SdkConstants.NEW_ID_PREFIX.length)
    id.startsWith(SdkConstants.ID_PREFIX) -> id.substring(SdkConstants.ID_PREFIX.length)
    else -> id
  }
}