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

package com.android.build.api.component.impl

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.impl.features.AndroidResourcesCreationConfigImpl
import com.android.build.api.component.impl.features.DexingImpl
import com.android.build.api.component.impl.features.ManifestPlaceholdersCreationConfigImpl
import com.android.build.api.component.impl.features.OptimizationCreationConfigImpl
import com.android.build.api.dsl.KotlinMultiplatformAndroidCompilation
import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidTest
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.ApkPackaging
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.Renderscript
import com.android.build.api.variant.ResValue
import com.android.build.api.variant.impl.AndroidResourcesImpl
import com.android.build.api.variant.impl.ApkPackagingImpl
import com.android.build.api.variant.impl.KmpVariantImpl
import com.android.build.api.variant.impl.ResValueKeyImpl
import com.android.build.api.variant.impl.SigningConfigImpl
import com.android.build.api.variant.impl.initializeAaptOptionsFromDsl
import com.android.build.gradle.internal.component.AndroidTestCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.component.features.AndroidResourcesCreationConfig
import com.android.build.gradle.internal.component.features.DexingCreationConfig
import com.android.build.gradle.internal.component.features.FeatureNames
import com.android.build.gradle.internal.component.features.ManifestPlaceholdersCreationConfig
import com.android.build.gradle.internal.component.features.NativeBuildCreationConfig
import com.android.build.gradle.internal.component.features.OptimizationCreationConfig
import com.android.build.gradle.internal.component.features.RenderscriptCreationConfig
import com.android.build.gradle.internal.component.features.ShadersCreationConfig
import com.android.build.gradle.internal.core.dsl.impl.KmpAndroidTestDslInfoImpl
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.ModulePropertyKey
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.variant.VariantPathHelper
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.File
import java.io.Serializable
import javax.inject.Inject

open class KmpAndroidTestImpl @Inject constructor(
    dslInfo: KmpAndroidTestDslInfoImpl,
    internalServices: VariantServices,
    buildFeatures: BuildFeatureValues,
    variantDependencies: VariantDependencies,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    taskContainer: MutableTaskContainer,
    services: TaskCreationServices,
    global: GlobalTaskCreationConfig,
    androidKotlinCompilation: KotlinMultiplatformAndroidCompilation,
    override val mainVariant: KmpVariantImpl,
    manifestFile: File
): KmpComponentImpl<KmpAndroidTestDslInfoImpl>(
    dslInfo,
    internalServices,
    buildFeatures,
    variantDependencies,
    paths,
    artifacts,
    taskContainer,
    services,
    global,
    androidKotlinCompilation,
    manifestFile
), AndroidTestCreationConfig, AndroidTest {

    override val testOnlyApk: Boolean
        get() = true

    override val debuggable: Boolean
        get() = true

    override fun <T> onTestedVariant(action: (VariantCreationConfig) -> T): T {
        return action.invoke(mainVariant)
    }

    override val targetSdkVersion: AndroidVersion
        get() = targetSdk
    override val targetSdk: AndroidVersion
        get() = global.androidTestOptions.targetSdkVersion ?: minSdk
    override val targetSdkOverride: AndroidVersion?
        get() = null
    override val testedApplicationId: Provider<String>
        get() = applicationId
    override val instrumentationRunner: Property<String> by lazy {
        internalServices.propertyOf(
            String::class.java,
            dslInfo.getInstrumentationRunner(dexing.dexingType)
        )
    }

    override val isAndroidTestCoverageEnabled: Boolean
        get() = dslInfo.isAndroidTestCoverageEnabled
    override val useJacocoTransformInstrumentation: Boolean
        get() = dslInfo.isAndroidTestCoverageEnabled
    override val packageJacocoRuntime: Boolean
        get() = dslInfo.isAndroidTestCoverageEnabled

    override val applicationId: Property<String>
        get() = dslInfo.applicationId
    override val instrumentationRunnerArguments: MapProperty<String, String> by lazy(LazyThreadSafetyMode.NONE) {
        internalServices.mapPropertyOf(
            String::class.java,
            String::class.java,
            dslInfo.instrumentationRunnerArguments)
    }
    override val handleProfiling: Property<Boolean> =
        internalServices.propertyOf(Boolean::class.java, dslInfo.handleProfiling)
    override val functionalTest: Property<Boolean> =
        internalServices.propertyOf(Boolean::class.java, dslInfo.functionalTest)
    override val testLabel: Property<String?> =
        internalServices.nullablePropertyOf(String::class.java, dslInfo.testLabel)

    // Even if android resources is not enabled, we still need to merge and link external resources
    // to create the test apk.
    override val androidResourcesCreationConfig: AndroidResourcesCreationConfig by lazy(LazyThreadSafetyMode.NONE) {
        AndroidResourcesCreationConfigImpl(
            this,
            dslInfo,
            dslInfo.androidResourcesDsl,
            internalServices
        )
    }

    override val dexing: DexingCreationConfig by lazy(LazyThreadSafetyMode.NONE) {
        DexingImpl(
            this,
            // TODO : Change this to VariantBuilder ?
            dslInfo.dexingDslInfo.isMultiDexEnabled,
            dslInfo.dexingDslInfo.multiDexKeepProguard,
            dslInfo.dexingDslInfo.multiDexKeepFile,
            internalServices,
        )
    }

    override val isCoreLibraryDesugaringEnabledLintCheck: Boolean
        get() = dexing.isCoreLibraryDesugaringEnabled

    override val signingConfig: SigningConfigImpl? by lazy {
        SigningConfigImpl(
            dslInfo.signingConfigResolver?.resolveConfig(profileable = false, debuggable = false),
            internalServices,
            minSdk.apiLevel,
            global.targetDeployApiFromIDE
        )
    }

    override val optimizationCreationConfig: OptimizationCreationConfig by lazy(LazyThreadSafetyMode.NONE) {
        OptimizationCreationConfigImpl(
            this,
            dslInfo.optimizationDslInfo,
            null,
            null,
            internalServices
        )
    }

    override val manifestPlaceholdersCreationConfig: ManifestPlaceholdersCreationConfig by lazy(LazyThreadSafetyMode.NONE) {
        ManifestPlaceholdersCreationConfigImpl(
            // no dsl for this
            emptyMap(),
            internalServices
        )
    }

    override fun finalizeAndLock() {
        dexing.finalizeAndLock()
    }

    override val packaging: ApkPackaging by lazy {
        ApkPackagingImpl(
            dslInfo.mainVariantDslInfo.packaging,
            internalServices,
            minSdk.apiLevel
        )
    }

    override val buildConfigFields: MapProperty<String, out BuildConfigField<out Serializable>> by lazy {
        warnAboutAccessingVariantApiValueForDisabledFeature(
            featureName = FeatureNames.BUILD_CONFIG,
            apiName = "buildConfigFields",
            value = internalServices.mapPropertyOf(
                String::class.java,
                BuildConfigField::class.java,
                emptyMap()
            )
        )
    }
    override val manifestPlaceholders: MapProperty<String, String>
        get() = manifestPlaceholdersCreationConfig.placeholders
    override val proguardFiles: ListProperty<RegularFile>
        get() = optimizationCreationConfig.proguardFiles

    override val androidResources: AndroidResourcesImpl =
        initializeAaptOptionsFromDsl(dslInfo.androidResourcesDsl.androidResources, buildFeatures, internalServices)

    override fun makeResValueKey(type: String, name: String): ResValue.Key =
        ResValueKeyImpl(type, name)

    override val resValues: MapProperty<ResValue.Key, ResValue> by lazy {
        resValuesCreationConfig?.resValues
            ?: warnAboutAccessingVariantApiValueForDisabledFeature(
                featureName = FeatureNames.RES_VALUES,
                apiName = "resValues",
                value = internalServices.mapPropertyOf(
                    ResValue.Key::class.java,
                    ResValue::class.java,
                    dslInfo.androidResourcesDsl.getResValues()
                )
            )
    }
    override val pseudoLocalesEnabled: Property<Boolean> by lazy {
        androidResourcesCreationConfig.pseudoLocalesEnabled
    }

    override fun <ParamT : InstrumentationParameters> transformClassesWith(
        classVisitorFactoryImplClass: Class<out AsmClassVisitorFactory<ParamT>>,
        scope: InstrumentationScope,
        instrumentationParamsConfig: (ParamT) -> Unit
    ) {
        instrumentation.transformClassesWith(
            classVisitorFactoryImplClass,
            scope,
            instrumentationParamsConfig
        )
    }

    override fun setAsmFramesComputationMode(mode: FramesComputationMode) {
        instrumentation.setAsmFramesComputationMode(mode)
    }

    override val isForceAotCompilation: Boolean
        get() = mainVariant.experimentalProperties.map {
            ModulePropertyKey.BooleanWithDefault.FORCE_AOT_COMPILATION.getValue(it)
        }.getOrElse(false)

    // unsupported features
    override val shouldPackageProfilerDependencies: Boolean = false
    override val embedsMicroApp: Boolean = false
    override val advancedProfilingTransforms: List<String> = emptyList()
    override val renderscript: Renderscript? = null
    override val renderscriptCreationConfig: RenderscriptCreationConfig? = null
    override val shadersCreationConfig: ShadersCreationConfig? = null
    override val nativeBuildCreationConfig: NativeBuildCreationConfig? = null
    override val enableApiModeling: Boolean = false
    override val enableGlobalSynthetics: Boolean = false
}
