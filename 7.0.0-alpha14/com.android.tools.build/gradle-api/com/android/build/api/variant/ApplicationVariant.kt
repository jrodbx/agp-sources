/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.build.api.component.AndroidTest
import org.gradle.api.Incubating
import org.gradle.api.provider.Property

/**
 * Properties for the main Variant of an application.
 */
@Incubating
interface ApplicationVariant : Variant {

    /**
     * Variant's application ID as present in the final manifest file of the APK.
     */
    override val applicationId: Property<String>

    /**
     * Variant's [AndroidTest] configuration, or null if android tests are disabled for this
     * variant.
     */
    val androidTest: AndroidTest?

    /**
     * Returns the final list of variant outputs.
     * @return read only list of [VariantOutput] for this variant.
     */
    val outputs: List<VariantOutput>

    /** Specify whether to include SDK dependency information in APKs and Bundles. */
    val dependenciesInfo: DependenciesInfo

    /**
     * Variant's aaptOptions, initialized by the corresponding global DSL element.
     */
    val androidResources: AndroidResources

    /**
     * Variant's signingConfig, initialized by the corresponding DSL element.
     * @return Variant's config or null if the variant is not configured for signing.
     */
    val signingConfig: SigningConfig?

    /**
     * Variant's packagingOptions, initialized by the corresponding global DSL element.
     */
    override val packaging: ApkPackaging

    /**
     * Variant settings related to transforming bytecodes into dex files initialized from
     * the corresponding fields in the DSL.
     */
    val dexing: Dexing

    /**
     * Variant specific settings for the renderscript compiler. This will return null when
     * [com.android.build.api.dsl.BuildFeatures.renderScript] is false.
     */
    val renderscript: Renderscript?
}
