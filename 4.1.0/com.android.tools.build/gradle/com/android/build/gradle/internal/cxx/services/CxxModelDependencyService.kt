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

package com.android.build.gradle.internal.cxx.services

import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons
import com.android.build.gradle.internal.cxx.logging.warnln
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxModuleModel
import com.android.build.gradle.internal.cxx.model.jsonFile
import com.android.build.gradle.internal.cxx.process.ProcessOutputJunction
import com.android.build.gradle.internal.scope.GlobalScope
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.file.FileCollection
import java.io.File
import java.io.IOException

/**
 * Create a gradle [FileCollection] of files that Json generation depends on.
 */
fun CxxModuleModel.jsonGenerationInputDependencyFileCollection(abis: List<CxxAbiModel>) =
    services[MODEL_DEPENDENCY_SERVICE_KEY].jsonGenerationInputDependencyFileCollection(abis)

/**
 * Create a gradle [FileCollection] of files that Json generation depends on.
 */
@VisibleForTesting
fun CxxModuleModel.jsonGenerationInputDependencyFileArray(abis: List<CxxAbiModel>) =
    services[MODEL_DEPENDENCY_SERVICE_KEY].jsonGenerationInputDependencyFileArray(abis)

/**
 * Create and register a [CxxProcessService] for creating [ProcessOutputJunction].
 */
internal fun createModelDependencyService(
    global: GlobalScope,
    services: CxxServiceRegistryBuilder) {
    services.registerFactory(MODEL_DEPENDENCY_SERVICE_KEY) {
        object : CxxModelDependencyService {
            override fun jsonGenerationInputDependencyFileCollection(abis: List<CxxAbiModel>) =
                global.project.files(*(jsonGenerationInputDependencyFileArray(abis)))

            override fun jsonGenerationInputDependencyFileArray(abis : List<CxxAbiModel>)
                    : Array<File> {
                val result = mutableSetOf<File>()
                abis.forEach { abi ->
                    if (abi.jsonFile.isFile) {
                        // Get the buildFiles from android_gradle_build_mini.json
                        try {
                            result += AndroidBuildGradleJsons
                                .getNativeBuildMiniConfig(abi.jsonFile, null)
                                .buildFiles
                        } catch (e : IOException) {
                            warnln("Could not get mini-config of '${abi.jsonFile}'")
                        }
                    }
                }
                return result.toTypedArray()
            }
        }
    }
}

private val MODEL_DEPENDENCY_SERVICE_KEY = object : CxxServiceKey<CxxModelDependencyService> {
    override val type = CxxModelDependencyService::class.java
}

private interface CxxModelDependencyService {
    fun jsonGenerationInputDependencyFileCollection(abis : List<CxxAbiModel>) : FileCollection
    fun jsonGenerationInputDependencyFileArray(abis : List<CxxAbiModel>) : Array<File>
}