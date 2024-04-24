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

import org.gradle.api.Incubating
import org.gradle.api.provider.Property

/**
 * Model for application components that only contains build-time properties.
 *
 * See [Component] and [Variant] for more information.
 */
interface ApplicationVariant : GeneratesApk,
    Variant,
    HasAndroidTest,
    HasUnitTest,
    HasTestFixtures,
    CanMinifyCode,
    CanMinifyAndroidResources {

    /**
     * Variant's application ID as present in the final manifest file of the APK.
     *
     * Setting this value will override anything set via the DSL with
     * [com.android.build.api.dsl.ApplicationBaseFlavor.applicationId], and
     * [com.android.build.api.dsl.ApplicationVariantDimension.applicationIdSuffix]
     */
    override val applicationId: Property<String>

    /**
     * Returns the final list of variant outputs.
     * @return read only list of [VariantOutput] for this variant.
     */
    val outputs: List<VariantOutput>

    /** Specify whether to include SDK dependency information in APKs and Bundles. */
    val dependenciesInfo: DependenciesInfo

    /**
     * Variant's signingConfig, initialized by the corresponding DSL element.
     * @return Variant's config or null if the variant is not configured for signing.
     */
    val signingConfig: SigningConfig

    /**
     * Variant's information related to the bundle creation configuration.
     * @return Variant's [BundleConfig].
     */
    @get:Incubating
    val bundleConfig: BundleConfig
}
