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

package com.android.ide.common.build

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.File
import java.io.IOException
import java.nio.file.Path
import com.android.utils.PathUtils

/**
 * Common behaviors for loading and saving [CommonBuiltArtifacts] subclass [T] to a json file.
 */
abstract class CommonBuiltArtifactsTypeAdapter<
        T: CommonBuiltArtifacts,
        ArtifactTypeT,
        ElementT,
        >(val projectPath: Path): TypeAdapter<T>() {

    abstract val artifactTypeTypeAdapter: TypeAdapter<ArtifactTypeT>
    abstract val elementTypeAdapter: TypeAdapter<ElementT>
    abstract fun getArtifactType(artifacts: T): ArtifactTypeT
    abstract fun getElements(artifacts: T): Collection<ElementT>
    abstract fun getElementType(artifacts: T): String?
    abstract fun getBaselineProfiles(artifacts: T): List<BaselineProfileDetails>?
    abstract fun getMinSdkVersionForDexing(artifacts: T): Int?

    final override fun write(out: JsonWriter, value: T?) {
        if (value == null) {
            out.nullValue()
            return
        }
        out.beginObject()
        out.name("version").value(value.version)
        out.name("artifactType")
        artifactTypeTypeAdapter.write(out, getArtifactType(value))
        out.name("applicationId").value(value.applicationId)
        out.name("variantName").value(value.variantName)
        out.name("elements").beginArray()
        for (element in getElements(value)) {
            elementTypeAdapter.write(out, element)
        }
        out.endArray()
        getElementType(value)?.let {elementType ->
            out.name("elementType").value(elementType)
        }
        val baselineProfiles = getBaselineProfiles(value)
        if (baselineProfiles != null) {
            out.name("baselineProfiles").beginArray()
            baselineProfiles.forEach { entry ->
                out.beginObject()
                out.name("minApi").value(entry.minApi)
                out.name("maxApi").value(entry.maxApi)
                out.name("baselineProfiles").beginArray()
                entry.baselineProfiles.forEach {
                    val relativePath = projectPath.relativize(it.toPath())
                    out.value(PathUtils.toSystemIndependentPath(relativePath))
                }
                out.endArray()
                out.endObject()
            }
            out.endArray()
        }
        getMinSdkVersionForDexing(value)?.let { out.name("minSdkVersionForDexing").value(it) }
        out.endObject()
    }


    abstract fun instantiate(
        version: Int,
        artifactType: ArtifactTypeT,
        applicationId: String,
        variantName: String,
        elements: List<ElementT>,
        elementType: String?,
        baselineProfiles: List<BaselineProfileDetails>?,
        minSdkVersionForDexing: Int?,
    ) : T

    final override fun read(reader: JsonReader): T {
        reader.beginObject()
        var version: Int? = null
        var artifactType: ArtifactTypeT? = null
        var applicationId: String? = null
        var variantName: String? = null
        val elements = mutableListOf<ElementT>()
        var elementType: String? = null
        var baselineProfiles: List<BaselineProfileDetails>? = null
        var minSdkVersionForDexing: Int? = null

        while (reader.hasNext()) {
            when (reader.nextName()) {
                "version" -> version = reader.nextInt()
                "artifactType" -> artifactType = artifactTypeTypeAdapter.read(reader)
                "applicationId" -> applicationId = reader.nextString()
                "variantName" -> variantName = reader.nextString()
                "elements" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        elements.add(elementTypeAdapter.read(reader))
                    }
                    reader.endArray()
                }
                "elementType" -> elementType = reader.nextString()
                "baselineProfiles" -> {
                    baselineProfiles = mutableListOf<BaselineProfileDetails>()
                    reader.beginArray()
                    while (reader.hasNext()) {
                        reader.beginObject()
                        var minApi: Int? = null
                        var maxApi: Int? = null
                        val baselineProfileFiles = mutableSetOf<File>()
                        while (reader.hasNext()) {
                            when (val attributeName = reader.nextName()) {
                                "minApi" -> minApi = reader.nextInt()
                                "maxApi" -> maxApi = reader.nextInt()
                                "baselineProfiles" -> {
                                    reader.beginArray()
                                    while (reader.hasNext()) {
                                        val baselineProfile =
                                            projectPath.resolve(reader.nextString())
                                                .normalize().toFile()
                                        baselineProfileFiles.add(baselineProfile)
                                    }
                                    reader.endArray()
                                }
                            }
                        }
                        reader.endObject()
                        baselineProfiles.add(
                            BaselineProfileDetails(
                                minApi ?: error("minApi is required"),
                                maxApi ?: error("maxApi is required"),
                                baselineProfileFiles
                            )
                        )
                    }
                    reader.endArray()
                }
                "minSdkVersionForDexing" -> minSdkVersionForDexing = reader.nextInt()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return instantiate(
            version ?: throw IOException("version is required"),
            artifactType ?: throw IOException("artifactType is required"),
            applicationId  ?: throw IOException("applicationId is required"),
            variantName ?: throw IOException("variantName is required"),
            elements,
            elementType,
            baselineProfiles,
            minSdkVersionForDexing
        )
    }
}

object GenericArtifactTypeTypeAdapter: TypeAdapter<GenericArtifactType>() {

    override fun write(writer: JsonWriter, type: GenericArtifactType) {
        writer.beginObject()
        writer.name("type").value(type.type)
        writer.name("kind").value(type.kind)
        writer.endObject()
    }

    override fun read(reader: JsonReader): GenericArtifactType {
        var type: String? = null
        var kind: String? = null
        reader.beginObject()
        while(reader.hasNext()) {
            when(val name = reader.nextName()) {
                "type" -> type = reader.nextString()
                "kind" -> kind = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return GenericArtifactType(
            type = type ?: throw IOException("artifactType.type is required"),
            kind = kind ?: throw IOException("artifactType.kind is required"),
        )
    }
}

class GenericBuiltArtifactsTypeAdapter(
    projectPath: Path
): CommonBuiltArtifactsTypeAdapter<
        GenericBuiltArtifacts,
        GenericArtifactType,
        GenericBuiltArtifact,
        >(projectPath) {

    override val artifactTypeTypeAdapter get() = GenericArtifactTypeTypeAdapter
    override val elementTypeAdapter: TypeAdapter<GenericBuiltArtifact>
        get() = GenericBuiltArtifactTypeAdapter
    override fun getArtifactType(artifacts: GenericBuiltArtifacts) = artifacts.artifactType
    override fun getElements(artifacts: GenericBuiltArtifacts) = artifacts.elements
    override fun getElementType(artifacts: GenericBuiltArtifacts): String? = artifacts.elementType
    override fun getBaselineProfiles(artifacts: GenericBuiltArtifacts): List<BaselineProfileDetails>? = artifacts.baselineProfiles
    override fun getMinSdkVersionForDexing(artifacts: GenericBuiltArtifacts): Int? = artifacts.minSdkVersionForDexing

    override fun instantiate(
        version: Int,
        artifactType: GenericArtifactType,
        applicationId: String,
        variantName: String,
        elements: List<GenericBuiltArtifact>,
        elementType: String?,
        baselineProfiles: List<BaselineProfileDetails>?,
        minSdkVersionForDexing: Int?,
    ) = GenericBuiltArtifacts(
        version = version,
        artifactType = artifactType,
        applicationId = applicationId,
        variantName = variantName,
        elements = elements,
        elementType = elementType,
        baselineProfiles = baselineProfiles,
        minSdkVersionForDexing = minSdkVersionForDexing
    )
}
