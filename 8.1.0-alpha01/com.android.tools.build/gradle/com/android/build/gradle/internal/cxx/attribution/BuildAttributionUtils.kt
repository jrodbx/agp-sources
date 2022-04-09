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
import com.android.build.gradle.internal.cxx.model.ninjaLogFile
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
    allTasks : BuildTaskAttributions,
    abiModel: CxxAbiModel,
    buildStartTime: Long,
    extraChromeTraceDir: File
) {
    val attributionKey = abiModel.createAttributionKey()
    extraChromeTraceDir.mkdirs()
    generateChromeTrace(
        extraChromeTraceDir.resolve(
            "external_native_build-$buildStartTime-${attributionKey.filename}.json.gz"
        ),
        mapOf(attributionKey to allTasks)
    )
}

fun generateChromeTrace(
    outputFile: File,
    allAttributions: Map<AttributionKey, BuildTaskAttributions>
) = run {
    val allEvents = mutableListOf<TraceEventJson>()
    allAttributions.entries
        .sortedBy { (_, tasks) -> tasks.attributionList.firstOrNull()?.startTimeOffsetMs ?: 0 }
        .forEachIndexed { pid, (key, tasksForAnAbi) ->
            allEvents.addProcessNameMetaEvent(pid, key)
            squashTasks(tasksForAnAbi).forEachIndexed { tid, tasks ->
                allEvents.addThreadNameMetaEvent(pid, tid)
                tasks.attributionList.forEach { task ->
                    allEvents.addTaskEvent(pid, tid, tasksForAnAbi.buildStartTimeMs, task)
                }
            }
        }
    outputFile.parentFile.mkdirs()
    ChromeTraceJson(allEvents).storeToFile(outputFile)
}

private fun readZipContent(file: File): Map<AttributionKey, BuildTaskAttributions> {
    val allAttributions = mutableMapOf<AttributionKey, BuildTaskAttributions.Builder>()
    val zipFile = ZipFile(file)
    ZipInputStream(FileInputStream(file)).use {
        while (true) {
            val entry = it.nextEntry ?: break
            if (entry.isDirectory) continue
            val (module, variant, abi) = entry.name.split('/', limit = 3)
            allAttributions[createAttributionKey(module, variant, abi)] =
                BuildTaskAttributions.newBuilder().apply {
                    InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8).use { inputStreamReader ->
                        for (line in inputStreamReader.readLines()) {
                            if (line.startsWith('#')) {
                                buildStartTimeMs = line
                                    .dropWhile { c -> !Character.isDigit(c) }
                                    .takeWhile { c -> Character.isDigit(c) }
                                    .toLong()
                            } else {
                                collectTask(line)
                            }
                        }
                    }
                }
        }
    }
    return allAttributions.map { (key,value) -> key to value.build() }.toMap()
}

private fun BuildTaskAttributions.Builder.collectTask(line: String) {
    val (start, end, _, output) = line.split('\t')
    addAttribution(
        BuildTaskAttribution.newBuilder()
            .setStartTimeOffsetMs(start.toInt())
            .setEndTimeOffsetMs(end.toInt())
            .setOutputFile(output)
            .build()
    )
}

/**
 * Create [BuildTaskAttributions] from a .ninja_log file starting at [linesToSkip].
 */
fun generateNinjaSourceFileAttribution(
    abi : CxxAbiModel,
    linesToSkip: Int,
    buildStartTime: Long
) : BuildTaskAttributions {
    val allTasks = BuildTaskAttributions.newBuilder()
        .setKey(abi.createAttributionKey())
        .setBuildFolder(abi.ninjaLogFile.parent)
        .setNinjaLogStartLine(linesToSkip)
        .setBuildStartTimeMs(buildStartTime)
    abi.ninjaLogFile.useLines(StandardCharsets.UTF_8) { lines ->
        lines.drop(linesToSkip).forEach { line ->
            if (!line.startsWith("# ")) {
                allTasks.collectTask(line)
            }
        }
    }
    return allTasks.build()
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
            args = mapOf("name" to key.describe)
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
private fun MutableList<TraceEventJson>.addTaskEvent(
    pid: Int,
    tid: Int,
    buildStartTimeMs : Long,
    task: BuildTaskAttribution) {
    add(
        TraceEventJson(
            pid,
            tid,
            (buildStartTimeMs + task.startTimeOffsetMs) * MICROSECOND_IN_MILLISECOND,
            "B",
            task.type.toString(),
            task.name,
            task.type.colorName,
            mapOf("output" to task.outputFile)
        )
    )
    add(TraceEventJson(pid, tid, (buildStartTimeMs + task.endTimeOffsetMs) * MICROSECOND_IN_MILLISECOND, "E"))
}

/**
 * Tries to fit tasks into some number of tracks compactly. The idea is to present a view of how
 * tasks are executed concurrently on a multi-core machine. There is no way to reconstruct which
 * CPU thread did what since that information is lost.
 */
private fun squashTasks(tasks: BuildTaskAttributions): List<BuildTaskAttributions> {
    val result = mutableListOf<MutableList<BuildTaskAttribution>>()
    for (task in tasks.attributionList.sortedBy { it.startTimeOffsetMs }) {
        val chosenTrack = result
            .filter { it.lastOrNull()?.endTimeOffsetMs ?: 0 <= task.startTimeOffsetMs }
            .maxByOrNull { it.lastOrNull()?.endTimeOffsetMs ?: 0 }
        if (chosenTrack == null) {
            result.add(mutableListOf(task))
        } else {
            chosenTrack.add(task)
        }
    }
    return result.map {
        BuildTaskAttributions.newBuilder()
            .addAllAttribution(it)
            .build()
    }
}

private val illegalChars = Regex("[:\\\\/\"'|?*<>]")

private val AttributionKey.describe: String
    get() = "$module / $variant / $abi"

private val AttributionKey.filename: String
    get() = "${module.replace(illegalChars, "_")}-${variant.replace(illegalChars, "_")}-$abi"

private fun createAttributionKey(module : String, variant : String, abi : String) : AttributionKey {
    return AttributionKey.newBuilder()
        .setModule(module)
        .setVariant(variant)
        .setAbi(abi)
        .build()
}
private fun CxxAbiModel.createAttributionKey() : AttributionKey {
    return AttributionKey.newBuilder()
        .setModule(variant.module.gradleModulePathName)
        .setVariant(variant.variantName)
        .setAbi(abi.tag)
        .build()
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

val BuildTaskAttribution.type : OperationType get() =
    if (outputFile.endsWith(".o")) OperationType.COMPILE else OperationType.LINK

val BuildTaskAttribution.name : String get() = File(outputFile).name
