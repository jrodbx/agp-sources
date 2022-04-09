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
import com.android.build.api.component.impl.AndroidTestImpl
import com.android.build.api.component.impl.TestFixturesImpl
import com.android.build.api.component.impl.features.DexingCreationConfigImpl
import com.android.build.api.component.impl.getAndroidResources
import com.android.build.api.component.impl.isTestApk
import com.android.build.api.variant.AndroidResources
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.ApkPackaging
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.Component
import com.android.build.api.variant.DependenciesInfo
import com.android.build.api.variant.DependenciesInfoBuilder
import com.android.build.api.variant.Renderscript
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.features.DexingCreationConfig
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.core.dsl.ApplicationVariantDslInfo
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.publishing.VariantPublishingInfo
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.StringOption
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
), ApplicationVariant, ApplicationCreationConfig, HasAndroidTest, HasTestFixtures {

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------

    override val applicationId: Property<String> = dslInfo.applicationId

    override val embedsMicroApp: Boolean
        get() = dslInfo.isEmbedMicroApp

    override val dependenciesInfo: DependenciesInfo by lazy {
        DependenciesInfoImpl(
                dependenciesInfoBuilder.includedInApk,
                dependenciesInfoBuilder.includedInBundle
        )
    }

    override val androidResources: AndroidResources by lazy {
        getAndroidResources()
    }

    override val signingConfigImpl: SigningConfigImpl? by lazy {
        signingConfig
    }

    override val signingConfig: SigningConfigImpl by lazy {
        SigningConfigImpl(
            dslInfo.signingConfig,
            internalServices,
            minSdk.apiLevel,
            internalServices.projectOptions.get(IntegerOption.IDE_TARGET_DEVICE_API)
        )
    }

    override val packaging: ApkPackaging by lazy {
        ApkPackagingImpl(
            dslInfo.packaging,
            internalServices,
            minSdk.apiLevel
        )
    }

    override val publishInfo: VariantPublishingInfo?
        get() = dslInfo.publishInfo

    override var androidTest: AndroidTestImpl? = null

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

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    override val dexingCreationConfig: DexingCreationConfig by lazy(LazyThreadSafetyMode.NONE) {
        DexingCreationConfigImpl(
            this,
            dslInfo.dexingDslInfo,
            internalServices
        )
    }

    override val testOnlyApk: Boolean
        get() = isTestApk()

    override val needAssetPackTasks: Boolean
        get() = global.assetPacks.isNotEmpty()

    override val debuggable: Boolean
        get() = dslInfo.isDebuggable
    override val profileable: Boolean
        get() = dslInfo.isProfileable

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
        get() = VariantOutputList(variantOutputs.toList())

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
                        paths.getOutputFileName(it, variantOutputConfiguration.baseName(this))
                    },
                )
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

    override val generateLocaleConfig: Boolean
        get() = dslInfo.generateLocaleConfig
}
