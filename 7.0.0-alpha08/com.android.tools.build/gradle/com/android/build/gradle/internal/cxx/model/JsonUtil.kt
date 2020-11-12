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

package com.android.build.gradle.internal.cxx.model

import com.android.build.gradle.internal.cxx.json.PlainFileGsonTypeAdaptor
import com.android.repository.Revision
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import org.gradle.api.file.FileCollection
import java.io.File
import java.io.IOException
import java.io.StringWriter

/**
 * Write the [CxxAbiModel] to Json string.
 */
fun CxxAbiModel.toJsonString(): String {
    return StringWriter()
        .also { writer -> GSON.toJson(this, writer) }
        .toString()
}

/**
 * Write the [CxxCmakeAbiModel] to Json string.
 */
fun CxxCmakeAbiModel.toJsonString(): String {
    return StringWriter()
        .also { writer -> GSON.toJson(this, writer) }
        .toString()
}

/**
 * Write the [CxxVariantModel] to Json string.
 */
fun CxxVariantModel.toJsonString(): String {
    return StringWriter()
        .also { writer -> GSON.toJson(this, writer) }
        .toString()
}

/**
 * Write the [CxxModuleModel] to Json string.
 */
fun CxxModuleModel.toJsonString(): String {
    return StringWriter()
        .also { writer -> GSON.toJson(this, writer) }
        .toString()
}

/**
 * Create a [CxxModuleModel] from Json string.
 */
fun createCxxModuleModelFromJson(json: String): CxxModuleModel {
    return GSON.fromJson(json, CxxModuleModel::class.java)
}

/**
 * Create a [CxxAbiModel] from Json string.
 */
fun createCxxAbiModelFromJson(json: String): CxxAbiModel {
    return GSON.fromJson(json, CxxAbiModel::class.java)
}

/**
 * Write model to JSON file.
 */
fun CxxAbiModel.writeJsonToFile() {
    modelOutputFile.parentFile.mkdirs()
    modelOutputFile.writeText(toJsonString())
}

/**
 * GSon TypeAdapter that will convert between File and String.
 */
class PlainFileGsonTypeAdaptor : TypeAdapter<File?>() {

    @Throws(IOException::class)
    override fun write(jsonWriter: JsonWriter, file: File?) {
        if (file == null) {
            jsonWriter.nullValue()
            return
        }
        jsonWriter.value(file.path)
    }

    @Throws(IOException::class)
    override fun read(jsonReader: JsonReader): File? {
        val path = jsonReader.nextString()
        return File(path)
    }
}

class FileCollectionTypeAdaptor : TypeAdapter<FileCollection?>() {
    override fun write(jsonWriter: JsonWriter, fileCollection: FileCollection?) {
        jsonWriter.beginArray()
        fileCollection?.onEach { jsonWriter.value(it.path) }
        jsonWriter.endArray()
    }

    override fun read(jsonReader: JsonReader): FileCollection? {
        // FileCollection is read but ignored
        jsonReader.beginArray()
        while(jsonReader.hasNext()) jsonReader.nextString()
        jsonReader.endArray()
        return null
    }
}

private val GSON = GsonBuilder()
    .registerTypeAdapter(File::class.java, PlainFileGsonTypeAdaptor())
    .registerTypeAdapter(Revision::class.java, RevisionTypeAdapter())
    .registerTypeAdapter(FileCollection::class.java, FileCollectionTypeAdaptor())
    .setPrettyPrinting()
    .create()

/**
 * [TypeAdapter] that converts between [Revision] and Json string.
 */
private class RevisionTypeAdapter : TypeAdapter<Revision>() {

    override fun write(writer: JsonWriter, revision: Revision) {
        writer.value(revision.toString())
    }

    override fun read(reader: JsonReader): Revision {
        return Revision.parseRevision(reader.nextString())
    }
}

/**
 * Prefab configuration state to be persisted to disk.
 *
 * Prefab configuration state needs to be persisted to disk because changes in configuration
 * require model regeneration.
 */
data class PrefabConfigurationState(
        val enabled: Boolean,
        val prefabPath: File?,
        val packages: List<File>
) {
    fun toJsonString(): String {
        return StringWriter()
            .also { writer -> GSON.toJson(this, writer) }
            .toString()
    }

    companion object {
        @JvmStatic
        fun fromJson(json: String): PrefabConfigurationState {
            return GSON.fromJson(json, PrefabConfigurationState::class.java)
        }
    }
}
