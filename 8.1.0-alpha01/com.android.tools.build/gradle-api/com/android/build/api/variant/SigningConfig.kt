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

package com.android.build.api.variant

import org.gradle.api.Incubating
import org.gradle.api.provider.Property

/**
 * Defines a variant's signing config.
 */
interface SigningConfig {

    /**
     * Sets the [com.android.build.api.dsl.SigningConfig] with information on how to retrieve
     * the signing configuration.
     */
    @Incubating
    fun setConfig(signingConfig: com.android.build.api.dsl.SigningConfig)

    /**
     * Enable signing using JAR Signature Scheme (aka v1 signing).
     *
     * See [Signing Your Applications](http://developer.android.com/tools/publishing/app-signing.html)
     *
     * This property will override any value set using the corresponding DSL.
     */
    val enableV1Signing: Property<Boolean>

    /**
     * Enable signing using APK Signature Scheme v2 (aka v2 signing).
     *
     * See [Signing Your Applications](http://developer.android.com/tools/publishing/app-signing.html)
     *
     * This property will override any value set using the corresponding DSL.
     */
    val enableV2Signing: Property<Boolean>

    /**
     * Enable signing using APK Signature Scheme v3 (aka v3 signing).
     *
     * See [APK Signature Scheme v3](https://source.android.com/security/apksigning/v3)
     *
     * This property will override any value set using the corresponding DSL.
     */
    val enableV3Signing: Property<Boolean>

    /**
     * Enable signing using APK Signature Scheme v4 (aka v4 signing).
     *
     * This property will override any value set using the corresponding DSL.
     */
    val enableV4Signing: Property<Boolean>
}
