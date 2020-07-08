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

package com.android.build.gradle.internal.cxx.attribution

import com.android.build.gradle.internal.cxx.logging.warnln
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.builder.profile.ChromeTraceJson
import com.android.builder.profile.TraceEventJson
import com.google.common.base.Throwables
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

private const val MICROSECOND_IN_MILLISECOND = 1000

/** Generates a Chrome trace file that can be opened in Chrome browser (chrome://tracing). */
fun generateChromeTrace(
    file: File,
    outputFile: File = File(file.parentFile, file.nameWithoutExtension + ".json.gz")
) {
    val allAttributions = try {
        readZipContent(file)
    } catch (e: Throwable) {
        warnln(
            "Cannot parse native build attribution zip file. " +
                    "Exception: ${Throwables.getStackTraceAsString(e)}"
        )
        return
    }
    generateChromeTrace(outputFile, allAttributions)
}

fun generateChromeTrace(
    abiModel: CxxAbiModel,
    ninjaLogFile: File,
    linesToSkip: Int,
    buildStartTime: Long,
    extraChromeTraceDir: File
) {
    val attributionKey = AttributionKey.fromAbi(abiModel)
    try {
        extraChromeTraceDir.mkdirs()
        val allTasks = mutableListOf<AttributionTask>()
        ninjaLogFile.useLines(StandardCharsets.UTF_8) { lines ->
            lines.drop(linesToSkip).forEach { line ->
                if (!line.startsWith("# ")) allTasks.collectTask(line, buildStartTime)
            }
        }

        generateChromeTrace(
            extraChromeTraceDir.resolve(
                "external_native_build-$buildStartTime-${attributionKey.filename}.json.gz"
            ),
            mapOf(attributionKey to allTasks)
        )
    } catch (e: Throwable) {
        warnln(
            "Cannot generate Chrome trace file for $attributionKey. " +
                    "Exception: ${Throwables.getStackTraceAsString(e)}"
        )
    }
}

fun generateChromeTrace(
    outputFile: File,
    allAttributions: Map<AttributionKey, List<AttributionTask>>
) = try {
    val allEvents = mutableListOf<TraceEventJson>()
    allAttributions.entries
        .sortedBy { (_, tasks) -> tasks.firstOrNull()?.startTimeMs ?: 0 }
        .forEachIndexed { pid, (key, tasksForAnAbi) ->
            allEvents.addProcessNameMetaEvent(pid, key)
            squashTasks(tasksForAnAbi).forEachIndexed { tid, tasks ->
                allEvents.addThreadNameMetaEvent(pid, tid)
                tasks.forEach { task ->
                    allEvents.addTaskEvent(pid, tid, task)
                }
            }
        }
    ChromeTraceJson(allEvents).storeToFile(outputFile)
} catch (e: Throwable) {
    warnln(
        "Cannot output native build attribution in Chrome trace format. " +
                "Exception: ${Throwables.getStackTraceAsString(e)}"
    )
}

private fun readZipContent(file: File): Map<AttributionKey, List<AttributionTask>> {
    val allAttributions = mutableMapOf<AttributionKey, List<AttributionTask>>()
    val zipFile = ZipFile(file)
    ZipInputStream(FileInputStream(file)).use {
        while (true) {
            val entry = it.nextEntry ?: break
            if (entry.isDirectory) continue
            val (module, variant, abi) = entry.name.split('/', limit = 3)
            allAttributions[AttributionKey(module, variant, abi)] =
                mutableListOf<AttributionTask>().apply {
                    InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8).use {
                        var startingTimestamp = 0L
                        for (line in it.readLines()) {
                            if (line.startsWith('#')) {
                                startingTimestamp = line
                                    .dropWhile { c -> !Character.isDigit(c) }
                                    .takeWhile { c -> Character.isDigit(c) }
                                    .toLong()
                            } else {
                                collectTask(line, startingTimestamp)
                            }
                        }
                    }
                }
        }
    }
    return allAttributions
}

private fun MutableList<AttributionTask>.collectTask(
    line: String,
    startingTimestamp: Long
) {
    val (start, end, _, output) = line.split('\t')
    val outputFile = File(output)
    add(
        AttributionTask(
            outputFile.name,
            when (outputFile.extension) {
                "o" -> OperationType.COMPILE
                else -> OperationType.LINK
            },
            start.toLong() + startingTimestamp,
            end.toLong() + startingTimestamp,
            output
        )
    )
}

/**
 * Creates a meta event that changes the process name to '<module> / <variant> / <ABI>'. See
 * https://docs.google.com/document/d/1CvAClvFfyA5R-PhYUmn5OOQtYMH4h6I0nSsKchNAySU/preview#heading=h.xqopa5m0e28f
 */
private fun MutableList<TraceEventJson>.addProcessNameMetaEvent(pid: Int, key: AttributionKey) {
    add(
        TraceEventJson(
            pid,
            0,
            0,
            "M",
            name = "process_name",
            args = mapOf("name" to key.toString())
        )
    )
}

/**
 * Creates a meta event that changes the thread to 'ninja->clang <index>'. See
 * https://docs.google.com/document/d/1CvAClvFfyA5R-PhYUmn5OOQtYMH4h6I0nSsKchNAySU/preview#heading=h.xqopa5m0e28f
 */
private fun MutableList<TraceEventJson>.addThreadNameMetaEvent(pid: Int, tid: Int) {
    add(
        TraceEventJson(
            pid,
            tid,
            0,
            "M",
            name = "thread_name",
            args = mapOf("name" to "ninja->clang $tid")
        )
    )
}

/**
 * Creates a duration event for a given attribution task. See
 * https://docs.google.com/document/d/1CvAClvFfyA5R-PhYUmn5OOQtYMH4h6I0nSsKchNAySU/preview#heading=h.nso4gcezn7n1
 */
private fun MutableList<TraceEventJson>.addTaskEvent(pid: Int, tid: Int, task: AttributionTask) {
    add(
        TraceEventJson(
            pid,
            tid,
            task.startTimeMs * MICROSECOND_IN_MILLISECOND,
            "B",
            task.type.toString(),
            task.name,
            task.type.colorName,
            mapOf("output" to task.output)
        )
    )
    add(TraceEventJson(pid, tid, task.endTimeMs * MICROSECOND_IN_MILLISECOND, "E"))
}

/**
 * Tries to fit tasks into some number of tracks compactly. The idea is to present a view of how
 * tasks are executed concurrently on a multi-core machine. There is no way to reconstruct which
 * CPU thread did what since that information is lost.
 */
private fun squashTasks(tasks: List<AttributionTask>): List<List<AttributionTask>> {
    val result = mutableListOf<MutableList<AttributionTask>>()
    for (task in tasks.sortedBy { it.startTimeMs }) {
        val chosenTrack = result
            .filter { it.lastOrNull()?.endTimeMs ?: 0 <= task.startTimeMs }
            .maxBy { it.lastOrNull()?.endTimeMs ?: 0 }
        if (chosenTrack == null) {
            result.add(mutableListOf(task))
        } else {
            chosenTrack.add(task)
        }
    }
    return result
}

private val illegalChars = Regex("[:\\\\/\"'|?*<>]")

data class AttributionKey(val module: String, val variant: String, val abi: String) {
    override fun toString(): String = "$module / $variant / $abi"
    val filename: String
        get() = "${module.replace(illegalChars, "_")}-${variant.replace(illegalChars, "_")}-$abi"

    companion object {
        fun fromAbi(abi: CxxAbiModel): AttributionKey = AttributionKey(
            abi.variant.module.gradleModulePathName,
            abi.variant.variantName,
            abi.abi.tag
        )
    }
}

enum class OperationType {
    COMPILE, LINK;

    val colorName: String
        get() = when (this) {
            // For all possible colors, see
            // https://github.com/catapult-project/catapult/blob/master/tracing/tracing/base/color_scheme.html
            COMPILE -> "thread_state_runnable"
            LINK -> "thread_state_running"
        }
}

data class AttributionTask(
    val name: String,
    val type: OperationType,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val output: String
)
