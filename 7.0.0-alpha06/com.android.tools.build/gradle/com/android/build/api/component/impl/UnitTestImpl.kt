/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.build.api.component.UnitTest
import com.android.build.api.component.analytics.AnalyticsEnabledUnitTest
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.api.variant.impl.VariantImpl
import com.android.build.gradle.internal.component.UnitTestCreationConfig
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
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.google.common.collect.ImmutableList
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import javax.inject.Inject

open class UnitTestImpl @Inject constructor(
    componentIdentity: UnitTestBuilderImpl,
    buildFeatureValues: BuildFeatureValues,
    variantDslInfo: VariantDslInfo,
    variantDependencies: VariantDependencies,
    variantSources: VariantSources,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    variantScope: VariantScope,
    variantData: BaseVariantData,
    testedVariant: VariantImpl,
    transformManager: TransformManager,
    internalServices: VariantPropertiesApiServices,
    taskCreationServices: TaskCreationServices,
    globalScope: GlobalScope
) : TestComponentImpl(
    componentIdentity,
    buildFeatureValues,
    variantDslInfo,
    variantDependencies,
    variantSources,
    paths,
    artifacts,
    variantScope,
    variantData,
    testedVariant,
    transformManager,
    internalServices,
    taskCreationServices,
    globalScope
), UnitTest, UnitTestCreationConfig {

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------
    override val applicationId: Provider<String> =
        internalServices.providerOf(String::class.java, variantDslInfo.applicationId)

    override val testedApplicationId: Provider<String>
        get() = testedConfig.applicationId

    override val debuggable: Boolean
        get() = testedConfig.debuggable

    // these would normally be public but not for unit-test. They are there to feed the
    // manifest but aren't actually used.
    override val instrumentationRunner: Provider<String>
        get() = variantDslInfo.getInstrumentationRunner(testedVariant.dexingType)
    override val handleProfiling: Provider<Boolean>
        get() = variantDslInfo.handleProfiling
    override val functionalTest: Provider<Boolean>
        get() = variantDslInfo.functionalTest
    override val testLabel: Provider<String?>
        get() = variantDslInfo.testLabel
    override val instrumentationRunnerArguments: Map<String, String>
        get() = variantDslInfo.instrumentationRunnerArguments
    override val isTestCoverageEnabled: Boolean
        get() = variantDslInfo.isTestCoverageEnabled

    override fun addDataBindingSources(
        sourceSets: ImmutableList.Builder<ConfigurableFileTree>) {}

    override val minSdkVersion: AndroidVersion
        get() = testedVariant.variantBuilder.minSdkVersion

    override val targetSdkVersion: AndroidVersion
        get() = testedVariant.variantBuilder.targetSdkVersion

    override fun <T : Component> createUserVisibleVariantObject(
            projectServices: ProjectServices,
            operationsRegistrar: VariantApiOperationsRegistrar<VariantBuilder, Variant>,
            stats: GradleBuildVariant.Builder?
    ): T =
        if (stats == null) {
             this as T
        } else {
            projectServices.objectFactory.newInstance(
                AnalyticsEnabledUnitTest::class.java,
                this,
                stats
            ) as T
        }


    /**
     * for unit tests, the placeholders are always empty.
     */
    override val manifestPlaceholders: MapProperty<String, String> =
            internalServices.mapPropertyOf(String::class.java, String::class.java, mapOf())
}
