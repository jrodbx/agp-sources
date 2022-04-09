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

package com.android.build.gradle.internal.core.dsl.impl

import com.android.build.api.dsl.ApplicationBuildType
import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.variant.ComponentIdentity
import com.android.build.gradle.internal.core.dsl.TestProjectVariantDslInfo
import com.android.build.gradle.internal.core.dsl.features.DexingDslInfo
import com.android.build.gradle.internal.core.dsl.impl.features.DexingDslInfoImpl
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.InternalTestExtension
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.manifest.ManifestDataProvider
import com.android.build.gradle.internal.profile.ProfilingMode
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.options.StringOption
import com.android.builder.core.ComponentType
import com.android.builder.dexing.DexingType
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

internal class TestProjectVariantDslInfoImpl(
    componentIdentity: ComponentIdentity,
    componentType: ComponentType,
    defaultConfig: DefaultConfig,
    buildTypeObj: BuildType,
    productFlavorList: List<ProductFlavor>,
    dataProvider: ManifestDataProvider,
    services: VariantServices,
    buildDirectory: DirectoryProperty,
    private val signingConfigOverride: SigningConfig?,
    extension: InternalTestExtension
) : VariantDslInfoImpl(
    componentIdentity,
    componentType,
    defaultConfig,
    buildTypeObj,
    productFlavorList,
    dataProvider,
    services,
    buildDirectory,
    extension
), TestProjectVariantDslInfo {

    override val applicationId: Property<String> =
        services.newPropertyBackingDeprecatedApi(
            String::class.java,
            initTestApplicationId(productFlavorList, defaultConfig, services)
        )

    override val isAndroidTestCoverageEnabled: Boolean
        get() = instrumentedTestDelegate.isAndroidTestCoverageEnabled

    // TODO: Test project doesn't have isDebuggable dsl in the build type, we should only have
    //  `debug` variants be debuggable
    override val isDebuggable: Boolean
        get() = ProfilingMode.getProfilingModeType(
            services.projectOptions[StringOption.PROFILING_MODE]
        ).isDebuggable
            ?: (buildTypeObj as? ApplicationBuildType)?.isDebuggable
            ?: false

    override val signingConfig: SigningConfig? by lazy {
        getSigningConfig(
            buildTypeObj,
            mergedFlavor,
            signingConfigOverride,
            extension,
            services
        )
    }

    override val isSigningReady: Boolean
        get() = signingConfig?.isSigningReady == true

    private val instrumentedTestDelegate by lazy {
        InstrumentedTestDslInfoImpl(
            buildTypeObj,
            productFlavorList,
            defaultConfig,
            dataProvider,
            services,
            mergedFlavor.testInstrumentationRunnerArguments
        )
    }

    override fun getInstrumentationRunner(dexingType: DexingType): Provider<String> {
        return instrumentedTestDelegate.getInstrumentationRunner(dexingType)
    }

    override val instrumentationRunnerArguments: Map<String, String>
        get() = instrumentedTestDelegate.instrumentationRunnerArguments
    override val handleProfiling: Provider<Boolean>
        get() = instrumentedTestDelegate.handleProfiling
    override val functionalTest: Provider<Boolean>
        get() = instrumentedTestDelegate.functionalTest
    override val testLabel: Provider<String?>
        get() = instrumentedTestDelegate.testLabel

    override val dexingDslInfo: DexingDslInfo by lazy {
        DexingDslInfoImpl(
            buildTypeObj, mergedFlavor
        )
    }
}
