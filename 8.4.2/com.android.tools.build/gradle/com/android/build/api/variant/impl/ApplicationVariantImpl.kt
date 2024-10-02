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
import com.android.build.api.component.analytics.AnalyticsEnabledApplicationVariant
import com.android.build.api.component.impl.TestFixturesImpl
import com.android.build.api.component.impl.features.DexingImpl
import com.android.build.api.component.impl.isTestApk
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.Component
import com.android.build.api.variant.DependenciesInfo
import com.android.build.api.variant.DependenciesInfoBuilder
import com.android.build.api.variant.DeviceTest
import com.android.build.api.variant.Renderscript
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.features.DexingCreationConfig
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.core.dsl.ApplicationVariantDslInfo
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.ModulePropertyKey
import com.android.build.gradle.internal.profile.ProfilingMode
import com.android.build.gradle.internal.publishing.VariantPublishingInfo
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.build.gradle.options.StringOption
import com.android.builder.errors.IssueReporter
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.provider.Property
import javax.inject.Inject

open class ApplicationVariantImpl @Inject constructor(
    override val variantBuilder: ApplicationVariantBuilderImpl,
    buildFeatureValues: BuildFeatureValues,
    dslInfo: ApplicationVariantDslInfo,
    variantDependencies: VariantDependencies,
    variantSources: VariantSources,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    variantData: BaseVariantData,
    taskContainer: MutableTaskContainer,
    dependenciesInfoBuilder: DependenciesInfoBuilder,
    internalServices: VariantServices,
    taskCreationServices: TaskCreationServices,
    globalTaskCreationConfig: GlobalTaskCreationConfig
) : VariantImpl<ApplicationVariantDslInfo>(
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
    globalTaskCreationConfig
), ApplicationVariant, ApplicationCreationConfig, InternalHasDeviceTests, HasTestFixtures {

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------

    override val applicationId: Property<String> = dslInfo.applicationId

    override val embedsMicroApp: Boolean
        get() = dslInfo.isEmbedMicroApp

    override val dependenciesInfo: DependenciesInfo by lazy {
        DependenciesInfoImpl(
                dependenciesInfoBuilder.includeInApk,
                dependenciesInfoBuilder.includeInBundle
        )
    }

    override val androidResources: ApplicationAndroidResourcesImpl by lazy {
        ApplicationAndroidResourcesImpl(
            getAndroidResources(dslInfo.androidResourcesDsl.androidResources),
            buildFeatures,
            variantBuilder.androidResources.generateLocaleConfig,
        )
    }

    override val signingConfig: SigningConfigImpl by lazy {
        SigningConfigImpl(
            dslInfo.signingConfigResolver?.resolveConfig(profileable, debuggable),
            internalServices,
            minSdk.apiLevel,
            global.targetDeployApiFromIDE
        )
    }

    override val packaging: TestedApkPackagingImpl by lazy {
        TestedApkPackagingImpl(
            dslInfo.packaging,
            internalServices,
            minSdk.apiLevel
        )
    }

    override val publishInfo: VariantPublishingInfo?
        get() = dslInfo.publishInfo
    override val deviceTests = mutableListOf<DeviceTest>()

    override var testFixtures: TestFixturesImpl? = null

    override val renderscript: Renderscript? by lazy {
        renderscriptCreationConfig?.renderscript
    }
    override val targetSdk: AndroidVersion by lazy(LazyThreadSafetyMode.NONE) {
        variantBuilder.targetSdkVersion
    }

    override val isMinifyEnabled: Boolean
        get() = variantBuilder.isMinifyEnabled

    override val shrinkResources: Boolean
        get() = variantBuilder.shrinkResources


    override val targetSdkVersion: AndroidVersion
        get() = targetSdk

    override val targetSdkOverride: AndroidVersion?
        get() = variantBuilder.mutableTargetSdk?.sanitize()


    override val dexing: DexingCreationConfig by lazy(LazyThreadSafetyMode.NONE) {
        DexingImpl(
            this,
            variantBuilder._enableMultiDex,
            dslInfo.dexingDslInfo.multiDexKeepProguard,
            dslInfo.dexingDslInfo.multiDexKeepFile,
            internalServices,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    override fun finalizeAndLock() {
        super.finalizeAndLock()
        dexing.finalizeAndLock()
        checkProfileableWithCompileSdk()
    }

    private fun checkProfileableWithCompileSdk() {
        val minProfileableSdk = 29
        val _compileSdk = dslInfo.compileSdk ?: minProfileableSdk
        val fromProfilingModeOption = ProfilingMode.getProfilingModeType(
            services.projectOptions[StringOption.PROFILING_MODE]
        ).isProfileable
        if ((fromProfilingModeOption == true || profileable) &&
            _compileSdk < minProfileableSdk
        ) {
            services.issueReporter.reportError(
                IssueReporter.Type.COMPILE_SDK_VERSION_TOO_LOW,
                """'profileable' is enabled in variant '$name' with compile SDK less than API 29.
                        Recommended action: If possible, upgrade compileSdk from $_compileSdk to at least API 29."""
                    .trimIndent()
            )
        }
    }

    override val testOnlyApk: Boolean
        get() = isTestApk()

    override val needAssetPackTasks: Boolean
        get() = global.assetPacks.isNotEmpty()

    override val debuggable: Boolean
        get() = dslInfo.isDebuggable
    override val profileable: Boolean
        get() = variantBuilder._profileable

    override val shouldPackageProfilerDependencies: Boolean
        get() = advancedProfilingTransforms.isNotEmpty()

    override val advancedProfilingTransforms: List<String>
        get() {
            return services.projectOptions[StringOption.IDE_ANDROID_CUSTOM_CLASS_TRANSFORMS]?.split(
                ","
            ) ?: emptyList()
        }

    // ---------------------------------------------------------------------------------------------
    // Private stuff
    // ---------------------------------------------------------------------------------------------

    override val consumesFeatureJars: Boolean
        get() = optimizationCreationConfig.minifiedEnabled && global.hasDynamicFeatures

    private fun createVersionNameProperty(): Property<String?> =
        internalServices.newNullablePropertyBackingDeprecatedApi(
            String::class.java,
            dslInfo.versionName,
        )

    private fun createVersionCodeProperty() : Property<Int?> =
        internalServices.newNullablePropertyBackingDeprecatedApi(
            Int::class.java,
            dslInfo.versionCode,
        )

    private val variantOutputs = mutableListOf<VariantOutputImpl>()

    override val outputs: VariantOutputList
        get() = VariantOutputList(variantOutputs.toList(), paths.targetFilterConfigurations)

    override fun addVariantOutput(variantOutputConfiguration: VariantOutputConfiguration) {
        variantOutputs.add(
            VariantOutputImpl(
                createVersionCodeProperty(),
                createVersionNameProperty(),
                internalServices.newPropertyBackingDeprecatedApi(Boolean::class.java, true),
                variantOutputConfiguration,
                variantOutputConfiguration.baseName(this),
                variantOutputConfiguration.fullName(this),
                internalServices.newPropertyBackingDeprecatedApi(
                    String::class.java,
                    internalServices.projectInfo.getProjectBaseName().map {
                        paths.getOutputFileName(signingConfig.hasConfig(), it, variantOutputConfiguration.baseName(this))
                    },
                ),
            )
        )
    }

    override fun <T : Component> createUserVisibleVariantObject(
            stats: GradleBuildVariant.Builder?
    ): T =
        if (stats == null) {
            this as T
        } else {
            services.newInstance(
                AnalyticsEnabledApplicationVariant::class.java,
                this,
                stats
            ) as T
        }

    override val bundleConfig: BundleConfigImpl = BundleConfigImpl(
        CodeTransparencyImpl(
            global.bundleOptions.codeTransparency.signing,
        ),
        internalServices,
    )

    override val useJacocoTransformInstrumentation: Boolean
        get() = isAndroidTestCoverageEnabled

    // Apps include the jacoco agent if test coverage is enabled
    override val packageJacocoRuntime: Boolean
        get() = isAndroidTestCoverageEnabled

    override val isWearAppUnbundled: Boolean?
        get() = dslInfo.isWearAppUnbundled

    override val enableApiModeling: Boolean
        get() = isApiModelingEnabled()

    override val enableGlobalSynthetics: Boolean
        get() = isGlobalSyntheticsEnabled()

    override val includeVcsInfo: Boolean?
        get() = dslInfo.includeVcsInfo

    override val isForceAotCompilation: Boolean
        get() = experimentalProperties.map {
            ModulePropertyKey.BooleanWithDefault.FORCE_AOT_COMPILATION.getValue(it)
        }.getOrElse(false)
}
