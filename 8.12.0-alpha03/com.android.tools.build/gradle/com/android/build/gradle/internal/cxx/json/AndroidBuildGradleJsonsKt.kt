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

package com.android.build.gradle.internal.cxx.json

import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons.parseToMiniConfig
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons.parseToMiniConfigAndGatherStatistics
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons.writeNativeBuildMiniConfigValueToJsonFile
import com.android.build.gradle.tasks.ExternalNativeBuildTaskUtils.fileIsUpToDate
import com.google.gson.stream.JsonReader
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import java.io.File
import java.io.FileReader

const val MINI_CONFIG_FILE_NAME = "android_gradle_build_mini.json"

/**
 * Read the miniconfig corresponding to [androidGradleBuildJsonFile]. Create it if necessary.
 */
fun readMiniConfigCreateIfNecessary(
    androidGradleBuildJsonFile: File,
    stats: GradleBuildVariant.Builder? = null,
): NativeBuildConfigValueMini {
    val persistedMiniConfig = androidGradleBuildJsonFile.parentFile.resolve(MINI_CONFIG_FILE_NAME)
    val result =
        if (fileIsUpToDate(
                androidGradleBuildJsonFile,
                persistedMiniConfig
            ) || !androidGradleBuildJsonFile.isFile
        ) {
            // The mini json has already been created for us. Just read it instead of parsing again.
            JsonReader(FileReader(persistedMiniConfig)).use { reader ->
                parseToMiniConfig(reader)
            }
        } else {
            JsonReader(FileReader(androidGradleBuildJsonFile)).use { reader ->
                if (stats == null) parseToMiniConfig(reader)
                else parseToMiniConfigAndGatherStatistics(reader, stats)
            }.also {
                writeNativeBuildMiniConfigValueToJsonFile(
                    persistedMiniConfig,
                    it
                )
            }
        }
    return result
}
