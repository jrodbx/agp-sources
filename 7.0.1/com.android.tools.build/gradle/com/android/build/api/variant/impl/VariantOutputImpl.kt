/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.VariantOutput
import com.android.build.api.variant.VariantOutputConfiguration
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import java.io.File
import java.io.Serializable

data class VariantOutputImpl(
    @get:Input
    @get:Optional
    override val versionCode: Property<Int?>,
    @get:Input
    @get:Optional
    override val versionName: Property<String?>,
    @get:Input
    override val enabled: Property<Boolean>,

    @get:Internal
    val variantOutputConfiguration: VariantOutputConfigurationImpl,

    // private APG APIs.
    @get:Input
    val baseName: String,
    @get:Input
    val fullName: String,
    @get:Input
    val outputFileName: Property<String>
) : VariantOutput, VariantOutputConfiguration {

    @get:Internal
    override val enable = enabled

    data class SerializedForm(
        @get:Input
        val versionCode: Int?,
        @get:Input
        val versionName: String?,
        @get:Input
        val variantOutputConfiguration: VariantOutputConfigurationImpl,
        @get:Input
        val baseName: String,
        @get:Input
        val fullName: String,
        @get:Input
        val outputFileName: String): Serializable {

        fun toBuiltArtifact(outputFile: File, properties: Map<String, String>): BuiltArtifactImpl =
            BuiltArtifactImpl.make(
                outputFile = outputFile.absolutePath,
                versionCode = versionCode,
                versionName = versionName,
                variantOutputConfiguration = variantOutputConfiguration
            )
    }

    fun toBuiltArtifact(outputFile: File): BuiltArtifactImpl =
        BuiltArtifactImpl.make(
            outputFile = outputFile.absolutePath,
            versionCode = versionCode.orNull,
            versionName = versionName.orNull,
            variantOutputConfiguration = variantOutputConfiguration
        )

    fun toSerializedForm() = SerializedForm(
        versionCode = versionCode.orNull,
        versionName = versionName.orNull,
        variantOutputConfiguration = variantOutputConfiguration,
        fullName = fullName,
        baseName = baseName,
        outputFileName = outputFileName.get())

    fun getFilter(filterType: FilterConfiguration.FilterType): FilterConfiguration? =
        filters.firstOrNull { it.filterType == filterType }

    @get:Input
    override val outputType: VariantOutputConfiguration.OutputType
        get() = variantOutputConfiguration.outputType

    @get:Nested
    override val filters: Collection<FilterConfigurationImpl>
        get() = variantOutputConfiguration.filters
}
