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

import com.android.build.api.dsl.ApplicationAndroidResources
import com.android.build.api.dsl.ApplicationBuildFeatures
import com.android.build.api.dsl.ApplicationBuildType
import com.android.build.api.dsl.ApplicationDefaultConfig
import com.android.build.api.dsl.ApplicationInstallation
import com.android.build.api.dsl.ApplicationProductFlavor
import com.android.build.gradle.internal.dsl.decorator.ApplicationInstallationImpl
import com.android.build.gradle.internal.dsl.DefaultConfig as InternalDefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor as InternalProductFlavor

import com.android.build.gradle.internal.plugins.DslContainerProvider
import com.android.build.gradle.internal.services.DslServices
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import javax.inject.Inject

/** Internal implementation of the 'new' DSL interface */
abstract class ApplicationExtensionImpl @Inject constructor(
    dslServices: DslServices,
    dslContainers: DslContainerProvider<
            ApplicationDefaultConfig,
            ApplicationBuildType,
            ApplicationProductFlavor,
            SigningConfig>
) :
    TestedExtensionImpl<
            ApplicationBuildType,
            ApplicationDefaultConfig,
            ApplicationProductFlavor,
            ApplicationInstallation>(
        dslServices,
        dslContainers
    ),
    InternalApplicationExtension {

    override val buildFeatures: ApplicationBuildFeatures =
        dslServices.newInstance(ApplicationBuildFeaturesImpl::class.java)


    override fun buildFeatures(action: ApplicationBuildFeatures.() -> Unit) {
        action(buildFeatures)
    }

    override fun buildFeatures(action: Action<ApplicationBuildFeatures>) {
        action.execute(buildFeatures)
    }

    override fun buildTypes(action: NamedDomainObjectContainer<ApplicationBuildType>.() -> Unit) {
        action(buildTypes)
    }

    override fun buildTypes(action: Action<in NamedDomainObjectContainer<BuildType>>) {
        action.execute(buildTypes as NamedDomainObjectContainer<BuildType>)
    }

    override fun NamedDomainObjectContainer<ApplicationBuildType>.debug(action: ApplicationBuildType.() -> Unit) {
        getByName("debug", action)
    }

    override fun NamedDomainObjectContainer<ApplicationBuildType>.release(action: ApplicationBuildType.() -> Unit)  {
        getByName("release", action)
    }

    override fun productFlavors(action: Action<NamedDomainObjectContainer<InternalProductFlavor>>) {
        action.execute(productFlavors as NamedDomainObjectContainer<InternalProductFlavor>)
    }

    override fun productFlavors(action: NamedDomainObjectContainer<ApplicationProductFlavor>.() -> Unit) {
        action.invoke(productFlavors)
    }

    override fun defaultConfig(action: Action<InternalDefaultConfig>) {
        action.execute(defaultConfig as InternalDefaultConfig)
    }

    override fun defaultConfig(action: ApplicationDefaultConfig.() -> Unit) {
        action.invoke(defaultConfig)
    }


    override val androidResources: ApplicationAndroidResources =
        dslServices.newDecoratedInstance(ApplicationAndroidResourcesImpl::class.java, dslServices)

    override fun androidResources(action: ApplicationAndroidResources.() -> Unit) {
        action(androidResources)
    }

    override fun androidResources(action: Action<ApplicationAndroidResources>) {
        action.execute(androidResources)
    }

    override val installation: ApplicationInstallation =
        dslServices.newDecoratedInstance(ApplicationInstallationImpl::class.java, dslServices)

    override fun installation(action: ApplicationInstallation.() -> Unit) {
        action(installation)
    }

    override fun installation(action: Action<ApplicationInstallation>) {
        action.execute(installation)
    }
}
