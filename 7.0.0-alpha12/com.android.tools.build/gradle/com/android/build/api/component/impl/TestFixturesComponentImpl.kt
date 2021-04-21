/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.api.component.impl

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.Component
import com.android.build.api.component.TestFixturesComponent
import com.android.build.api.component.analytics.AnalyticsEnabledTestFixturesComponent
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.api.variant.impl.VariantImpl
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantPropertiesApiServices
import com.android.build.gradle.internal.variant.TestFixturesVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import javax.inject.Inject

open class TestFixturesComponentImpl @Inject constructor(
    componentIdentity: TestFixturesComponentBuilderImpl,
    buildFeatureValues: BuildFeatureValues,
    variantDslInfo: VariantDslInfo,
    variantDependencies: VariantDependencies,
    variantSources: VariantSources,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    variantScope: VariantScope,
    variantData: TestFixturesVariantData,
    private val mainVariant: VariantImpl,
    transformManager: TransformManager,
    variantPropertiesApiServices: VariantPropertiesApiServices,
    taskCreationServices: TaskCreationServices,
    globalScope: GlobalScope
) : ComponentImpl(
    componentIdentity,
    buildFeatureValues,
    variantDslInfo,
    variantDependencies,
    variantSources,
    paths,
    artifacts,
    variantScope,
    variantData,
    transformManager,
    variantPropertiesApiServices,
    taskCreationServices,
    globalScope
), TestFixturesComponent, ComponentCreationConfig {

    override val applicationId: Provider<String> =
        internalServices.providerOf(String::class.java, variantDslInfo.namespace)
    override val debuggable: Boolean
        get() = mainVariant.debuggable
    override val minSdkVersion: AndroidVersion
        get() = mainVariant.minSdkVersion
    override val needsMainDexListForBundle: Boolean
        get() = mainVariant.needsMainDexListForBundle
    override val isPseudoLocalesEnabled: Property<Boolean>
        get() = mainVariant.isPseudoLocalesEnabled

    override fun <T : Component> createUserVisibleVariantObject(
        projectServices: ProjectServices,
        operationsRegistrar: VariantApiOperationsRegistrar<out VariantBuilder, out Variant>,
        stats: GradleBuildVariant.Builder?
    ): T {
        return if (stats == null) {
            this as T
        } else {
            projectServices.objectFactory.newInstance(
                AnalyticsEnabledTestFixturesComponent::class.java,
                this,
                stats
            ) as T
        }
    }

}
