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

import com.google.common.annotations.VisibleForTesting

/**
 * Used in tests to fake out the Environment code used in production to allow injecting custom
 * environment variable values.
 */
@VisibleForTesting
object EnvironmentFakes {
  /** Helper to fake the ANDROID_PREFS_ROOT environment variable to be set to `path`. */
  fun setCustomAndroidPrefsRootEnvironment(path: String) {
    setSingleProperty("ANDROID_PREFS_ROOT", path)
  }

  fun setMap(map: Map<String, String>) {
    Environment.instance =
      object : Environment() {
        override fun getVariable(name: EnvironmentVariable): String? {
          return map[name.key]
        }
      }
  }

  fun setSingleProperty(key: String, value: String) {
    Environment.instance =
      object : Environment() {
        override fun getVariable(name: EnvironmentVariable): String? {
          return if (key == name.key) {
            value
          } else null
        }
      }
  }

  /** Helper to fake the ANDROID_PREFS_ROOT environment variable to be unset. */
  fun setNoEnvironmentVariable() {
    Environment.instance =
      object : Environment() {
        override fun getVariable(name: EnvironmentVariable): String? {
          return null
        }
      }
  }

  /** Helper to undo faking the environment variable reading. */
  fun setSystemEnvironment() {
    Environment.instance = Environment.SYSTEM
  }
}
