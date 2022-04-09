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
import com.android.build.api.component.analytics.AnalyticsEnabledTestVariant
import com.android.build.api.component.impl.TestVariantCreationConfigImpl
import com.android.build.api.component.impl.getAndroidResources
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar
import com.android.build.api.variant.AndroidResources
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.ApkPackaging
import com.android.build.api.variant.Component
import com.android.build.api.variant.Renderscript
import com.android.build.api.variant.TestVariant
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.gradle.internal.ProguardFileType
import com.android.build.gradle.internal.component.TestVariantCreationConfig
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.core.dsl.TestProjectVariantDslInfo
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.ModulePropertyKeys
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.publishing.AndroidArtifacts
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
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import javax.inject.Inject

open class TestVariantImpl @Inject constructor(
    override val variantBuilder: TestVariantBuilderImpl,
    buildFeatureValues: BuildFeatureValues,
    dslInfo: TestProjectVariantDslInfo,
    variantDependencies: VariantDependencies,
    variantSources: VariantSources,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    variantData: BaseVariantData,
    taskContainer: MutableTaskContainer,
    transformManager: TransformManager,
    internalServices: VariantServices,
    taskCreationServices: TaskCreationServices,
    globalTaskCreationConfig: GlobalTaskCreationConfig
) : VariantImpl<TestProjectVariantDslInfo>(
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
), TestVariant, TestVariantCreationConfig {

    init {
        dslInfo.multiDexKeepProguard?.let {
            artifacts.getArtifactContainer(MultipleArtifact.MULTIDEX_KEEP_PROGUARD)
                .addInitialProvider(null, internalServices.toRegularFileProvider(it))
        }
    }

    private val delegate by lazy {
        TestVariantCreationConfigImpl(
            this,
            dslInfo
        )
    }

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------

    override val applicationId: Property<String> =
        internalServices.propertyOf(String::class.java, dslInfo.applicationId)

    override val androidResources: AndroidResources by lazy {
        getAndroidResources()
    }

    // TODO: We should keep this (for the manifest) but just fix the test runner to get the
    //         tested application id from the APK metadata file for uninstalling.
    override val testedApplicationId: Provider<String> by lazy {
        experimentalProperties.flatMap {
            if (ModulePropertyKeys.SELF_INSTRUMENTING.getValueAsBoolean(it)) {
                applicationId
            } else {
                calculateTestedApplicationId(variantDependencies)
            }
        }
    }

    override val minifiedEnabled: Boolean
        get() = variantBuilder.isMinifyEnabled
    override val resourcesShrink: Boolean
        get() = false

    override val instrumentationRunner: Property<String> =
        internalServices.propertyOf(String::class.java, dslInfo.getInstrumentationRunner(dexingType))

    override val handleProfiling: Property<Boolean> =
        internalServices.propertyOf(Boolean::class.java, dslInfo.handleProfiling)

    override val functionalTest: Property<Boolean> =
        internalServices.propertyOf(Boolean::class.java, dslInfo.functionalTest)

    override val testLabel: Property<String?> =
        internalServices.nullablePropertyOf(String::class.java, dslInfo.testLabel)

    override val packaging: ApkPackaging by lazy {
        ApkPackagingImpl(
            dslInfo.packaging,
            internalServices,
            minSdkVersion.apiLevel
        )
    }

    override val renderscript: Renderscript? by lazy {
        renderscriptCreationConfig?.renderscript
    }

    override val proguardFiles: ListProperty<RegularFile> by lazy {
        internalServices.listPropertyOf(RegularFile::class.java) {
            val projectDir = services.projectInfo.projectDirectory
            it.addAll(
                dslInfo.gatherProguardFiles(ProguardFileType.TEST).map { file ->
                    projectDir.file(file.absolutePath)
                }
            )
        }
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
        get() = dslInfo.instrumentationRunnerArguments

    override val isTestCoverageEnabled: Boolean
        get() = dslInfo.isAndroidTestCoverageEnabled

    override val shouldPackageDesugarLibDex: Boolean = delegate.isCoreLibraryDesugaringEnabled(this)
    override val debuggable: Boolean
        get() = delegate.isDebuggable
    override val isCoreLibraryDesugaringEnabled: Boolean
        get() = delegate.isCoreLibraryDesugaringEnabled

    override val shouldPackageProfilerDependencies: Boolean = false
    override val advancedProfilingTransforms: List<String> = emptyList()

    override val signingConfigImpl: SigningConfigImpl? by lazy {
        dslInfo.signingConfig?.let {
            SigningConfigImpl(
                it,
                internalServices,
                minSdkVersion.apiLevel,
                services.projectOptions.get(IntegerOption.IDE_TARGET_DEVICE_API)
            )
        }
    }

    override val useJacocoTransformInstrumentation: Boolean
        get() = false

    // ---------------------------------------------------------------------------------------------
    // DO NOT USE, Deprecated DSL APIs.
    // ---------------------------------------------------------------------------------------------
    override val multiDexKeepFile = dslInfo.multiDexKeepFile

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

    override fun <T : Component> createUserVisibleVariantObject(
            projectServices: ProjectServices,
            operationsRegistrar: VariantApiOperationsRegistrar<out CommonExtension<*, *, *, *>, out VariantBuilder, out Variant>,
            stats: GradleBuildVariant.Builder?
    ): T =
        if (stats == null) {
            this as T
        } else {
            projectServices.objectFactory.newInstance(
                AnalyticsEnabledTestVariant::class.java,
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

}
