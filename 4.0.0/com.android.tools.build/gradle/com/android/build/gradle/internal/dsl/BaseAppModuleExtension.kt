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

import com.android.build.api.dsl.ApplicationBuildFeatures
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.DependenciesInfo
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.ApplicationVariantProperties
import com.android.build.gradle.AppExtension
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.api.ViewBindingOptions
import com.android.build.gradle.internal.CompileOptions
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.coverage.JacocoOptions
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.options.ProjectOptions
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project

/** The `android` extension for base feature module (application plugin).  */
open class BaseAppModuleExtension(
    dslScope: DslScope,
    projectOptions: ProjectOptions,
    globalScope: GlobalScope,
    buildOutputs: NamedDomainObjectContainer<BaseVariantOutput>,
    sourceSetManager: SourceSetManager,
    extraModelInfo: ExtraModelInfo,
    private val publicExtensionImpl: ApplicationExtensionImpl
) : AppExtension(
    dslScope,
    projectOptions,
    globalScope,
    buildOutputs,
    sourceSetManager,
    extraModelInfo,
    true
), ApplicationExtension<
        BuildType,
        CmakeOptions,
        CompileOptions,
        DefaultConfig,
        ExternalNativeBuild,
        JacocoOptions,
        NdkBuildOptions,
        ProductFlavor,
        SigningConfig,
        TestOptions,
        TestOptions.UnitTestOptions> by publicExtensionImpl,
    ActionableVariantObjectOperationsExecutor<ApplicationVariant, ApplicationVariantProperties> by publicExtensionImpl {

    override val dataBinding: DataBindingOptions =
        dslScope.objectFactory.newInstance(
            DataBindingOptions::class.java,
            publicExtensionImpl.buildFeatures,
            projectOptions,
            dslScope
        )

    override val viewBinding: ViewBindingOptions =
        dslScope.objectFactory.newInstance(
            ViewBindingOptionsImpl::class.java,
            publicExtensionImpl.buildFeatures,
            projectOptions,
            dslScope
        )

    // this is needed because the impl class needs this but the interface does not,
    // so CommonExtension does not define it, which means, that even though it's part of
    // ApplicationExtensionImpl, the implementation by delegate does not bring it.
    fun buildFeatures(action: Action<ApplicationBuildFeatures>) {
        publicExtensionImpl.buildFeatures(action)
    }

    fun dependenciesInfo(action: Action<DependenciesInfo>) {
        publicExtensionImpl.dependenciesInfo(action)
    }

    var dynamicFeatures: MutableSet<String> = mutableSetOf()

    /**
     * Set of asset pack subprojects to be included in the app's bundle.
     */
    var assetPacks: MutableSet<String> = mutableSetOf()

    val bundle: BundleOptions =
        dslScope.objectFactory.newInstance(
            BundleOptions::class.java,
            dslScope.objectFactory,
            dslScope.deprecationReporter
        )

    fun bundle(action: Action<BundleOptions>) {
        action.execute(bundle)
    }
}
