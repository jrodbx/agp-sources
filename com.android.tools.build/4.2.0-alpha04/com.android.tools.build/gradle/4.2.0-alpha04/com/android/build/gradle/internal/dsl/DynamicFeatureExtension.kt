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

import com.android.build.api.dsl.DynamicFeatureBuildFeatures
import com.android.build.api.dsl.DynamicFeatureExtension
import com.android.build.api.variant.DynamicFeatureVariant
import com.android.build.api.variant.DynamicFeatureVariantProperties
import com.android.build.gradle.AppExtension
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.api.ViewBindingOptions
import com.android.build.gradle.internal.CompileOptions
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.coverage.JacocoOptions
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.LibraryRequest
import com.android.repository.Revision
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer

internal open class DynamicFeatureExtension(
    dslServices: DslServices,
    globalScope: GlobalScope,
    buildOutputs: NamedDomainObjectContainer<BaseVariantOutput>,
    sourceSetManager: SourceSetManager,
    extraModelInfo: ExtraModelInfo,
    private val publicExtensionImpl: DynamicFeatureExtensionImpl
) : AppExtension(
    dslServices,
     globalScope,
    buildOutputs, sourceSetManager, extraModelInfo, false
), InternalDynamicFeatureExtension by publicExtensionImpl,
    ActionableVariantObjectOperationsExecutor<DynamicFeatureVariant<DynamicFeatureVariantProperties>, DynamicFeatureVariantProperties> by publicExtensionImpl {

    override val viewBinding: ViewBindingOptions =
        dslServices.newInstance(
            ViewBindingOptionsImpl::class.java,
            publicExtensionImpl.buildFeatures,
            dslServices
        )

    // this is needed because the impl class needs this but the interface does not,
    // so CommonExtension does not define it, which means, that even though it's part of
    // DynamicFeatureExtensionImpl, the implementation by delegate does not bring it.
    fun buildFeatures(action: Action<DynamicFeatureBuildFeatures>) {
        publicExtensionImpl.buildFeatures(action)
    }

    override val flavorDimensionList: MutableList<String>
        get() = flavorDimensions

    override val buildToolsRevision: Revision
        get() = Revision.parseRevision(buildToolsVersion, Revision.Precision.MICRO)

    override val libraryRequests: MutableCollection<LibraryRequest>
        get() = publicExtensionImpl.libraryRequests

    override var compileSdkVersion: String?
        get() = publicExtensionImpl.compileSdkPreview
        set(value) {
            publicExtensionImpl.compileSdkPreview = value
        }
}
