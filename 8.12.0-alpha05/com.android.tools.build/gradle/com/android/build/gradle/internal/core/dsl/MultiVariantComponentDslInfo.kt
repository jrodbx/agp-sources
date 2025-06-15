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

package com.android.build.gradle.internal.core.dsl

import com.android.build.api.dsl.ProductFlavor
import com.android.build.gradle.internal.variant.DimensionCombination

/**
 * Represents the dsl info for a component that supports multiple variants.
 */
interface MultiVariantComponentDslInfo: ComponentDslInfo, DimensionCombination {
    /** The list of product flavors. Items earlier in the list override later items.  */
    val productFlavorList: List<ProductFlavor>

    override val buildType: String?
        get() = componentIdentity.buildType
    override val productFlavors: List<Pair<String, String>>
        get() = componentIdentity.productFlavors
}
