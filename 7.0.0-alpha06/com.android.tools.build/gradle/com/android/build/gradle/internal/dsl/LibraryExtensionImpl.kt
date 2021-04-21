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

import com.android.build.api.dsl.LibraryBuildFeatures
import com.android.build.api.dsl.PrefabPackagingOptions
import com.android.build.api.variant.LibraryVariant
import com.android.build.api.variant.LibraryVariantBuilder
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.plugins.DslContainerProvider
import org.gradle.api.NamedDomainObjectContainer

/** Internal implementation of the 'new' DSL interface */
class LibraryExtensionImpl(
    dslServices: DslServices,
    dslContainers: DslContainerProvider<DefaultConfig, BuildType, ProductFlavor, SigningConfig>
) :
    TestedExtensionImpl<
            LibraryBuildFeatures,
            BuildType,
            DefaultConfig,
            ProductFlavor,
            LibraryVariantBuilder,
            LibraryVariant>(
        dslServices,
        dslContainers
    ),
    InternalLibraryExtension {

    override val buildFeatures: LibraryBuildFeatures =
        dslServices.newInstance(LibraryBuildFeaturesImpl::class.java)

    @get:Suppress("WrongTerminology")
    @set:Suppress("WrongTerminology")
    @Deprecated("Use aidlPackagedList instead", ReplaceWith("aidlPackagedList"))
    var aidlPackageWhiteList: MutableCollection<String>
        get() = aidlPackagedList
        set(value) {
            aidlPackagedList = value
        }

    override var aidlPackagedList: MutableCollection<String> = ArrayList<String>()
        set(value) {
            field.addAll(value)
        }

    override val prefab: NamedDomainObjectContainer<PrefabPackagingOptions> =
        dslServices.domainObjectContainer(
            PrefabPackagingOptions::class.java,
            PrefabModuleFactory(dslServices)
        )
}
