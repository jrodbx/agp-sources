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

import com.android.build.api.artifact.ArtifactType
import com.android.build.api.variant.BuiltArtifact
import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.gradle.internal.api.artifact.toArtifactType
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Path

data class BuiltArtifactImpl(
    override val outputFile: Path,
    override val properties: Map<String, String>,
    override val versionCode: Int,
    override val versionName: String,
    override val isEnabled: Boolean,
    override val outputType: VariantOutputConfiguration.OutputType,
    override val filters: Collection<FilterConfiguration>
) : BuiltArtifact {
    fun newOutput(newOutputFile: Path): BuiltArtifactImpl {
        return BuiltArtifactImpl(newOutputFile,
            properties, versionCode, versionName, isEnabled, outputType, filters)
    }
}

internal class BuiltArtifactTypeAdapter: TypeAdapter<BuiltArtifact>() {

    @Throws(IOException::class)
    override fun write(out: JsonWriter, value: BuiltArtifact?) {
        if (value == null) {
            out.nullValue()
            return
        }
        out.beginObject()
        out.name("type").value(value.outputType.toString())
        out.name("filters").beginArray()
        for (filter in value.filters) {
            out.beginObject()
            out.name("filterType").value(filter.filterType.toString())
            out.name("value").value(filter.identifier)
            out.endObject()
        }
        out.endArray()
        out.name("properties").beginArray()
        for (entry in value.properties.entries) {
            out.beginObject()
            out.name("key").value(entry.key)
            out.name("value").value(entry.value)
            out.endObject()
        }
        out.endArray()
        out.name("versionCode").value(value.versionCode)
        out.name("versionName").value(value.versionName)
        out.name("enabled").value(value.isEnabled)
        out.name("outputFile").value(value.outputFile.toString())
        out.endObject()
    }

    @Throws(IOException::class)
    override fun read(reader: JsonReader): BuiltArtifact {
        reader.beginObject()
        var outputType: String? = null
        val filters = ImmutableList.builder<FilterConfiguration>()
        val properties = ImmutableMap.Builder<String, String>()
        var versionCode= 0
        var versionName: String? = null
        var outputFile: String? = null
        var isEnabled = true

        while (reader.hasNext()) {
            when (reader.nextName()) {
                "type" -> outputType = reader.nextString()
                "filters" -> readFilters(reader, filters)
                "properties" -> readProperties(reader, properties)
                "versionCode" -> versionCode = reader.nextInt()
                "versionName" -> versionName = reader.nextString()
                "outputFile" -> outputFile = reader.nextString()
                "enabled" -> isEnabled = reader.nextBoolean()
            }
        }
        reader.endObject()

        return BuiltArtifactImpl(
            outputFile = FileSystems.getDefault().getPath(outputFile!!),
            properties = properties.build(),
            versionCode = versionCode,
            versionName = versionName.orEmpty(),
            isEnabled = isEnabled,
            outputType = VariantOutputConfiguration.OutputType.valueOf(outputType!!),
            filters = filters.build())
    }

    @Throws(IOException::class)
    private fun readFilters(reader: JsonReader, filters: ImmutableList.Builder<FilterConfiguration>) {

        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginObject()
            var filterType: FilterConfiguration.FilterType? = null
            var value: String? = null
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "filterType" -> filterType = FilterConfiguration.FilterType.valueOf(reader.nextString())
                    "value" -> value = reader.nextString()
                }
            }
            if (filterType != null && value != null) {
                filters.add(FilterConfiguration(filterType, value))
            }
            reader.endObject()
        }
        reader.endArray()
    }

    @Throws(IOException::class)
    private fun readProperties(reader: JsonReader, properties: ImmutableMap.Builder<String, String>) {

        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginObject()
            var key: String? = null
            var value: String? = null
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "key" -> key = reader.nextString()
                    "value" -> value = reader.nextString()
                }
            }
            if (key != null) {
                properties.put(key, value.orEmpty())
            }
            reader.endObject()
        }
        reader.endArray()
    }
}

internal class ArtifactTypeTypeAdapter : TypeAdapter<ArtifactType<*>>() {

    @Throws(IOException::class)
    override fun write(out: JsonWriter, value: ArtifactType<*>) {
        out.beginObject()
        out.name("type").value(value.name())
        out.name("kind").value(value.kind.dataType().simpleName)
        out.endObject()
    }

    @Throws(IOException::class)
    override fun read(reader: JsonReader): ArtifactType<*> {
        reader.beginObject()
        var artifactType: ArtifactType<*>? = null
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "type" -> artifactType = reader.nextString().toArtifactType()
                "kind" -> reader.nextString()
            }
        }
        reader.endObject()
        if (artifactType == null) {
            throw IOException("Invalid artifact type declaration")
        }
        return artifactType
    }
}