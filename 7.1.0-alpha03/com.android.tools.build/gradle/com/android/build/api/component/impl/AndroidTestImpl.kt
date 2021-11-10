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
import com.android.build.api.component.AndroidTest
import com.android.build.api.component.Component
import com.android.build.api.component.ComponentIdentity
import com.android.build.api.component.analytics.AnalyticsEnabledAndroidTest
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar
import com.android.build.api.variant.*
import com.android.build.api.variant.impl.*
import com.android.build.api.variant.impl.initializeAaptOptionsFromDsl
import com.android.build.gradle.internal.ProguardFileType
import com.android.build.gradle.internal.component.AndroidTestCreationConfig
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantPropertiesApiServices
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.build.gradle.options.IntegerOption
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import com.android.builder.dexing.DexingType
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.Serializable
import javax.inject.Inject

open class AndroidTestImpl @Inject constructor(
    componentIdentity: ComponentIdentity,
    buildFeatureValues: BuildFeatureValues,
    variantDslInfo: VariantDslInfo<*>,
    variantDependencies: VariantDependencies,
    variantSources: VariantSources,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    variantScope: VariantScope,
    variantData: BaseVariantData,
    testedVariant: VariantImpl,
    transformManager: TransformManager,
    variantPropertiesApiServices: VariantPropertiesApiServices,
    taskCreationServices: TaskCreationServices,
    globalScope: GlobalScope
) : TestComponentImpl(
    componentIdentity,
    buildFeatureValues,
    variantDslInfo,
    variantDependencies,
    variantSources,
    paths,
    artifacts,
    variantScope,
    variantData,
    testedVariant,
    transformManager,
    variantPropertiesApiServices,
    taskCreationServices,
    globalScope
), AndroidTest, AndroidTestCreationConfig {

    init {
        variantDslInfo.multiDexKeepProguard?.let {
            artifacts.getArtifactContainer(MultipleArtifact.MULTIDEX_KEEP_PROGUARD)
                    .addInitialProvider(
                            taskCreationServices.regularFile(internalServices.provider { it })
                    )
        }
    }

    private val delegate by lazy { AndroidTestCreationConfigImpl(
        this,
        internalServices.projectOptions,
        globalScope,
        variantDslInfo) }

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------

    override val debuggable: Boolean
        get() = variantDslInfo.isDebuggable

    override val minSdkVersion: AndroidVersion
        get() = testedVariant.minSdkVersion

    override val maxSdkVersion: Int?
        get() = testedVariant.maxSdkVersion

    override val targetSdkVersion: AndroidVersion
        get() = testedVariant.targetSdkVersion

    override val applicationId: Property<String> = internalServices.propertyOf(
        String::class.java,
        variantDslInfo.applicationId
    )

    override val manifestPlaceholders: MapProperty<String, String> by lazy {
        internalServices.mapPropertyOf(
            String::class.java,
            String::class.java,
            variantDslInfo.manifestPlaceholders
        )
    }

    override val androidResources: AndroidResources by lazy {
        initializeAaptOptionsFromDsl(
                taskCreationServices.projectInfo.getExtension().aaptOptions,
                variantPropertiesApiServices
        )
    }

    override val packaging: ApkPackaging by lazy {
        ApkPackagingImpl(
            globalScope.extension.packagingOptions,
            variantPropertiesApiServices,
            minSdkVersion.apiLevel
        )
    }

    override val minifiedEnabled: Boolean
        get() {
            return when {
                testedConfig.variantType.isAar -> false
                else -> variantDslInfo.getPostProcessingOptions().codeShrinkerEnabled()
            }
        }

    override val instrumentationRunner: Property<String> =
        internalServices.propertyOf(
            String::class.java,
            variantDslInfo.getInstrumentationRunner(dexingType)
        )

    override val handleProfiling: Property<Boolean> =
        internalServices.propertyOf(Boolean::class.java, variantDslInfo.handleProfiling)

    override val functionalTest: Property<Boolean> =
        internalServices.propertyOf(Boolean::class.java, variantDslInfo.functionalTest)

    override val testLabel: Property<String?> =
        internalServices.nullablePropertyOf(String::class.java, variantDslInfo.testLabel)

    override val buildConfigFields: MapProperty<String, BuildConfigField<out Serializable>> by lazy {
        internalServices.mapPropertyOf(
            String::class.java,
            BuildConfigField::class.java,
            variantDslInfo.getBuildConfigFields()
        )
    }

    override val signingConfig: SigningConfigImpl? by lazy {
        variantDslInfo.signingConfig?.let {
            SigningConfigImpl(
                it,
                variantPropertiesApiServices,
                minSdkVersion.apiLevel,
                services.projectOptions.get(IntegerOption.IDE_TARGET_DEVICE_API)
            )
        }
    }

    override val renderscript: Renderscript? by lazy {
        delegate.renderscript(internalServices)
    }

    override val proguardFiles: ListProperty<RegularFile> by lazy {
        variantPropertiesApiServices.projectInfo.getProject().objects
            .listProperty(RegularFile::class.java).also {
                variantDslInfo.gatherProguardFiles(ProguardFileType.TEST, it)
                it.finalizeValueOnRead()
            }
    }

    override fun makeResValueKey(type: String, name: String): ResValue.Key =
            ResValueKeyImpl(type, name)

    override val resValues: MapProperty<ResValue.Key, ResValue> by lazy {
        internalServices.mapPropertyOf(
                ResValue.Key::class.java,
                ResValue::class.java,
                variantDslInfo.getResValues()
        )
    }

    override val experimentalProperties: MapProperty<String, Any> = internalServices.mapPropertyOf(
            String::class.java,
            Any::class.java,
            variantDslInfo.experimentalProperties)

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    // always false for this type
    override val embedsMicroApp: Boolean
        get() = false

    // always true for this kind
    override val testOnlyApk: Boolean
        get() = true

    override val testedApplicationId: Provider<String>
        get() = if (testedConfig.variantType.isAar) {
            // if the tested variant is an AAR, the test is self contained and therefore
            // testedAppID == appId
            applicationId
        } else {
            testedConfig.applicationId
        }

    override val instrumentationRunnerArguments: Map<String, String>
        get() = variantDslInfo.instrumentationRunnerArguments

    override val isTestCoverageEnabled: Boolean
        get() = variantDslInfo.isTestCoverageEnabled

    override val renderscriptTargetApi: Int
        get() = testedVariant.variantBuilder.renderscriptTargetApi

    /**
     * Package desugar_lib DEX for base feature androidTest only if the base packages shrunk
     * desugar_lib. This should be fixed properly by analyzing the test code when generating L8
     * keep rules, and thus packaging desugar_lib dex in the tested APK only which contains
     * necessary classes used both in test and tested APKs.
     */
    override val shouldPackageDesugarLibDex: Boolean
        get() = when {
            !isCoreLibraryDesugaringEnabled -> false
            testedConfig.variantType.isAar -> true
            else -> testedConfig.variantType.isBaseModule && needsShrinkDesugarLibrary
        }

    override val minSdkVersionWithTargetDeviceApi: AndroidVersion =
        testedVariant.minSdkVersionWithTargetDeviceApi

    override val isMultiDexEnabled: Boolean =
        testedVariant.isMultiDexEnabled

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
            operationsRegistrar: VariantApiOperationsRegistrar<out CommonExtension<*, *, *, *>,out VariantBuilder, out Variant>,
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

    override fun getNeedsMergedJavaResStream(): Boolean =
        delegate.getNeedsMergedJavaResStream()

    override fun getJava8LangSupportType(): VariantScope.Java8LangSupport = delegate.getJava8LangSupportType()

    override val dslSigningConfig: com.android.build.gradle.internal.dsl.SigningConfig? =
        variantDslInfo.signingConfig

    // ---------------------------------------------------------------------------------------------
    // DO NOT USE, Deprecated DSL APIs.
    // ---------------------------------------------------------------------------------------------

    override val multiDexKeepFile = variantDslInfo.multiDexKeepFile
}

