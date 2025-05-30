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

package com.android.build.gradle.internal.variant

import com.android.build.gradle.internal.BuildTypeData
import com.android.build.gradle.internal.DefaultConfigData
import com.android.build.gradle.internal.ProductFlavorData
import com.android.build.gradle.internal.dependency.SourceSetManager

/**
 * Model containing the inputs for the variants to be created.
 */
interface VariantInputModel<
        DefaultConfigT : com.android.build.api.dsl.DefaultConfig,
        BuildTypeT : com.android.build.api.dsl.BuildType,
        ProductFlavorT : com.android.build.api.dsl.ProductFlavor,
        SigningConfigT : com.android.build.api.dsl.ApkSigningConfig> {

    val defaultConfigData: DefaultConfigData<DefaultConfigT>

    val buildTypes: Map<String, BuildTypeData<BuildTypeT>>

    val productFlavors: Map<String, ProductFlavorData<ProductFlavorT>>

    val signingConfigs: Map<String, SigningConfigT>

    val sourceSetManager: SourceSetManager
}
