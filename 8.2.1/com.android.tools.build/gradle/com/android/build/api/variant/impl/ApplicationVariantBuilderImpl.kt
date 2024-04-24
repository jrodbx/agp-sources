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
package com.android.build.api.variant.impl

import com.android.build.api.component.analytics.AnalyticsEnabledApplicationVariantBuilder
import com.android.build.api.variant.ApplicationVariantBuilder
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.DependenciesInfoBuilder
import com.android.build.api.variant.VariantBuilder
import com.android.build.gradle.internal.core.dsl.ApplicationVariantDslInfo
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.VariantBuilderServices
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import javax.inject.Inject

open class ApplicationVariantBuilderImpl @Inject constructor(
    globalVariantBuilderConfig: GlobalVariantBuilderConfig,
    dslInfo: ApplicationVariantDslInfo,
    componentIdentity: ComponentIdentity,
    variantBuilderServices: VariantBuilderServices
) : VariantBuilderImpl(
    globalVariantBuilderConfig,
    dslInfo,
    componentIdentity,
    variantBuilderServices
), ApplicationVariantBuilder {

    override val debuggable: Boolean
        get() = (dslInfo as ApplicationVariantDslInfo).isDebuggable

    override var androidTestEnabled: Boolean
        get() = enableAndroidTest
        set(value) {
            enableAndroidTest = value
        }

    override var enableAndroidTest: Boolean = true

    override var enableTestFixtures: Boolean = dslInfo.testFixtures?.enable ?: false

    // only instantiate this if this is needed. This allows non-built variant to not do too much work.
    override val dependenciesInfo: DependenciesInfoBuilder by lazy {
        variantBuilderServices.newInstance(
            DependenciesInfoBuilderImpl::class.java,
            variantBuilderServices,
            globalVariantBuilderConfig.dependenciesInfo
        )
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T: VariantBuilder> createUserVisibleVariantObject(
        projectServices: ProjectServices,
        stats: GradleBuildVariant.Builder?,
    ): T =
        if (stats == null) {
            this as T
        } else {
            projectServices.objectFactory.newInstance(
                AnalyticsEnabledApplicationVariantBuilder::class.java,
                this,
                stats
            ) as T
        }

    override var isMinifyEnabled: Boolean =
        dslInfo.optimizationDslInfo.postProcessingOptions.codeShrinkerEnabled()
        set(value) = setMinificationIfPossible("minifyEnabled", value){ field = it }

    override var shrinkResources: Boolean =
        dslInfo.optimizationDslInfo.postProcessingOptions.resourcesShrinkingEnabled()
        set(value) = setMinificationIfPossible("shrinkResources", value){ field = it }

}
