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

import com.android.build.api.dsl.AndroidResources

interface LibraryCreationConfig: VariantCreationConfig, AarCreationConfig {
    /**
     * Access to the (locked) AndroidResources DSL.
     * Libraries don't allow overriding these values via the Variant API but they still need access
     * to the DSL-set values.
     */
    val dslAndroidResources: AndroidResources
}
