/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.incremental

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.File
import java.io.Reader
import java.io.Writer

class ApkChangeList(val changes: Iterable<ChangedItem>, val deletions: Iterable<ChangedItem>) {

    companion object {

        const val CHANGE_LIST_FN = "__adt_change_list__.json"

        @JvmStatic
        fun changeListFileName(apkFile: File): String=
            apkFile.nameWithoutExtension + CHANGE_LIST_FN

        @JvmStatic
        fun write(source: CapturingChangesApkCreator, writer: Writer) {

            val jsonWriter = JsonWriter(writer)
            jsonWriter.beginObject()
            if (source.changedItems.size > 0) {
                jsonWriter.name("changed")
                jsonWriter.beginArray()
                source.changedItems.forEach {
                    jsonWriter.beginObject()
                    jsonWriter.name(it.path).value(it.lastModified)
                    jsonWriter.endObject()
                }
                jsonWriter.endArray()
            }
            if (source.deletedItems.size > 0) {
                jsonWriter.name("deleted")
                jsonWriter.beginArray()
                source.deletedItems.forEach {
                    jsonWriter.beginObject()
                    jsonWriter.name(it.path).value(0)
                    jsonWriter.endObject()
                }
                jsonWriter.endArray()
            }
            jsonWriter.endObject()
        }

        @JvmStatic
        fun read(reader: Reader): ApkChangeList {
            val jsonReader = JsonReader(reader)
            jsonReader.beginObject()
            val changedItems = mutableListOf<ChangedItem>()
            val deletedItems = mutableListOf<ChangedItem>()
            while (jsonReader.hasNext()) {
                val name = jsonReader.nextName()
                if (name == "changed") {
                    jsonReader.beginArray()
                    while(jsonReader.hasNext()) {
                        jsonReader.beginObject()
                        changedItems.add(ChangedItem(jsonReader.nextName(), jsonReader.nextLong()))
                        jsonReader.endObject()
                    }
                    jsonReader.endArray()
                }
                if (name == "deleted") {
                    jsonReader.beginArray()
                    while(jsonReader.hasNext()) {
                        jsonReader.beginObject()
                        deletedItems.add(ChangedItem(jsonReader.nextName(), jsonReader.nextLong()))
                        jsonReader.endObject()
                    }
                    jsonReader.endArray()
                }
            }
            return ApkChangeList(
                changedItems.toList(),
                deletedItems.toList()
            )
        }
    }

    data class ChangedItem(
        val path: String,
        val lastModified: Long
    )
}
