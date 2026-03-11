/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.builder.files

import com.android.zipflinger.Entry
import com.android.zipflinger.ZipMap
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.File

/**
 * Represents a single file in a zip archive. The available arguments of [name], [crc] and [size] are enough to identify files that have
 * been changed, for the purpose of incremental build.
 */
data class ZipEntry(val name: String, val crc: Long, val size: Long)

/**
 * Represents all files in a zip archive, enough to recognize what files have been added, removed or changed - for the purpose of
 * incremental build. Actual file content (zip payload) is not persisted.
 */
data class ZipEntryList(val entries: Map<String, ZipEntry>) {

  fun toByteArray() = ZipEntryListAdapter.toJson(this).toByteArray()

  companion object {

    /**
     * Creates a representation of entries from a zip archive, omitting the payload.
     *
     * @param file a zip archive, source for creating a snapshot of entries.
     * @throws IllegalStateException if the [file] is not a valid zip archive.
     */
    fun fromZip(file: File): ZipEntryList =
      ZipMap.from(file.toPath()).entries.filterNot { (_, entry) -> entry.isDirectory }.let { fromEntries(it) }

    /**
     * Reads a [com.android.builder.files.ZipEntryList] that has been persisted in a file.
     *
     * @param file contains a serialized representation of a [com.android.builder.files.ZipEntryList]
     * @throws IllegalArgumentException when [file] is not a proper result of [com.android.builder.files.ZipEntryList] serialization.
     */
    fun deserializeFile(file: File): ZipEntryList {
      return ZipEntryListAdapter.fromJson(file.readText())
    }

    private fun fromEntries(map: Map<String, Entry>): ZipEntryList {
      return map
        .mapValues { entry -> ZipEntry(entry.value.name, entry.value.crc.toLong(), entry.value.uncompressedSize) }
        .let { ZipEntryList(it) }
    }
  }
}

private object ZipEntryListAdapter : TypeAdapter<ZipEntryList>() {

  override fun write(writer: JsonWriter?, zipEntryList: ZipEntryList?) {
    if (writer == null) return
    with(writer) {
      beginArray()
      zipEntryList?.entries?.forEach { (key, value) ->
        beginObject()
        name("key").value(key)
        name("name").value(value.name)
        name("size").value(value.size)
        name("crc").value(value.crc)
        endObject()
      }
      endArray()
    }
  }

  override fun read(reader: JsonReader): ZipEntryList {
    reader.beginArray()
    val output = mutableMapOf<String, ZipEntry>()
    while (reader.hasNext()) {
      reader.beginObject()
      var key: String? = null
      var name: String? = null
      var crc: Long = 0L
      var size: Long = 0L

      while (reader.hasNext()) {
        when (reader.nextName()) {
          "key" -> key = reader.nextString()
          "name" -> name = reader.nextString()
          "crc" -> crc = reader.nextLong()
          "size" -> size = reader.nextLong()
          else -> throw IllegalArgumentException("Unexpected field")
        }
      }
      reader.endObject()

      if (key != null && name != null) {
        output[key] = ZipEntry(name = name, crc = crc, size = size)
      }
    }
    reader.endArray()
    return ZipEntryList(output)
  }
}
