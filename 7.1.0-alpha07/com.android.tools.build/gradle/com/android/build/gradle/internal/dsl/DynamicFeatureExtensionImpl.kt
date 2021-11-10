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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.DynamicFeatureBuildFeatures
import com.android.build.api.dsl.DynamicFeatureBuildType
import com.android.build.api.dsl.DynamicFeatureDefaultConfig
import com.android.build.api.dsl.DynamicFeatureProductFlavor
import com.android.build.api.variant.DynamicFeatureVariantBuilder
import com.android.build.api.variant.DynamicFeatureVariant
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.plugins.DslContainerProvider
import javax.inject.Inject

abstract class DynamicFeatureExtensionImpl @Inject constructor(
    dslServices: DslServices,
    dslContainers: DslContainerProvider<
            DynamicFeatureDefaultConfig,
            DynamicFeatureBuildType,
            DynamicFeatureProductFlavor,
            SigningConfig>
)  :
    TestedExtensionImpl<
            DynamicFeatureBuildFeatures,
            DynamicFeatureBuildType,
            DynamicFeatureDefaultConfig,
            DynamicFeatureProductFlavor>(
        dslServices,
        dslContainers
    ),
    InternalDynamicFeatureExtension {

    override val buildFeatures: DynamicFeatureBuildFeatures =
        dslServices.newInstance(DynamicFeatureBuildFeaturesImpl::class.java)
}
