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

import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.ApkPackaging
import com.android.build.api.variant.impl.BundleConfigImpl
import com.android.build.api.variant.impl.SigningConfigImpl
import com.android.build.gradle.internal.component.features.DexingCreationConfig

/**
 * Interface for properties common to all variant generating APKs
 */
interface ApkCreationConfig: ConsumableCreationConfig {
    val targetSdk: AndroidVersion
    val targetSdkOverride: AndroidVersion?

    val embedsMicroApp: Boolean

    // TODO: move to a non variant object (GlobalTaskScope?)
    val testOnlyApk: Boolean

    /**
     * If this variant should package additional dependencies (code and native libraries) needed for
     * profilers support in the IDE.
     */
    val shouldPackageProfilerDependencies: Boolean

    /** List of transforms for profilers support in the IDE. */
    val advancedProfilingTransforms: List<String>

    override val packaging: ApkPackaging

    /**
     * Variant's signing information of null if signing is not configured for this variant.
     */
    val signingConfigImpl: SigningConfigImpl?

    val bundleConfig: BundleConfigImpl?
        get() = null

    val useJacocoTransformInstrumentation: Boolean

    val packageJacocoRuntime: Boolean

    val dexingCreationConfig: DexingCreationConfig

    val enableApiModeling: Boolean

    val enableGlobalSynthetics: Boolean
}
