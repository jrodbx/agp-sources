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

import com.android.build.api.variant.TestSuiteBuilder
import com.android.build.gradle.internal.component.TestSuiteCreationConfig

interface HasTestSuitesCreationConfig {

  /**
   * Variant's [TestSuiteBuilder] configuration to configure test suites associated with this variant.
   *
   * @return a [Map] which keys are unique names within the test suites
   */
  val suites: Map<String, TestSuiteCreationConfig>

  /** Internal API to add a new test suite to this variant. */
  fun addTestSuite(testName: String, testComponent: TestSuiteCreationConfig)
}
