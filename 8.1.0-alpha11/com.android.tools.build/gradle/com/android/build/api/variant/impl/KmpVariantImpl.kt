/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.SdkConstants.DOT_AAR
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.impl.KmpComponentImpl
import com.android.build.api.component.impl.features.OptimizationCreationConfigImpl
import com.android.build.api.variant.AarMetadata
import com.android.build.api.variant.Component
import com.android.build.api.variant.Packaging
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.KmpCreationConfig
import com.android.build.gradle.internal.component.features.NativeBuildCreationConfig
import com.android.build.gradle.internal.component.features.RenderscriptCreationConfig
import com.android.build.gradle.internal.component.features.ShadersCreationConfig
import com.android.build.gradle.internal.core.dsl.KmpVariantDslInfo
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.AarMetadataTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import java.io.File
import javax.inject.Inject

open class KmpVariantImpl @Inject constructor(
    dslInfo: KmpVariantDslInfo,
    internalServices: VariantServices,
    buildFeatures: BuildFeatureValues,
    variantDependencies: VariantDependencies,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    taskContainer: MutableTaskContainer,
    services: TaskCreationServices,
    global: GlobalTaskCreationConfig,
    manifestFile: File
): KmpComponentImpl<KmpVariantDslInfo>(
    dslInfo,
    internalServices,
    buildFeatures,
    variantDependencies,
    paths,
    artifacts,
    taskContainer,
    services,
    global,
    manifestFile
), KotlinMultiplatformAndroidVariant, KmpCreationConfig {

    override val aarOutputFileName: Property<String> =
        internalServices.newPropertyBackingDeprecatedApi(
            String::class.java,
            services.projectInfo.getProjectBaseName().map {
                it + DOT_AAR
            }
        )

    override val aarMetadata: AarMetadata =
        internalServices.newInstance(AarMetadata::class.java).also {
            it.minCompileSdk.set(dslInfo.aarMetadata.minCompileSdk ?: 1)
            it.minCompileSdkExtension.set(
                dslInfo.aarMetadata.minCompileSdkExtension ?: AarMetadataTask.DEFAULT_MIN_COMPILE_SDK_EXTENSION
            )
            it.minAgpVersion.set(
                dslInfo.aarMetadata.minAgpVersion ?: AarMetadataTask.DEFAULT_MIN_AGP_VERSION
            )
        }

    override val optimizationCreationConfig by lazy(LazyThreadSafetyMode.NONE) {
        OptimizationCreationConfigImpl(
            this,
            dslInfo.optimizationDslInfo,
            null,
            null,
            internalServices
        )
    }

    override var unitTest = null

    override var androidTest = null

    override val nestedComponents: List<ComponentCreationConfig>
        get() = listOfNotNull(unitTest, androidTest)

    override val experimentalProperties: MapProperty<String, Any> =
        internalServices.mapPropertyOf(
            String::class.java,
            Any::class.java,
            dslInfo.experimentalProperties
        )

    override val maxSdk: Int?
        get() = dslInfo.maxSdkVersion

    override val packaging: Packaging by lazy(LazyThreadSafetyMode.NONE) {
        PackagingImpl(dslInfo.packaging, internalServices)
    }

    override fun <T : Component> createUserVisibleVariantObject(
        stats: GradleBuildVariant.Builder?
    ): T {
        // TODO(b/243387425): Support analytics
        throw IllegalAccessException("Unsupported")
    }

    // Not supported
    override val renderscriptCreationConfig: RenderscriptCreationConfig? = null
    override val shadersCreationConfig: ShadersCreationConfig? = null
    override val nativeBuildCreationConfig: NativeBuildCreationConfig? = null

    // TODO(b/243387425): Figure out coverage
    override val isAndroidTestCoverageEnabled: Boolean
        get() = false

    override val isCoreLibraryDesugaringEnabledLintCheck: Boolean
        get() = false
}
