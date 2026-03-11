/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.core

import com.android.build.gradle.internal.dsl.BaselineProfileImpl
import com.android.build.gradle.internal.dsl.KeepRulesImpl
import com.android.build.gradle.internal.dsl.OptimizationImpl
import java.io.File

class MergedOptimization : MergedOptions<OptimizationImpl> {

  var ignoreFromInKeepRules: MutableSet<String> = mutableSetOf()
    private set

  var ignoreFromAllExternalDependenciesInKeepRules: Boolean = false
    private set

  var ignoreFromInBaselineProfile: MutableSet<String> = mutableSetOf()
    private set

  var ignoreFromAllExternalDependenciesInBaselineProfile: Boolean = false
    private set

  var enable: Boolean = false
    private set

  var packageScope: Set<String> = setOf()
    private set

  var keepRuleFiles: Set<File> = setOf()
    private set

  var includeDefault: Boolean = true
    private set

  override fun reset() {
    ignoreFromInKeepRules = mutableSetOf()
    ignoreFromAllExternalDependenciesInKeepRules = false
    ignoreFromInBaselineProfile = mutableSetOf()
    ignoreFromAllExternalDependenciesInBaselineProfile = false

    enable = false
    packageScope = setOf()
    keepRuleFiles = setOf()
  }

  override fun append(option: OptimizationImpl) {
    ignoreFromInKeepRules.addAll((option.keepRules as KeepRulesImpl).ignoreFrom)
    ignoreFromAllExternalDependenciesInKeepRules =
      ignoreFromAllExternalDependenciesInKeepRules || (option.keepRules as KeepRulesImpl).ignoreFromAllExternalDependencies
    ignoreFromInBaselineProfile.addAll((option.baselineProfile as BaselineProfileImpl).ignoreFrom)
    ignoreFromAllExternalDependenciesInBaselineProfile =
      ignoreFromAllExternalDependenciesInBaselineProfile || option.baselineProfile.ignoreFromAllExternalDependencies

    // set instead of append as we only need build type that is appended last
    enable = option.enable
    includeDefault = option.keepRules.includeDefault
    packageScope = option.packageScope.get()
    if (option.keepRules.files.isPresent) keepRuleFiles = option.keepRules.files.get()
  }
}
