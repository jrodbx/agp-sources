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
 * DSL object to configure the target SDK.
 *
 * To configure target SDK with a release API level,
 * ```
 * android {
 *   defaultConfig {
 *     targetSdk {
 *       version = release(33)
 *     }
 *   }
 * }
 * ```
 *
 * or with a preview API level
 *
 * ```
 * android {
 *   defaultConfig {
 *     targetSdk {
 *       version = preview("Tiramisu")
 *     }
 *   }
 * }
 * ```
 */
interface TargetSdkSpec {

  /** The target SDK version. */
  var version: TargetSdkVersion?

  /**
   * To set target SDK version with a released API level, use this function to compute the [TargetSdkVersion] and assign it to
   * [TargetSdkSpec.version] property.
   */
  fun release(version: Int): TargetSdkVersion

  /**
   * To set target SDK version with a preview API level, use this function to compute the [TargetSdkVersion] and assign it to
   * [TargetSdkSpec.version] property.
   */
  fun preview(codeName: String): TargetSdkVersion
}

/** DSL object to represent the target SDK. */
interface TargetSdkVersion {

  /** The released API level for the target SDK. */
  val apiLevel: Int?

  /** The preview API level for the target SDK. */
  val codeName: String?
}
