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

package com.android.build.gradle.internal.dexing

import com.android.build.gradle.internal.tasks.DexArchiveBuilderTaskDelegate
import com.android.build.gradle.options.SyncOptions
import java.io.File
import java.io.Serializable

/** Parameters required for dexing (with D8). */
class DexParameters(
    val minSdkVersion: Int,
    val debuggable: Boolean,
    val withDesugaring: Boolean,
    val desugarBootclasspath: List<File>,
    val desugarClasspath: List<File>,
    val coreLibDesugarConfig: String?,
    val enableApiModeling: Boolean,
    val errorFormatMode: SyncOptions.ErrorFormatMode,
) {

    fun toDexParametersForWorkers(
            dexPerClass: Boolean,
            bootClasspath: DexArchiveBuilderTaskDelegate.ClasspathServiceKey,
            classpath: DexArchiveBuilderTaskDelegate.ClasspathServiceKey,
    ): DexParametersForWorkers {
        return DexParametersForWorkers(
            minSdkVersion = minSdkVersion,
            debuggable = debuggable,
            dexPerClass = dexPerClass,
            withDesugaring = withDesugaring,
            desugarBootclasspath = bootClasspath,
            desugarClasspath = classpath,
            coreLibDesugarConfig = coreLibDesugarConfig,
            enableApiModeling = enableApiModeling,
            errorFormatMode = errorFormatMode)
    }
}

/**
 * Parameters required for dexing (with D8). They are slightly different from [DexParameters].
 *
 * This class is serializable as it is passed to Gradle workers.
 */
class DexParametersForWorkers(
    val minSdkVersion: Int,
    val debuggable: Boolean,
    val dexPerClass: Boolean,
    val withDesugaring: Boolean,
    val desugarBootclasspath: DexArchiveBuilderTaskDelegate.ClasspathServiceKey,
    val desugarClasspath: DexArchiveBuilderTaskDelegate.ClasspathServiceKey,
    val coreLibDesugarConfig: String?,
    val enableApiModeling: Boolean,
    val errorFormatMode: SyncOptions.ErrorFormatMode
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
