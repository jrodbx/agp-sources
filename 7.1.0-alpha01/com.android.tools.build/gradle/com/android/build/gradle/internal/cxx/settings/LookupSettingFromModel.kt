/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.settings

import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxCmakeAbiModel
import com.android.build.gradle.internal.cxx.model.CxxCmakeModuleModel
import com.android.build.gradle.internal.cxx.model.CxxModuleModel
import com.android.build.gradle.internal.cxx.model.CxxProjectModel
import com.android.build.gradle.internal.cxx.model.CxxVariantModel

/**
 * Look up [Macro] equivalent value from the C/C++ build abi model.
 */
fun CxxAbiModel.resolveMacroValue(macro : Macro) : String {
    fun fail() = "Could not resolve the C/C++ macro ${macro.ref}"
    return when(macro.bindingType) {
        Macro::class -> macro.takeFrom(macro) ?: fail()
        CxxAbiModel::class -> macro.takeFrom(this) ?: fail()
        CxxCmakeAbiModel::class -> cmake?.let { macro.takeFrom(it) ?: fail() } ?: ""
        CxxVariantModel::class -> macro.takeFrom(variant) ?: fail()
        CxxModuleModel::class -> macro.takeFrom(variant.module) ?: fail()
        CxxProjectModel::class -> macro.takeFrom(variant.module.project) ?: fail()
        CxxCmakeModuleModel::class -> variant.module.cmake?.let { macro.takeFrom(it) ?: fail() } ?: ""
        else -> fail()
    }
}
