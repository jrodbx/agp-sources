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
import com.android.build.api.component.impl.features.AndroidResourcesCreationConfigImpl
import com.android.build.api.component.impl.features.ManifestPlaceholdersCreationConfigImpl
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.ComponentIdentity
import com.android.build.gradle.internal.component.UnitTestCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.component.features.AndroidResourcesCreationConfig
import com.android.build.gradle.internal.component.features.BuildConfigCreationConfig
import com.android.build.gradle.internal.component.features.ManifestPlaceholdersCreationConfig
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.core.dsl.UnitTestComponentDslInfo
import com.android.build.gradle.internal.core.dsl.features.ManifestPlaceholdersDslInfo
import com.android.build.gradle.internal.core.dsl.impl.DEFAULT_TEST_RUNNER
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import javax.inject.Inject

open class UnitTestImpl @Inject constructor(
    componentIdentity: ComponentIdentity,
    buildFeatureValues: BuildFeatureValues,
    dslInfo: UnitTestComponentDslInfo,
    variantDependencies: VariantDependencies,
    variantSources: VariantSources,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    variantData: BaseVariantData,
    taskContainer: MutableTaskContainer,
    testedVariant: VariantCreationConfig,
    internalServices: VariantServices,
    taskCreationServices: TaskCreationServices,
    global: GlobalTaskCreationConfig
) : TestComponentImpl<UnitTestComponentDslInfo>(
    componentIdentity,
    buildFeatureValues,
    dslInfo,
    variantDependencies,
    variantSources,
    paths,
    artifacts,
    variantData,
    taskContainer,
    testedVariant,
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
        get() = mainVariant.minSdkVersion

    override val applicationId: Provider<String> =
        internalServices.providerOf(String::class.java, dslInfo.applicationId)

    /**
     * In unit tests, we don't produce an apk. However, we still need to set the target sdk version
     * in the test manifest as robolectric depends on it.
     */
    override val targetSdkVersion: AndroidVersion
        get() = getMainTargetSdkVersion()

    /**
     * Return the default runner as with unit tests, there is no dexing. However aapt2 requires
     * the instrumentation tag to be present in the merged manifest to process android resources.
     */
    override val instrumentationRunner: Provider<String>
        get() = services.provider { DEFAULT_TEST_RUNNER }

    override val testedApplicationId: Provider<String>
        get() = mainVariant.applicationId

    override val debuggable: Boolean
        get() = mainVariant.debuggable

    override val manifestPlaceholders: MapProperty<String, String>
        get() = manifestPlaceholdersCreationConfig.placeholders

    // these would normally be public but not for unit-test. They are there to feed the
    // manifest but aren't actually used.
    override val isUnitTestCoverageEnabled: Boolean
        get() = dslInfo.isUnitTestCoverageEnabled

    override val androidResourcesCreationConfig: AndroidResourcesCreationConfig? by lazy(LazyThreadSafetyMode.NONE) {
        // in case of unit tests, we add the R jar even if android resources are
        // disabled (includeAndroidResources) as we want to be able to compile against
        // the values inside.
        if (buildFeatures.androidResources || mainVariant.buildFeatures.androidResources) {
            AndroidResourcesCreationConfigImpl(
                this,
                dslInfo,
                dslInfo.androidResourcesDsl!!,
                internalServices,
            )
        } else {
            null
        }
    }

    override val manifestPlaceholdersCreationConfig: ManifestPlaceholdersCreationConfig by lazy(LazyThreadSafetyMode.NONE) {
        createManifestPlaceholdersCreationConfig(
                dslInfo.mainVariantDslInfo.manifestPlaceholdersDslInfo?.placeholders)
    }

    /**
     * There is no build config fields for unit tests.
     */
    override val buildConfigCreationConfig: BuildConfigCreationConfig? = null
}
