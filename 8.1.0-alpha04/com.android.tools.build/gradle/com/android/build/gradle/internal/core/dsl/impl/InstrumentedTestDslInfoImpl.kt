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

import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.ProductFlavor
import com.android.build.gradle.internal.core.dsl.InstrumentedTestComponentDslInfo
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.manifest.ManifestDataProvider
import com.android.build.gradle.internal.services.VariantServices
import com.android.builder.dexing.DexingType
import com.android.builder.dexing.isLegacyMultiDexMode
import org.gradle.api.provider.Provider

internal class InstrumentedTestDslInfoImpl(
    private val buildTypeObj: BuildType,
    private val productFlavorList: List<ProductFlavor>,
    private val defaultConfig: DefaultConfig,
    private val dataProvider: ManifestDataProvider,
    private val services: VariantServices,
    override val instrumentationRunnerArguments: Map<String, String>
): InstrumentedTestComponentDslInfo {
    override fun getInstrumentationRunner(dexingType: DexingType): Provider<String> {
        // first check whether the DSL has the info
        val fromFlavor =
            productFlavorList.asSequence().map { it.testInstrumentationRunner }
                .firstOrNull { it != null }
                ?: defaultConfig.testInstrumentationRunner

        if (fromFlavor != null) {
            val finalFromFlavor: String = fromFlavor
            return services.provider{ finalFromFlavor }
        }

        // else return the value from the Manifest
        return dataProvider.manifestData.map {
            it.instrumentationRunner
                ?: if (dexingType.isLegacyMultiDexMode()) {
                    MULTIDEX_TEST_RUNNER
                } else {
                    DEFAULT_TEST_RUNNER
                }
        }
    }

    override val handleProfiling: Provider<Boolean>
        get() {
            // first check whether the DSL has the info
            val fromFlavor =
                productFlavorList.asSequence().map { it.testHandleProfiling }
                    .firstOrNull { it != null }
                    ?: defaultConfig.testHandleProfiling

            if (fromFlavor != null) {
                val finalFromFlavor: Boolean = fromFlavor
                return services.provider { finalFromFlavor }
            }

            // else return the value from the Manifest
            return dataProvider.manifestData.map { it.handleProfiling ?: DEFAULT_HANDLE_PROFILING }
        }
    override val functionalTest: Provider<Boolean>
        get() {
            // first check whether the DSL has the info
            val fromFlavor =
                productFlavorList.asSequence().map { it.testFunctionalTest }
                    .firstOrNull { it != null }
                    ?: defaultConfig.testFunctionalTest

            if (fromFlavor != null) {
                val finalFromFlavor: Boolean = fromFlavor
                return services.provider { finalFromFlavor }
            }

            // else return the value from the Manifest
            return dataProvider.manifestData.map { it.functionalTest ?: DEFAULT_FUNCTIONAL_TEST }
        }
    override val testLabel: Provider<String?>
        get() {
            // there is actually no DSL value for this.
            return dataProvider.manifestData.map { it.testLabel }
        }

    override val isAndroidTestCoverageEnabled: Boolean
        get() = buildTypeObj.enableAndroidTestCoverage || buildTypeObj.isTestCoverageEnabled
}
