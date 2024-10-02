/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.core

import com.android.build.gradle.internal.PostprocessingFeatures
import com.android.build.gradle.internal.ProguardFilesProvider
import java.io.File

/**
 * This is a common interface to get post-processing options from DSL, no matter which DSL we use
 * old one or a new (block) one
 */
interface PostProcessingOptions : ProguardFilesProvider {
    fun getDefaultProguardFiles(): List<File>

    fun getPostprocessingFeatures(): PostprocessingFeatures?

    fun codeShrinkerEnabled(): Boolean

    fun resourcesShrinkingEnabled(): Boolean

    fun hasPostProcessingConfiguration(): Boolean
}
