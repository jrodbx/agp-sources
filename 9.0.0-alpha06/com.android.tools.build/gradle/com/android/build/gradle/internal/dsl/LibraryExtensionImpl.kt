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
import com.android.build.api.dsl.LibraryInstallation
import com.android.build.api.dsl.LibraryProductFlavor
import com.android.build.api.dsl.Prefab
import com.android.build.gradle.internal.dsl.DefaultConfig as InternalDefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor as InternalProductFlavor
import com.android.build.gradle.internal.plugins.DslContainerProvider
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.options.BooleanOption
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import javax.inject.Inject
import java.util.function.Supplier

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
            LibraryBuildType,
            LibraryDefaultConfig,
            LibraryProductFlavor,
            LibraryInstallation>(
        dslServices,
        dslContainers
    ),
    InternalLibraryExtension {

    override val buildFeatures: LibraryBuildFeatures =
        dslServices.newDecoratedInstance(LibraryBuildFeaturesImpl::class.java, Supplier { androidResources }, dslServices)

    override fun buildFeatures(action: LibraryBuildFeatures.() -> Unit) {
        action(buildFeatures)
    }

    override fun buildFeatures(action: Action<LibraryBuildFeatures>) {
        action.execute(buildFeatures)
    }

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

    override val androidResources: LibraryAndroidResources = dslServices.newDecoratedInstance(
        LibraryAndroidResourcesImpl::class.java,
        dslServices,
        dslServices.projectOptions[BooleanOption.BUILD_FEATURE_ANDROID_RESOURCES]
    )

    override fun androidResources(action: LibraryAndroidResources.() -> Unit) {
        action.invoke(androidResources)
    }

    override fun androidResources(action: Action<LibraryAndroidResources>) {
        action.execute(androidResources)
    }

    override fun buildTypes(action: NamedDomainObjectContainer<LibraryBuildType>.() -> Unit) {
        action(buildTypes)
    }

    override fun buildTypes(action: Action<in NamedDomainObjectContainer<BuildType>>) {
        action.execute(buildTypes as NamedDomainObjectContainer<BuildType>)
    }

    override fun NamedDomainObjectContainer<LibraryBuildType>.debug(action: LibraryBuildType.() -> Unit) {
        getByName("debug", action)
    }

    override fun NamedDomainObjectContainer<LibraryBuildType>.release(action: LibraryBuildType.() -> Unit)  {
        getByName("release", action)
    }

    override fun productFlavors(action: Action<NamedDomainObjectContainer<InternalProductFlavor>>) {
        action.execute(productFlavors as NamedDomainObjectContainer<InternalProductFlavor>)
    }

    override fun productFlavors(action: NamedDomainObjectContainer<LibraryProductFlavor>.() -> Unit) {
        action.invoke(productFlavors)
    }

    override fun defaultConfig(action: Action<InternalDefaultConfig>) {
        action.execute(defaultConfig as InternalDefaultConfig)
    }

    override fun defaultConfig(action: LibraryDefaultConfig.() -> Unit) {
        action.invoke(defaultConfig)
    }

    override val installation: LibraryInstallation
        = dslServices.newDecoratedInstance(LibraryInstallationImpl::class.java, dslServices)

    override fun installation(action: LibraryInstallation.() -> Unit) {
        action.invoke(installation)
    }

    override fun installation(action: Action<LibraryInstallation>) {
        action.execute(installation)
    }
}
