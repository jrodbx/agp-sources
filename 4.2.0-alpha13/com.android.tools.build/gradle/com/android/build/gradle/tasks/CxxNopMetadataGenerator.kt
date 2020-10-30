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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.cxx.gradle.generator.CxxMetadataGenerator
import com.android.build.gradle.internal.cxx.gradle.generator.NativeAndroidProjectBuilder
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxVariantModel
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.process.ExecOperations
import java.util.concurrent.Callable

/**
 * NOP C/C++ metadata generator to be used when there was an earlier
 * configuration error. It returns an empty list from getMetadataGenerators
 * so that the calling code does nothing and expects no outputs.
 */
class CxxNopMetadataGenerator(
        override val variant: CxxVariantModel,
        override val abis: List<CxxAbiModel>,
        override val variantBuilder: GradleBuildVariant.Builder
) : CxxMetadataGenerator {
    override fun getMetadataGenerators(
            ops: ExecOperations,
            forceGeneration: Boolean,
            abiName: String?): List<Callable<Unit>> = listOf()

    override fun addCurrentMetadata(builder: NativeAndroidProjectBuilder) { }
}
