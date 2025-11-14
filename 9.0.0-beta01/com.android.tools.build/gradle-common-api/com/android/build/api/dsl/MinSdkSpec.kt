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
 * DSL object to configure min SDK version.
 *
 * To set min SDK version with a released API level,
 *
 * ```
 * android {
 *   defaultConfig {
 *     minSdk {
 *       version = release(33)
 *     }
 *   }
 * }
 * ```
 *
 * or to set min SDK version with a preview API level
 *
 * ```
 * android {
 *   defaultConfig {
 *     minSdk {
 *       version = preview("Tiramisu")
 *     }
 *   }
 * }
 * ```
 */
interface MinSdkSpec {

    /**
     * The min SDK version.
     */
    var version: MinSdkVersion?

    /**
     * To set min SDK version with a released API level, use this function to compute the
     * [MinSdkVersion] and assign it to [MinSdkSpec.version] property.
     */
    fun release(version: Int): MinSdkVersion

    /**
     * To set min SDK version with a preview API level, use this function to compute the
     * [MinSdkVersion] and assign it to [MinSdkSpec.version] property.
     */
    fun preview(codeName: String): MinSdkVersion
}

/**
 * DSL object to represent the min SDK version.
 */
interface MinSdkVersion {

    /**
     * The API level of the min SDK version.
     */
    val apiLevel: Int?

    /**
     * The preview API level of the min SDK version.
     */
    val codeName: String?
}
