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

package com.android.build.gradle.internal.testsuites

import com.android.build.api.variant.HasHostTestsBuilder
import org.gradle.api.Incubating
import org.gradle.api.Named
import org.gradle.api.tasks.testing.Test

interface TestSuite: Named {

    /**
     * Runs some action to configure the Variant's unit test [Test] task.
     *
     * The action will only run if the task is configured. In particular the
     * [HasHostTestsBuilder.hostTests[HasHostTestsBuilder.UNIT_TEST_TYPE]?.enable] must be set to
     * true (it is true by default).
     *
     * Example :
     * ```(kotlin)
     *  androidComponents {
     *      onVariants { variant ->
     *          variant.hostTests[HostTestsBuilder.UNIT_TEST_TYPE]?.configureTestTask { testTask ->
     *              testTask.beforeTest { descriptor ->
     *                  println("Running test: " + descriptor)
     *              }
     *          }
     *      }
     *  }
     * ```
     * @param action to configure the [Test] task.
     */
    fun configureTestTask(action: (Test)-> Unit)

    /**
     * Returns the [JUnitEngineSpec] for this test suite.
     */
    @get:Incubating
    val junitEngineSpec: JUnitEngineSpec
}
