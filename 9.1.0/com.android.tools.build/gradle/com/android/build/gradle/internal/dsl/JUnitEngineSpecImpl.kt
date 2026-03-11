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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.AgpTestSuiteInputParameters
import com.android.build.api.dsl.JUnitEngineSpec
import org.gradle.api.provider.Provider

abstract class JUnitEngineSpecImpl : JUnitEngineSpec {

  override val includeEngines = mutableSetOf<String>()

  /**
   * Adds one or more engine IDs to the [includeEngines] set.
   *
   * This method is provided for Groovy DSL support, allowing a more idiomatic syntax. For example:
   * ```groovy
   * includeEngines 'journeys-test-engine', 'another-engine'
   * ```
   *
   * @param includeEngines The unique IDs of the JUnit Platform engines to add.
   */
  fun includeEngines(vararg includeEngines: String) {
    this.includeEngines.addAll(includeEngines)
  }

  override val inputs = mutableListOf<AgpTestSuiteInputParameters>()

  /**
   * Adds one or more AGP-provided parameters to the [inputs] list.
   *
   * This method is provided for Groovy DSL support, allowing a more idiomatic syntax. For example:
   * ```groovy
   * inputs AgpTestSuiteInputParameters.TESTED_APKS, AgpTestSuiteInputParameters.ADB_EXECUTABLE
   * ```
   *
   * @param inputs The [AgpTestSuiteInputParameters] to add.
   */
  fun inputs(vararg inputs: AgpTestSuiteInputParameters) {
    this.inputs.addAll(inputs)
  }

  override fun addInputProperty(propertyName: String, propertyValue: String) {
    inputStaticProperties[propertyName] = propertyValue
  }

  override fun addInputProperty(propertyName: String, propertyValue: Provider<String>) {
    inputProperties[propertyName] = propertyValue
  }

  internal val inputStaticProperties = mutableMapOf<String, String>()
  internal val inputProperties = mutableMapOf<String, Provider<String>>()
}
