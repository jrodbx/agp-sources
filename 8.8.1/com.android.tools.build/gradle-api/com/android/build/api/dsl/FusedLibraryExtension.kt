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
@Incubating
interface FusedLibraryExtension {

    @get: Incubating
    @set: Incubating
    var namespace: String?

    /**
     * For basic validation that all included libraries in the fused library are at least the minSdk.
     * Eventually (b/229956178) this value should be able to be automatically determined by the plugin.
     */
    @get: Incubating
    @set: Incubating
    var minSdk: Int?

    @get: Incubating
    /** Options for configuring AAR metadata. */
    val aarMetadata: AarMetadata

    @Incubating
    /** Options for configuring AAR metadata. */
    fun aarMetadata(action: AarMetadata.() -> Unit)
}
