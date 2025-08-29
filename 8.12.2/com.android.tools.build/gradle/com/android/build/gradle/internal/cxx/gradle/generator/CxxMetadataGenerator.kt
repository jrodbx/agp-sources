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

package com.android.build.gradle.internal.cxx.gradle.generator

import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.tasks.Internal
import org.gradle.process.ExecOperations

/**
 * Abstraction of C/C++ configure and gradle model generation.
 */
interface CxxMetadataGenerator {
    @get:Internal
    val variantBuilder: GradleBuildVariant.Builder?

    /**
     * Configure C/C++ and create gradle model
     *
     * If [forceConfigure] is true then configure regardless of whether it is otherwise considered
     * to be up-to-date. This flag will be set when the user chose Build/Refresh Linked C++
     * Projects.
     */
    fun configure(ops: ExecOperations, forceConfigure: Boolean)
}
