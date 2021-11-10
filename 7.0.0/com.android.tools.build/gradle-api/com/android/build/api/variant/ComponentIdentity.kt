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

/**
 * Variant Configuration represents the identify of a variant
 *
 * This is computed from the list of build types and flavors.
 */
interface ComponentIdentity {

    /**
     * Component's name.
     */
    val name: String

    /**
     * Build type name, might be replaced with access to locked DSL object once ready.
     */
    val buildType: String?

    /**
     * List of flavor names, might be replaced with access to locked DSL objects once ready.
     *
     * The order is properly sorted based on the associated dimension order.
     */
    val productFlavors: List<Pair<String, String>>

    /**
     * The multi-flavor name of the variant.
     *
     * This does not include the build type. If no flavors are present, this will return null
     *
     * The full name of the variant is queried via [name].
     */
    val flavorName: String?
}
