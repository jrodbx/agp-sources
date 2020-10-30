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

import com.google.common.base.Strings

import java.io.File
import java.nio.file.Paths

/**
 * Helpers to get paths used to configure analytics reporting.
 */
object AnalyticsPaths {
  /**
   * Gets the spooling directory used for temporary storage of analytics data.
   */
  @JvmStatic
  val spoolDirectory: String
    get() = Paths.get(getAndEnsureAndroidSettingsHome(), "metrics", "spool").toString()

  /**
   * Gets the directory used to store android related settings (usually ~/.android).
   */
  @JvmStatic
  fun getAndEnsureAndroidSettingsHome(): String {
    // currently can't be shared with AndroidLocation see b/37123089
    var home = Environment.instance.getVariable("ANDROID_SDK_HOME")
    if (Strings.isNullOrEmpty(home)) {
      home = System.getProperty("ANDROID_SDK_HOME")
    }
    if (Strings.isNullOrEmpty(home)) {
      home = Paths.get(System.getProperty("user.home"), ".android").toString()
    }
    File(home).mkdirs()
    return home!!
  }
}
