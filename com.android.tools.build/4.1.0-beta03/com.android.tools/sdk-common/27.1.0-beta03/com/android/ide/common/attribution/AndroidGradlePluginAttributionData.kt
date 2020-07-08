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

package com.android.ide.common.attribution

import com.android.SdkConstants
import com.android.utils.FileUtils
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.Serializable

data class AndroidGradlePluginAttributionData(
    /**
     * A map that maps a task name to its class name
     * ex: mergeDevelopmentDebugResources -> com.android.build.gradle.tasks.MergeResources
     */
    val taskNameToClassNameMap: Map<String, String> = emptyMap(),

    /**
     * Contains registered tasks that are not cacheable.
     */
    val noncacheableTasks: Set<String> = emptySet(),

    /**
     * Contains a list of tasks sharing the same outputs.
     * The key of the map represents the absolute path to the file or the directory output and the
     * key contains a list of tasks declaring this file or directory as their output.
     */
    val tasksSharingOutput: Map<String, List<String>> = emptyMap(),

    /**
     * Contains garbage collection data for the last build.
     * The key of the map represents the name of the garbage collector, while the value represents
     * the time spent collecting in milliseconds.
     */
    val garbageCollectionData: Map<String, Long> = emptyMap(),
    /**
     * Contains the ids of the plugins defined in the buildSrc.
     */
    val buildSrcPlugins: Set<String> = emptySet()
) : Serializable {
    companion object {
        fun save(outputDir: File, attributionData: AndroidGradlePluginAttributionData) {
            val file = FileUtils.join(
                outputDir,
                SdkConstants.FD_BUILD_ATTRIBUTION,
                SdkConstants.FN_AGP_ATTRIBUTION_DATA
            )
            file.parentFile.mkdirs()
            BufferedWriter(FileWriter(file)).use {
                it.write(AttributionDataAdapter.toJson(attributionData))
            }
        }

        fun load(outputDir: File): AndroidGradlePluginAttributionData? {
            val file = FileUtils.join(
                outputDir,
                SdkConstants.FD_BUILD_ATTRIBUTION,
                SdkConstants.FN_AGP_ATTRIBUTION_DATA
            )
            try {
                BufferedReader(FileReader(file)).use {
                    return AttributionDataAdapter.fromJson(it)
                }
            } catch (e: Exception) {
                return null
            }
        }
    }

    internal object AttributionDataAdapter : TypeAdapter<AndroidGradlePluginAttributionData>() {
        override fun write(writer: JsonWriter, data: AndroidGradlePluginAttributionData) {
            writer.beginObject()
            writer.name("taskNameToClassNameMap").beginArray()
            data.taskNameToClassNameMap.forEach { (taskName, className) ->
                writer.beginObject()
                writer.name("taskName").value(taskName)
                writer.name("className").value(className)
                writer.endObject()
            }
            writer.endArray()

            writer.name("tasksSharingOutput").beginArray()
            data.tasksSharingOutput.forEach { (filePath, tasksList) ->
                writer.beginObject()
                writer.name("filePath").value(filePath)
                writer.name("tasksList").beginArray()
                tasksList.forEach { taskName ->
                    writer.value(taskName)
                }
                writer.endArray()
                writer.endObject()
            }
            writer.endArray()

            writer.name("garbageCollectionData").beginArray()
            data.garbageCollectionData.forEach { (gcName, duration) ->
                writer.beginObject()
                writer.name("gcName").value(gcName)
                writer.name("duration").value(duration)
                writer.endObject()
            }
            writer.endArray()

            writer.name("buildSrcPlugins").beginArray()
            data.buildSrcPlugins.forEach { plugin ->
                writer.value(plugin)
            }
            writer.endArray()
            writer.endObject()
        }

        override fun read(reader: JsonReader): AndroidGradlePluginAttributionData {
            val taskNameToClassNameMap = HashMap<String, String>()
            val tasksSharingOutput = HashMap<String, List<String>>()
            val garbageCollectionData = HashMap<String, Long>()
            val buildSrcPlugins = HashSet<String>()

            reader.beginObject()

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "taskNameToClassNameMap" -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            reader.beginObject()
                            var taskName: String? = null
                            var className: String? = null
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "taskName" -> taskName = reader.nextString()
                                    "className" -> className = reader.nextString()
                                }
                            }
                            taskNameToClassNameMap[taskName!!] = className!!
                            reader.endObject()
                        }
                        reader.endArray()
                    }

                    "tasksSharingOutput" -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            reader.beginObject()
                            var filePath: String? = null
                            val tasksList = ArrayList<String>()
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "filePath" -> filePath = reader.nextString()
                                    "tasksList" -> {
                                        reader.beginArray()
                                        while (reader.hasNext()) {
                                            tasksList.add(reader.nextString())
                                        }
                                        reader.endArray()
                                    }
                                }
                            }
                            tasksSharingOutput[filePath!!] = tasksList
                            reader.endObject()
                        }
                        reader.endArray()
                    }

                    "garbageCollectionData" -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            reader.beginObject()
                            var gcName: String? = null
                            var duration: Long? = null
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "gcName" -> gcName = reader.nextString()
                                    "duration" -> duration = reader.nextLong()
                                }
                            }
                            garbageCollectionData[gcName!!] = duration!!
                            reader.endObject()
                        }
                        reader.endArray()
                    }
                    "buildSrcPlugins" -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            buildSrcPlugins.add(reader.nextString())
                        }
                        reader.endArray()
                    }
                    else -> {
                        reader.skipValue()
                    }
                }
            }

            reader.endObject()

            return AndroidGradlePluginAttributionData(
                taskNameToClassNameMap = taskNameToClassNameMap,
                tasksSharingOutput = tasksSharingOutput,
                garbageCollectionData = garbageCollectionData,
                buildSrcPlugins = buildSrcPlugins
            )
        }
    }
}
