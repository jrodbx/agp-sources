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
import com.android.build.api.component.analytics.AnalyticsEnabledTestFixtures
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar
import com.android.build.api.variant.AarMetadata
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.Component
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.JavaCompilation
import com.android.build.api.variant.ResValue
import com.android.build.api.variant.TestFixtures
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.api.variant.impl.ResValueKeyImpl
import com.android.build.api.variant.impl.VariantImpl
import com.android.build.gradle.internal.component.AarCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.AarMetadataTask.Companion.DEFAULT_MIN_AGP_VERSION
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.testFixtures.testFixturesFeatureName
import com.android.build.gradle.internal.variant.TestFixturesVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import javax.inject.Inject

open class TestFixturesImpl @Inject constructor(
    componentIdentity: ComponentIdentity,
    buildFeatureValues: BuildFeatureValues,
    variantDslInfo: VariantDslInfo,
    variantDependencies: VariantDependencies,
    variantSources: VariantSources,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    variantScope: VariantScope,
    variantData: TestFixturesVariantData,
    val mainVariant: VariantImpl,
    transformManager: TransformManager,
    variantServices: VariantServices,
    taskCreationServices: TaskCreationServices,
    global: GlobalTaskCreationConfig
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
    variantServices,
    taskCreationServices,
    global
), TestFixtures, ComponentCreationConfig, AarCreationConfig {

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------

    override val applicationId: Provider<String> =
        internalServices.providerOf(String::class.java, variantDslInfo.namespace)
    override val debuggable: Boolean
        get() = mainVariant.debuggable
    override val profileable: Boolean
        get() = mainVariant.profileable
    override val minSdkVersion: AndroidVersion
        get() = mainVariant.minSdkVersion
    override val targetSdkVersion: AndroidVersion
        get() = mainVariant.targetSdkVersion
    override val needsMainDexListForBundle: Boolean
        get() = mainVariant.needsMainDexListForBundle

    override val aarMetadata: AarMetadata =
            internalServices.newInstance(AarMetadata::class.java).also {
                it.minCompileSdk.set(variantDslInfo.aarMetadata.minCompileSdk ?: 1)
                it.minAgpVersion.set(
                    variantDslInfo.aarMetadata.minAgpVersion ?: DEFAULT_MIN_AGP_VERSION
                )
            }

    override val javaCompilation: JavaCompilation =
        JavaCompilationImpl(
            variantDslInfo.javaCompileOptions,
            buildFeatures.dataBinding,
            internalServices
        )

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    override val targetSdkVersionOverride: AndroidVersion?
        get() = mainVariant.targetSdkVersionOverride

    override fun <T : Component> createUserVisibleVariantObject(
        projectServices: ProjectServices,
        operationsRegistrar: VariantApiOperationsRegistrar<out CommonExtension<*, *, *, *>, out VariantBuilder, out Variant>,
        stats: GradleBuildVariant.Builder?
    ): T {
        return if (stats == null) {
            this as T
        } else {
            projectServices.objectFactory.newInstance(
                AnalyticsEnabledTestFixtures::class.java,
                this,
                stats
            ) as T
        }
    }

    override val resValues: MapProperty<ResValue.Key, ResValue> by lazy {
        internalServices.mapPropertyOf(
            ResValue.Key::class.java,
            ResValue::class.java,
            variantDslInfo.getResValues()
        )
    }

    override fun makeResValueKey(type: String, name: String): ResValue.Key = ResValueKeyImpl(type, name)

    override val pseudoLocalesEnabled: Property<Boolean> =
        internalServices.newPropertyBackingDeprecatedApi(
            Boolean::class.java,
            variantDslInfo.isPseudoLocalesEnabled
        )

    override fun getArtifactName(name: String): String {
        return "$testFixturesFeatureName-$name"
    }

    // ---------------------------------------------------------------------------------------------
    // Private stuff
    // ---------------------------------------------------------------------------------------------

    override val androidResourcesEnabled: Boolean =
        variantDslInfo.testFixtures.androidResources
}
