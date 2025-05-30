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

package com.android.build.api.variant.impl

import com.android.SdkConstants.DOT_AAR
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.attributes.ProductFlavorAttr
import com.android.build.api.component.analytics.AnalyticsEnabledKotlinMultiplatformAndroidVariant
import com.android.build.api.component.impl.KmpAndroidTestImpl
import com.android.build.api.component.impl.KmpComponentImpl
import com.android.build.api.component.impl.KmpHostTestImpl
import com.android.build.api.component.impl.features.OptimizationCreationConfigImpl
import com.android.build.api.dsl.KotlinMultiplatformAndroidCompilation
import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AarMetadata
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.CanMinifyAndroidResourcesBuilder
import com.android.build.api.variant.CanMinifyCodeBuilder
import com.android.build.api.variant.Component
import com.android.build.api.variant.DeviceTest
import com.android.build.api.variant.ExternalNativeBuild
import com.android.build.api.variant.KotlinMultiplatformAndroidVariant
import com.android.build.api.variant.Renderscript
import com.android.build.api.variant.ResValue
import com.android.build.api.variant.TestFixtures
import com.android.build.api.variant.TestedComponentPackaging
import com.android.build.gradle.internal.DependencyConfigurator
import com.android.build.gradle.internal.KotlinMultiplatformCompileOptionsImpl
import com.android.build.gradle.internal.component.HostTestCreationConfig
import com.android.build.gradle.internal.component.KmpCreationConfig
import com.android.build.gradle.internal.component.TestSuiteCreationConfig
import com.android.build.gradle.internal.component.features.NativeBuildCreationConfig
import com.android.build.gradle.internal.component.features.RenderscriptCreationConfig
import com.android.build.gradle.internal.component.features.ShadersCreationConfig
import com.android.build.gradle.internal.core.dsl.KmpVariantDslInfo
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.AarMetadataTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.google.common.collect.ImmutableMap
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import java.io.File
import java.io.Serializable
import javax.inject.Inject

open class KmpVariantImpl @Inject constructor(
    dslInfo: KmpVariantDslInfo,
    internalServices: VariantServices,
    buildFeatures: BuildFeatureValues,
    variantDependencies: VariantDependencies,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    taskContainer: MutableTaskContainer,
    services: TaskCreationServices,
    global: GlobalTaskCreationConfig,
    androidKotlinCompilation: KotlinMultiplatformAndroidCompilation,
    manifestFile: File,
): KmpComponentImpl<KmpVariantDslInfo>(
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
    manifestFile,
), KotlinMultiplatformAndroidVariant, KmpCreationConfig, HasDeviceTestsCreationConfig {

    override val minSdkVersion: AndroidVersion
        get() = minSdk

    override val aarOutputFileName: Property<String> =
        internalServices.newPropertyBackingDeprecatedApi(
            String::class.java,
            services.projectInfo.getProjectBaseName().map {
                it + DOT_AAR
            }
        )

    override val aarMetadata: AarMetadata =
        internalServices.newInstance(AarMetadata::class.java).also {
            it.minCompileSdk.set(dslInfo.aarMetadata.minCompileSdk ?: 1)
            it.minCompileSdkExtension.set(
                dslInfo.aarMetadata.minCompileSdkExtension ?: AarMetadataTask.DEFAULT_MIN_COMPILE_SDK_EXTENSION
            )
            it.minAgpVersion.set(
                dslInfo.aarMetadata.minAgpVersion ?: AarMetadataTask.DEFAULT_MIN_AGP_VERSION
            )
        }

    override val optimizationCreationConfig by lazy(LazyThreadSafetyMode.NONE) {
        OptimizationCreationConfigImpl(
            this,
            dslInfo.optimizationDslInfo,
            object : CanMinifyCodeBuilder {
                override var isMinifyEnabled =
                    dslInfo.optimizationDslInfo.postProcessingOptions.codeShrinkerEnabled()
            },
            object : CanMinifyAndroidResourcesBuilder {
                override var shrinkResources =
                    dslInfo.optimizationDslInfo.postProcessingOptions.codeShrinkerEnabled()
            },
            internalServices
        )
    }

    override val proguardFiles: ListProperty<RegularFile>
        get() = optimizationCreationConfig.proguardFiles

    override val deviceTests: Map<String, DeviceTest>
        get() = internalDeviceTests

    override val hostTests = mutableMapOf<String, HostTestCreationConfig>()

    override val isMinifyEnabled: Boolean
        get() = optimizationCreationConfig.minifiedEnabled

    override var unitTest: KmpHostTestImpl? = null

    override val androidDeviceTest: KmpAndroidTestImpl?
        get() = deviceTests.values.filterIsInstance<KmpAndroidTestImpl>().firstOrNull()

    override val requiresJacocoTransformation: Boolean
        get() = androidDeviceTest?.codeCoverageEnabled ?: false

    override val nestedComponents: List<KmpComponentImpl<*>>
        get() = listOfNotNull(
            unitTest,
            androidDeviceTest
        )
    override val components: List<Component>
        get() = mutableListOf<Component>(this).also {
            it.addAll(nestedComponents)
        }

    override fun missingDimensionStrategy(dimension: String, vararg requestedValues: String) {
        val attributeKey = ProductFlavorAttr.of(dimension)
        val attributeValue: ProductFlavorAttr = services.named(
            ProductFlavorAttr::class.java, name
        )

        variantDependencies.compileClasspath.attributes.attribute(attributeKey, attributeValue)
        variantDependencies.runtimeClasspath.attributes.attribute(attributeKey, attributeValue)
        variantDependencies
            .annotationProcessorConfiguration
            ?.attributes
            ?.attribute(attributeKey, attributeValue)

        // then add the fallbacks which contain the actual requested value
        DependencyConfigurator.addFlavorStrategy(
            services.dependencies.attributesSchema,
            dimension,
            ImmutableMap.of(name, requestedValues.toList())
        )
    }

    override val experimentalProperties: MapProperty<String, Any> =
        internalServices.mapPropertyOf(
            String::class.java,
            Any::class.java,
            dslInfo.experimentalProperties,
            disallowUnsafeRead = false
        )

    override val maxSdk: Int?
        get() = dslInfo.maxSdkVersion

    override val maxSdkVersion: Int?
        get() = maxSdk

    override val targetSdkVersion: AndroidVersion
        get() = minSdk

    override val buildConfigFields: MapProperty<String, BuildConfigField<out Serializable>>?
        get() = buildConfigCreationConfig?.buildConfigFields

    override val manifestPlaceholders: MapProperty<String, String>
        get() = manifestPlaceholdersCreationConfig?.placeholders ?: internalServices.mapPropertyOf(
            String::class.java,
            String::class.java,
            emptyMap()
        )

    override val packaging: TestedComponentPackaging by lazy(LazyThreadSafetyMode.NONE) {
        TestedComponentPackagingImpl(dslInfo.packaging, internalServices)
    }

    override val externalNativeBuild: ExternalNativeBuild?
        get() = null

    override val isCoreLibraryDesugaringEnabledLintCheck: Boolean
        get() = global.compileOptions.isCoreLibraryDesugaringEnabled

    override fun syncAndroidAndKmpClasspathAndSources() {
        super.syncAndroidAndKmpClasspathAndSources()

        (global.compileOptions as KotlinMultiplatformCompileOptionsImpl)
            .initFromCompilation(androidKotlinCompilation)
    }

    @Deprecated("Will be removed in v9.0, use the instrumentation block.")
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

    @Deprecated("Will be removed in v9.0, use the instrumentation block.")
    override fun setAsmFramesComputationMode(mode: FramesComputationMode) {
        instrumentation.setAsmFramesComputationMode(mode)
    }

    override fun makeResValueKey(type: String, name: String): ResValue.Key {
        return ResValueKeyImpl(type, name)
    }

    override val resValues: MapProperty<ResValue.Key, ResValue> by lazy {
        resValuesCreationConfig?.resValues ?: internalServices.mapPropertyOf(
            ResValue.Key::class.java,
            ResValue::class.java,
            emptyMap()
        )
    }

    override val pseudoLocalesEnabled: Property<Boolean> by lazy {
        androidResourcesCreationConfig?.pseudoLocalesEnabled
            ?: internalServices.propertyOf(Boolean::class.java, false)
    }

    override fun <T : Component> createUserVisibleVariantObject(
        stats: GradleBuildVariant.Builder?
    ): T {
        return if (stats == null) {
            this as T
        } else {
            services.newInstance(
                AnalyticsEnabledKotlinMultiplatformAndroidVariant::class.java,
                this,
                stats
            ) as T
        }
    }

    private val internalDeviceTests = mutableMapOf<String, DeviceTest>()

    override fun addDeviceTest(testTypeName: String, deviceTest: DeviceTest) {
        internalDeviceTests[testTypeName] = deviceTest
    }

    // Not supported
    override val renderscriptCreationConfig: RenderscriptCreationConfig? = null
    override val renderscript: Renderscript? = null
    override val shadersCreationConfig: ShadersCreationConfig? = null
    override val nativeBuildCreationConfig: NativeBuildCreationConfig? = null
    override fun <T> getExtension(type: Class<T>): T?  = null
    override val testFixtures: TestFixtures? = null
    override val testSuites: List<TestSuiteCreationConfig> = listOf()
}
