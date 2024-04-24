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
import com.android.build.api.component.impl.features.BuildConfigCreationConfigImpl
import com.android.build.api.component.impl.features.DexingImpl
import com.android.build.api.component.impl.features.OptimizationCreationConfigImpl
import com.android.build.api.component.impl.features.RenderscriptCreationConfigImpl
import com.android.build.api.component.impl.features.ShadersCreationConfigImpl
import com.android.build.api.variant.AndroidTest
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.ApkPackaging
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.Renderscript
import com.android.build.api.variant.ResValue
import com.android.build.api.variant.impl.AndroidResourcesImpl
import com.android.build.api.variant.impl.ApkPackagingImpl
import com.android.build.api.variant.impl.DeviceTestBuilderImpl
import com.android.build.api.variant.impl.ResValueKeyImpl
import com.android.build.api.variant.impl.SigningConfigImpl
import com.android.build.gradle.internal.component.AndroidTestCreationConfig
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.LibraryCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.component.features.AndroidResourcesCreationConfig
import com.android.build.gradle.internal.component.features.BuildConfigCreationConfig
import com.android.build.gradle.internal.component.features.DexingCreationConfig
import com.android.build.gradle.internal.component.features.FeatureNames
import com.android.build.gradle.internal.component.features.ManifestPlaceholdersCreationConfig
import com.android.build.gradle.internal.component.features.NativeBuildCreationConfig
import com.android.build.gradle.internal.component.features.OptimizationCreationConfig
import com.android.build.gradle.internal.component.features.RenderscriptCreationConfig
import com.android.build.gradle.internal.component.features.ShadersCreationConfig
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.core.dsl.AndroidTestComponentDslInfo
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.ModulePropertyKey
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.builder.core.ComponentTypeImpl
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.Serializable
import javax.inject.Inject

open class DeviceTestImpl @Inject constructor(
    componentIdentity: ComponentIdentity,
    buildFeatureValues: BuildFeatureValues,
    dslInfo: AndroidTestComponentDslInfo,
    variantDependencies: VariantDependencies,
    variantSources: VariantSources,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    variantData: BaseVariantData,
    taskContainer: MutableTaskContainer,
    mainVariant: VariantCreationConfig,
    variantServices: VariantServices,
    taskCreationServices: TaskCreationServices,
    global: GlobalTaskCreationConfig,
    defaultDeviceTestBuilder: DeviceTestBuilderImpl,
) : TestComponentImpl<AndroidTestComponentDslInfo>(
    componentIdentity,
    buildFeatureValues,
    dslInfo,
    variantDependencies,
    variantSources,
    paths,
    artifacts,
    variantData,
    taskContainer,
    mainVariant,
    variantServices,
    taskCreationServices,
    global,
), AndroidTest, AndroidTestCreationConfig {

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------

    override val debuggable: Boolean
        get() = dslInfo.isDebuggable

    override val minSdk: AndroidVersion
        get() = mainVariant.minSdk

    override val targetSdk: AndroidVersion
        get() = global.androidTestOptions.targetSdkVersion ?: getMainTargetSdkVersion()

    override val targetSdkVersion: AndroidVersion
        get() = targetSdk

    override val targetSdkOverride: AndroidVersion?
        get() = when (mainVariant) {
            is ApkCreationConfig -> (mainVariant as ApkCreationConfig).targetSdkOverride
            is LibraryCreationConfig -> (mainVariant as LibraryCreationConfig).targetSdkOverride
            else -> null
        }

    override val applicationId: Property<String> = internalServices.propertyOf(
        String::class.java,
        dslInfo.applicationId
    )

    override val androidResources: AndroidResourcesImpl by lazy {
        getAndroidResources(dslInfo.androidResourcesDsl.androidResources)
    }

    override val pseudoLocalesEnabled: Property<Boolean> by lazy {
        androidResourcesCreationConfig.pseudoLocalesEnabled
    }

    override val packaging: ApkPackaging by lazy {
        ApkPackagingImpl(
            dslInfo.mainVariantDslInfo.packaging,
            variantServices,
            minSdk.apiLevel
        )
    }

    override val instrumentationRunner: Property<String> by lazy {
        internalServices.propertyOf(
            String::class.java,
            dslInfo.getInstrumentationRunner(dexing.dexingType)
        )
    }

    override val handleProfiling: Property<Boolean> =
        internalServices.propertyOf(Boolean::class.java, dslInfo.handleProfiling)

    override val functionalTest: Property<Boolean> =
        internalServices.propertyOf(Boolean::class.java, dslInfo.functionalTest)

    override val testLabel: Property<String?> =
        internalServices.nullablePropertyOf(String::class.java, dslInfo.testLabel)

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

    override val signingConfig: SigningConfigImpl? by lazy {
        dslInfo.signingConfigResolver?.let {
            SigningConfigImpl(
                it.resolveConfig(profileable = false, debuggable = debuggable),
                variantServices,
                minSdk.apiLevel,
                global.targetDeployApiFromIDE
            )
        }
    }

    override val renderscript: Renderscript? by lazy {
        renderscriptCreationConfig?.renderscript
    }

    override val proguardFiles: ListProperty<RegularFile>
        get() = optimizationCreationConfig.proguardFiles

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
                    dslInfo.androidResourcesDsl!!.getResValues()
                )
            )
    }

    override val manifestPlaceholders: MapProperty<String, String>
        get() = manifestPlaceholdersCreationConfig.placeholders

    override val dexing: DexingCreationConfig by lazy(LazyThreadSafetyMode.NONE) {
        DexingImpl(
            this,
            defaultDeviceTestBuilder.enableMultiDex,
            dslInfo.dexingDslInfo.multiDexKeepProguard,
            dslInfo.dexingDslInfo.multiDexKeepFile,
            internalServices,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    // Even if android resources is disabled in a library project, we still need to merge and link
    // external resources to create the test apk.
    override val androidResourcesCreationConfig: AndroidResourcesCreationConfig by lazy(LazyThreadSafetyMode.NONE) {
        AndroidResourcesCreationConfigImpl(
            this,
            dslInfo,
            dslInfo.androidResourcesDsl!!,
            internalServices,
        )
    }

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

    override val manifestPlaceholdersCreationConfig: ManifestPlaceholdersCreationConfig by lazy(LazyThreadSafetyMode.NONE) {
        createManifestPlaceholdersCreationConfig(
                dslInfo.manifestPlaceholdersDslInfo?.placeholders)
    }

    override val renderscriptCreationConfig: RenderscriptCreationConfig? by lazy(LazyThreadSafetyMode.NONE) {
        if (buildFeatures.renderScript) {
            RenderscriptCreationConfigImpl(
                dslInfo.renderscriptDslInfo!!,
                internalServices,
                renderscriptTargetApi = mainVariant.renderscriptCreationConfig!!.renderscriptTargetApi
            )
        } else {
            null
        }
    }

    override val isCoreLibraryDesugaringEnabledLintCheck: Boolean
        get() = dexing.isCoreLibraryDesugaringEnabled

    override val shadersCreationConfig: ShadersCreationConfig by lazy(LazyThreadSafetyMode.NONE) {
        ShadersCreationConfigImpl(
            dslInfo.shadersDslInfo!!
        )
    }

    final override val optimizationCreationConfig: OptimizationCreationConfig by lazy(LazyThreadSafetyMode.NONE) {
        OptimizationCreationConfigImpl(
            this,
            dslInfo.optimizationDslInfo,
            null,
            null,
            internalServices
        )
    }

    override val nativeBuildCreationConfig: NativeBuildCreationConfig?
        get() = mainVariant.nativeBuildCreationConfig

    // always false for this type
    override val embedsMicroApp: Boolean
        get() = false

    // always true for this kind
    override val testOnlyApk: Boolean
        get() = true

    override val testedApplicationId: Provider<String>
        get() = if (mainVariant.componentType.isAar) {
            // if the tested variant is an AAR, the test is self contained and therefore
            // testedAppID == appId
            applicationId
        } else {
            mainVariant.applicationId
        }

    override val instrumentationRunnerArguments: MapProperty<String, String> by lazy(LazyThreadSafetyMode.NONE) {
        internalServices.mapPropertyOf(
            String::class.java,
            String::class.java,
            dslInfo.instrumentationRunnerArguments
        )
    }

    override val shouldPackageProfilerDependencies: Boolean = false

    override val advancedProfilingTransforms: List<String> = emptyList()

    override val isAndroidTestCoverageEnabled: Boolean
        get() = dslInfo.isAndroidTestCoverageEnabled

    // Only instrument library androidTests. In app modules, the main classes are instrumented.
    override val useJacocoTransformInstrumentation: Boolean
        get() = dslInfo.isAndroidTestCoverageEnabled && mainVariant.componentType.isAar

    // Only include the jacoco agent if coverage is enabled in library test components
    // as in apps it will have already been included in the tested application.
    override val packageJacocoRuntime: Boolean
        get() = dslInfo.isAndroidTestCoverageEnabled && mainVariant.componentType.isAar

    override val enableApiModeling: Boolean
        get() = isApiModelingEnabled()

    override val enableGlobalSynthetics: Boolean
        get() = isGlobalSyntheticsEnabled()

    override fun finalizeAndLock() {
        super.finalizeAndLock()
        dexing.finalizeAndLock()
    }

    override val isForceAotCompilation: Boolean
        get() = mainVariant.experimentalProperties.map {
            ModulePropertyKey.BooleanWithDefault.FORCE_AOT_COMPILATION.getValue(it)
        }.getOrElse(false)

    /**
     * Returns the name used in the builder model for artifacts that correspond to this variant
     * type.
     */
    // So far this is hard coded to ANDROID_TEST's artifactName. Eventually, once more DeviceTest
    // can be added to the variant, we will need to find a unique name so the model builder
    // can sync all these device tests correctly.
    val artifactName = ComponentTypeImpl.ANDROID_TEST.artifactName
}

