/*
 * Copyright (C) 2022 The Android Open Source Project
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

/**
 * Extension properties for Fused libraries.
 *
 * @suppress Do not use from production code. Only exposed for prototype.
 **/
interface FusedLibraryExtension {

    // Options required to be explicitly specified:

    /** Namespace of the Fused Library. */
    var namespace: String?

    /**
     * Configures minSdk, see [MinSdkSpec] for available options.
     */
    fun minSdk(action: MinSdkSpec.() -> Unit)

    // Additional options:

    @get:Incubating
    /** Used to set module-specific experimental property values. */
    val experimentalProperties: MutableMap<String, Any>

    /** Map with Manifest placeholder key and placeholder resolved value. See
     * [Inject build variables into the manifest](https://developer.android.com/build/manage-manifests#inject_build_variables_into_the_manifest)
     */
    val manifestPlaceholders: MutableMap<String, String>

    /** Options for configuring AAR metadata. */
    val aarMetadata: AarMetadata

    /** Options for configuring AAR metadata. */
    fun aarMetadata(action: AarMetadata.() -> Unit)
}
