/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.google.gson.GsonBuilder
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.IOException

/** Module information like its application ID, version code and version name  */
class ModuleMetadata(
    val applicationId: String,
    val versionCode: String?,
    val versionName: String?,
    val debuggable: Boolean,
    val abiFilters: List<String>,
    val ignoredLibraryKeepRules: Set<String>,
    val ignoreAllLibraryKeepRules: Boolean
) {

    @Throws(IOException::class)
    fun save(outputFile: File) {
        val gsonBuilder = GsonBuilder()
        val gson = gsonBuilder.create()
        FileUtils.write(outputFile, gson.toJson(this))
    }

    companion object {

        internal const val PERSISTED_FILE_NAME = "application-metadata.json"

        @Throws(IOException::class)
        @JvmStatic
        fun load(input: File): ModuleMetadata {
            if (input.name != PERSISTED_FILE_NAME) {
                throw FileNotFoundException("No application declaration present.")
            }
            val gsonBuilder = GsonBuilder()
            val gson = gsonBuilder.create()
            FileReader(input).use { fileReader ->
                return gson.fromJson(
                    fileReader,
                    ModuleMetadata::class.java
                )
            }
        }
    }
}
