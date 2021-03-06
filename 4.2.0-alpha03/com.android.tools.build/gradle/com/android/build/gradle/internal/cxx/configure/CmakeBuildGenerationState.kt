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

package com.android.build.gradle.internal.cxx.configure

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter

/**
 * The class is the class for build_generation_state.json. It contains information from CMake
 * gathered as the Ninja project was generated.
 */
data class CmakeBuildGenerationState(val properties : List<CmakePropertyValue>) {

    /**
     * Write to a file.
     */
    fun toFile(file : File) {
        FileWriter(file).use { writer ->
            GSON_BUILDER.toJson(this, writer)
        }
    }

    companion object {
        private val TYPE_TOKEN = object : TypeToken<CmakeBuildGenerationState>() {}
        private val ADAPTER = Gson().getAdapter<CmakeBuildGenerationState>(TYPE_TOKEN)
        private val GSON_BUILDER = GsonBuilder().setPrettyPrinting().create()

        /**
         * Read from a file.
         */
        fun fromFile(file : File) : CmakeBuildGenerationState {
            try {
                JsonReader(FileReader(file)).use { reader ->
                    reader.isLenient = false
                    return ADAPTER.read(reader)
                }
            } catch (e: Exception) {
                throw RuntimeException("Exception while reading $file", e)
            }
        }
    }
}

/**
 * Represents a single CMake property
 */
data class CmakePropertyValue(val name : String, val value : String) {
    companion object {
        /**
         * Construct a CmakePropertyValue with CmakeProperty
         */
        fun from(property : CmakeProperty, value : String) : CmakePropertyValue {
            return CmakePropertyValue(property.name, value)
        }

    }
}
