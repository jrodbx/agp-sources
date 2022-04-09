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
import com.android.build.api.component.UnitTest
import com.android.build.api.component.analytics.AnalyticsEnabledUnitTest
import com.android.build.api.dsl.AndroidResources
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.Component
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.api.variant.impl.DirectoryEntry
import com.android.build.api.variant.impl.VariantImpl
import com.android.build.gradle.internal.component.UnitTestCreationConfig
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.core.VariantDslInfoImpl
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.provider.Provider
import javax.inject.Inject

open class UnitTestImpl @Inject constructor(
    componentIdentity: ComponentIdentity,
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
    internalServices: VariantServices,
    taskCreationServices: TaskCreationServices,
    global: GlobalTaskCreationConfig
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
    global
), UnitTest, UnitTestCreationConfig {

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    override val minSdkVersion: AndroidVersion
        get() = testedVariant.minSdkVersion

    override val targetSdkVersion: AndroidVersion
        get() = testedVariant.targetSdkVersion

    override val dslAndroidResources: AndroidResources
        get() = variantDslInfo.androidResources

    override val applicationId: Provider<String> =
        internalServices.providerOf(String::class.java, variantDslInfo.applicationId)

    override val targetSdkVersionOverride: AndroidVersion?
        get() = testedVariant.targetSdkVersionOverride

    /**
     * Return the default runner as with unit tests, there is no dexing. However aapt2 requires
     * the instrumentation tag to be present in the merged manifest to process android resources.
     */
    override val instrumentationRunner: Provider<out String>
        get() = services.provider { VariantDslInfoImpl.DEFAULT_TEST_RUNNER }

    override val testedApplicationId: Provider<String>
        get() = testedConfig.applicationId

    override val debuggable: Boolean
        get() = testedConfig.debuggable

    override val profileable: Boolean
        get() = testedConfig.profileable

    // these would normally be public but not for unit-test. They are there to feed the
    // manifest but aren't actually used.
    override val isTestCoverageEnabled: Boolean
        get() = variantDslInfo.isUnitTestCoverageEnabled

    override fun addDataBindingSources(
        sourceSets: MutableList<DirectoryEntry>
    ) {}

    override fun <T : Component> createUserVisibleVariantObject(
            projectServices: ProjectServices,
            operationsRegistrar: VariantApiOperationsRegistrar<out CommonExtension<*, *, *, *>, out VariantBuilder, out Variant>,
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
     * There is no build config fields for unit tests.
     */
    override val buildConfigEnabled: Boolean = false
}
