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
import com.android.build.gradle.internal.dsl.DefaultConfig as InternalDefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor as InternalProductFlavor
import com.android.build.gradle.internal.plugins.DslContainerProvider
import com.android.build.gradle.internal.services.DslServices
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
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
            DynamicFeatureBuildType,
            DynamicFeatureDefaultConfig,
            DynamicFeatureProductFlavor,
            DynamicFeatureInstallation>(
        dslServices,
        dslContainers
    ),
    InternalDynamicFeatureExtension {

    override val buildFeatures: DynamicFeatureBuildFeatures =
        dslServices.newInstance(DynamicFeatureBuildFeaturesImpl::class.java)

    override fun buildFeatures(action: DynamicFeatureBuildFeatures.() -> Unit) {
        action(buildFeatures)
    }

    override fun buildFeatures(action: Action<DynamicFeatureBuildFeatures>) {
        action.execute(buildFeatures)
    }

    override fun buildTypes(action: NamedDomainObjectContainer<DynamicFeatureBuildType>.() -> Unit) {
        action(buildTypes)
    }

    override fun buildTypes(action: Action<in NamedDomainObjectContainer<BuildType>>) {
        action.execute(buildTypes as NamedDomainObjectContainer<BuildType>)
    }

    override fun NamedDomainObjectContainer<DynamicFeatureBuildType>.debug(action: DynamicFeatureBuildType.() -> Unit) {
        getByName("debug", action)
    }

    override fun NamedDomainObjectContainer<DynamicFeatureBuildType>.release(action: DynamicFeatureBuildType.() -> Unit)  {
        getByName("release", action)
    }

    override fun productFlavors(action: Action<NamedDomainObjectContainer<InternalProductFlavor>>) {
        action.execute(productFlavors as NamedDomainObjectContainer<InternalProductFlavor>)
    }

    override fun productFlavors(action: NamedDomainObjectContainer<DynamicFeatureProductFlavor>.() -> Unit) {
        action.invoke(productFlavors)
    }

    override fun defaultConfig(action: Action<InternalDefaultConfig>) {
        action.execute(defaultConfig as InternalDefaultConfig)
    }

    override fun defaultConfig(action: DynamicFeatureDefaultConfig.() -> Unit) {
        action.invoke(defaultConfig)
    }

    override val androidResources: DynamicFeatureAndroidResources
        = dslServices.newDecoratedInstance(DynamicFeatureAndroidResourcesImpl::class.java, dslServices)
    override fun androidResources(action: DynamicFeatureAndroidResources.() -> Unit) {
        action(androidResources)
    }
    override fun androidResources(action: Action<DynamicFeatureAndroidResources>) {
        action.execute(androidResources)
    }
    override val installation: DynamicFeatureInstallation
        = dslServices.newDecoratedInstance(DynamicFeatureInstallationImpl::class.java, dslServices)

    override fun installation(action: DynamicFeatureInstallation.() -> Unit) {
        action(installation)
    }
    override fun installation(action: Action<DynamicFeatureInstallation>) {
        action.execute(installation)
    }
}
