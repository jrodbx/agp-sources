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
import com.android.build.api.variant.JUnitEngineSpecBuilder
import com.android.build.api.variant.TestSuiteBuilder
import com.android.build.gradle.internal.core.dsl.AgpTestSuiteDslInfo
import com.android.build.gradle.internal.dsl.JUnitEngineSpecImpl
import com.android.build.gradle.internal.dsl.TestSuiteAssetsSpecImpl
import com.android.build.gradle.internal.services.BaseServices
import com.android.build.gradle.internal.services.VariantBuilderServices
import com.android.build.gradle.internal.testsuites.TestSuiteSourceCreationConfig
import com.android.build.gradle.options.BooleanOption
import com.android.builder.errors.IssueReporter
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

internal abstract class TestSuiteBuilderImpl @Inject internal constructor(
    private val objects: ObjectFactory,
    private val _name: String,
    override var enable: Boolean,
    testSuiteDslInfo: AgpTestSuiteDslInfo,
    private val services: BaseServices,
): TestSuiteBuilder {

    companion object {

        val flagChecked = AtomicBoolean(false)

        /**
         * Create the list of test suites for this component, the list is driven by
         * the passed [dslDefinedTestSuites] which is the list of
         * test suites targets implicitly or explicitly defined by the Component type and its DSL.
         */
        fun create(
            dslDefinedTestSuites: List<AgpTestSuiteDslInfo>,
            variantBuilderServices: VariantBuilderServices,
            experimentalProperties: Map<String, Any>,
        ): Map<String, TestSuiteBuilder> {

            if (dslDefinedTestSuites.isEmpty()) return mapOf()

            if (!flagChecked.getAndSet(true)) {
                val unstableNotice =
                    "*Important* Test suite support is experimental and subject to change\n"
                if (variantBuilderServices.projectOptions[BooleanOption.TEST_SUITE_SUPPORT]) {
                    variantBuilderServices.issueReporter.reportWarning(IssueReporter.Type.GENERIC, unstableNotice)
                } else {
                    variantBuilderServices.issueReporter.reportError(
                        IssueReporter.Type.GENERIC,
                        unstableNotice +
                                "If you want to use test suites support, acknowledge the warning by " +
                                "setting `${BooleanOption.TEST_SUITE_SUPPORT.propertyName}=true` to gradle.properties"
                    )
                }
            }

            return dslDefinedTestSuites.associate { agpTestSuite ->
                agpTestSuite.testSuite.getJunitEngineIfUsed()
                    ?: throw RuntimeException("Test suites must use junit engines for now")
                agpTestSuite.testSuite.name to
                        // TODO: lock JUnitEngineSpec instance.
                        variantBuilderServices.newInstance(
                            TestSuiteBuilderImpl::class.java,
                            agpTestSuite.testSuite.name,
                            true,
                            agpTestSuite,
                            variantBuilderServices
                        )
            }
        }
    }

    internal val codeCoverage = testSuiteDslInfo.testSuite.codeCoverage

    override val junitEngineSpec: JUnitEngineSpecBuilder =
        JUnitEngineSpecForVariantBuilder(
            objects,
            testSuiteDslInfo.testSuite.useJunitEngine as JUnitEngineSpecImpl
        )

    internal val testSuite = testSuiteDslInfo.testSuite

    override val targets = testSuiteDslInfo.targets.associate {
        it.name to TestSuiteTargetBuilderImpl(it)
    }

    override fun getName(): String = _name
    internal fun getSources(): Collection<TestSuiteSourceCreationConfig>
    {
        // if the user does not define a single source set, add an assets one by default.
        if (testSuite.getSourceContainers().isEmpty()) {
            return listOf(TestSuiteAssetsSpecImpl(objects, _name,))
        }
        return testSuite.getSourceContainers()
    }
}

internal class JUnitEngineSpecForVariantBuilder(
    objects: ObjectFactory,
    dslDefinedJUnitEngineSpec: JUnitEngineSpecImpl
): JUnitEngineSpecBuilder {
    override val includeEngines: MutableSet<String> =
        mutableSetOf<String>().also { list ->
            list.addAll(dslDefinedJUnitEngineSpec.includeEngines)
        }
    override val inputs: MutableList<AgpTestSuiteInputParameters> =
        mutableListOf<AgpTestSuiteInputParameters>().also { list ->
            list.addAll(dslDefinedJUnitEngineSpec.inputs)
        }

    override fun addInputProperty(propertyName: String, propertyValue: String) {
        inputProperties.put(propertyName, propertyValue)
    }

    override fun addInputProperty(propertyName: String, propertyValue: Provider<String>) {
        inputProperties.put(propertyName, propertyValue)
    }

    internal val inputProperties: MapProperty<String, String> =
        objects.mapProperty(String::class.java, String::class.java).also {
            it.putAll(dslDefinedJUnitEngineSpec.inputStaticProperties)
            dslDefinedJUnitEngineSpec.inputProperties.forEach { (t, u) ->
                it.put(t, u)
            }
        }

    override val enginesDependencies: DependencyCollector =
        dslDefinedJUnitEngineSpec.enginesDependencies
}
