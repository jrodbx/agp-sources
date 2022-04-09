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

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.analytics.AnalyticsEnabledLibraryVariant
import com.android.build.api.component.impl.AndroidTestImpl
import com.android.build.api.component.impl.ConsumableCreationConfigImpl
import com.android.build.api.component.impl.TestFixturesImpl
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar
import com.android.build.api.variant.AarMetadata
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.Component
import com.android.build.api.variant.LibraryVariant
import com.android.build.api.variant.Renderscript
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.gradle.internal.component.LibraryCreationConfig
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.core.dsl.LibraryVariantDslInfo
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.publishing.VariantPublishingInfo
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.Java8LangSupport
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.AarMetadataTask.Companion.DEFAULT_MIN_AGP_VERSION
import com.android.build.gradle.internal.tasks.AarMetadataTask.Companion.DEFAULT_MIN_COMPILE_SDK_EXTENSION
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.builder.dexing.DexingType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.provider.Provider
import javax.inject.Inject

open class LibraryVariantImpl @Inject constructor(
    override val variantBuilder: LibraryVariantBuilderImpl,
    buildFeatureValues: BuildFeatureValues,
    dslInfo: LibraryVariantDslInfo,
    variantDependencies: VariantDependencies,
    variantSources: VariantSources,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    variantData: BaseVariantData,
    taskContainer: MutableTaskContainer,
    transformManager: TransformManager,
    internalServices: VariantServices,
    taskCreationServices: TaskCreationServices,
    globalTaskCreationConfig: GlobalTaskCreationConfig,
) : VariantImpl<LibraryVariantDslInfo>(
    variantBuilder,
    buildFeatureValues,
    dslInfo,
    variantDependencies,
    variantSources,
    paths,
    artifacts,
    variantData,
    taskContainer,
    transformManager,
    internalServices,
    taskCreationServices,
    globalTaskCreationConfig,
), LibraryVariant, LibraryCreationConfig, HasAndroidTest, HasTestFixtures {

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------

    override val applicationId: Provider<String> =
        internalServices.newProviderBackingDeprecatedApi(
            type = String::class.java,
            value = dslInfo.namespace
        )

    override var androidTest: AndroidTestImpl? = null

    override var testFixtures: TestFixturesImpl? = null

    override val renderscript: Renderscript? by lazy {
        renderscriptCreationConfig?.renderscript
    }

    override val aarMetadata: AarMetadata =
        internalServices.newInstance(AarMetadata::class.java).also {
            it.minCompileSdk.set(dslInfo.aarMetadata.minCompileSdk ?: 1)
            it.minCompileSdkExtension.set(
                dslInfo.aarMetadata.minCompileSdkExtension ?: DEFAULT_MIN_COMPILE_SDK_EXTENSION
            )
            it.minAgpVersion.set(
                dslInfo.aarMetadata.minAgpVersion ?: DEFAULT_MIN_AGP_VERSION
            )
        }

    override val isMinifyEnabled: Boolean
        get() = variantBuilder.isMinifyEnabled

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    private val delegate by lazy { ConsumableCreationConfigImpl(this, dslInfo) }

    override val dexingType: DexingType
        get() = delegate.dexingType

    override val debuggable: Boolean
        get() = dslInfo.isDebuggable

    override val isCoreLibraryDesugaringEnabled: Boolean
        get() = delegate.isCoreLibraryDesugaringEnabled

    override fun <T : Component> createUserVisibleVariantObject(
        projectServices: ProjectServices,
        operationsRegistrar: VariantApiOperationsRegistrar<out CommonExtension<*, *, *, *>, out VariantBuilder, out Variant>,
        stats: GradleBuildVariant.Builder?
    ): T =
        if (stats == null) {
            this as T
        } else {
            projectServices.objectFactory.newInstance(
                AnalyticsEnabledLibraryVariant::class.java,
                this,
                stats
            ) as T
        }

    override val minifiedEnabled: Boolean
        get() = variantBuilder.isMinifyEnabled
    override val resourcesShrink: Boolean
        // need to return shrink flag for PostProcessing as this API has the flag for libraries
        // return false otherwise
        get() = dslInfo.postProcessingOptions
            .let { it.hasPostProcessingConfiguration() && it.resourcesShrinkingEnabled() }

    override val needsMergedJavaResStream: Boolean = delegate.getNeedsMergedJavaResStream()

    override fun getJava8LangSupportType(): Java8LangSupport =
        delegate.getJava8LangSupportType()

    override val needsShrinkDesugarLibrary: Boolean
        get() = delegate.needsShrinkDesugarLibrary

    override val minSdkVersionForDexing: AndroidVersion
        get() = delegate.minSdkVersionForDexing

    override val packageJacocoRuntime: Boolean
        get() = dslInfo.isAndroidTestCoverageEnabled

    override val publishInfo: VariantPublishingInfo?
        get() = dslInfo.publishInfo
}
