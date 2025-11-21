/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.model.CxxVariantModel
import com.android.utils.cxx.CxxDiagnosticCode.INVALID_EXTERNAL_NATIVE_BUILD_CONFIG

/**
 * Check some basic requirements. This code executes at sync time but any call to
 * recordConfigurationError will later cause the generation of json to fail.
*/
fun cmakeMakefileChecks(variant: CxxVariantModel) {
    val cmakelists = variant.module.makeFile
    if (cmakelists.isDirectory) {
        errorln(
            INVALID_EXTERNAL_NATIVE_BUILD_CONFIG,
            "Gradle project cmake.path %s is a folder. It must be CMakeLists.txt",
            cmakelists
        )
    } else if (cmakelists.isFile) {
        val filename = cmakelists.name
        if (filename != "CMakeLists.txt") {
            errorln(
                INVALID_EXTERNAL_NATIVE_BUILD_CONFIG,
                "Gradle project cmake.path specifies %s but it must be CMakeLists.txt",
                filename
            )
        }
    } else {
        errorln(
            INVALID_EXTERNAL_NATIVE_BUILD_CONFIG,
            "Gradle project cmake.path is %s but that file doesn't exist",
            cmakelists
        )
    }
}