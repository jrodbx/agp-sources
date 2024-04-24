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
import com.android.build.api.attributes.ProductFlavorAttr
import com.android.build.api.component.impl.ComponentImpl
import com.android.build.api.component.impl.UnitTestImpl
import com.android.build.api.component.impl.features.BuildConfigCreationConfigImpl
import com.android.build.api.component.impl.features.ManifestPlaceholdersCreationConfigImpl
import com.android.build.api.component.impl.features.NativeBuildCreationConfigImpl
import com.android.build.api.component.impl.features.OptimizationCreationConfigImpl
import com.android.build.api.component.impl.features.RenderscriptCreationConfigImpl
import com.android.build.api.component.impl.features.ShadersCreationConfigImpl
import com.android.build.api.component.impl.warnAboutAccessingVariantApiValueForDisabledFeature
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.CanMinifyAndroidResourcesBuilder
import com.android.build.api.variant.CanMinifyCodeBuilder
import com.android.build.api.variant.Component
import com.android.build.api.variant.ExternalNativeBuild
import com.android.build.api.variant.Packaging
import com.android.build.api.variant.ResValue
import com.android.build.api.variant.Variant
import com.android.build.gradle.internal.DependencyConfigurator
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.component.features.BuildConfigCreationConfig
import com.android.build.gradle.internal.component.features.FeatureNames
import com.android.build.gradle.internal.component.features.ManifestPlaceholdersCreationConfig
import com.android.build.gradle.internal.component.features.NativeBuildCreationConfig
import com.android.build.gradle.internal.component.features.OptimizationCreationConfig
import com.android.build.gradle.internal.component.features.RenderscriptCreationConfig
import com.android.build.gradle.internal.component.features.ShadersCreationConfig
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.core.dsl.ConsumableComponentDslInfo
import com.android.build.gradle.internal.core.dsl.VariantDslInfo
import com.android.build.gradle.internal.core.dsl.features.ManifestPlaceholdersDslInfo
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.utils.appendCapitalized
import com.android.utils.capitalizeAndAppend
import com.google.common.collect.ImmutableMap
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import java.io.Serializable

abstract class VariantImpl<DslInfoT: VariantDslInfo>(
    open val variantBuilder: VariantBuilderImpl,
    buildFeatureValues: BuildFeatureValues,
    dslInfo: DslInfoT,
    variantDependencies: VariantDependencies,
    variantSources: VariantSources,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    variantData: BaseVariantData,
    taskContainer: MutableTaskContainer,
    variantServices: VariantServices,
    taskCreationServices: TaskCreationServices,
    global: GlobalTaskCreationConfig
) : ComponentImpl<DslInfoT>(
    variantBuilder,
    buildFeatureValues,
    dslInfo,
    variantDependencies,
    variantSources,
    paths,
    artifacts,
    variantData,
    taskContainer,
    variantServices,
    taskCreationServices,
    global
), Variant, VariantCreationConfig, HasUnitTest {

    override val description: String
        get() = if (componentIdentity.productFlavors.isNotEmpty()) {
            val sb = StringBuilder(50)
            componentIdentity.buildType?.let { sb.appendCapitalized(it) }
            sb.append(" build for flavor ")
            componentIdentity.flavorName?.let { sb.appendCapitalized(it) }
            sb.toString()
        } else {
            componentIdentity.buildType!!.capitalizeAndAppend(" build")
        }

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------

    override val minSdk: AndroidVersion by lazy {
        variantBuilder.minSdkVersion
    }

    override val minSdkVersion: AndroidVersion
        get() = minSdk

    override val maxSdk: Int?
        get() = variantBuilder.maxSdk

    override val maxSdkVersion: Int?
        get() = variantBuilder.maxSdk

    override val buildConfigFields: MapProperty<String, BuildConfigField<out Serializable>> by lazy {
        buildConfigCreationConfig?.buildConfigFields
            ?: warnAboutAccessingVariantApiValueForDisabledFeature(
                featureName = FeatureNames.BUILD_CONFIG,
                apiName = "buildConfigFields",
                value = internalServices.mapPropertyOf(
                    String::class.java,
                    BuildConfigField::class.java,
                    dslInfo.buildConfigDslInfo!!.getBuildConfigFields()
                )
            )
    }

    override val packaging: Packaging by lazy {
        PackagingImpl(dslInfo.packaging, internalServices)
    }


    override val externalNativeBuild: ExternalNativeBuild?
        get() = nativeBuildCreationConfig.externalNativeBuild

    override fun <T> getExtension(type: Class<T>): T? =
        type.cast(externalExtensions?.get(type))

    override val proguardFiles: ListProperty<RegularFile>
        get() = optimizationCreationConfig.proguardFiles

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    override val buildConfigCreationConfig: BuildConfigCreationConfig? by lazy(LazyThreadSafetyMode.NONE) {
        if (buildFeatures.buildConfig) {
            BuildConfigCreationConfigImpl(
                this,
                dslInfo.buildConfigDslInfo!!,
                internalServices
            )
        } else {
            null
        }
    }

    override val renderscriptCreationConfig: RenderscriptCreationConfig? by lazy(LazyThreadSafetyMode.NONE) {
        if (buildFeatures.renderScript) {
            RenderscriptCreationConfigImpl(
                dslInfo.renderscriptDslInfo!!,
                internalServices,
                renderscriptTargetApi = variantBuilder.renderscriptTargetApi
            )
        } else {
            null
        }
    }

    override val shadersCreationConfig: ShadersCreationConfig by lazy(LazyThreadSafetyMode.NONE) {
        ShadersCreationConfigImpl(
            dslInfo.shadersDslInfo!!
        )
    }

    override val optimizationCreationConfig: OptimizationCreationConfig by lazy(LazyThreadSafetyMode.NONE) {
        OptimizationCreationConfigImpl(
            this,
            dslInfo.optimizationDslInfo,
            variantBuilder as? CanMinifyCodeBuilder,
            variantBuilder as? CanMinifyAndroidResourcesBuilder,
            internalServices
        )
    }

    override val nativeBuildCreationConfig: NativeBuildCreationConfig by lazy(LazyThreadSafetyMode.NONE) {
        NativeBuildCreationConfigImpl(
            this,
            dslInfo.nativeBuildDslInfo!!,
            internalServices
        )
    }

    private val externalExtensions: Map<Class<*>, Any>? by lazy {
        variantBuilder.getRegisteredExtensions()
    }

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

    override var unitTest: UnitTestImpl? = null

    override val pseudoLocalesEnabled: Property<Boolean> by lazy {
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

    override val experimentalProperties: MapProperty<String, Any> =
            internalServices.mapPropertyOf(
                String::class.java,
                Any::class.java,
                dslInfo.experimentalProperties,
                disallowUnsafeRead = false
            )

    override val nestedComponents: List<ComponentImpl<*>>
        get() = listOfNotNull(
            unitTest,
            (this as? HasAndroidTest)?.androidTest,
            (this as? HasTestFixtures)?.testFixtures
        )

    override val components: List<Component>
        get() = listOfNotNull(
            this,
            unitTest,
            (this as? HasAndroidTest)?.androidTest,
            (this as? HasTestFixtures)?.testFixtures
        )

    override val manifestPlaceholders: MapProperty<String, String>
        get() = manifestPlaceholdersCreationConfig.placeholders

    override val manifestPlaceholdersCreationConfig: ManifestPlaceholdersCreationConfig by lazy(LazyThreadSafetyMode.NONE) {
        createManifestPlaceholdersCreationConfig(
                dslInfo.manifestPlaceholdersDslInfo?.placeholders)
    }

    override val isAndroidTestCoverageEnabled: Boolean
        get() = (this as? HasAndroidTest)?.androidTest?.isAndroidTestCoverageEnabled == true

    override val isCoreLibraryDesugaringEnabledLintCheck: Boolean
        get() = if (this is ApkCreationConfig) {
            dexingCreationConfig.isCoreLibraryDesugaringEnabled
        } else {
            // We don't dex library variants, but we still need to check if core library desugaring
            // for lint checks.
            global.compileOptions.isCoreLibraryDesugaringEnabled
        }

    override fun missingDimensionStrategy(dimension: String, vararg requestedValues: String) {
        val attributeKey = ProductFlavorAttr.of(dimension)
        val attributeValue: ProductFlavorAttr = services.named(
            ProductFlavorAttr::class.java, name
        )

        variantDependencies.compileClasspath.attributes.attribute(attributeKey, attributeValue)
        variantDependencies.runtimeClasspath.attributes.attribute(attributeKey, attributeValue)
        variantDependencies
            .annotationProcessorConfiguration
            ?.attributes
            ?.attribute(attributeKey, attributeValue)

        // then add the fallbacks which contain the actual requested value
        DependencyConfigurator.addFlavorStrategy(
            services.dependencies.attributesSchema,
            dimension,
            ImmutableMap.of(name, requestedValues.toList())
        )
    }
}
