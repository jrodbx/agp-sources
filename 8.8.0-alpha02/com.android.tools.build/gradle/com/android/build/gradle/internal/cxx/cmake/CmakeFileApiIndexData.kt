/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.cmake

import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileReader

/**
 * The main entry point into a CMake query API result
 */
data class IndexData(val objects : List<IndexObjectData>) {

    /**
     * Get an object of specific name and type by looking it up in the index and
     * reading the corresponding JSON file.
     */
    fun <T> getIndexObject(name : String, replyFolder : File, type : Class<T>) : T? {
        return objects
                .filter { obj -> obj.kind == name }
                .map { obj ->
                    val json = replyFolder.resolve(obj.jsonFile)
                    FileReader(json).use { reader ->
                        GSON.fromJson(reader, type)
                    }
                }
                .singleOrNull()
    }
}

/**
 *  "jsonFile" : "codemodel-v2-ba560e640682820c771e.json",
 *  "kind" : "codemodel",
 *  "version" : { "major" : 2, "minor" : 0 }
 */
data class IndexObjectData(
        val jsonFile : String,
        val kind : String,
        val version : IndexObjectVersionData
)

data class IndexObjectVersionData(
        val major : Int,
        val minor : Int
)

private val GSON = GsonBuilder()
        .setPrettyPrinting()
        .create()
