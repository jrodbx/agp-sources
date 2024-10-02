/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.component

import com.android.build.api.variant.Packaging
import com.android.build.gradle.internal.component.features.NativeBuildCreationConfig
import com.android.build.gradle.internal.component.features.OptimizationCreationConfig
import com.android.build.gradle.internal.component.features.RenderscriptCreationConfig
import com.android.build.gradle.internal.component.features.ShadersCreationConfig

/**
 * CreationConfig for variants that produces an artifact that is directly install-able to devices
 * like APKs or AABs or used by other projects as a versioned reusable logic like AARs.
 */
interface ConsumableCreationConfig: ComponentCreationConfig {
    val packaging: Packaging

    val optimizationCreationConfig: OptimizationCreationConfig

    val isAndroidTestCoverageEnabled: Boolean

    /**
     * Used by lint to run checks related to core library desugaring.
     */
    val isCoreLibraryDesugaringEnabledLintCheck: Boolean

    // optional features
    val renderscriptCreationConfig: RenderscriptCreationConfig?
    val shadersCreationConfig: ShadersCreationConfig?
    val nativeBuildCreationConfig: NativeBuildCreationConfig?
}
