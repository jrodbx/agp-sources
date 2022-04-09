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
import com.android.build.api.component.impl.ComponentImpl
import com.android.build.api.component.impl.UnitTestImpl
import com.android.build.api.component.impl.features.BuildConfigCreationConfigImpl
import com.android.build.api.component.impl.features.ManifestPlaceholdersCreationConfigImpl
import com.android.build.api.component.impl.features.RenderscriptCreationConfigImpl
import com.android.build.api.component.impl.warnAboutAccessingVariantApiValueForDisabledFeature
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.Component
import com.android.build.api.variant.ExternalNativeBuild
import com.android.build.api.variant.ExternalNdkBuildImpl
import com.android.build.api.variant.Packaging
import com.android.build.api.variant.ResValue
import com.android.build.api.variant.Variant
import com.android.build.gradle.internal.PostprocessingFeatures
import com.android.build.gradle.internal.ProguardFileType
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.component.TestFixturesCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.component.features.BuildConfigCreationConfig
import com.android.build.gradle.internal.component.features.FeatureNames
import com.android.build.gradle.internal.component.features.ManifestPlaceholdersCreationConfig
import com.android.build.gradle.internal.component.features.RenderscriptCreationConfig
import com.android.build.gradle.internal.core.MergedNdkConfig
import com.android.build.gradle.internal.core.NativeBuiltType
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.core.dsl.VariantDslInfo
import com.android.build.gradle.internal.cxx.configure.externalNativeNinjaOptions
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.utils.immutableListBuilder
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.builder.core.ComponentType
import com.android.utils.appendCapitalized
import com.android.utils.capitalizeAndAppend
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.File
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
    transformManager: TransformManager,
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
    transformManager,
    variantServices,
    taskCreationServices,
    global
), Variant, VariantCreationConfig {

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

    override val minSdkVersion: AndroidVersion by lazy {
        variantBuilder.minSdkVersion
    }

    override val targetSdkVersion: AndroidVersion by lazy {
        variantBuilder.targetSdkVersion
    }

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
                    dslInfo.getBuildConfigFields()
                )
            )
    }

    override val packaging: Packaging by lazy {
        PackagingImpl(dslInfo.packaging, internalServices)
    }


    override val externalNativeBuild: ExternalNativeBuild? by lazy {
        dslInfo.nativeBuildSystem?.let { nativeBuildType ->
            when(nativeBuildType) {
                NativeBuiltType.CMAKE ->
                    dslInfo.externalNativeBuildOptions.externalNativeCmakeOptions?.let {
                        ExternalCmakeImpl(
                                it,
                                variantServices
                        )
                    }
                NativeBuiltType.NDK_BUILD ->
                    dslInfo.externalNativeBuildOptions.externalNativeNdkBuildOptions?.let {
                        ExternalNdkBuildImpl(
                                it,
                                variantServices
                        )
                    }
                NativeBuiltType.NINJA -> {
                    ExternalNinjaImpl(
                        externalNativeNinjaOptions,
                        variantServices
                    )
                }
            }
        }
    }

    override fun <T> getExtension(type: Class<T>): T? =
        type.cast(externalExtensions?.get(type))

    override val proguardFiles: ListProperty<RegularFile> =
        variantServices.listPropertyOf(RegularFile::class.java) {
            dslInfo.getProguardFiles(it)
        }

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    override val buildConfigCreationConfig: BuildConfigCreationConfig? by lazy(LazyThreadSafetyMode.NONE) {
        if (buildFeatures.buildConfig) {
            BuildConfigCreationConfigImpl(
                this,
                dslInfo,
                internalServices
            )
        } else {
            null
        }
    }

    override val renderscriptCreationConfig: RenderscriptCreationConfig? by lazy(LazyThreadSafetyMode.NONE) {
        if (buildFeatures.renderScript) {
            RenderscriptCreationConfigImpl(
                dslInfo,
                internalServices,
                renderscriptTargetApi = variantBuilder.renderscriptTargetApi
            )
        } else {
            null
        }
    }

    override val manifestPlaceholdersCreationConfig: ManifestPlaceholdersCreationConfig by lazy(LazyThreadSafetyMode.NONE) {
        ManifestPlaceholdersCreationConfigImpl(
            dslInfo,
            internalServices
        )
    }

    override val testComponents = mutableMapOf<ComponentType, TestComponentCreationConfig>()
    override var testFixturesComponent: TestFixturesCreationConfig? = null

    private val externalExtensions: Map<Class<*>, Any>? by lazy {
        variantBuilder.getRegisteredExtensions()
    }

    override val targetSdkVersionOverride: AndroidVersion?
        get() = variantBuilder.mutableTargetSdk?.sanitize()

    override val resValues: MapProperty<ResValue.Key, ResValue> by lazy {
        resValuesCreationConfig?.resValues
            ?: warnAboutAccessingVariantApiValueForDisabledFeature(
                featureName = FeatureNames.RES_VALUES,
                apiName = "resValues",
                value = internalServices.mapPropertyOf(
                    ResValue.Key::class.java,
                    ResValue::class.java,
                    dslInfo.getResValues()
                )
            )
    }

    override fun makeResValueKey(type: String, name: String): ResValue.Key = ResValueKeyImpl(type, name)

    private var _isMultiDexEnabled: Boolean? = dslInfo.isMultiDexEnabled
    override val isMultiDexEnabled: Boolean
        get() {
            return _isMultiDexEnabled ?: (minSdkVersion.getFeatureLevel() >= 21)
        }

    override val needsMainDexListForBundle: Boolean
        get() = dslInfo.componentType.isBaseModule
                && global.hasDynamicFeatures
                && dexingType.needsMainDexList

    override var unitTest: UnitTestImpl? = null

    override val pseudoLocalesEnabled: Property<Boolean> by lazy {
        androidResourcesCreationConfig?.pseudoLocalesEnabled
            ?: warnAboutAccessingVariantApiValueForDisabledFeature(
                featureName = FeatureNames.ANDROID_RESOURCES,
                apiName = "pseudoLocalesEnabled",
                value = internalServices.newPropertyBackingDeprecatedApi(
                    Boolean::class.java,
                    dslInfo.isPseudoLocalesEnabled
                )
            )
    }

    override val experimentalProperties: MapProperty<String, Any> =
            internalServices.mapPropertyOf(
                String::class.java,
                Any::class.java,
                dslInfo.experimentalProperties
            )

    override val externalNativeExperimentalProperties: Map<String, Any>
        get() = dslInfo.externalNativeExperimentalProperties

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

    override val ignoredLibraryKeepRules: Provider<Set<String>> =
            internalServices.setPropertyOf(
                String::class.java,
                dslInfo.ignoredLibraryKeepRules
            )

    override val ignoreAllLibraryKeepRules: Boolean = dslInfo.ignoreAllLibraryKeepRules

    override val defaultGlslcArgs: List<String>
        get() = dslInfo.defaultGlslcArgs
    override val scopedGlslcArgs: Map<String, List<String>>
        get() = dslInfo.scopedGlslcArgs

    override val ndkConfig: MergedNdkConfig
        get() = dslInfo.ndkConfig
    override val isJniDebuggable: Boolean
        get() = dslInfo.isJniDebuggable
    override val supportedAbis: Set<String>
        get() = dslInfo.supportedAbis

    override val postProcessingFeatures: PostprocessingFeatures?
        get() = dslInfo.postProcessingOptions.getPostprocessingFeatures()
    override val consumerProguardFiles: List<File> by lazy(LazyThreadSafetyMode.NONE) {
        immutableListBuilder<File> {
            addAll(dslInfo.gatherProguardFiles(ProguardFileType.CONSUMER))
            // We include proguardFiles if we're in a dynamic-feature module.
            if (dslInfo.componentType.isDynamicFeature) {
                addAll(dslInfo.gatherProguardFiles(ProguardFileType.EXPLICIT))
            }
        }
    }
    override val manifestPlaceholders: MapProperty<String, String>
        get() = manifestPlaceholdersCreationConfig.placeholders
}
