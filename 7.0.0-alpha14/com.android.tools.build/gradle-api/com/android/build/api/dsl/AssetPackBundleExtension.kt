/*
 * Copyright (C) 2021 The Android Open Source Project
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

import org.gradle.api.Incubating

@Incubating
interface AssetPackBundleExtension {
    var applicationId: String
    var compileSdk: Int

    var versionTag: String

    val versionCodes: MutableSet<Int>

    val assetPacks: MutableSet<String>

    val signingConfig: SigningConfig
    fun signingConfig(action: SigningConfig.() -> Unit)

    val texture: BundleTexture
    fun texture(action: BundleTexture.() -> Unit)

    val deviceTier: BundleDeviceTier
    fun deviceTier(action: BundleDeviceTier.() -> Unit)
}
