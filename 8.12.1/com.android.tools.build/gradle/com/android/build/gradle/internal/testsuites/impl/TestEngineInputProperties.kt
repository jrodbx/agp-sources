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

package com.android.build.gradle.internal.testsuites.impl

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

/**
 * Data class representing the list of input properties passed from the Test Task to the junit
 * engine.
 *
 * Eventually, this data class will be serialized to JSON and the file location will be provided
 * to the junit engine using an environment variable named [INPUT_PARAMETERS].
 *
 */
data class TestEngineInputProperties(val properties: List<TestEngineInputProperty>) {
    companion object {

        /**
         * Name of the environment property the junit engine expect to be set. This environment
         * property will point to a file location which content will be an instance of
         * [TestEngineInputProperties] serialized as a Json object.
         */
        const val INPUT_PARAMETERS = "com.android.junit.engine.input.parameters"

        /**
         * Convenience function for junit engines to deserialize and return the input parameters
         */
        fun read(): TestEngineInputProperties =
            read(File(System.getenv(INPUT_PARAMETERS)))

        fun read(input: File): TestEngineInputProperties =
            Gson().fromJson(input.readText(), TestEngineInputProperties::class.java)
    }

    fun save(output: File) {
        output.writeText(GsonBuilder().setPrettyPrinting().create().toJson(this))
    }

    fun get(name: String): String {
        for (property in properties) {
            if (property.name == name) {
                return property.value
            }
        }
        throw RuntimeException("Cannot find property $name in input parameters ${
            properties.joinToString(separator = "\n") { it.name }
        }")
    }
}
