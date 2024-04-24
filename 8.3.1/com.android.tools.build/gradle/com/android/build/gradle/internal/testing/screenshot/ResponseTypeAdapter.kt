/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.gradle.internal.testing.screenshot

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import org.gradle.api.GradleException

class ResponseTypeAdapter() : TypeAdapter<Response>() {
    private val previewResultTypeAdapter = PreviewResultTypeAdapter()

    override fun write(writer: JsonWriter, src: Response) {
        writer.beginObject()

        writer.name("status").value(src.status)
        writer.name("message").value(src.message)

        if (!src.previewResults.isNullOrEmpty()) {
            writer.name("previewResults")
            writer.beginArray()
            for(result in src.previewResults) {
                previewResultTypeAdapter.write(writer, result)
            }
            writer.endArray()
        }

        writer.endObject()
    }

    override fun read(reader: JsonReader): Response {
        var status: Int? = null
        var message: String? = null
        var previewResults = mutableListOf<PreviewResult>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "status" -> status = reader.nextInt()
                "message" -> message = reader.nextString()
                "previewResults" -> {
                    reader.beginArray()
                    while(reader.hasNext()) {
                        previewResults.add(previewResultTypeAdapter.read(reader))
                    }
                    reader.endArray()
                }
            }
        }
        reader.endObject()
        if (status == null || message == null) {
            throw GradleException("Could not read Response.")
        }
        return Response(status, message, previewResults)
    }
}
