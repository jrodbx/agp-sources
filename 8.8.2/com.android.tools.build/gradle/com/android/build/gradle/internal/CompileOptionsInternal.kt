/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.internal

import com.android.build.api.dsl.CompileOptions
import org.gradle.api.JavaVersion
import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.Restricted

interface CompileOptionsInternal : CompileOptions {
    // Because expressions that reference enums are not supported, this is a factory func that returns an enum
    @Restricted
    fun getjava17(): JavaVersion {
        return JavaVersion.VERSION_17
    }

    @Adding
    fun sourceCompatibility(sourceCompatibility: String)

    @Adding
    fun targetCompatibility(targetCompatibility: String)
}
