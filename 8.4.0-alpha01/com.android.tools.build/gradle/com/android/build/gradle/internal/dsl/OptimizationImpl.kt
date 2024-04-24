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

abstract class OptimizationImpl@Inject constructor(
    dslService: DslServices
) : Optimization {

    abstract val keepRules: KeepRules
    abstract val baselineProfile: BaselineProfile

    override fun keepRules(action: KeepRules.() -> Unit) {
        action.invoke(keepRules)
    }

    override fun baselineProfile(action: BaselineProfile.() -> Unit) {
        action.invoke(baselineProfile)
    }

    fun initWith(that: OptimizationImpl) {
        (keepRules as KeepRulesImpl).ignoreAllDependencies =
                (that.keepRules as KeepRulesImpl).ignoreAllDependencies

        (keepRules as KeepRulesImpl).dependencies.clear()
        (keepRules as KeepRulesImpl).dependencies.addAll(
                (that.keepRules as KeepRulesImpl).dependencies)

        (baselineProfile as BaselineProfileImpl).ignoreFromAllExternalDependencies =
                (that.baselineProfile as BaselineProfileImpl).ignoreFromAllExternalDependencies

        (baselineProfile as BaselineProfileImpl).ignoreFrom.clear()
        (baselineProfile as BaselineProfileImpl).ignoreFrom.addAll(
                (that.baselineProfile as BaselineProfileImpl).ignoreFrom)

    }
}
