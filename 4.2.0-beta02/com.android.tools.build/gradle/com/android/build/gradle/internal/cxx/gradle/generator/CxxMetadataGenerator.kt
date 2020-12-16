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

import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxVariantModel
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.tasks.Internal
import org.gradle.process.ExecOperations
import java.util.concurrent.Callable

/**
 * Abstraction of C/C++ gradle model generation and build.
 */
interface CxxMetadataGenerator {
    //region Build model data
    @get:Internal
    val variant: CxxVariantModel

    @get:Internal
    val abis: List<CxxAbiModel>

    @get:Internal
    val variantBuilder: GradleBuildVariant.Builder
    //endregion

    //region Build metadata generation
    /**
     * Get futures that will create native build metadata.
     *
     * If [forceGeneration] is true then rebuild metadata regardless of whether
     * it is otherwise considered to be up-to-date. This flag will be set when
     * the user chose Build/Refresh Linked C++ Projects.
     *
     * If [abiName] is specified then only that ABI will be built. Otherwise,
     * all available ABIs will be built.
     */
    fun getMetadataGenerators(
        ops: ExecOperations,
        forceGeneration: Boolean,
        abiName : String? = null
    ): List<Callable<Unit>>

    /**
     * Append all currently available C/C++ metadata to the builder without
     * running any slow processes to create metadata that isn't there. If
     * the caller needs to ensure metadata is available then first call
     * [getMetadataGenerators] and invoke futures.
     *
     * Build metadata is added to [builder].
     */
    fun addCurrentMetadata(builder: NativeAndroidProjectBuilder)
    //endregion
}
