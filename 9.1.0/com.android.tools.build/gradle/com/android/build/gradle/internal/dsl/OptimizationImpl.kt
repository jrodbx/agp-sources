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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.BaselineProfile
import com.android.build.api.dsl.KeepRules
import com.android.build.api.dsl.Optimization
import com.android.build.gradle.internal.services.DslServices
import javax.inject.Inject
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.SetProperty

abstract class OptimizationImpl @Inject constructor(dslService: DslServices, internal val objectFactory: ObjectFactory) : Optimization {

  abstract val keepRules: KeepRules
  abstract val baselineProfile: BaselineProfile

  abstract override var enable: Boolean
  override val packageScope: SetProperty<String> = objectFactory.setProperty(String::class.java).convention(listOf("**"))

  override fun keepRules(action: KeepRules.() -> Unit) {
    action.invoke(keepRules)
  }

  override fun baselineProfile(action: BaselineProfile.() -> Unit) {
    action.invoke(baselineProfile)
  }

  fun initWith(that: OptimizationImpl) {
    (keepRules as KeepRulesImpl).ignoreFromAllExternalDependencies = (that.keepRules as KeepRulesImpl).ignoreFromAllExternalDependencies

    (keepRules as KeepRulesImpl).ignoreFrom.clear()
    (keepRules as KeepRulesImpl).ignoreFrom.addAll((that.keepRules as KeepRulesImpl).ignoreFrom)

    // when merging includeDefault, prefer opt-out since it's an explicit choice
    keepRules.includeDefault = keepRules.includeDefault && that.keepRules.includeDefault

    (baselineProfile as BaselineProfileImpl).ignoreFromAllExternalDependencies =
      (that.baselineProfile as BaselineProfileImpl).ignoreFromAllExternalDependencies

    (baselineProfile as BaselineProfileImpl).ignoreFrom.clear()
    (baselineProfile as BaselineProfileImpl).ignoreFrom.addAll((that.baselineProfile as BaselineProfileImpl).ignoreFrom)

    enable = that.enable

    packageScope.empty()
    packageScope.addAll(that.packageScope)

    keepRules.files.empty()
    keepRules.files.addAll(that.keepRules.files)
  }
}
