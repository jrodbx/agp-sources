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

import com.android.build.api.dsl.ApplicationBuildFeatures
import com.android.build.api.dsl.Bundle
import com.android.build.api.dsl.DependenciesInfo
import com.android.build.api.variant.ApplicationVariantBuilder
import com.android.build.api.variant.ApplicationVariant
import com.android.build.gradle.internal.plugins.DslContainerProvider
import com.android.build.gradle.internal.services.DslServices
import org.gradle.api.Action

/** Internal implementation of the 'new' DSL interface */
class ApplicationExtensionImpl(
    dslServices: DslServices,
    dslContainers: DslContainerProvider<DefaultConfig, BuildType, ProductFlavor, SigningConfig>
) :
    TestedExtensionImpl<
            ApplicationBuildFeatures,
            BuildType,
            DefaultConfig,
            ProductFlavor,
            ApplicationVariantBuilder,
            ApplicationVariant>(
        dslServices,
        dslContainers
    ),
    InternalApplicationExtension {

    override val buildFeatures: ApplicationBuildFeatures =
        dslServices.newInstance(ApplicationBuildFeaturesImpl::class.java)

    override var dynamicFeatures: MutableSet<String> = mutableSetOf()
    override var assetPacks: MutableSet<String> = mutableSetOf()

    override val dependenciesInfo: DependenciesInfo =
        dslServices.newInstance(DependenciesInfoImpl::class.java)

    override fun dependenciesInfo(action: DependenciesInfo.() -> Unit) {
        action.invoke(dependenciesInfo)
    }

    fun dependenciesInfo(action: Action<DependenciesInfo>) {
        action.execute(dependenciesInfo)
    }

    override val bundle = dslServices.newInstance(BundleOptions::class.java, dslServices)

    override fun bundle(action: Bundle.() -> Unit) {
        action.invoke(bundle)
    }
}
