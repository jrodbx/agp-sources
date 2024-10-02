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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.Bundle
import com.android.build.api.dsl.BundleCodeTransparency
import org.gradle.api.Action
import org.gradle.api.file.DirectoryProperty

/** Features that apply to distribution by the bundle  */
abstract class BundleOptions : Bundle {

    abstract override val abi: BundleOptionsAbi
    abstract override val density: BundleOptionsDensity
    abstract override val language: BundleOptionsLanguage
    abstract override val texture: BundleOptionsTexture
    abstract override val deviceTier: BundleOptionsDeviceTier
    abstract override val codeTransparency: BundleCodeTransparency
    abstract override val storeArchive: BundleOptionsStoreArchive
    abstract override val integrityConfigDir: DirectoryProperty
    abstract override val countrySet: BundleOptionsCountrySet

    abstract fun abi(action: Action<BundleOptionsAbi>)
    abstract fun density(action: Action<BundleOptionsDensity>)
    abstract fun language(action: Action<BundleOptionsLanguage>)
    abstract fun texture(action: Action<BundleOptionsTexture>)
    abstract fun deviceTier(action: Action<BundleOptionsDeviceTier>)
    abstract fun storeArchive(action: Action<BundleOptionsStoreArchive>)
    abstract fun countrySet(action: Action<BundleOptionsCountrySet>)
}
