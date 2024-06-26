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

package com.android.build.gradle.internal.component.features

import com.android.build.api.variant.Dexing
import com.android.build.gradle.internal.scope.Java8LangSupport
import com.android.builder.dexing.DexingType

/**
 * Creation config for components that runs D8, usually these are components that generates APKs.
 *
 * To use this in a task that requires dexing support, use
 * [com.android.build.gradle.internal.tasks.factory.features.DexingTaskCreationAction].
 * Otherwise, access the property on the component
 * [com.android.build.gradle.internal.component.ApkCreationConfig.dexing].
 */
interface DexingCreationConfig: Dexing {

    fun finalizeAndLock()

    /**
     * The minimum API level that the output `.dex` files support.
     *
     * Note that this value may be different from the minSdkVersion specified in the DSL/manifest.
     * For example, if the IDE is deploying to a device (i.e., the API level of the device is known)
     * and if a few more conditions are met, AGP may use a higher minSdkVersion for dexing to
     * improve build performance.
     */
    val minSdkVersionForDexing: Int

    val isCoreLibraryDesugaringEnabled: Boolean

    val dexingType: DexingType

    val needsMainDexListForBundle: Boolean

    val java8LangSupportType: Java8LangSupport

    /** If this variant should package desugar_lib DEX in the final APK. */
    val shouldPackageDesugarLibDex: Boolean

    /**
     * Returns if we need to shrink desugar lib when desugaring Core Library.
     */
    val needsShrinkDesugarLibrary: Boolean
}
