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

package com.android.build.gradle.internal.testing.utp

import com.android.builder.testing.api.DeviceConfigProvider

/**
 * Implementation of [DeviceConfigProvider] using a [UtpManagedDevice] defined from the
 * Virtual managed device dsl.
 *
 * The device config provider is meant to have the equivalent of an "empty" configuration
 * with the noticeable exception of api level and the device abi. This allows the config
 * to match with the default Apk for the device.
 */
class ManagedDeviceConfigProvider(val device: UtpManagedDevice) : DeviceConfigProvider {

    override fun getConfigFor(abi: String?): String = requireNotNull(abi)

    /**
     * Returns -1, stating that the config has no density value.
     */
    override fun getDensity(): Int = -1

    /**
     * Returns null, stating the config has no language value.
     */
    override fun getLanguage(): String? = null

    /**
     * Returns null, stating the config has no language splits value.
     */
    override fun getLanguageSplits(): MutableSet<String>? = null

    /**
     * Returns null, stating the config has no region value.
     */
    override fun getRegion(): String? = null

    override fun getAbis(): MutableList<String> = mutableListOf(device.abi)

    override fun getApiCodeName(): String? = null

    override fun getApiLevel(): Int = device.api
}
