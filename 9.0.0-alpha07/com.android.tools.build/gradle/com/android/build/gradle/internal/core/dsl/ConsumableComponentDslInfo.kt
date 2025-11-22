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

package com.android.build.gradle.internal.core.dsl

import com.android.build.gradle.internal.core.dsl.features.BuildConfigDslInfo
import com.android.build.gradle.internal.core.dsl.features.ManifestPlaceholdersDslInfo
import com.android.build.gradle.internal.core.dsl.features.OptimizationDslInfo
import com.android.build.gradle.internal.core.dsl.features.RenderscriptDslInfo
import com.android.build.gradle.internal.core.dsl.features.ShadersDslInfo

/**
 * Contains the final dsl info computed from the DSL object model (extension, default config,
 * build type, flavors) that are needed by consumable components.
 */
interface ConsumableComponentDslInfo: ComponentDslInfo {

    val optimizationDslInfo: OptimizationDslInfo

    // optional features

    val shadersDslInfo: ShadersDslInfo?
    val renderscriptDslInfo: RenderscriptDslInfo?
    val buildConfigDslInfo: BuildConfigDslInfo?
    val manifestPlaceholdersDslInfo: ManifestPlaceholdersDslInfo?
}
