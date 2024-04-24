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
interface BundleCodeTransparency {

    /**
     * Specifies the signing configuration for the code transparency feature of `bundletool`.
     *
     * When the [SigningConfig] has all necessary values set, it will be used for signing
     * non-debuggable bundles using code transparency.
     *
     */
    val signing: SigningConfig

    /**
     * Specifies the signing configuration for the code transparency feature of `bundletool`.
     *
     * When the [SigningConfig] has all necessary values set, it will be used for signing
     * non-debuggable bundles using code transparency.
     *
     */
    fun signing(action: SigningConfig.() -> Unit)
}
