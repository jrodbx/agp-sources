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

import com.android.build.api.dsl.TestAndroidResources
import com.android.build.api.dsl.TestBuildFeatures
import com.android.build.api.dsl.TestBuildType
import com.android.build.api.dsl.TestDefaultConfig
import com.android.build.api.dsl.TestInstallation
import com.android.build.api.dsl.TestProductFlavor
import com.android.build.gradle.internal.dsl.DefaultConfig as InternalDefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor as InternalProductFlavor
import com.android.build.gradle.internal.plugins.DslContainerProvider
import com.android.build.gradle.internal.services.DslServices
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import javax.inject.Inject

/** Internal implementation of the 'new' DSL interface */
abstract class TestExtensionImpl @Inject constructor(
    dslServices: DslServices,
    dslContainers: DslContainerProvider<
            TestDefaultConfig,
            TestBuildType,
            TestProductFlavor, SigningConfig>
) :
    CommonExtensionImpl<
            TestBuildType,
            TestDefaultConfig,
            TestProductFlavor>(
        dslServices,
        dslContainers
    ),
    InternalTestExtension {

    override val buildFeatures: TestBuildFeatures =
        dslServices.newInstance(TestBuildFeaturesImpl::class.java)

    override fun buildFeatures(action: TestBuildFeatures.() -> Unit) {
        action(buildFeatures)
    }

    override fun buildFeatures(action: Action<TestBuildFeatures>) {
        action.execute(buildFeatures)
    }

    override fun buildTypes(action: NamedDomainObjectContainer<TestBuildType>.() -> Unit) {
        action(buildTypes)
    }

    override fun buildTypes(action: Action<in NamedDomainObjectContainer<BuildType>>) {
        action.execute(buildTypes as NamedDomainObjectContainer<BuildType>)
    }

    override fun NamedDomainObjectContainer<TestBuildType>.debug(action: TestBuildType.() -> Unit) {
        getByName("debug", action)
    }

    override fun NamedDomainObjectContainer<TestBuildType>.release(action: TestBuildType.() -> Unit)  {
        getByName("release", action)
    }

    override fun productFlavors(action: Action<NamedDomainObjectContainer<InternalProductFlavor>>) {
        action.execute(productFlavors as NamedDomainObjectContainer<InternalProductFlavor>)
    }

    override fun productFlavors(action: NamedDomainObjectContainer<TestProductFlavor>.() -> Unit) {
        action.invoke(productFlavors)
    }

    override fun defaultConfig(action: Action<InternalDefaultConfig>) {
        action.execute(defaultConfig as InternalDefaultConfig)
    }

    override fun defaultConfig(action: TestDefaultConfig.() -> Unit) {
        action.invoke(defaultConfig)
    }

    override var targetProjectPath: String? = null

    override val androidResources: TestAndroidResources
        = dslServices.newDecoratedInstance(TestAndroidResourcesImpl::class.java, dslServices)

    override fun androidResources(action: TestAndroidResources.() -> Unit) {
        action(androidResources)
    }

    override fun androidResources(action: Action<TestAndroidResources>) {
        action.execute(androidResources)
    }

    override val installation: TestInstallation
        = dslServices.newDecoratedInstance(TestInstallationImpl::class.java, dslServices)

    override fun installation(action: TestInstallation.() -> Unit) {
        action(installation)
    }

    override fun installation(action: Action<TestInstallation>) {
        action.execute(installation)
    }
}
