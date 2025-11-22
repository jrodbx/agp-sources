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

package com.android.build.api.variant

import com.android.build.api.dsl.TestTaskContext
import org.gradle.api.Incubating
import org.gradle.api.Named
import org.gradle.api.tasks.testing.Test

/**
 * Model for test suites.
 *
 * This object is accessible on subtypes of [Variant] that implement [HasTestSuites], via
 * [HasTestSuites.suites].
 */
interface TestSuite: Named {

    /**
     * Configure the test tasks for this test target.
     *
     * There can be one to many instances of [org.gradle.api.tasks.testing.Test] tasks for a particular test suite target. For
     * instance, if the test suite targets more than one device, AGP may decide to create one [org.gradle.api.tasks.testing.Test]
     * instance per device.
     *
     * The configuration block can use the [action]'s context parameter to disambiguate between each
     * [org.gradle.api.tasks.testing.Test] task instance.
     *
     * Do not make assumption about how AGP decides to allocate [org.gradle.api.tasks.testing.Test] task instances per device, as
     * each AGP version can potentially change it in future release, always use the context object to
     * determine what the [org.gradle.api.tasks.testing.Test] task applies to.
     *
     * @param action a block to configure the [org.gradle.api.tasks.testing.Test] tasks associated with this test suite target.
     *
     * Example :
     * ```(kotlin)
     *  androidComponents {
     *      onVariants { variant ->
     *          variant.testSuites.forEach { testSuite ->
     *              testSuite.configureTestTask { testTask ->
     *                  testTask.beforeTest { descriptor ->
     *                      println("Running test: " + descriptor)
     *                  }
     *              }
     *          }
     *      }
     *  }
     * ```
     * @param action to configure the [org.gradle.api.tasks.testing.Test] task.
     */
    fun configureTestTasks(action: Test.(context: TestTaskContext) -> Unit)

    /**
     * Returns the [JUnitEngineSpec] for this test suite.
     */
    @get:Incubating
    val junitEngineSpec: JUnitEngineSpec

    /**
     * Returns the list of [TestSuiteTarget] for this test suite in this variant.
     */
    val targets: Map<String, TestSuiteTarget>
}
