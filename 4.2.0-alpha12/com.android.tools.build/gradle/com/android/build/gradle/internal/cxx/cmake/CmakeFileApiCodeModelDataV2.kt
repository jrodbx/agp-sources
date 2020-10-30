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

data class CmakeFileApiCodeModelDataV2(
        val configurations: List<ConfigurationDataV2>
)

data class ConfigurationDataV2(
        val targets : List<ConfigurationTargetDataV2>
)

/**
 *  "directoryIndex" : 0,
 *  "id" : "hello-jni::@6890427a1f51a3e7e1df",
 *  "jsonFile" : "target-hello-jni-Debug-071fc3242d06c7a40797.json",
 *  "name" : "hello-jni",
 *  "projectIndex" : 0
 */
data class ConfigurationTargetDataV2(
        val id : String,
        val name : String,
        val jsonFile : String
)
