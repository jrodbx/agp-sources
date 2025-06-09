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

package com.android.build.gradle.internal.cxx.prefab

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File
import java.io.Serializable

/*************************************************************************************
 * This model represents the information needed to generate a Prefab package directory
 * structure from information in a local Gradle build.
 *
 * It's annotated with Gradle Input/Output notations so that it can be directly used
 * as a Gradle task dependency declaration.
 ************************************************************************************/

data class PrefabPublication(
    // Internal: The individual tasks should define their unique output.
    @get:Internal
    val installationFolder : File,
    @get:Input
    val gradlePath : String,
    @get:Nested
    val packageInfo : PrefabPackagePublication
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1
    }
}

data class PrefabPackagePublication(
    @get:Input
    val packageName : String,
    @get:Input
    @get:Optional
    val packageVersion : String?,
    @get:Input
    val packageSchemaVersion : Int,
    @get:Input
    val packageDependencies : List<String>,
    @get:Nested
    val modules : List<PrefabModulePublication>
) : Serializable

data class PrefabModulePublication(
    @get:Input
    val moduleName : String,
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    val moduleHeaders : File?,
    @get:Input
    @get:Optional
    val moduleExportLibraries : List<String>,
    @get:Input
    @get:Optional
    val moduleLibraryName : String?,
    @get:Nested
    val abis : List<PrefabAbiPublication>
) : Serializable

data class PrefabAbiPublication(
    @get:Input
    val abiName : String,
    @get:Input
    val abiApi : Int,
    @get:Input
    val abiNdkMajor : Int,
    @get:Input
    val abiStl : String,
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    val abiLibrary : File?,
    @get:InputFile
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    val abiAndroidGradleBuildJsonFile : File
) : Serializable
