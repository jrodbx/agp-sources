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

import com.android.build.api.dsl.DynamicFeatureAndroidResources
import com.android.build.api.dsl.DynamicFeatureBuildFeatures
import com.android.build.api.dsl.DynamicFeatureBuildType
import com.android.build.api.dsl.DynamicFeatureDefaultConfig
import com.android.build.api.dsl.DynamicFeatureInstallation
import com.android.build.api.dsl.DynamicFeatureProductFlavor
import com.android.build.gradle.internal.plugins.DslContainerProvider
import com.android.build.gradle.internal.services.DslServices
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
            DynamicFeatureProductFlavor,
            DynamicFeatureAndroidResources,
            DynamicFeatureInstallation>(
        dslServices,
        dslContainers
    ),
    InternalDynamicFeatureExtension {

    override val buildFeatures: DynamicFeatureBuildFeatures =
        dslServices.newInstance(DynamicFeatureBuildFeaturesImpl::class.java)
    override val androidResources: DynamicFeatureAndroidResources
        = dslServices.newDecoratedInstance(DynamicFeatureAndroidResourcesImpl::class.java, dslServices)
    override val installation: DynamicFeatureInstallation
        = dslServices.newDecoratedInstance(DynamicFeatureInstallationImpl::class.java, dslServices)
}
