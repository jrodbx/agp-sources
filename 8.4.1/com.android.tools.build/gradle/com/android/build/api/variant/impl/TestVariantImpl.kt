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
import com.android.build.api.component.analytics.AnalyticsEnabledTestVariant
import com.android.build.api.component.impl.features.DexingImpl
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.ApkPackaging
import com.android.build.api.variant.Component
import com.android.build.api.variant.Renderscript
import com.android.build.api.variant.TestVariant
import com.android.build.gradle.internal.component.TestVariantCreationConfig
import com.android.build.gradle.internal.component.features.DexingCreationConfig
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.core.dsl.TestProjectVariantDslInfo
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.ModulePropertyKey
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.file.Directory
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
    internalServices,
    taskCreationServices,
    globalTaskCreationConfig
), TestVariant, TestVariantCreationConfig {

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------

    override val applicationId: Property<String> =
        internalServices.propertyOf(String::class.java, dslInfo.applicationId)

    override val androidResources: AndroidResourcesImpl by lazy {
        getAndroidResources(dslInfo.androidResourcesDsl.androidResources)
    }

    // TODO: We should keep this (for the manifest) but just fix the test runner to get the
    //         tested application id from the APK metadata file for uninstalling.
    override val testedApplicationId: Provider<String> by lazy {
        experimentalProperties.flatMap {
            if (ModulePropertyKey.BooleanWithDefault.SELF_INSTRUMENTING.getValue(it)) {
                applicationId
            } else {
                calculateTestedApplicationId(variantDependencies)
            }
        }
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

    override val packaging: ApkPackaging by lazy {
        ApkPackagingImpl(
            dslInfo.packaging,
            internalServices,
            minSdk.apiLevel
        )
    }

    override val renderscript: Renderscript? by lazy {
        renderscriptCreationConfig?.renderscript
    }
    override val testedApks: Provider<Directory> by lazy {
        val projectDirectory = services.projectInfo.projectDirectory
        variantDependencies.getArtifactFileCollection(
            AndroidArtifacts.ConsumedConfigType.PROVIDED_CLASSPATH,
            AndroidArtifacts.ArtifactScope.ALL,
            AndroidArtifacts.ArtifactType.APK
        ).elements.map {
            projectDirectory.dir(
                it.single().asFile.absolutePath
            )
        }
    }

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

    override val targetSdk: AndroidVersion by lazy(LazyThreadSafetyMode.NONE) {
        global.androidTestOptions.targetSdkVersion ?: variantBuilder.targetSdkVersion
    }

    override val targetSdkVersion: AndroidVersion
        get() = targetSdk

    override val targetSdkOverride: AndroidVersion?
        get() = variantBuilder.mutableTargetSdk?.sanitize()

    // always false for this type
    override val embedsMicroApp: Boolean
        get() = false

    // always true for this kind
    override val testOnlyApk: Boolean
        get() = true

    override val instrumentationRunnerArguments =
        internalServices.mapPropertyOf(
            String::class.java,
            String::class.java,
            dslInfo.instrumentationRunnerArguments
        )

    override val debuggable: Boolean
        get() = dslInfo.isDebuggable

    override val shouldPackageProfilerDependencies: Boolean = false
    override val advancedProfilingTransforms: List<String> = emptyList()

    override val signingConfig: SigningConfigImpl? by lazy {
        dslInfo.signingConfigResolver?.let {
            SigningConfigImpl(
                it.resolveConfig(profileable = false, debuggable),
                internalServices,
                minSdk.apiLevel,
                global.targetDeployApiFromIDE
            )
        }
    }

    /**
     * For test projects, coverage will only be effective if set by the tested project.
     */
    override val isAndroidTestCoverageEnabled: Boolean
        get() = dslInfo.isAndroidTestCoverageEnabled
    override val useJacocoTransformInstrumentation: Boolean
        get() = false
    override val packageJacocoRuntime: Boolean
        get() = false

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

    override fun <T : Component> createUserVisibleVariantObject(
            stats: GradleBuildVariant.Builder?
    ): T =
        if (stats == null) {
            this as T
        } else {
            services.newInstance(
                AnalyticsEnabledTestVariant::class.java,
                this,
                stats
            ) as T
        }

    override val enableApiModeling: Boolean
        get() = isApiModelingEnabled()

    override val enableGlobalSynthetics: Boolean
        get() = isGlobalSyntheticsEnabled()

    override fun finalizeAndLock() {
        super.finalizeAndLock()
        dexing.finalizeAndLock()
    }

    override val isForceAotCompilation: Boolean
        get() = experimentalProperties.map {
            ModulePropertyKey.BooleanWithDefault.FORCE_AOT_COMPILATION.getValue(it)
        }.getOrElse(false)
}
