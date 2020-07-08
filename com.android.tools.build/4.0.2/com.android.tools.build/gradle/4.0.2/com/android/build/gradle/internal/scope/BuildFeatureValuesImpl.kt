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

package com.android.build.gradle.internal.scope

import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.LibraryBuildFeatures
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions

class BuildFeatureValuesImpl(
    private val projectOptions: ProjectOptions
) : BuildFeatureValues {
    lateinit var dslBuildFeatures: BuildFeatures

    // add new flags here with computation:
    // dslFeatures.flagX ?: projectOptions[BooleanOption.FlagX]

    // ------------------
    // Common flags

    override val aidl: Boolean
        get() = dslBuildFeatures.aidl ?: projectOptions[BooleanOption.BUILD_FEATURE_AIDL]

    override val compose: Boolean
        get() = dslBuildFeatures.compose ?: false

    override val buildConfig: Boolean
        get() = dslBuildFeatures.buildConfig ?: projectOptions[BooleanOption.BUILD_FEATURE_BUILDCONFIG]

    override val dataBinding: Boolean
        get() = dslBuildFeatures.dataBinding ?: projectOptions[BooleanOption.BUILD_FEATURE_DATABINDING]

    override val renderScript: Boolean
        get() = dslBuildFeatures.renderScript ?: projectOptions[BooleanOption.BUILD_FEATURE_RENDERSCRIPT]

    override val resValues: Boolean
        get() = dslBuildFeatures.resValues ?: projectOptions[BooleanOption.BUILD_FEATURE_RESVALUES]

    override val shaders: Boolean
        get() = dslBuildFeatures.shaders ?: projectOptions[BooleanOption.BUILD_FEATURE_SHADERS]

    override val viewBinding: Boolean
        get() = dslBuildFeatures.viewBinding ?: projectOptions[BooleanOption.BUILD_FEATURE_VIEWBINDING]

    // ------------------
    // Application flags

    // ------------------
    // Dynamic-Feature flags

    // ------------------
    // Library flags

    override val buildType: Boolean
        get() = true

    // ------------------
    // Test flags
}