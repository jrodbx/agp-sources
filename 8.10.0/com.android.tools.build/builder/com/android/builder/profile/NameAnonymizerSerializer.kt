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

package com.android.builder.profile

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

class NameAnonymizerSerializer: TypeAdapter<NameAnonymizer>() {

    override fun write(writer: JsonWriter, anonymizer: NameAnonymizer) {
        val deanonymizer = anonymizer.createDeanonymizer()
        writer.beginObject()
        var projectId = 1L
        while (true) {
            val project = deanonymizer[projectId] ?: break
            writer.name(project.first)
            writer.beginArray()
            var variantId = 1L
            while (true) {
                val variantName = project.second[variantId] ?: break
                writer.value(variantName)
                variantId++
            }
            writer.endArray()
            projectId++
        }
        writer.endObject()
    }

    // Serialized form:
    // {":a":["debug","release"],":b":["release","debug"]}
    override fun read(reader: JsonReader): NameAnonymizer {
        return NameAnonymizer().apply {
            reader.beginObject()
            while (reader.hasNext()) {
                val projectPath = reader.nextName()
                anonymizeProjectPath(projectPath)
                reader.beginArray()
                    while (reader.hasNext()) {
                        anonymizeVariant(projectPath, reader.nextString())
                    }
                reader.endArray()
            }
            reader.endObject()
        }
    }
}
