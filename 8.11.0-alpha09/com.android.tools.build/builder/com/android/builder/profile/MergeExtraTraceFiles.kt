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

package com.android.builder.profile

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.LongSerializationPolicy
import com.google.gson.stream.JsonWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

private val gson: Gson =
    GsonBuilder().setLongSerializationPolicy(LongSerializationPolicy.STRING).create()

/**
 * Merges trace events stored in files under [extraChromeTraceDir] into the given [writer]. The
 * writer should be ready to accept a stream of JSON event objects. That is, the writer should be
 * writing a JSON array when this method is invoked. The [pidOffset] is used to offset PIDs so that
 * PIDs spread among different files won't conflict with each other.
 */
fun mergeExtraTraceFiles(
    pidOffset: Int,
    writer: JsonWriter,
    extraChromeTraceDir: Path
) {
    var startPid = pidOffset
    extraChromeTraceDir.toFile().listFiles()?.apply { sortBy { it.name } }?.forEach { traceFile ->
        var maxPid = startPid
        InputStreamReader(
            GZIPInputStream(FileInputStream(traceFile)),
            StandardCharsets.UTF_8
        ).use { reader ->
            val chromeTraceJson = gson.fromJson(reader, ChromeTraceJson::class.java)
            chromeTraceJson.traceEvents.forEach { event ->
                val offsetPid = event.pid + startPid
                if (offsetPid > maxPid) maxPid = offsetPid
                gson.toJson(event.copy(pid = offsetPid), TraceEventJson::class.java, writer)
            }
        }
        startPid = maxPid + 1
    }
}

/**
 * A POJO corresponding to the JSON format used by Chrome. See
 * https://docs.google.com/document/d/1CvAClvFfyA5R-PhYUmn5OOQtYMH4h6I0nSsKchNAySU/edit#heading=h.uxpopqvbjezh
 */
data class TraceEventJson(
    val pid: Int,
    val tid: Int,
    val ts: Long,
    val ph: String,
    val cat: String? = null,
    val name: String? = null,
    val cname: String? = null,
    val args: Map<String, String>? = null
)

data class ChromeTraceJson(val traceEvents: List<TraceEventJson>) {
    fun storeToFile(file: File) =
        OutputStreamWriter(GZIPOutputStream(FileOutputStream(file)), StandardCharsets.UTF_8)
            .use { gson.toJson(this, it) }
}

