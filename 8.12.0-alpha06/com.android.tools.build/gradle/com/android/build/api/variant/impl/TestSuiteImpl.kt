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
import com.android.build.gradle.internal.component.TestSuiteCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.testsuites.JUnitEngineSpec
import com.android.build.gradle.internal.testsuites.TestSuite
import com.android.build.gradle.internal.testsuites.impl.JUnitEngineSpecForVariantBuilder
import com.android.build.gradle.internal.testsuites.impl.TestSuiteBuilderImpl
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test

/**
 * Implementation of [com.android.build.gradle.internal.testsuites.TestSuite] for test suites declared via the DSL.
 */
class TestSuiteImpl internal constructor(
    testSuiteBuilder: TestSuiteBuilderImpl,
    override val sources: Collection<TestSuiteSourceContainer>,
    override val testedVariant: VariantCreationConfig,
    override val global: GlobalTaskCreationConfig,
    val variantServices: VariantServices,
    override val services: TaskCreationServices,
    override val artifacts: ArtifactsImpl,
    override val testTaskName: String,
) : TestSuite, TestSuiteCreationConfig {

    private val _name = testSuiteBuilder.name

    override fun getName() = _name

    override val junitEngineSpec: JUnitEngineSpec =
            JUnitEngineSpecImplForVariant(
                testSuiteBuilder.junitEngineSpec as JUnitEngineSpecForVariantBuilder,
                { variantServices.mapPropertyOf(String::class.java, String::class.java, mapOf()) }
            )

    override fun configureTestTask(action: (Test) -> Unit) {
        throw RuntimeException("Not yet implemented")
    }

    override fun runTestTaskConfigurationActions(testTask: TaskProvider<out Test>) {
        throw RuntimeException("Not yet implemented")
    }
}
