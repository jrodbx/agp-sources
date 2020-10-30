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

package com.android.build.gradle.internal.tasks.structureplugin

import com.android.utils.FileUtils
import com.google.common.base.Charsets
import com.google.common.io.Files
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File

data class ASPoetInfo(
    val poetVersion: String = "0.1",
    var gradleVersion: String = "",
    var agpVersion: String = "",
    var modules: MutableList<ModuleInfo> = mutableListOf()) {

    fun saveAsJsonTo(to: File) {
        FileUtils.mkdirs(to.parentFile)
        Files.asCharSink(to, Charsets.UTF_8).write(toJson())
    }

    fun toJson() : String {
        val gson = GsonBuilder()
            .registerTypeAdapter(ASPoetInfo::class.java, ASPoetInfoJsonAdapter())
            .setPrettyPrinting()
            .create()
        return gson.toJson(this)
    }
}

data class ModuleInfo(
    var name: String = "",
    var type: ModuleType = ModuleType.PURE,
    var javaSourceInfo: SourceFilesInfo = SourceFilesInfo(),
    var useKotlin: Boolean = false,
    var kotlinSourceInfo: SourceFilesInfo = SourceFilesInfo(),
    var dependencies: MutableList<PoetDependenciesInfo> = mutableListOf(),
    // Android Specific:
    var activityCount: Int = 0,
    var hasLaunchActivity: Boolean = false,
    var androidBuildConfig: AndroidBuildConfig = AndroidBuildConfig(),
    var resources: PoetResourceInfo = PoetResourceInfo()) {

    fun saveAsJsonTo(to: File) {
        FileUtils.mkdirs(to.parentFile)
        Files.asCharSink(to, Charsets.UTF_8).write(toJson())
    }

    fun toJson() : String {
        val gson = GsonBuilder()
            .registerTypeAdapter(ModuleInfo::class.java, JavaModuleInfoJsonAdapter())
            .setPrettyPrinting()
            .create()
        return gson.toJson(this)
    }

    companion object {
        fun readAsJsonFrom(from: File): ModuleInfo {
            return fromJson(from.readText(Charsets.UTF_8))
        }

        fun fromJson(from: String): ModuleInfo {
            val gson = GsonBuilder()
                .registerTypeAdapter(ModuleInfo::class.java, JavaModuleInfoJsonAdapter())
                .create()

            val recordType = object : TypeToken<ModuleInfo>() {}.type
            return gson.fromJson(from, recordType)
        }
    }
}

enum class ModuleType {
    PURE, ANDROID
}

data class SourceFilesInfo(
    var packages: Int = 0,
    var classesPerPackage: Int = 0,
    var methodsPerClass: Int = 0,
    var fieldsPerClass: Int = 0) {

    val isEmpty = packages == 0 &&
            classesPerPackage == 0 &&
            methodsPerClass == 0 &&
            fieldsPerClass == 0
}

enum class DependencyType(val jsonValue: String) {
    MODULE("moduleName"), EXTERNAL_LIBRARY("library");
}

data class PoetDependenciesInfo(
    val type: DependencyType,
    val scope: String,
    var dependency: String
)

data class PoetResourceInfo(
    var stringCount: Int = 0,
    var imageCount: Int = 0,
    var layoutCount: Int = 0
)

data class AndroidBuildConfig(
    var minSdkVersion: Int = 0,
    var targetSdkVersion: Int = 0,
    var compileSdkVersion: Int = 0
)
