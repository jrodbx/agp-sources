/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.build.api.dsl.ApplicationDefaultConfig
import com.android.build.api.dsl.ComposeOptions
import com.android.build.gradle.AppExtension
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.internal.ApplicationBuildTypeContainer
import com.android.build.gradle.internal.CompileOptionsInternal
import com.android.build.gradle.internal.DependenciesExtension
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfig
import com.android.builder.core.LibraryRequest
import com.android.repository.Revision
import com.google.wireless.android.sdk.stats.GradleBuildProject
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.declarative.dsl.model.annotations.Configuring

open class BaseAppModuleExtensionInternal(
    dslServices: DslServices,
    bootClasspathConfig: BootClasspathConfig,
    buildOutputs: NamedDomainObjectContainer<BaseVariantOutput>,
    sourceSetManager: SourceSetManager,
    extraModelInfo: ExtraModelInfo,
    private val publicExtensionImpl: ApplicationExtensionImpl,
    stats: GradleBuildProject.Builder?
) : BaseAppModuleExtension (
    dslServices,
    bootClasspathConfig,
    buildOutputs,
    sourceSetManager,
    extraModelInfo,
    publicExtensionImpl,
    stats
) {
    @Configuring
    fun compileOptionsDcl(action: CompileOptionsInternal.() -> Unit) {
        super.compileOptions(action)
    }

    private val appBuildTypes: ApplicationBuildTypeContainer
        get() = ApplicationBuildTypeContainer(publicExtensionImpl.buildTypes)

    @Configuring
    fun appBuildTypes(action: ApplicationBuildTypeContainer.() -> Unit) {
        action.invoke(appBuildTypes)
    }

    val dependenciesDcl: DependenciesExtension by lazy {
        dslServices.newInstance(DependenciesExtension::class.java)
    }

    @Configuring
    fun dependenciesDcl(configure: DependenciesExtension.() -> Unit) {
        configure.invoke(dependenciesDcl)
    }
}

/** The `android` extension for base feature module (application plugin).  */
open class BaseAppModuleExtension(
    dslServices: DslServices,
    bootClasspathConfig: BootClasspathConfig,
    buildOutputs: NamedDomainObjectContainer<BaseVariantOutput>,
    sourceSetManager: SourceSetManager,
    extraModelInfo: ExtraModelInfo,
    private val publicExtensionImpl: ApplicationExtensionImpl,
    stats: GradleBuildProject.Builder?
) : AppExtension(
    dslServices,
    bootClasspathConfig,
    buildOutputs,
    sourceSetManager,
    extraModelInfo,
    true,
    stats
), InternalApplicationExtension by publicExtensionImpl {

    // Overrides to make the parameterized types match, due to BaseExtension being part of
    // the previous public API and not wanting to paramerterize that.
    override val buildTypes: NamedDomainObjectContainer<BuildType>
        get() = publicExtensionImpl.buildTypes as NamedDomainObjectContainer<BuildType>
    override val defaultConfig: DefaultConfig
        get() = publicExtensionImpl.defaultConfig as DefaultConfig
    override val productFlavors: NamedDomainObjectContainer<ProductFlavor>
        get() = publicExtensionImpl.productFlavors as NamedDomainObjectContainer<ProductFlavor>
    override val sourceSets: NamedDomainObjectContainer<AndroidSourceSet>
        get() = publicExtensionImpl.sourceSets

    override val composeOptions: ComposeOptions = publicExtensionImpl.composeOptions

    override val bundle: BundleOptions = publicExtensionImpl.bundle as BundleOptions

    override val flavorDimensionList: MutableList<String>
        get() = flavorDimensions

    override val buildToolsRevision: Revision
        get() = Revision.parseRevision(buildToolsVersion, Revision.Precision.MICRO)

    override val libraryRequests: MutableCollection<LibraryRequest>
        get() = publicExtensionImpl.libraryRequests

    override val androidResources: ApplicationAndroidResources
        get() = publicExtensionImpl.androidResources

    override val buildFeatures: ApplicationBuildFeatures
        get() = publicExtensionImpl.buildFeatures

    @Configuring
    override fun defaultConfig(action: ApplicationDefaultConfig.() -> Unit) {
        action.invoke(defaultConfig)
    }

    @Configuring
    override fun buildFeatures(action: ApplicationBuildFeatures.() -> Unit) {
        action.invoke(buildFeatures)
    }
}
