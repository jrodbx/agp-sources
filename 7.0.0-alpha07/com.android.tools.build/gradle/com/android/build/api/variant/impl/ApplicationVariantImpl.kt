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
import com.android.build.api.component.AndroidTest
import com.android.build.api.component.Component
import com.android.build.api.component.analytics.AnalyticsEnabledApplicationVariant
import com.android.build.api.component.impl.ApkCreationConfigImpl
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar
import com.android.build.api.variant.*
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.NdkOptions
import com.android.build.gradle.internal.dsl.NdkOptions.DebugSymbolLevel
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
import com.android.builder.dexing.DexingType
import com.android.builder.model.CodeShrinker
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import com.android.build.gradle.options.StringOption
import org.gradle.api.provider.Property
import javax.inject.Inject

open class ApplicationVariantImpl @Inject constructor(
        override val variantBuilder: ApplicationVariantBuilderImpl,
        buildFeatureValues: BuildFeatureValues,
        variantDslInfo: VariantDslInfo,
        variantDependencies: VariantDependencies,
        variantSources: VariantSources,
        paths: VariantPathHelper,
        artifacts: ArtifactsImpl,
        variantScope: VariantScope,
        variantData: BaseVariantData,
        variantDependencyInfo: DependenciesInfo,
        transformManager: TransformManager,
        internalServices: VariantPropertiesApiServices,
        taskCreationServices: TaskCreationServices,
        globalScope: GlobalScope
) : VariantImpl(
    variantBuilder,
    buildFeatureValues,
    variantDslInfo,
    variantDependencies,
    variantSources,
    paths,
    artifacts,
    variantScope,
    variantData,
    transformManager,
    internalServices,
    taskCreationServices,
    globalScope
), ApplicationVariant, ApplicationCreationConfig, HasAndroidTestImpl {

    val delegate by lazy { ApkCreationConfigImpl(this, globalScope, variantDslInfo) }

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------

    override val applicationId: Property<String> = variantDslInfo.applicationId

    override val embedsMicroApp: Boolean
        get() = variantDslInfo.isEmbedMicroApp

    override val dependenciesInfo: DependenciesInfo = variantDependencyInfo

    override val aapt: Aapt by lazy {
        initializeAaptOptionsFromDsl(
            globalScope.extension.aaptOptions,
            internalServices
        )
    }

    override fun aaptOptions(action: Aapt.() -> Unit) {
        action.invoke(aapt)
    }

    override val signingConfig: SigningConfig by lazy {
        SigningConfigImpl(
            variantDslInfo.signingConfig,
            internalServices,
            minSdkVersion.apiLevel,
            globalScope.projectOptions.get(IntegerOption.IDE_TARGET_DEVICE_API)
        )
    }

    override fun signingConfig(action: SigningConfig.() -> Unit) {
        action.invoke(signingConfig)
    }

    override val packaging: ApkPackaging by lazy {
        ApkPackagingImpl(
            globalScope.extension.packagingOptions,
            internalServices,
            minSdkVersion.apiLevel
        )
    }

    override fun packaging(action: ApkPackaging.() -> Unit) {
        action.invoke(packaging)
    }

    override val minifiedEnabled: Boolean
        get() = variantDslInfo.isMinifyEnabled

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    override val testOnlyApk: Boolean
        get() = variantScope.isTestOnly(this)

    override val needAssetPackTasks: Property<Boolean> =
        internalServices.propertyOf(Boolean::class.java, false)

    override val shouldPackageDesugarLibDex: Boolean
        get() = delegate.isCoreLibraryDesugaringEnabled
    override val debuggable: Boolean
        get() = delegate.isDebuggable

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
                    variantDslInfo.ndkConfig.debugSymbolLevel
                )
            return debugSymbolLevelOrNull ?: if (debuggable) DebugSymbolLevel.NONE else DebugSymbolLevel.SYMBOL_TABLE
        }

    // ---------------------------------------------------------------------------------------------
    // Private stuff
    // ---------------------------------------------------------------------------------------------

    override fun createVersionNameProperty(): Property<String?> =
        internalServices.newNullablePropertyBackingDeprecatedApi(
            String::class.java,
            variantDslInfo.versionName,
            "$name::versionName"
        )

    override fun createVersionCodeProperty() : Property<Int?> =
        internalServices.newNullablePropertyBackingDeprecatedApi(
            Int::class.java,
            variantDslInfo.versionCode,
            "$name::versionCode"
        )

    override val renderscriptTargetApi: Int
        get() {
            return variantBuilder.renderscriptTargetApi
        }

    override val dexingType: DexingType
        get() = delegate.dexingType

    override val needsMainDexListForBundle: Boolean
        get() = (variantType.isBaseModule
                    && globalScope.hasDynamicFeatures()
                    && dexingType.needsMainDexList)

    override fun <T : Component> createUserVisibleVariantObject(
            projectServices: ProjectServices,
            operationsRegistrar: VariantApiOperationsRegistrar<VariantBuilder, Variant>,
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

    override val minSdkVersionWithTargetDeviceApi: AndroidVersion
        get() = delegate.minSdkVersionWithTargetDeviceApi

    override val codeShrinker: CodeShrinker?
        get() = delegate.getCodeShrinker()

    override fun getNeedsMergedJavaResStream(): Boolean = delegate.getNeedsMergedJavaResStream()

    override fun getJava8LangSupportType(): VariantScope.Java8LangSupport = delegate.getJava8LangSupportType()

    override val needsShrinkDesugarLibrary: Boolean
        get() = delegate.needsShrinkDesugarLibrary

    // Apps include the jacoco agent if test coverage is enabled
    override val packageJacocoRuntime: Boolean
        get() = variantDslInfo.isTestCoverageEnabled

    override var androidTest: AndroidTest? = null
}
