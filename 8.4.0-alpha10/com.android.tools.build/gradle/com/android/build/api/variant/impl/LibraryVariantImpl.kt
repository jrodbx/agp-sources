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
import com.android.build.api.component.impl.TestFixturesImpl
import com.android.build.api.variant.AarMetadata
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.Component
import com.android.build.api.variant.DeviceTest
import com.android.build.api.variant.LibraryVariant
import com.android.build.api.variant.Renderscript
import com.android.build.gradle.internal.component.LibraryCreationConfig
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.core.dsl.LibraryVariantDslInfo
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.publishing.VariantPublishingInfo
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.AarMetadataTask.Companion.DEFAULT_MIN_AGP_VERSION
import com.android.build.gradle.internal.tasks.AarMetadataTask.Companion.DEFAULT_MIN_COMPILE_SDK_EXTENSION
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.builder.core.BuilderConstants
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.provider.Property
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
    internalServices,
    taskCreationServices,
    globalTaskCreationConfig,
), LibraryVariant, LibraryCreationConfig, InternalHasDeviceTests, HasTestFixtures {

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------

    override val targetSdk: AndroidVersion by lazy(LazyThreadSafetyMode.NONE) {
        variantBuilder.targetSdkVersion
    }

    override val targetSdkVersion: AndroidVersion
        get() = targetSdk

    override val targetSdkOverride: AndroidVersion?
        get() = variantBuilder.mutableTargetSdk?.sanitize()

    override val applicationId: Provider<String> =
        internalServices.newProviderBackingDeprecatedApi(
            type = String::class.java,
            value = dslInfo.namespace
        )


    override val deviceTests = mutableListOf<DeviceTest>()

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

    override val androidResources: AndroidResourcesImpl =
        getAndroidResources(dslInfo.androidResourcesDsl.androidResources)

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    override val aarOutputFileName: Property<String> =
        internalServices.newPropertyBackingDeprecatedApi(
            String::class.java,
            services.projectInfo.getProjectBaseName().map {
                "$it-$baseName.${BuilderConstants.EXT_LIB_ARCHIVE}"
            }
        )

    override val debuggable: Boolean
        get() = dslInfo.isDebuggable

    override fun <T : Component> createUserVisibleVariantObject(
        stats: GradleBuildVariant.Builder?
    ): T =
        if (stats == null) {
            this as T
        } else {
           services.newInstance(
                AnalyticsEnabledLibraryVariant::class.java,
                this,
                stats
            ) as T
        }

    override val publishInfo: VariantPublishingInfo?
        get() = dslInfo.publishInfo
}
