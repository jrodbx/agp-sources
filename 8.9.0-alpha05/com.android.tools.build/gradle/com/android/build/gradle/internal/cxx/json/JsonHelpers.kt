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

package com.android.build.gradle.internal.cxx.json

import com.android.build.gradle.internal.cxx.io.writeTextIfDifferent
import com.google.gson.GsonBuilder
import java.io.File

/**
 * Convert a value into a JSON string.
 */
fun <T> jsonStringOf(value : T) = GsonBuilder()
    .registerTypeAdapter(File::class.java, PlainFileGsonTypeAdaptor())
    .setPrettyPrinting()
    .create()
    .toJson(value)!!

/**
 * Write a value to a file as JSON.
 */
fun <T> writeJsonFile(file : File, value : T) {
    val parent = file.parentFile
    if (!parent.exists()) parent.mkdirs()
    file.writeText(jsonStringOf(value))
}

/**
 * Write a value to a file as JSON. Only write if it changed.
 */
fun <T> writeJsonFileIfDifferent(file : File, value : T) {
    val parent = file.parentFile
    if (!parent.exists()) parent.mkdirs()
    file.writeTextIfDifferent(jsonStringOf(value))
}

/**
 * Read a value of specific type from a JSON file.
 */
inline fun <reified T> readJsonFile(file : File) = readJsonFile(file, T::class.java)

/**
 * Read a value of specific type from a JSON file.
 */
fun <T> readJsonFile(file : File, type : Class<T>) : T {
    return GsonBuilder()
        .registerTypeAdapter(File::class.java, PlainFileGsonTypeAdaptor())
        .create()
        .fromJson(file.readText(), type)
}

