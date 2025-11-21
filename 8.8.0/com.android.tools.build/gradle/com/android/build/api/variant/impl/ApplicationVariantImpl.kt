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

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.analytics.AnalyticsEnabledApplicationVariant
import com.android.build.api.component.impl.TestFixturesImpl
import com.android.build.api.component.impl.features.DexingImpl
import com.android.build.api.component.impl.isTestApk
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.ApkInstallGroup
import com.android.build.api.variant.ApkOutput
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.Component
import com.android.build.api.variant.DependenciesInfo
import com.android.build.api.variant.DependenciesInfoBuilder
import com.android.build.api.variant.DeviceSpec
import com.android.build.api.variant.DeviceTest
import com.android.build.api.variant.HasHostTests
import com.android.build.api.variant.HasUnitTest
import com.android.build.api.variant.Renderscript
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.api.variant.ApkOutputProviders
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.HostTestCreationConfig
import com.android.build.gradle.internal.component.features.DexingCreationConfig
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.core.dsl.ApplicationVariantDslInfo
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.ModulePropertyKey
import com.android.build.gradle.internal.profile.ProfilingMode
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.VariantPublishingInfo
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.utils.ApkSources
import com.android.build.gradle.internal.utils.DefaultDeviceApkOutput
import com.android.build.gradle.internal.utils.PrivacySandboxApkSources
import com.android.build.gradle.internal.utils.ViaBundleDeviceApkOutput
import com.android.build.gradle.internal.utils.toImmutableMap
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.builder.errors.IssueReporter
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.TaskProvider
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
), ApplicationVariant,
    ApplicationCreationConfig,
    HasDeviceTestsCreationConfig,
    HasHostTestsCreationConfig,
    HasTestFixtures,
    HasHostTests,
    HasUnitTest {

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------

    override val applicationId: Property<String> = dslInfo.applicationId

    override val embedsMicroApp: Boolean = dslInfo.isEmbedMicroApp

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
            internalServices.setPropertyOf(String::class.java, dslInfo.localeFilters)
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

    override val publishInfo: VariantPublishingInfo? = dslInfo.publishInfo

    override val hostTests: Map<String, HostTestCreationConfig>
        get() = internalHostTests.toImmutableMap() // immutableMap so java users cannot modify it.
    override val deviceTests: Map<String, DeviceTest>
        get() = internalDeviceTests

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

    override val debuggable: Boolean = dslInfo.isDebuggable
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

    /**
     * Add a new [HostTest] to the host tests for this application.
     *
     * This method must be called during configuration as tasks are created to execute the testing.
     */
    override fun addTestComponent(testTypeName: String, testComponent: HostTestCreationConfig) {
        internalHostTests[testTypeName] = testComponent
    }

    override fun addDeviceTest(testTypeName: String, deviceTest: DeviceTest) {
        internalDeviceTests[testTypeName] = deviceTest
    }

    // ---------------------------------------------------------------------------------------------
    // Private stuff
    // ---------------------------------------------------------------------------------------------

    /**
     * use [addTestComponent] to add a [HostTest] to the map.
     */
    private val internalHostTests = mutableMapOf<String, HostTestCreationConfig>()
    private val internalDeviceTests = mutableMapOf<String, DeviceTest>()

    override val consumesDynamicFeatures: Boolean
        get() = optimizationCreationConfig.minifiedEnabled && global.hasDynamicFeatures

    private fun createVersionNameProperty(): Property<String> =
        internalServices.newPropertyBackingDeprecatedApi(
            String::class.java,
            dslInfo.versionName,
        )

    private fun createVersionCodeProperty() : Property<Int> =
        internalServices.newPropertyBackingDeprecatedApi(
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

    override val isWearAppUnbundled: Boolean? = dslInfo.isWearAppUnbundled

    override val enableApiModeling: Boolean
        get() = isApiModelingEnabled()

    override val enableGlobalSynthetics: Boolean
        get() = isGlobalSyntheticsEnabled()

    override val includeVcsInfo: Boolean? = dslInfo.includeVcsInfo

    override val isForceAotCompilation: Boolean
        get() = experimentalProperties.map {
            ModulePropertyKey.BooleanWithDefault.FORCE_AOT_COMPILATION.getValue(it)
        }.getOrElse(false)

    override val outputProviders: ApkOutputProviders
        get() = object: ApkOutputProviders {
            override fun <TaskT: Task> provideApkOutputToTask(
                taskProvider: TaskProvider<TaskT>,
                taskInput: (TaskT) -> Property<ApkOutput>,
                deviceSpec: DeviceSpec) {
                val skipApksViaBundleIfPossible = services.projectOptions.get(BooleanOption.SKIP_APKS_VIA_BUNDLE_IF_POSSIBLE)
                taskProvider.configure { task ->
                    if (skipApksViaBundleIfPossible && !global.hasDynamicFeatures) {
                        provideApkOutputToTask(task, taskInput, deviceSpec)
                    } else {
                        provideApkOutputToTaskViaBundle(task, taskInput, deviceSpec)
                    }
                }
            }
        }

    private fun <TaskT: Task> provideApkOutputToTask(
        task: TaskT,
        taskInput: (TaskT) -> Property<ApkOutput>,
        deviceSpec: DeviceSpec
    ) {
        val apkSources = getApkSources()
        val deviceApkOutput = DefaultDeviceApkOutput(
            apkSources, nativeBuildCreationConfig.supportedAbis, minSdk.toSharedAndroidVersion(),
            baseName, services.projectInfo.path)
        task.inputs.files(DefaultDeviceApkOutput.getApkInputs(apkSources, deviceSpec))
        val apkOutput = services.provider {
            object: ApkOutput {
                override val apkInstallGroups: List<ApkInstallGroup>
                    get() = deviceApkOutput.getApks(deviceSpec)
            }
        }
        taskInput(task).set(apkOutput)
    }

    private fun <TaskT: Task> provideApkOutputToTaskViaBundle(
        task: TaskT,
        taskInput: (TaskT) -> Property<ApkOutput>,
        deviceSpec: DeviceSpec) {
        val apkBundle = artifacts.get(InternalArtifactType.APKS_FROM_BUNDLE)
        val privacySandboxSdkApks = getPrivacySandboxSdkApks()
        task.inputs.files(
            apkBundle,
            privacySandboxSdkApks,
        )
            .withNormalizer(ClasspathNormalizer::class.java)
        val viaBundleApkOutput = ViaBundleDeviceApkOutput(
            apkBundle,
            minSdk.toSharedAndroidVersion(),
            privacySandboxSdkApks,
            baseName,
            services.projectInfo.path
        )
        taskInput(task).set(services.provider {
            object: ApkOutput {
                override val apkInstallGroups: List<ApkInstallGroup>
                    get() = viaBundleApkOutput.getApks(deviceSpec)
            }
        })
    }

    private fun getApkSources(): ApkSources {
        val privacySandboxApkSources = getPrivacySandboxApkSources()
        return ApkSources(
            mainApkArtifact = artifacts.get(SingleArtifact.APK),
            privacySandboxSdksApksFiles = privacySandboxApkSources.privacySandboxSdksApksFiles,
            additionalSupportedSdkApkSplits = privacySandboxApkSources.additionalSupportedSdkApkSplits,
            privacySandboxSdkSplitApksForLegacy = privacySandboxApkSources.privacySandboxSdkSplitApksForLegacy,
            dexMetadataDirectory = artifacts.get(InternalArtifactType.DEX_METADATA_DIRECTORY))
    }

    private fun getPrivacySandboxSdkApks(): FileCollection = variantDependencies
        .getArtifactFileCollection(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            AndroidArtifacts.ArtifactScope.ALL,
            AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_APKS)

    private fun getPrivacySandboxApkSources() : PrivacySandboxApkSources {
        return PrivacySandboxApkSources(
            privacySandboxSdksApksFiles = getPrivacySandboxSdkApks(),
            additionalSupportedSdkApkSplits = artifacts.get(
                InternalArtifactType.USES_SDK_LIBRARY_SPLIT_FOR_LOCAL_DEPLOYMENT),
            privacySandboxSdkSplitApksForLegacy = artifacts.get(InternalArtifactType.EXTRACTED_SDK_APKS))
    }
}
