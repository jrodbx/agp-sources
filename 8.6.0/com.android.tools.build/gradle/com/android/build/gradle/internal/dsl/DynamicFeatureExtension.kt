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

import com.android.build.gradle.AppExtension
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfig
import com.android.builder.core.LibraryRequest
import com.android.repository.Revision
import org.gradle.api.NamedDomainObjectContainer

abstract class DynamicFeatureExtension(
    dslServices: DslServices,
    bootClasspathConfig: BootClasspathConfig,
    buildOutputs: NamedDomainObjectContainer<BaseVariantOutput>,
    sourceSetManager: SourceSetManager,
    extraModelInfo: ExtraModelInfo,
    private val publicExtensionImpl: DynamicFeatureExtensionImpl
) : AppExtension(
    dslServices,
    bootClasspathConfig,
    buildOutputs, sourceSetManager, extraModelInfo, false
), InternalDynamicFeatureExtension by publicExtensionImpl {

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

    override val flavorDimensionList: MutableList<String>
        get() = flavorDimensions

    override val buildToolsRevision: Revision
        get() = Revision.parseRevision(buildToolsVersion, Revision.Precision.MICRO)

    override val libraryRequests: MutableCollection<LibraryRequest>
        get() = publicExtensionImpl.libraryRequests
}
