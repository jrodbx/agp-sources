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

import com.android.build.gradle.api.JavaCompileOptions
import com.android.build.gradle.internal.core.dsl.features.AndroidResourcesDslInfo

/**
 * Represents the dsl info for a component that is part of the kotlin multiplatform android plugin,
 * initialized from extension.
 *
 * This class allows querying for the values set via the DSL model.
 **
 * @see [com.android.build.gradle.internal.component.KmpComponentCreationConfig]
 */
interface KmpComponentDslInfo: ComponentDslInfo {
    val buildTypeMatchingFallbacks: List<String>

    // KMP doesn't support android resources
    override val androidResourcesDsl: AndroidResourcesDslInfo?
        get() = null

    override val javaCompileOptionsSetInDSL: JavaCompileOptions
        get() {
            throw IllegalAccessException("Not supported")
        }
}
