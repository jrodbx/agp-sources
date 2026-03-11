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

/**
 * Helper class to create indirection reading environment variables and system properties. This allows providing custom logic for reading
 * these values. E.g. because Java doesn't allow overwriting environment variables for the current process, in tests this is used to provide
 * the environment variables. Also, build system may implement this in a way that is using build system specific APIs to access system
 * properties and environment variables.
 */
abstract class Environment {

  enum class EnvironmentVariable(val key: String) {
    ANDROID_PREFS_ROOT("ANDROID_PREFS_ROOT"),
    // FIXME b/162859043
    @Deprecated("Use ANDROID_PREFS_ROOT") ANDROID_SDK_HOME("ANDROID_SDK_HOME"), // former name of ANDROID_PREFS_ROOT
    PROCESSOR_ARCHITEW6432("PROCESSOR_ARCHITEW6432"),
    HOSTTYPE("HOSTTYPE"),
  }

  enum class SystemProperty(val key: String) {
    OS_VERSION("os.version"),
    ANDROID_PREFS_ROOT("ANDROID_PREFS_ROOT"),
    // FIXME b/162859043
    @Deprecated("Use ANDROID_PREFS_ROOT") ANDROID_SDK_HOME("ANDROID_SDK_HOME"), // former name of ANDROID_PREFS_ROOT
    USER_HOME("user.home"),
    OS_ARCH("os.arch"),
    OS_NAME("os.name"),
  }

  abstract fun getVariable(name: EnvironmentVariable): String?

  open fun getSystemProperty(name: SystemProperty): String? {
    return System.getProperty(name.key)
  }

  companion object {
    @JvmStatic
    val SYSTEM: Environment =
      object : Environment() {
        override fun getVariable(name: EnvironmentVariable): String? {
          return System.getenv(name.key)
        }
      }

    @JvmStatic var instance: Environment = SYSTEM
  }
}
