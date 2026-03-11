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

package com.android.build.api.dsl

/**
 * Specifies the API level to compile your project against, which is required by the Android plugin.
 *
 * This means your code can use only the Android APIs included in that API level and lower. You can configure the compile sdk version by
 * adding the following block:
 * ```
 * android {
 *   compileSdk {
 *     version = release(33) {
 *       // this block and its properties are optional
 *       minorApiLevel = 1
 *       sdkExtension = 18
 *     }
 *   }
 * }
 * ```
 *
 * or to use a preview version:
 * ```
 * android {
 *   compileSdk {
 *     version = preview("Tiramisu")
 *   }
 * }
 * ```
 *
 * You should generally
 * [use the most up-to-date API level](https://developer.android.com/guide/topics/manifest/uses-sdk-element.html#ApiLevels) available.
 *
 * If you are planning to also support older API levels, it's good practice to
 * [use the Lint tool](https://developer.android.com/studio/write/lint.html) to check if you are using APIs that are not available in
 * earlier API levels.
 *
 * This can be set on all Gradle projects with [com.android.build.api.dsl.SettingsExtension.compileSdk]
 */
interface CompileSdkSpec {

  /** The compile SDK version set for this project. */
  var version: CompileSdkVersion?

  /**
   * To set compile SDK version with a released API level, use this function to compute the [CompileSdkVersion] and assign it to
   * [CompileSdkSpec.version] property. When using a released API level, you can also set minor API level and SDK extension level via
   * [CompileSdkReleaseSpec] block.
   */
  fun release(version: Int, action: (CompileSdkReleaseSpec.() -> Unit)): CompileSdkVersion

  /**
   * To set compile SDK version with a released API level, use this function to compute the [CompileSdkVersion] and assign it to
   * [CompileSdkSpec.version] property.
   */
  fun release(version: Int): CompileSdkVersion

  /**
   * To set compile SDK version with a preview API level, use this function to compute the [CompileSdkVersion] and assign it to
   * [CompileSdkSpec.version] property.
   */
  fun preview(codeName: String): CompileSdkVersion

  /**
   * Specify an SDK add-on to compile your project against.
   *
   * This can be set on all Gradle projects with [com.android.build.api.dsl.SettingsExtension.compileSdkAddon]
   *
   * @param vendor the vendor name of the add-on.
   * @param name the name of the add-on
   * @param version the integer API level of the add-on
   */
  fun addon(vendor: String, name: String, version: Int): CompileSdkVersion
}

/** DSL object to represent compile sdk version. */
interface CompileSdkVersion {

  /** The API level to compile your project against. */
  val apiLevel: Int?

  /** Minor API version for compile SDK. This should be used with the [apiLevel] property to specify a full SDK version. */
  val minorApiLevel: Int?

  /** The SDK Extension level to compile your project against. */
  val sdkExtension: Int?

  /**
   * The preview API to compile your project against. Once the preview APIs are finalized, they will be allocated a stable integer value.
   */
  val codeName: String?

  /** The add-on name of the SDK to compile your project against. */
  val addonName: String?

  /** The vendor name of the SDK to compile your project against. */
  val vendorName: String?
}
