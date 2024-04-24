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

class MergedOptimization : MergedOptions<OptimizationImpl> {

    var ignoredLibraryKeepRules: MutableSet<String> = mutableSetOf()
        private set

    var ignoreAllLibraryKeepRules: Boolean = false
        private set

    var ignoreFromInBaselineProfile: MutableSet<String> = mutableSetOf()
        private set

    var ignoreFromAllExternalDependenciesInBaselineProfile: Boolean = false
        private set

    override fun reset() {
        ignoredLibraryKeepRules = mutableSetOf()
        ignoreAllLibraryKeepRules = false
        ignoreFromInBaselineProfile = mutableSetOf()
        ignoreFromAllExternalDependenciesInBaselineProfile = false
    }

    override fun append(option: OptimizationImpl) {
        ignoredLibraryKeepRules.addAll((option.keepRules as KeepRulesImpl).dependencies)
        ignoreAllLibraryKeepRules = ignoreAllLibraryKeepRules ||
                (option.keepRules as KeepRulesImpl).ignoreAllDependencies
        ignoreFromInBaselineProfile.addAll((option.baselineProfile as BaselineProfileImpl).ignoreFrom)
        ignoreFromAllExternalDependenciesInBaselineProfile =
            ignoreFromAllExternalDependenciesInBaselineProfile ||
                    (option.baselineProfile as BaselineProfileImpl).ignoreFromAllExternalDependencies
    }
}
