/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.attribution

import com.android.SdkConstants
import com.android.buildanalyzer.common.TaskCategoryIssue
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.File
import java.util.UUID

/**
 * Contains the partial results of a [BuildAnalyzerService]. Each service should combine all
 * partial results into a single result which is used to generate the build analyzer report file.
 */
class BuildAnalyzerPartialResult(issues: Iterable<TaskCategoryIssue>) {
    val issues = mutableSetOf<TaskCategoryIssue>()

    init {
        this.issues.addAll(issues)
    }

    fun combineWith(partialResult: BuildAnalyzerPartialResult) {
        this.issues.addAll(partialResult.issues)
    }

    fun saveToDir(outputDir: File) {
        if (issues.isNotEmpty()) {
            outputDir.mkdirs()
            File(outputDir, getUniquePartialResultsFileName()).writeText(
                DataAdapter.toJson(this)
            )
        }
    }

    companion object {
        private const val FILE_NAME_PREFIX = "Build-Analyzer-partial-result"

        private fun isPartialResultsFile(file: File): Boolean {
            return file.name.startsWith(FILE_NAME_PREFIX)
        }

        private fun getUniquePartialResultsFileName(): String {
            return "$FILE_NAME_PREFIX-${UUID.randomUUID()}${SdkConstants.DOT_JSON}"
        }

        fun getAllPartialResults(outputDir: File): List<BuildAnalyzerPartialResult> {
            return outputDir.listFiles()?.filter { isPartialResultsFile(it) }?.map { file ->
                file.reader().use {
                    DataAdapter.fromJson(it)
                }
            } ?: emptyList()
        }

        private object DataAdapter : TypeAdapter<BuildAnalyzerPartialResult>() {

            override fun write(writer: JsonWriter, partialResult: BuildAnalyzerPartialResult) {
                writer.beginObject()

                writer.name("issues").beginArray()
                partialResult.issues.forEach { writer.value(it.toString()) }
                writer.endArray()

                writer.endObject()
            }

            override fun read(reader: JsonReader): BuildAnalyzerPartialResult {
                val issues = mutableListOf<TaskCategoryIssue>()
                reader.beginObject()

                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "issues" -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                issues.add(
                                    TaskCategoryIssue.valueOf(reader.nextString())
                                )
                            }
                            reader.endArray()
                        }
                        else -> {
                            reader.skipValue()
                        }
                    }
                }

                reader.endObject()

                return BuildAnalyzerPartialResult(issues)
            }
        }
    }
}
