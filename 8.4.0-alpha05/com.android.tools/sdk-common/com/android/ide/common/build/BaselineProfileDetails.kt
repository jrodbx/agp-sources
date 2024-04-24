/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.ide.common.build

import java.io.File

/**
 * Specifies Baseline Profiles file and the API range that they support
 *
 * @param minApi the lower bound of the API range that this baseline profile targets
 * @param maxApi the upper bound that this baseline profile targets; no bound if null
 * @param baselineProfiles the baseline profile files that should be installed
 */
data class BaselineProfileDetails(
    val minApi: Int,
    val maxApi: Int,
    val baselineProfiles: Set<File>
) {
    fun getBaselineProfileFile(apkName: String) =
        baselineProfiles.singleOrNull {
            it.nameWithoutExtension == apkName
        } ?: error("Cannot find apkName $apkName in baselineProfiles $baselineProfiles")
}
