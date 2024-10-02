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
import com.android.build.api.variant.AarMetadata
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.JavaCompilation
import com.android.build.api.variant.ResValue
import com.android.build.api.variant.TestFixtures
import com.android.build.api.variant.impl.ResValueKeyImpl
import com.android.build.gradle.internal.component.PublishableCreationConfig
import com.android.build.gradle.internal.component.TestFixturesCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.component.features.BuildConfigCreationConfig
import com.android.build.gradle.internal.component.features.FeatureNames
import com.android.build.gradle.internal.component.features.ManifestPlaceholdersCreationConfig
import com.android.build.gradle.internal.component.legacy.OldVariantApiLegacySupport
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.core.dsl.TestFixturesComponentDslInfo
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.publishing.VariantPublishingInfo
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.AarMetadataTask.Companion.DEFAULT_MIN_AGP_VERSION
import com.android.build.gradle.internal.tasks.AarMetadataTask.Companion.DEFAULT_MIN_COMPILE_SDK_EXTENSION
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.testFixtures.testFixturesFeatureName
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.builder.core.BuilderConstants
import com.android.utils.appendCapitalized
import com.android.utils.capitalizeAndAppend
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import javax.inject.Inject

open class TestFixturesImpl @Inject constructor(
    componentIdentity: ComponentIdentity,
    buildFeatureValues: BuildFeatureValues,
    variantDslInfo: TestFixturesComponentDslInfo,
    variantDependencies: VariantDependencies,
    variantSources: VariantSources,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    taskContainer: MutableTaskContainer,
    override val mainVariant: VariantCreationConfig,
    variantServices: VariantServices,
    taskCreationServices: TaskCreationServices,
    global: GlobalTaskCreationConfig
): ComponentImpl<TestFixturesComponentDslInfo>(
    componentIdentity,
    buildFeatureValues,
    variantDslInfo,
    variantDependencies,
    variantSources,
    paths,
    artifacts,
    taskContainer = taskContainer,
    internalServices = variantServices,
    services = taskCreationServices,
    global = global
), TestFixtures, TestFixturesCreationConfig {

    override val description: String
        get() = if (productFlavorList.isNotEmpty()) {
            val sb = StringBuilder(50)
            dslInfo.componentIdentity.buildType?.let { sb.appendCapitalized(it) }
            sb.append(" build for flavor ")
            dslInfo.componentIdentity.flavorName?.let { sb.appendCapitalized(it) }
            sb.toString()
        } else {
            dslInfo.componentIdentity.buildType!!.capitalizeAndAppend(" build")
        }

    // test fixtures doesn't exist in the old variant api
    override val oldVariantApiLegacySupport: OldVariantApiLegacySupport? = null

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------

    override val applicationId: Provider<String> =
        internalServices.providerOf(String::class.java, variantDslInfo.namespace)
    override val debuggable: Boolean
        get() = mainVariant.debuggable
    override val minSdk: AndroidVersion
        get() = mainVariant.minSdk
    override val publishInfo: VariantPublishingInfo?
        get() = (mainVariant as? PublishableCreationConfig)?.publishInfo

    override val aarMetadata: AarMetadata =
        internalServices.newInstance(AarMetadata::class.java).also {
            it.minCompileSdk.set(1)
            it.minCompileSdkExtension.set(DEFAULT_MIN_COMPILE_SDK_EXTENSION)
            it.minAgpVersion.set(DEFAULT_MIN_AGP_VERSION)
        }

    override val javaCompilation: JavaCompilation =
        JavaCompilationImpl(
            variantDslInfo.javaCompileOptionsSetInDSL,
            buildFeatures.dataBinding,
            internalServices
        )

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    override val aarOutputFileName: Property<String> =
        variantServices.newPropertyBackingDeprecatedApi(
            String::class.java,
            services.projectInfo.getProjectBaseName().map {
                "it-$baseName-testFixtures.${BuilderConstants.EXT_LIB_ARCHIVE}"
            }
        )

    override val buildConfigCreationConfig: BuildConfigCreationConfig? = null

    override val manifestPlaceholdersCreationConfig: ManifestPlaceholdersCreationConfig?
        get() = mainVariant.manifestPlaceholdersCreationConfig

    override val resValues: MapProperty<ResValue.Key, ResValue> by lazy {
        resValuesCreationConfig?.resValues
            ?: warnAboutAccessingVariantApiValueForDisabledFeature(
                featureName = FeatureNames.RES_VALUES,
                apiName = "resValues",
                value = internalServices.mapPropertyOf(
                    ResValue.Key::class.java,
                    ResValue::class.java,
                    dslInfo.androidResourcesDsl!!.getResValues()
                )
            )
    }

    override fun makeResValueKey(type: String, name: String): ResValue.Key = ResValueKeyImpl(type, name)

    override val pseudoLocalesEnabled: Property<Boolean>  by lazy {
        androidResourcesCreationConfig?.pseudoLocalesEnabled
            ?: warnAboutAccessingVariantApiValueForDisabledFeature(
                featureName = FeatureNames.ANDROID_RESOURCES,
                apiName = "pseudoLocalesEnabled",
                value = internalServices.newPropertyBackingDeprecatedApi(
                    Boolean::class.java,
                    dslInfo.androidResourcesDsl!!.isPseudoLocalesEnabled
                )
            )
    }

    override fun getArtifactName(name: String): String {
        return "$testFixturesFeatureName-$name"
    }
}
