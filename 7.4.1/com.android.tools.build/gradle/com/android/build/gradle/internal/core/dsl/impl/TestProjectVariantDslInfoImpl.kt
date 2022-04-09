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
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.core.dsl.TestProjectVariantDslInfo
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.InternalTestExtension
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.manifest.ManifestDataProvider
import com.android.build.gradle.internal.profile.ProfilingMode
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.options.Version
import com.android.builder.core.ComponentType
import com.android.builder.dexing.DexingType
import com.android.builder.errors.IssueReporter
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
    oldExtension: BaseExtension?,
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
    oldExtension,
    extension
), TestProjectVariantDslInfo {

    override val namespace: Provider<String> by lazy {
        // -------------
        // Special case for separate test sub-projects
        // If there is no namespace from the DSL or package attribute in the manifest, we use
        // testApplicationId, if present. This allows the test project to not have a manifest if
        // all is declared in the DSL.
        // TODO(b/170945282, b/172361895) Remove this special case - users should use namespace
        //  DSL instead of testApplicationId DSL for this... currently a warning
        if (extension.namespace != null) {
            services.provider { extension.namespace!! }
        } else {
            val testAppIdFromFlavors =
                productFlavorList.asSequence().map { it.testApplicationId }
                    .firstOrNull { it != null }
                    ?: defaultConfig.testApplicationId

            dataProvider.manifestData.map {
                it.packageName
                    ?: testAppIdFromFlavors?.also {
                        val message =
                            "Namespace not specified. Please specify a namespace for " +
                                    "the generated R and BuildConfig classes via " +
                                    "android.namespace in the test module's " +
                                    "build.gradle file. Currently, this test module " +
                                    "uses the testApplicationId " +
                                    "($testAppIdFromFlavors) as its namespace, but " +
                                    "version ${Version.VERSION_8_0} of the Android " +
                                    "Gradle Plugin will require that a namespace be " +
                                    "specified explicitly like so:\n\n" +
                                    "android {\n" +
                                    "    namespace '$testAppIdFromFlavors'\n" +
                                    "}\n\n"
                        services.issueReporter
                            .reportWarning(IssueReporter.Type.GENERIC, message)
                    }
                    ?: throw RuntimeException(
                        getMissingPackageNameErrorMessage(dataProvider.manifestLocation)
                    )
            }
        }
    }

    override val applicationId: Property<String> =
        services.newPropertyBackingDeprecatedApi(
            String::class.java,
            initTestApplicationId(defaultConfig, services)
        )

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
}
