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
package com.android.builder.model.v2.ide

import com.android.builder.model.v2.AndroidModel

/**
 * A build Variant.
 *
 * @since 4.2
 */
interface Variant: AndroidModel {
    /**
     * The name of the variant.
     */
    val name: String

    /**
     * The display name for the variant.
     */
    val displayName: String

    /**
     * The main artifact for this variant.
     */
    val mainArtifact: AndroidArtifact

    /**
     * The AndroidTest artifact for this variant, if applicable.
     */
    val androidTestArtifact: AndroidArtifact?

    /**
     * The Unit Test artifact for this variant, if applicable.
     */
    val unitTestArtifact: JavaArtifact?

    /**
     * The TestFixtures artifact for this variant, if applicable.
     */
    val testFixturesArtifact: AndroidArtifact?

    /**
     * The build type name.
     *
     * If null, no build type is associated with the variant (this generally means that no build
     * types exist, which can only happen for libraries)
     */
    val buildType: String?

    /**
     * The flavors for this variants. This can be empty if no flavors are configured.
     */
    val productFlavors: List<String>

    /**
     * For standalone test plugins: information about the tested project.
     *
     * For other plugin types, this is null
     */
    val testedTargetVariant: TestedTargetVariant?

    /**
     * Whether the variant is instant app compatible.
     *
     * Only application modules and dynamic feature modules will set this property.
     */
    val isInstantAppCompatible: Boolean

    /**
     * The list of desugared methods, including =backported methods handled by D8 and methods
     * provided by core library desugaring.
     */
    val desugaredMethods: List<String>
}
