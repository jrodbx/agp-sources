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

package com.android.build.api.variant.impl

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.impl.computeTaskName
import com.android.build.api.dsl.TestTaskContext
import com.android.build.api.variant.JUnitEngineSpec
import com.android.build.api.variant.TestSuite
import com.android.build.gradle.internal.component.TestSuiteCreationConfig
import com.android.build.gradle.internal.component.TestSuiteTargetCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.testsuites.impl.JUnitEngineSpecForVariantBuilder
import com.android.build.gradle.internal.testsuites.impl.TestSuiteBuilderImpl
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test

/**
 * Implementation of [TestSuite] for test suites declared via the DSL.
 */
class TestSuiteImpl internal constructor(
    testSuiteBuilder: TestSuiteBuilderImpl,
    override val sources: Collection<TestSuiteSourceContainer>,
    override val testedVariant: VariantCreationConfig,
    override val global: GlobalTaskCreationConfig,
    val variantServices: VariantServices,
    override val services: TaskCreationServices,
    override val artifacts: ArtifactsImpl,
) : TestSuite, TestSuiteCreationConfig {

    private val _name = testSuiteBuilder.name
    //
    // Public APIs
    //
    override fun getName() = _name

    override val junitEngineSpec: JUnitEngineSpec =
            JUnitEngineSpecImplForVariant(
                testSuiteBuilder.junitEngineSpec as JUnitEngineSpecForVariantBuilder,
                { variantServices.mapPropertyOf(String::class.java, String::class.java, mapOf()) }
            )

    override val targets: Map<String, TestSuiteTargetCreationConfig> =
        testSuiteBuilder.targets.mapValues { entry ->
            TestSuiteTargetImpl(
                entry.value,
                computeTaskName(
                    testedVariant.name,
                    "test${_name.capitalizeFirstChar()}${entry.value.uniqueName().capitalizeFirstChar()}",
                    "TestSuite"
                )
            )
        }

    @Synchronized
    override fun configureTestTasks(action: Test.(context: TestTaskContext) -> Unit) {
        testTaskConfigActions.add(action)
    }

    override val codeCoverage: Property<Boolean> = variantServices.propertyOf(
        Boolean::class.java, testSuiteBuilder.codeCoverage
    )

    /**
     * Internal APIs
     */
    private val testTaskConfigActions = mutableListOf<Test.(TestTaskContext) -> Unit>().also {
        it.addAll(testSuiteBuilder.testSuite.testTaskConfigActions)
    }

    @Synchronized
    override fun runTestTaskConfigurationActions(
        context: TestTaskContext,
        testTaskProvider: TaskProvider<out Test>
    ) {
        testTaskConfigActions.forEach {
            testTaskProvider.configure { testTask -> it(testTask, context) }
        }
    }
}
