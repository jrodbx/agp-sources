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

import com.android.build.api.dsl.ApkSigningConfig
import com.android.build.api.dsl.ComposeOptions
import com.android.build.api.dsl.LibraryAndroidResources
import com.android.build.api.dsl.LibraryBuildFeatures
import com.android.build.api.dsl.LibraryBuildType
import com.android.build.api.dsl.LibraryDefaultConfig
import com.android.build.api.dsl.LibraryInstallation
import com.android.build.api.dsl.LibraryProductFlavor
import com.android.build.api.dsl.Packaging
import com.android.build.api.dsl.Prefab
import com.android.build.api.dsl.TestCoverage
import com.android.build.api.dsl.ViewBinding
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.coverage.JacocoOptions
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

    override val aaptOptions: AaptOptions get() = androidResources as AaptOptions

    override fun aaptOptions(action: com.android.build.api.dsl.AaptOptions.() -> Unit) {
        action.invoke(aaptOptions)
    }

    override fun aaptOptions(action: Action<AaptOptions>) {
        action.execute(aaptOptions)
    }

    override val adbOptions: AdbOptions get() = installation as AdbOptions

    override fun adbOptions(action: com.android.build.api.dsl.AdbOptions.() -> Unit) {
        action.invoke(adbOptions)
    }

    override fun adbOptions(action: Action<AdbOptions>) {
        action.execute(adbOptions)
    }

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

    override val composeOptions: ComposeOptionsImpl =
        dslServices.newInstance(ComposeOptionsImpl::class.java, dslServices)

    override fun composeOptions(action: ComposeOptions.() -> Unit) {
        action.invoke(composeOptions)
    }

    override fun composeOptions(action: Action<ComposeOptions>) {
        action.execute(composeOptions)
    }

    override val dataBinding: DataBindingOptions =
        dslServices.newDecoratedInstance(
            DataBindingOptions::class.java,
            Supplier { buildFeatures },
            dslServices
        )

    override fun dataBinding(action: com.android.build.api.dsl.DataBinding.() -> Unit) {
        action.invoke(dataBinding)
    }

    override fun dataBinding(action: Action<DataBindingOptions>) {
        action.execute(dataBinding)
    }

    override val viewBinding: ViewBindingOptionsImpl
        get() = dslServices.newDecoratedInstance(
            ViewBindingOptionsImpl::class.java,
            Supplier { buildFeatures },
            dslServices
        )

    override fun viewBinding(action: Action<ViewBindingOptionsImpl>) {
        action.execute(viewBinding)
    }

    override fun viewBinding(action: ViewBinding.() -> Unit) {
        action.invoke(viewBinding)
    }

    override val jacoco: JacocoOptions
        get() = testCoverage as JacocoOptions

    override fun jacoco(action: com.android.build.api.dsl.JacocoOptions.() -> Unit) {
        action.invoke(jacoco)
    }

    override fun jacoco(action: Action<JacocoOptions>) {
        action.execute(jacoco)
    }

    override val testCoverage: TestCoverage  = dslServices.newInstance(JacocoOptions::class.java)

    override fun testCoverage(action: TestCoverage.() -> Unit) {
        action.invoke(testCoverage)
    }

    override fun testCoverage(action: Action<TestCoverage>) {
        action.execute(testCoverage)
    }

    override val testOptions: TestOptions =
        dslServices.newInstance(TestOptions::class.java, dslServices)

    override fun testOptions(action: com.android.build.api.dsl.TestOptions.() -> Unit) {
        action.invoke(testOptions)
    }

    override fun testOptions(action: Action<TestOptions>) {
        action.execute(testOptions)
    }

    override val sourceSets: NamedDomainObjectContainer<AndroidSourceSet>
        get() = sourceSetManager.sourceSetsContainer

    override fun sourceSets(action: NamedDomainObjectContainer<out com.android.build.api.dsl.AndroidSourceSet>.() -> Unit) {
        sourceSetManager.executeAction(action)
    }

    override fun sourceSets(action: Action<NamedDomainObjectContainer<AndroidSourceSet>>) {
        action.execute(sourceSets)
    }

    final override val lintOptions: LintOptions by lazy(LazyThreadSafetyMode.PUBLICATION) {
        dslServices.newInstance(LintOptions::class.java, dslServices, lint)
    }

    override fun lintOptions(action: com.android.build.api.dsl.LintOptions.() -> Unit) {
        action.invoke(lintOptions)
    }

    override fun lintOptions(action: Action<LintOptions>) {
        action.execute(lintOptions)
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

    override fun signingConfigs(action: Action<NamedDomainObjectContainer<SigningConfig>>) {
        action.execute(signingConfigs)
    }

    override fun signingConfigs(action: NamedDomainObjectContainer<out ApkSigningConfig>.() -> Unit) {
        action.invoke(signingConfigs)
    }

    override val installation: LibraryInstallation
        = dslServices.newDecoratedInstance(LibraryInstallationImpl::class.java, dslServices)

    override fun installation(action: LibraryInstallation.() -> Unit) {
        action.invoke(installation)
    }

    override fun installation(action: Action<LibraryInstallation>) {
        action.execute(installation)
    }

    override val packagingOptions: PackagingOptions
        get() = packaging as PackagingOptions

    override fun packagingOptions(action: Packaging.() -> Unit) {
        action.invoke(packaging)
    }

    override fun packagingOptions(action: Action<PackagingOptions>) {
        action.execute(packaging as PackagingOptions)
    }
}
