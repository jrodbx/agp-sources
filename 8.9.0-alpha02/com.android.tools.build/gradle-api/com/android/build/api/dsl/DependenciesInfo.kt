/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.api.dsl

/** DSL object to specify whether to include SDK dependency information in APKs and Bundles.
 *
 * Including dependency information in your APK or Bundle allows Google Play to ensure that
 * any third-party software your app uses complies with
 * [Google Play's Developer Program Policies](https://support.google.com/googleplay/android-developer/topic/9858052).
 * For more information, see the Play Console support page
 * [Using third-party SDKs in your app](https://support.google.com/googleplay/android-developer/answer/10358880).*/
interface DependenciesInfo {

  /** If false, information about SDK dependencies of an APK will not be added to its signature
   * block. */
  var includeInApk: Boolean

  /** If false, information about SDK dependencies of an App Bundle will not be added to it. */
  var includeInBundle: Boolean
}
