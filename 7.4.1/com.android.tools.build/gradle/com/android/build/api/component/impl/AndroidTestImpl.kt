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

import com.android.build.api.artifact.MultipleArtifact
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.analytics.AnalyticsEnabledAndroidTest
import com.android.build.api.component.impl.features.AndroidResourcesCreationConfigImpl
import com.android.build.api.component.impl.features.BuildConfigCreationConfigImpl
import com.android.build.api.component.impl.features.ManifestPlaceholdersCreationConfigImpl
import com.android.build.api.component.impl.features.RenderscriptCreationConfigImpl
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar
import com.android.build.api.variant.AndroidResources
import com.android.build.api.variant.AndroidTest
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.ApkPackaging
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.Component
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.Renderscript
import com.android.build.api.variant.ResValue
import com.android.build.api.variant.SigningConfig
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.api.variant.impl.ApkPackagingImpl
import com.android.build.api.variant.impl.ResValueKeyImpl
import com.android.build.api.variant.impl.SigningConfigImpl
import com.android.build.gradle.internal.PostprocessingFeatures
import com.android.build.gradle.internal.ProguardFileType
import com.android.build.gradle.internal.component.AndroidTestCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.component.features.AndroidResourcesCreationConfig
import com.android.build.gradle.internal.component.features.BuildConfigCreationConfig
import com.android.build.gradle.internal.component.features.FeatureNames
import com.android.build.gradle.internal.component.features.ManifestPlaceholdersCreationConfig
import com.android.build.gradle.internal.component.features.RenderscriptCreationConfig
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.core.dsl.AndroidTestComponentDslInfo
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.Java8LangSupport
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.build.gradle.options.IntegerOption
import com.android.builder.dexing.DexingType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import java.io.Serializable
import javax.inject.Inject

open class AndroidTestImpl @Inject constructor(
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
    transformManager: TransformManager,
    variantServices: VariantServices,
    taskCreationServices: TaskCreationServices,
    global: GlobalTaskCreationConfig,
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
    transformManager,
    variantServices,
    taskCreationServices,
    global,
), AndroidTest, AndroidTestCreationConfig {

    init {
        dslInfo.multiDexKeepProguard?.let {
            artifacts.getArtifactContainer(MultipleArtifact.MULTIDEX_KEEP_PROGUARD)
                .addInitialProvider(null, internalServices.toRegularFileProvider(it))
        }
    }

    private val delegate by lazy {
        AndroidTestCreationConfigImpl(
            this, dslInfo
        )
    }

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------

    override val debuggable: Boolean
        get() = dslInfo.isDebuggable

    override val namespaceForR: Provider<String> = dslInfo.namespaceForR

    override val minSdkVersion: AndroidVersion
        get() = mainVariant.minSdkVersion

    override val targetSdkVersion: AndroidVersion
        get() = mainVariant.targetSdkVersion

    override val applicationId: Property<String> = internalServices.propertyOf(
        String::class.java,
        dslInfo.applicationId
    )

    override val androidResources: AndroidResources by lazy {
        getAndroidResources()
    }

    override val pseudoLocalesEnabled: Property<Boolean> by lazy {
        androidResourcesCreationConfig.pseudoLocalesEnabled
    }

    override val packaging: ApkPackaging by lazy {
        ApkPackagingImpl(
            dslInfo.testedVariantDslInfo.packaging,
            variantServices,
            minSdkVersion.apiLevel
        )
    }

    override val minifiedEnabled: Boolean
        get() {
            return when {
                mainVariant.componentType.isAar -> false
                !dslInfo.postProcessingOptions.hasPostProcessingConfiguration() ->
                    mainVariant.minifiedEnabled
                else -> dslInfo.postProcessingOptions.codeShrinkerEnabled()
            }
        }

    override val resourcesShrink: Boolean
        get() {
            return when {
                mainVariant.componentType.isAar -> false
                !dslInfo.postProcessingOptions.hasPostProcessingConfiguration() ->
                    mainVariant.resourcesShrink
                else -> dslInfo.postProcessingOptions.resourcesShrinkingEnabled()
            }
        }

    override val instrumentationRunner: Property<String> =
        internalServices.propertyOf(
            String::class.java,
            dslInfo.getInstrumentationRunner(dexingType)
        )

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
                    dslInfo.getBuildConfigFields()
                )
            )
    }

    override val signingConfig: SigningConfig?
        get() = signingConfigImpl

    override val signingConfigImpl: SigningConfigImpl? by lazy {
        dslInfo.signingConfig?.let {
            SigningConfigImpl(
                it,
                variantServices,
                minSdkVersion.apiLevel,
                services.projectOptions.get(IntegerOption.IDE_TARGET_DEVICE_API)
            )
        }
    }

    override val renderscript: Renderscript? by lazy {
        renderscriptCreationConfig?.renderscript
    }

    override val proguardFiles: ListProperty<RegularFile> by lazy {
        variantServices.listPropertyOf(RegularFile::class.java) {
            val projectDir = services.projectInfo.projectDirectory
            it.addAll(
                dslInfo.gatherProguardFiles(ProguardFileType.TEST).map { file ->
                    projectDir.file(file.absolutePath)
                }
            )
        }
    }

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
                    dslInfo.getResValues()
                )
            )
    }

    override val manifestPlaceholders: MapProperty<String, String>
        get() = manifestPlaceholdersCreationConfig.placeholders

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    // Even if android resources is disabled in a library project, we still need to merge and link
    // external resources to create the test apk.
    override val androidResourcesCreationConfig: AndroidResourcesCreationConfig by lazy(LazyThreadSafetyMode.NONE) {
        AndroidResourcesCreationConfigImpl(
            this,
            dslInfo,
            internalServices,
        )
    }

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
                renderscriptTargetApi = mainVariant.renderscriptCreationConfig!!.renderscriptTargetApi
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

    override val targetSdkVersionOverride: AndroidVersion?
        get() = mainVariant.targetSdkVersionOverride

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

    override val instrumentationRunnerArguments: Map<String, String>
        get() = dslInfo.instrumentationRunnerArguments

    override val isTestCoverageEnabled: Boolean
        get() = dslInfo.isAndroidTestCoverageEnabled

    /**
     * Package desugar_lib DEX for base feature androidTest only if the base packages shrunk
     * desugar_lib. This should be fixed properly by analyzing the test code when generating L8
     * keep rules, and thus packaging desugar_lib dex in the tested APK only which contains
     * necessary classes used both in test and tested APKs.
     */
    override val shouldPackageDesugarLibDex: Boolean
        get() = when {
            !isCoreLibraryDesugaringEnabled -> false
            mainVariant.componentType.isAar -> true
            else -> mainVariant.componentType.isBaseModule && needsShrinkDesugarLibrary
        }

    override val minSdkVersionForDexing: AndroidVersion =
        mainVariant.minSdkVersionForDexing

    override val isMultiDexEnabled: Boolean =
        mainVariant.isMultiDexEnabled

    override val needsShrinkDesugarLibrary: Boolean
        get() = delegate.needsShrinkDesugarLibrary

    override val isCoreLibraryDesugaringEnabled: Boolean
        get() = delegate.isCoreLibraryDesugaringEnabled

    override val dexingType: DexingType
        get() = delegate.dexingType

    override val needsMainDexListForBundle: Boolean
        get() = false

    override fun <T : Component> createUserVisibleVariantObject(
        projectServices: ProjectServices,
        operationsRegistrar: VariantApiOperationsRegistrar<out CommonExtension<*, *, *, *>, out VariantBuilder, out Variant>,
        stats: GradleBuildVariant.Builder?
    ): T =
        if (stats == null) {
            this as T
        } else {
            projectServices.objectFactory.newInstance(
                AnalyticsEnabledAndroidTest::class.java,
                this,
                stats
            ) as T
        }

    override val shouldPackageProfilerDependencies: Boolean = false

    override val advancedProfilingTransforms: List<String> = emptyList()

    override val needsMergedJavaResStream: Boolean =
        delegate.getNeedsMergedJavaResStream()

    override fun getJava8LangSupportType(): Java8LangSupport = delegate.getJava8LangSupportType()

    override val defaultGlslcArgs: List<String>
        get() = dslInfo.defaultGlslcArgs
    override val scopedGlslcArgs: Map<String, List<String>>
        get() = dslInfo.scopedGlslcArgs

    override val ignoredLibraryKeepRules: SetProperty<String>
        get() = internalServices.setPropertyOf(
            String::class.java,
            dslInfo.ignoredLibraryKeepRules
        )

    override val ignoreAllLibraryKeepRules: Boolean
        get() = dslInfo.ignoreAllLibraryKeepRules

    // Only instrument library androidTests. In app modules, the main classes are instrumented.
    override val useJacocoTransformInstrumentation: Boolean
        get() = isTestCoverageEnabled && mainVariant.componentType.isAar

    override val postProcessingFeatures: PostprocessingFeatures?
        get() = dslInfo.postProcessingOptions.getPostprocessingFeatures()

    // ---------------------------------------------------------------------------------------------
    // DO NOT USE, Deprecated DSL APIs.
    // ---------------------------------------------------------------------------------------------

    override val multiDexKeepFile = dslInfo.multiDexKeepFile
}

