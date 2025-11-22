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

package com.android.build.api.variant

import org.gradle.api.Incubating
import java.util.Objects

/**
 * A class representing device-specific attributes.
 */
@Incubating
class DeviceSpec private constructor(
    /**
     * The name of the device which can be helpful in debugging.
     */
    val name: String?,

    /**
     * The API level of the device.
     */
    val apiLevel: Int,

    /**
     * The code name of the device.
     */
    val codeName: String?,

    /**
     * The ABIs of the device.
     */
    val abis: List<String>,

    /**
     * The screen density of the device.
     */
    val screenDensity: Int,

    /**
     * Whether the device supports Privacy Sandbox.
     */
    val supportsPrivacySandbox: Boolean
) {
    override fun toString() = "DeviceSpec(name=$name, apiLevel=$apiLevel, codeName=$codeName, abis=$abis, supportsPrivacySandbox=$supportsPrivacySandbox, screenDensity=$screenDensity)"
    override fun equals(other: Any?) = other is DeviceSpec
            && name == other.name
            && apiLevel == other.apiLevel
            && codeName == other.codeName
            && supportsPrivacySandbox == other.supportsPrivacySandbox
            && abis.toSet() == other.abis.toSet()
            && screenDensity == other.screenDensity

    override fun hashCode() = Objects.hash(name, apiLevel, codeName, supportsPrivacySandbox, abis.toSet(), screenDensity)

    @Incubating
    class Builder {
        @set:JvmSynthetic
        var name: String? = null

        @set:JvmSynthetic
        var apiLevel: Int = 0

        @set:JvmSynthetic
        var codeName: String? = null

        @set:JvmSynthetic
        var supportsPrivacySandbox: Boolean = false

        @set:JvmSynthetic
        var abis: List<String> = listOf()

        @set:JvmSynthetic
        var screenDensity: Int = 0

        fun setName(name: String?) = apply { this.name = name }
        fun setApiLevel(apiLevel: Int) = apply { this.apiLevel = apiLevel }
        fun setCodeName(codeName: String?) = apply { this.codeName = codeName }
        fun setSupportsPrivacySandbox(supportsPrivacySandbox: Boolean) = apply { this.supportsPrivacySandbox = supportsPrivacySandbox }
        fun setAbis(abis: List<String>) = apply { this.abis = abis }
        fun setScreenDensity(screenDensity: Int) = apply { this.screenDensity = screenDensity }
        fun build() = DeviceSpec(name, apiLevel, codeName, abis, screenDensity, supportsPrivacySandbox)
    }
}
