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

import com.android.build.api.artifact.MultipleArtifact
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.analytics.AnalyticsEnabledApplicationVariant
import com.android.build.api.component.impl.AndroidTestImpl
import com.android.build.api.component.impl.ApkCreationConfigImpl
import com.android.build.api.component.impl.TestFixturesImpl
import com.android.build.api.component.impl.getAndroidResources
import com.android.build.api.component.impl.isTestApk
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar
import com.android.build.api.variant.AndroidResources
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.ApkPackaging
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.Component
import com.android.build.api.variant.DependenciesInfo
import com.android.build.api.variant.DependenciesInfoBuilder
import com.android.build.api.variant.Renderscript
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.core.dsl.ApplicationVariantDslInfo
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.NdkOptions
import com.android.build.gradle.internal.dsl.NdkOptions.DebugSymbolLevel
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.publishing.VariantPublishingInfo
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
import com.android.build.gradle.options.StringOption
import com.android.builder.dexing.DexingType
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
    transformManager: TransformManager,
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
    transformManager,
    internalServices,
    taskCreationServices,
    globalTaskCreationConfig
), ApplicationVariant, ApplicationCreationConfig, HasAndroidTest, HasTestFixtures {

    val delegate by lazy { ApkCreationConfigImpl(
        this,
        dslInfo) }

    init {
        dslInfo.multiDexKeepProguard?.let {
            artifacts.getArtifactContainer(MultipleArtifact.MULTIDEX_KEEP_PROGUARD)
                .addInitialProvider(null, internalServices.toRegularFileProvider(it))
        }
    }

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
            minSdkVersion.apiLevel,
            internalServices.projectOptions.get(IntegerOption.IDE_TARGET_DEVICE_API)
        )
    }

    override val packaging: ApkPackaging by lazy {
        ApkPackagingImpl(
            dslInfo.packaging,
            internalServices,
            minSdkVersion.apiLevel
        )
    }

    override val publishInfo: VariantPublishingInfo?
        get() = dslInfo.publishInfo

    override val minifiedEnabled: Boolean
        get() = variantBuilder.isMinifyEnabled
    override val resourcesShrink: Boolean
        get() = variantBuilder.shrinkResources

    override var androidTest: AndroidTestImpl? = null

    override var testFixtures: TestFixturesImpl? = null

    override val renderscript: Renderscript? by lazy {
        renderscriptCreationConfig?.renderscript
    }

    override val isMinifyEnabled: Boolean
        get() = variantBuilder.isMinifyEnabled

    override val shrinkResources: Boolean
        get() = variantBuilder.shrinkResources

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    override val testOnlyApk: Boolean
        get() = isTestApk()

    override val needAssetPackTasks: Boolean
        get() = global.assetPacks.isNotEmpty()

    override val shouldPackageDesugarLibDex: Boolean
        get() = delegate.isCoreLibraryDesugaringEnabled
    override val debuggable: Boolean
        get() = delegate.isDebuggable
    override val profileable: Boolean
        get() = dslInfo.isProfileable
    override val isCoreLibraryDesugaringEnabled: Boolean
        get() = delegate.isCoreLibraryDesugaringEnabled

    override val shouldPackageProfilerDependencies: Boolean
        get() = advancedProfilingTransforms.isNotEmpty()

    override val advancedProfilingTransforms: List<String>
        get() {
            return services.projectOptions[StringOption.IDE_ANDROID_CUSTOM_CLASS_TRANSFORMS]?.split(
                ","
            ) ?: emptyList()
        }

    override val nativeDebugSymbolLevel: DebugSymbolLevel
        get() {
            val debugSymbolLevelOrNull =
                NdkOptions.DEBUG_SYMBOL_LEVEL_CONVERTER.convert(
                    dslInfo.ndkConfig.debugSymbolLevel
                )
            return debugSymbolLevelOrNull ?: if (debuggable) DebugSymbolLevel.NONE else DebugSymbolLevel.SYMBOL_TABLE
        }

    // ---------------------------------------------------------------------------------------------
    // DO NOT USE, Deprecated DSL APIs.
    // ---------------------------------------------------------------------------------------------

    override val multiDexKeepFile = dslInfo.multiDexKeepFile

    // ---------------------------------------------------------------------------------------------
    // Private stuff
    // ---------------------------------------------------------------------------------------------

    override val consumesFeatureJars: Boolean =
        minifiedEnabled && global.hasDynamicFeatures

    override fun createVersionNameProperty(): Property<String?> =
        internalServices.newNullablePropertyBackingDeprecatedApi(
            String::class.java,
            dslInfo.versionName,
        )

    override fun createVersionCodeProperty() : Property<Int?> =
        internalServices.newNullablePropertyBackingDeprecatedApi(
            Int::class.java,
            dslInfo.versionCode,
        )

    override val dexingType: DexingType
        get() = delegate.dexingType

    override fun <T : Component> createUserVisibleVariantObject(
            projectServices: ProjectServices,
            operationsRegistrar: VariantApiOperationsRegistrar<out CommonExtension<*, *, *, *>, out VariantBuilder, out Variant>,
            stats: GradleBuildVariant.Builder?
    ): T =
        if (stats == null) {
            this as T
        } else {
            projectServices.objectFactory.newInstance(
                AnalyticsEnabledApplicationVariant::class.java,
                this,
                stats
            ) as T
        }

    override val minSdkVersionForDexing: AndroidVersion
        get() = delegate.minSdkVersionForDexing

    override val needsMergedJavaResStream: Boolean = delegate.getNeedsMergedJavaResStream()

    override fun getJava8LangSupportType(): Java8LangSupport = delegate.getJava8LangSupportType()

    override val needsShrinkDesugarLibrary: Boolean
        get() = delegate.needsShrinkDesugarLibrary

    // Apps include the jacoco agent if test coverage is enabled
    override val packageJacocoRuntime: Boolean
        get() = dslInfo.isAndroidTestCoverageEnabled

    override val bundleConfig: BundleConfigImpl = BundleConfigImpl(
        CodeTransparencyImpl(
            global.bundleOptions.codeTransparency.signing,
        ),
        internalServices,
    )

    override val useJacocoTransformInstrumentation: Boolean
        get() = isAndroidTestCoverageEnabled

    override val isWearAppUnbundled: Boolean?
        get() = dslInfo.isWearAppUnbundled
}
