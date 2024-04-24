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

class PreviewResultTypeAdapter: TypeAdapter<PreviewResult>() {
    override fun write(writer: JsonWriter, src: PreviewResult) {
        writer.beginObject()

        writer.name("responseCode").value(src.responseCode)
        writer.name("previewName").value(src.previewName)
        writer.name("message").value(src.message)

        writer.endObject()
    }

    override fun read(reader: JsonReader): PreviewResult {
        var responseCode: Int? = null
        var previewName: String? = null
        var message: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "responseCode" -> responseCode = reader.nextInt()
                "previewName" -> previewName = reader.nextString()
                "message" -> message = reader.nextString()
            }
        }
        reader.endObject()
        if (responseCode == null || previewName == null) {
            throw GradleException("Could not read PreviewResult.")
        }
        return PreviewResult(responseCode, previewName, message)
    }
}
