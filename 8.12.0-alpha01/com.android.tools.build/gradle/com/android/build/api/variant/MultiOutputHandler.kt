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

package com.android.build.api.variant

import com.android.build.api.variant.impl.ApplicationMultiOutputHandler
import com.android.build.api.variant.impl.BuiltArtifactImpl
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.SingleOutputHandler
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import java.io.File

/**
 * An abstraction of all operations that apply to multi output artifacts, including finding the main
 * artifact, getting the output of a specific split, ...
 *
 * This interface has two different implementations for application variants and the rest of the
 * components that don't have splits, since we currently use the same tasks and artifact types for
 * all types of components.
 *
 * Use [MultiOutputHandler.create] to create an object for a component and the implementation should
 * handle both splits and non-splits cases internally.
 */
interface MultiOutputHandler {

    companion object {
        fun create(creationConfig: ComponentCreationConfig): MultiOutputHandler {
            return if (creationConfig is ApplicationCreationConfig) {
                ApplicationMultiOutputHandler(creationConfig)
            } else {
                SingleOutputHandler(creationConfig)
            }
        }
    }

    @get:Internal
    val mainVersionCode: Int?
    @get:Internal
    val mainVersionName: String?

    fun getMainSplitArtifact(
        artifactsDirectory: Provider<Directory>
    ): BuiltArtifactImpl?

    fun extractArtifactForSplit(
        artifacts: BuiltArtifactsImpl,
        config: VariantOutputConfiguration
    ): BuiltArtifactImpl?

    fun getOutputs(
        configFilter: (VariantOutputConfiguration) -> Boolean
    ): Collection<VariantOutputImpl.SerializedForm>

    fun getOutput(
        config: VariantOutputConfiguration
    ): VariantOutputImpl.SerializedForm?

    fun computeBuildOutputFile(
        dir: File,
        output: VariantOutputImpl.SerializedForm,
    ): File

    fun computeUniqueDirForSplit(
        dir: File,
        output: VariantOutputImpl.SerializedForm,
        variantName: String
    ): File

    fun getOutputNameForSplit(
        prefix: String,
        suffix: String,
        outputType: VariantOutputConfiguration.OutputType,
        filters: Collection<FilterConfiguration>
    ): String

    fun toSerializable(): MultiOutputHandler
}
