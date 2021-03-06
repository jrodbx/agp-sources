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
import com.android.build.api.component.analytics.AnalyticsEnabledTestVariantProperties
import com.android.build.api.component.analytics.AnalyticsEnabledVariantProperties
import com.android.build.api.component.impl.TestVariantCreationConfigImpl
import com.android.build.api.variant.AaptOptions
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.ApkPackagingOptions
import com.android.build.api.variant.TestVariantProperties
import com.android.build.gradle.internal.component.TestVariantCreationConfig
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantPropertiesApiServices
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.builder.dexing.DexingType
import com.android.builder.model.CodeShrinker
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import javax.inject.Inject

open class TestVariantPropertiesImpl @Inject constructor(
    override val variant: TestVariantImpl,
    buildFeatureValues: BuildFeatureValues,
    variantDslInfo: VariantDslInfo,
    variantDependencies: VariantDependencies,
    variantSources: VariantSources,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    variantScope: VariantScope,
    variantData: BaseVariantData,
    transformManager: TransformManager,
    internalServices: VariantPropertiesApiServices,
    taskCreationServices: TaskCreationServices,
    globalScope: GlobalScope
) : VariantPropertiesImpl(
    variant,
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
), TestVariantProperties, TestVariantCreationConfig {

    private val delegate by lazy { TestVariantCreationConfigImpl(this, globalScope, variantDslInfo) }

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------

    override val applicationId: Property<String> =
        internalServices.propertyOf(String::class.java, variantDslInfo.applicationId)

    override val aaptOptions: AaptOptions by lazy {
        initializeAaptOptionsFromDsl(
            globalScope.extension.aaptOptions,
            internalServices
        )
    }

    override fun aaptOptions(action: AaptOptions.() -> Unit) {
        action.invoke(aaptOptions)
    }

    override val testedApplicationId: Provider<String> = calculateTestedApplicationId(variantDependencies)

    override val minifiedEnabled: Boolean
        get() = variantDslInfo.isMinifyEnabled

    override val instrumentationRunner: Property<String> =
        internalServices.propertyOf(String::class.java, variantDslInfo.getInstrumentationRunner(dexingType))

    override val handleProfiling: Property<Boolean> =
        internalServices.propertyOf(Boolean::class.java, variantDslInfo.handleProfiling)

    override val functionalTest: Property<Boolean> =
        internalServices.propertyOf(Boolean::class.java, variantDslInfo.functionalTest)

    override val testLabel: Property<String?> =
        internalServices.nullablePropertyOf(String::class.java, variantDslInfo.testLabel)

    override val packagingOptions: ApkPackagingOptions by lazy {
        ApkPackagingOptionsImpl(globalScope.extension.packagingOptions, internalServices)
    }

    override fun packagingOptions(action: ApkPackagingOptions.() -> Unit) {
        action.invoke(packagingOptions)
    }

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    // always false for this type
    override val embedsMicroApp: Boolean
        get() = false

    // always true for this kind
    override val testOnlyApk: Boolean
        get() = true

    override val instrumentationRunnerArguments: Map<String, String>
        get() = variantDslInfo.instrumentationRunnerArguments

    override val isTestCoverageEnabled: Boolean
        get() = variantDslInfo.isTestCoverageEnabled

    override val shouldPackageDesugarLibDex: Boolean = delegate.isCoreLibraryDesugaringEnabled(this)
    override val debuggable: Boolean
        get() = delegate.isDebuggable

    override val shouldPackageProfilerDependencies: Boolean = false
    override val advancedProfilingTransforms: List<String> = emptyList()

    // ---------------------------------------------------------------------------------------------
    // Private stuff
    // ---------------------------------------------------------------------------------------------

    private fun calculateTestedApplicationId(
        variantDependencies: VariantDependencies
    ): Provider<String> {
        return variantDependencies
            .getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                AndroidArtifacts.ArtifactScope.PROJECT,
                AndroidArtifacts.ArtifactType.MANIFEST_METADATA
            ).elements.map {
                val manifestDirectory = it.single().asFile
                BuiltArtifactsLoaderImpl.loadFromDirectory(manifestDirectory)?.applicationId
                    ?: throw RuntimeException("Cannot find merged manifest at '$manifestDirectory', please file a bug.\"")
            }
    }

    override val dexingType: DexingType
        get() = delegate.dexingType

    override val needsMainDexListForBundle: Boolean
        get() = false

    override fun createUserVisibleVariantPropertiesObject(
        projectServices: ProjectServices,
        stats: GradleBuildVariant.Builder
    ): AnalyticsEnabledVariantProperties =
        projectServices.objectFactory.newInstance(
            AnalyticsEnabledTestVariantProperties::class.java,
            this,
            stats
        )
    override val minSdkVersionWithTargetDeviceApi: AndroidVersion
        get() = delegate.minSdkVersionWithTargetDeviceApi

    override val codeShrinker: CodeShrinker?
        get() = delegate.getCodeShrinker()

    override fun getNeedsMergedJavaResStream(): Boolean = delegate.getNeedsMergedJavaResStream()

    override fun getJava8LangSupportType(): VariantScope.Java8LangSupport = delegate.getJava8LangSupportType()
    override val needsShrinkDesugarLibrary: Boolean
        get() = delegate.needsShrinkDesugarLibrary

}
