/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.core.dsl.impl.features

import com.android.build.api.dsl.ApplicationBuildType
import com.android.build.api.dsl.BuildType
import com.android.build.gradle.internal.core.MergedFlavor
import com.android.build.gradle.internal.core.dsl.features.DexingDslInfo
import java.io.File

class DexingDslInfoImpl(
    private val buildTypeObj: BuildType,
    private val mergedFlavor: MergedFlavor
): DexingDslInfo {

    // Only require specific multidex opt-in for legacy multidex.
    override val isMultiDexEnabled: Boolean?
        get() {
            // Only require specific multidex opt-in for legacy multidex.
            return (buildTypeObj as? ApplicationBuildType)?.multiDexEnabled
                ?: mergedFlavor.multiDexEnabled
        }
    override val multiDexKeepProguard: File?
        get() {
            var value = buildTypeObj.multiDexKeepProguard
            if (value != null) {
                return value
            }
            value = mergedFlavor.multiDexKeepProguard
            return value
        }
    override val multiDexKeepFile: File?
        get() {
            var value = buildTypeObj.multiDexKeepFile
            if (value != null) {
                return value
            }
            value = mergedFlavor.multiDexKeepFile
            return value
        }
}
