/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.instrumentation

import com.android.build.api.instrumentation.ClassData
import com.google.common.collect.ImmutableList
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter

fun saveClassData(outputFile: File, classData: ClassData) {
    BufferedWriter(FileWriter(outputFile)).use {
        it.write(ClassDataAdapter.toJson(classData))
    }
}

fun loadClassData(inputFile: File): ClassData? {
    try {
        BufferedReader(FileReader(inputFile)).use {
            return ClassDataAdapter.fromJson(it)
        }
    } catch (e: Exception) {
        return null
    }
}

internal object ClassDataAdapter : TypeAdapter<ClassData>() {

    private fun JsonWriter.writeList(name: String, list: List<String>) {
        name(name).beginArray()
        list.forEach { value(it) }
        endArray()
    }

    private fun JsonReader.readList(): List<String> {
        val list = ImmutableList.Builder<String>()
        beginArray()
        while (hasNext()) {
            list.add(nextString())
        }
        endArray()
        return list.build()
    }

    override fun write(writer: JsonWriter, data: ClassData) {
        writer.beginObject()
        writer.name("className").value(data.className)
        writer.writeList("classAnnotations", data.classAnnotations)
        writer.writeList("interfaces", data.interfaces)
        writer.writeList("superClasses", data.superClasses)
        writer.endObject()
    }

    override fun read(reader: JsonReader): ClassData {
        var className: String? = null
        var classAnnotations: List<String>? = null
        var interfaces: List<String>? = null
        var superClasses: List<String>? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "className" -> className = reader.nextString()
                "classAnnotations" -> classAnnotations = reader.readList()
                "interfaces" -> interfaces = reader.readList()
                "superClasses" -> superClasses = reader.readList()
            }
        }
        reader.endObject()

        return ClassDataImpl(className!!, classAnnotations!!, interfaces!!, superClasses!!)
    }
}
