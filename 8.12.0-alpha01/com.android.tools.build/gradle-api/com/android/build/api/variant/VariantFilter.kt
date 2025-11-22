/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.builder.model.BuildType
import com.android.builder.model.ProductFlavor

/**
 * Interface for variant control, allowing to query a variant for some base
 * data and allowing to disable some variants.
 */
@Deprecated("Use AndroidComponentsExtension.beforeVariants API to disable specific variants")
interface VariantFilter {

    /**
     * Whether or not to ignore this particular variant. Default is false.
     */
    var ignore: Boolean

    /**
     * Returns the ProductFlavor that represents the default config.
     */
    val defaultConfig: ProductFlavor

    /**
     * Returns the Build Type.
     */
    val buildType: BuildType

    /**
     * Returns the list of flavors, or an empty list.
     */
    val flavors: List<ProductFlavor>

    /**
     * Returns the unique variant name.
     */
    val name: String
}
