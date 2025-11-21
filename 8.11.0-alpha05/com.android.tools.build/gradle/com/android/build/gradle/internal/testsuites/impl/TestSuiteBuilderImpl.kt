/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.gradle.internal.testsuites.impl

import com.android.build.api.dsl.AgpTestSuiteInputParameters
import com.android.build.api.dsl.JUnitEngineSpec
import com.android.build.gradle.internal.dsl.AgpTestSuiteImpl
import com.android.build.gradle.internal.services.BaseServices
import com.android.build.gradle.internal.services.VariantBuilderServices
import com.android.build.gradle.internal.testsuites.TestSuiteDependencies
import com.android.build.gradle.internal.testsuites.TestSuiteBuilder

class TestSuiteBuilderImpl(
    private val _name: String,
    override var enable: Boolean,
    _junitEngineSpec: JUnitEngineSpec,
    internal val dslDeclaredDependencies: com.android.build.api.dsl.AgpTestSuiteDependencies,
    services: BaseServices,
): TestSuiteBuilder {

    companion object {

        /**
         * Create the list of test suites for this component, the list is driven by
         * the passed [dslDefinedTestSuiteDefinitions] which is the list of
         * test suites implicitly or explicitly defined by the Component type and its DSL.
         */
        fun create(
            dslDefinedTestSuiteDefinitions: List<AgpTestSuiteImpl>,
            variantBuilderServices: VariantBuilderServices,
            experimentalProperties: Map<String, Any>,
        ): Map<String, TestSuiteBuilder> =
            dslDefinedTestSuiteDefinitions.associate { agpTestSuite ->
                val junitTestEngine = agpTestSuite.getJunitEngineIfUsed()
                    ?: throw RuntimeException("Test suites must use junit engines for now")
                agpTestSuite.name to
                        // TODO: lock JUnitEngineSpec instance.
                        TestSuiteBuilderImpl(
                            _name = agpTestSuite.name,
                            enable = true,
                            _junitEngineSpec = junitTestEngine,
                            dslDeclaredDependencies = agpTestSuite.dependencies,
                            services = variantBuilderServices
                        )
            }
    }

    override val junitEngineSpec: JUnitEngineSpec = object : JUnitEngineSpec {
        override val includeEngines: MutableSet<String> =
            mutableSetOf<String>().also { list ->
                list.addAll(_junitEngineSpec.includeEngines)
            }
        override val inputs: MutableList<AgpTestSuiteInputParameters> =
            mutableListOf<AgpTestSuiteInputParameters>().also { list ->
                list.addAll(_junitEngineSpec.inputs)
            }
    }

    override fun getName(): String = _name

    override val dependencies: TestSuiteDependencies =
        services.newInstance(TestSuiteDependencies::class.java)
}
