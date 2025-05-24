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

import com.android.build.api.dsl.BuildType
import com.android.build.gradle.internal.core.MergedFlavor
import com.android.build.gradle.internal.core.dsl.features.RenderscriptDslInfo

class RenderscriptDslInfoImpl(
    private val mergedFlavor: MergedFlavor,
    private val buildTypeObj: BuildType
): RenderscriptDslInfo {

    override val renderscriptTarget: Int
        get() = mergedFlavor.renderscriptTargetApi ?: -1
    override val renderscriptSupportModeEnabled: Boolean
        get() = mergedFlavor.renderscriptSupportModeEnabled ?: false
    override val renderscriptSupportModeBlasEnabled: Boolean
        get() {
            val value = mergedFlavor.renderscriptSupportModeBlasEnabled
            return value ?: false
        }
    override val renderscriptNdkModeEnabled: Boolean
        get() = mergedFlavor.renderscriptNdkModeEnabled ?: false

    override val renderscriptOptimLevel: Int
        get() = buildTypeObj.renderscriptOptimLevel
}
