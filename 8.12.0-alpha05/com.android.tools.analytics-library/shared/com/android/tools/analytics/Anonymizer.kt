/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.analytics

import com.google.common.base.Charsets
import com.google.common.hash.Hashing

/**
 * Anonymizes strings for analytics reporting. Each string is sha256 encoded with a salt that is
 * unique per user and rotated every 28 days with a predictable time window.
 */
object Anonymizer {
  /** Anonymizes a string. Returns null if the salt is not initialized in AnalyticsSettings. */
  @JvmStatic
  fun anonymize(data: String?): String? {
    if (data == null || data == "") {
      return ""
    }

    val salt = AnalyticsSettings.salt
    if (salt.isEmpty()) {
      return null
    } else {
      val hasher = Hashing.sha256().newHasher()
      hasher.putBytes(salt)
      hasher.putString(data, Charsets.UTF_8)
      return hasher.hash().toString()
    }
  }
}
