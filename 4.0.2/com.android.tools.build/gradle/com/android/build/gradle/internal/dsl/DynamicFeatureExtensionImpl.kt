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

import com.android.build.api.component.FilteredComponentActionRegistrar
import com.android.build.api.component.GenericFilteredComponentActionRegistrar
import com.android.build.api.component.impl.FilteredComponentActionRegistrarImpl
import com.android.build.api.component.impl.GenericFilteredComponentActionRegistrarImpl
import com.android.build.api.dsl.DynamicFeatureBuildFeatures
import com.android.build.api.dsl.DynamicFeatureExtension
import com.android.build.api.variant.DynamicFeatureVariant
import com.android.build.api.variant.DynamicFeatureVariantProperties
import com.android.build.gradle.internal.CompileOptions
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.coverage.JacocoOptions
import org.gradle.api.NamedDomainObjectContainer

class DynamicFeatureExtensionImpl(
    dslScope: DslScope,
    buildTypes: NamedDomainObjectContainer<BuildType>,
    defaultConfig: DefaultConfig,
    productFlavors: NamedDomainObjectContainer<ProductFlavor>,
    signingConfigs: NamedDomainObjectContainer<SigningConfig>
)  :
    CommonExtensionImpl<
            DynamicFeatureBuildFeatures,
            BuildType,
            DefaultConfig,
            ProductFlavor,
            SigningConfig,
            DynamicFeatureVariant,
            DynamicFeatureVariantProperties>(
        dslScope,
        buildTypes,
        defaultConfig,
        productFlavors,
        signingConfigs
    ),

    DynamicFeatureExtension<
            BuildType,
            CmakeOptions,
            CompileOptions,
            DefaultConfig,
            ExternalNativeBuild,
            JacocoOptions,
            NdkBuildOptions,
            ProductFlavor,
            SigningConfig,
            TestOptions,
            TestOptions.UnitTestOptions> {

    override val buildFeatures: DynamicFeatureBuildFeatures =
        dslScope.objectFactory.newInstance(DynamicFeatureBuildFeaturesImpl::class.java)

    @Suppress("UNCHECKED_CAST")
    override val onVariants: GenericFilteredComponentActionRegistrar<DynamicFeatureVariant>
        get() = dslScope.objectFactory.newInstance(
            GenericFilteredComponentActionRegistrarImpl::class.java,
            dslScope,
            variantOperations,
            DynamicFeatureVariant::class.java
        ) as GenericFilteredComponentActionRegistrar<DynamicFeatureVariant>
    @Suppress("UNCHECKED_CAST")
    override val onVariantProperties: GenericFilteredComponentActionRegistrar<DynamicFeatureVariantProperties>
        get() = dslScope.objectFactory.newInstance(
            GenericFilteredComponentActionRegistrarImpl::class.java,
            dslScope,
            variantPropertiesOperations,
            DynamicFeatureVariantProperties::class.java
        ) as GenericFilteredComponentActionRegistrar<DynamicFeatureVariantProperties>
}
