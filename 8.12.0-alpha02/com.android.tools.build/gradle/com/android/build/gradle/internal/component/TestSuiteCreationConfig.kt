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

package com.android.build.gradle.internal.component

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.gradle.internal.api.TestSuiteSourceSet
import com.android.build.gradle.internal.dependency.TestSuiteClasspath
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.testsuites.JUnitEngineSpec
import com.android.build.gradle.internal.testsuites.TestSuite
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test

interface TestSuiteCreationConfig: TestSuite {

    val global: GlobalTaskCreationConfig

    fun runTestTaskConfigurationActions(testTask: TaskProvider<out Test>)

    /**
     * Returns information on the junit engines to run the tests with or null if no junit test
     * engine needs to be configured.
     */
    override val junitEngineSpec: JUnitEngineSpec

    // Internal delegates.
    val services: TaskCreationServices

    /**
     * Tested variant, should be only read-only at this point.
     */
    val testedVariant: VariantCreationConfig

    /**
     * Returns all the classpath Configurations for this test suite
     */
    val testSuiteClasspath: TestSuiteClasspath

    /**
     * Returns the sources for this test suite.
     */
    val sources: TestSuiteSourceSet

    /**
     * Artifacts specific to this Test suite.
     */
    val artifacts: ArtifactsImpl

    /**
     * Test task name, within the current project scope.
     */
    val testTaskName: String
}
