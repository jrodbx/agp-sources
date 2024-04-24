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

import com.android.build.api.dsl.LibraryAndroidResources
import com.android.build.api.dsl.LibraryBuildFeatures
import com.android.build.api.dsl.LibraryBuildType
import com.android.build.api.dsl.LibraryDefaultConfig
import com.android.build.api.dsl.LibraryProductFlavor
import com.android.build.api.dsl.Prefab
import com.android.build.gradle.internal.plugins.DslContainerProvider
import com.android.build.gradle.internal.services.DslServices
import org.gradle.api.NamedDomainObjectContainer
import javax.inject.Inject

/** Internal implementation of the 'new' DSL interface */
abstract class LibraryExtensionImpl @Inject constructor(
    dslServices: DslServices,
    dslContainers: DslContainerProvider<
            LibraryDefaultConfig,
            LibraryBuildType,
            LibraryProductFlavor,
            SigningConfig>
) :
    TestedExtensionImpl<
            LibraryBuildFeatures,
            LibraryBuildType,
            LibraryDefaultConfig,
            LibraryProductFlavor,
            LibraryAndroidResources>(
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

    override val prefab: NamedDomainObjectContainer<Prefab> =
        dslServices.domainObjectContainer(
            Prefab::class.java,
            PrefabModuleFactory(dslServices)
        )

    override val androidResources: LibraryAndroidResources
     = dslServices.newDecoratedInstance(LibraryAndroidResourcesImpl::class.java, dslServices)
}
