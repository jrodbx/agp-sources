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

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.UnitTest
import com.android.build.api.component.impl.features.AndroidResourcesCreationConfigImpl
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.HostTest
import com.android.build.api.variant.HostTestBuilder
import com.android.build.api.variant.impl.AndroidResourcesImpl
import com.android.build.api.variant.impl.HostTestBuilderImpl
import com.android.build.gradle.internal.component.HostTestCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.component.features.AndroidResourcesCreationConfig
import com.android.build.gradle.internal.component.features.BuildConfigCreationConfig
import com.android.build.gradle.internal.component.features.ManifestPlaceholdersCreationConfig
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.core.dsl.HostTestComponentDslInfo
import com.android.build.gradle.internal.core.dsl.impl.DEFAULT_TEST_RUNNER
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.utils.KOTLIN_KAPT_PLUGIN_ID
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.builder.core.ComponentTypeImpl
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import javax.inject.Inject

abstract class HostTestImpl @Inject constructor(
    componentIdentity: ComponentIdentity,
    buildFeatureValues: BuildFeatureValues,
    dslInfo: HostTestComponentDslInfo,
    variantDependencies: VariantDependencies,
    variantSources: VariantSources,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    variantData: BaseVariantData,
    taskContainer: MutableTaskContainer,
    testedVariant: VariantCreationConfig,
    internalServices: VariantServices,
    taskCreationServices: TaskCreationServices,
    global: GlobalTaskCreationConfig,
    hostTestBuilder: HostTestBuilderImpl,
) : TestComponentImpl<HostTestComponentDslInfo>(
    componentIdentity,
    buildFeatureValues,
    dslInfo,
    variantDependencies,
    variantSources,
    paths,
    artifacts,
    variantData,
    taskContainer,
    testedVariant,
    internalServices,
    taskCreationServices,
    global
), HostTest, HostTestCreationConfig, UnitTest {

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------
    override val codeCoverageEnabled: Boolean = hostTestBuilder._enableCodeCoverage

    final override val hostTestName: String
    final override val useBuiltInKotlinSupport: Boolean
    final override val useBuiltInKaptSupport: Boolean

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    override val minSdk: AndroidVersion
        get() = mainVariant.minSdk

    override val applicationId: Provider<String> =
        internalServices.providerOf(String::class.java, dslInfo.applicationId)

    /**
     * Return the default runner as with host tests, there is no dexing. However, aapt2 requires
     * the instrumentation tag to be present in the merged manifest to process android resources.
     */
    override val instrumentationRunner: Provider<String>
        get() = services.provider { DEFAULT_TEST_RUNNER }

    override val testedApplicationId: Provider<String>
        get() = mainVariant.applicationId

    override val debuggable: Boolean
        get() = mainVariant.debuggable

    override val manifestPlaceholders: MapProperty<String, String>
        get() = manifestPlaceholdersCreationConfig.placeholders

    override val manifestPlaceholdersCreationConfig: ManifestPlaceholdersCreationConfig by lazy(LazyThreadSafetyMode.NONE) {
        createManifestPlaceholdersCreationConfig(
                dslInfo.mainVariantDslInfo.manifestPlaceholdersDslInfo?.placeholders)
    }

    /**
     * There is no build config fields for host tests.
     */
    override val buildConfigCreationConfig: BuildConfigCreationConfig? = null

    private val testTaskConfigActions = mutableListOf<(Test) -> Unit>()

    @Synchronized
    override fun configureTestTask(action: (Test) -> Unit) {
        testTaskConfigActions.add(action)
    }

    @Synchronized
    override fun runTestTaskConfigurationActions(testTaskProvider: TaskProvider<out Test>) {
        testTaskConfigActions.forEach {
            testTaskProvider.configure { testTask -> it(testTask) }
        }
    }

    /**
     * In unit tests, we don't produce an apk. However, we still need to set the target sdk version
     * in the test manifest as robolectric depends on it.
     */
    override val targetSdkVersion: AndroidVersion
        get() = when(dslInfo.componentType) {
            ComponentTypeImpl.UNIT_TEST -> {
                /**
                 * In unit tests, we don't produce an apk. However, we still need to set the
                 * target sdk version in the test manifest as robolectric depends on it.
                 */
                global.unitTestOptions.targetSdkVersion ?: getMainTargetSdkVersion()
            }
            ComponentTypeImpl.SCREENSHOT_TEST -> {
                getMainTargetSdkVersion()
            }
            else -> throw RuntimeException("Invalid component type ${dslInfo.componentType}")
        }

    override val androidResourcesCreationConfig: AndroidResourcesCreationConfig? by lazy(
        LazyThreadSafetyMode.NONE
    ) {
        // in case of unit tests, we add the R jar even if android resources are
        // disabled (includeAndroidResources) as we want to be able to compile against
        // the values inside.
        if (buildFeatures.androidResources ||
            (dslInfo.componentType == ComponentTypeImpl.UNIT_TEST &&  mainVariant.buildFeatures.androidResources)) {
            AndroidResourcesCreationConfigImpl(
                this,
                dslInfo,
                dslInfo.androidResourcesDsl!!,
                internalServices,
            )
        } else {
            null
        }
    }


    override val androidResources: AndroidResourcesImpl =
        getAndroidResources(dslInfo.androidResourcesDsl!!.androidResources)


    init {
        when(dslInfo.componentType) {
            ComponentTypeImpl.UNIT_TEST -> {
                hostTestName = HostTestBuilder.UNIT_TEST_TYPE
                useBuiltInKotlinSupport = mainVariant.useBuiltInKotlinSupport
                useBuiltInKaptSupport = mainVariant.useBuiltInKaptSupport
            }
            ComponentTypeImpl.SCREENSHOT_TEST -> {
                hostTestName = HostTestBuilder.SCREENSHOT_TEST_TYPE
                useBuiltInKotlinSupport = true
                // For screenshotTest components, the application of the Jetbrains KAPT plugin
                // should also enable built-in KAPT support.
                useBuiltInKaptSupport =
                    mainVariant.useBuiltInKaptSupport ||
                            internalServices.projectInfo.hasPlugin(KOTLIN_KAPT_PLUGIN_ID)
            }
            else -> throw IllegalStateException(
                "Expected a test component type, but ${componentIdentity.name} has type " +
                        "$dslInfo.componentType")
        }
    }
}
