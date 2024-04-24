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

package com.android.build.api.variant.impl

import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

/**
 * Temporary interface to develop the kotlin multiplatform android plugin.
 *
 * TODO(b/267309622): Move to gradle-api
 */
interface KotlinMultiplatformAndroidTarget: KotlinTarget {
    val options: KotlinMultiplatformAndroidExtension

    fun options(action: KotlinMultiplatformAndroidExtension.() -> Unit)

    fun onMainCompilation(action: KotlinMultiplatformAndroidCompilation.() -> Unit)

    fun onUnitTestCompilation(action: KotlinMultiplatformAndroidCompilation.() -> Unit)

    fun onInstrumentedTestCompilation(action: KotlinMultiplatformAndroidCompilation.() -> Unit)
}
