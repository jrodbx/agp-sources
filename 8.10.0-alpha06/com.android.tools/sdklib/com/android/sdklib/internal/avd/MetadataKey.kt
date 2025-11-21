/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.sdklib.internal.avd

/**
 * Keys to config entries in an AVD's metadata .ini file (i.e. the file which resides in the
 * .android/avd directory and normally shares a basename with the AVD's data folder).
 */
object MetadataKey {
  /** The *absolute* path to the AVD folder (which contains the #CONFIG_INI file). */
  const val ABS_PATH = "path" // $NON-NLS-1$

  /**
   * The path to the AVD folder (which contains the config.ini file) relative to
   * AbstractAndroidLocations.FOLDER_DOT_ANDROID.
   *
   * This information is written in the avd ini *only* if the AVD folder is located under the
   * .android path (i.e. the relative path has no backward `..` references).
   */
  const val REL_PATH = "path.rel" // $NON-NLS-1$

  /** The [IAndroidTarget.hashString] of the AVD. */
  const val TARGET = "target" // $NON-NLS-1$
}
