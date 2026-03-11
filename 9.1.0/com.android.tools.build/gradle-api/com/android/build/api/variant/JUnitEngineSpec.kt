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

import com.android.build.api.dsl.AgpTestSuiteInputParameters
import org.gradle.api.Incubating
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.provider.Provider

@Incubating
interface JUnitEngineSpec {

  /**
   * Returns the final list of included engines for this tes suite.
   *
   * you must use the [AndroidComponentsExtension.beforeVariants] API and access the parameters located in
   * [TestSuiteBuilder.junitEngineSpec] to add new engines.
   */
  @get:Incubating val includeEngines: MutableSet<String>

  /** Adds a new key value pair property to the list of inputs for this test engine. */
  @Incubating fun addInputProperty(propertyName: String, propertyValue: String)

  /** Adds a new key value pair property to the list of inputs of this test engine, the value will only be resolved at execution time. */
  fun addInputProperty(propertyName: String, propertyValue: Provider<String>)

  /**
   * Returns the final list of inputs required by the junit engine running the test suite.
   *
   * Since these inputs can change the build flow, it is too late to change them here, you must use the
   * [AndroidComponentsExtension.beforeVariants] API and access the parameters located in [TestSuiteBuilder.junitEngineSpec]
   */
  @get:Incubating val inputs: List<AgpTestSuiteInputParameters>

  /** Returns a [DependencyCollector] that collects the set of runtime-only dependencies to find and load configured junit engines. */
  @get:Incubating val enginesDependencies: DependencyCollector
}
