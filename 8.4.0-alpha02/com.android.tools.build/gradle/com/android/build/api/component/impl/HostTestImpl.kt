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
import com.android.build.api.component.impl.features.AndroidResourcesCreationConfigImpl
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.ComponentIdentity
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.component.features.AndroidResourcesCreationConfig
import com.android.build.gradle.internal.component.features.BuildConfigCreationConfig
import com.android.build.gradle.internal.component.features.ManifestPlaceholdersCreationConfig
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.core.dsl.HostTestComponentDslInfo
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

open class HostTestImpl @Inject constructor(
    componentIdentity: ComponentIdentity,
    buildFeatureValues: BuildFeatureValues,
    dslInfo: HostTestComponentDslInfo,
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
) : TestComponentImpl<HostTestComponentDslInfo>(
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
) {

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    override val minSdk: AndroidVersion
        get() = mainVariant.minSdk

    override val applicationId: Provider<String> =
        internalServices.providerOf(String::class.java, dslInfo.applicationId)

    override val targetSdkVersion: AndroidVersion
        get() = getMainTargetSdkVersion()

    /**
     * Return the default runner as with host tests, there is no dexing. However, aapt2 requires
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


    // TODO: We will need R.jar for Screenshot tests too.
    override val androidResourcesCreationConfig: AndroidResourcesCreationConfig? = null

    override val manifestPlaceholdersCreationConfig: ManifestPlaceholdersCreationConfig by lazy(LazyThreadSafetyMode.NONE) {
        createManifestPlaceholdersCreationConfig(
                dslInfo.mainVariantDslInfo.manifestPlaceholdersDslInfo?.placeholders)
    }

    /**
     * There is no build config fields for host tests.
     */
    override val buildConfigCreationConfig: BuildConfigCreationConfig? = null
}
