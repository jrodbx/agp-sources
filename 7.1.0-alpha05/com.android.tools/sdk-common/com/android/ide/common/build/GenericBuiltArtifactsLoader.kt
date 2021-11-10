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

package com.android.ide.common.build

import com.android.utils.ILogger
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileReader

/**
 * Singleton object to load metadata file returned by the model into a [GenericBuiltArtifacts]
 * in memory model.
 */
object GenericBuiltArtifactsLoader {

    /**
     * Load a metadata file if it exists or return null otherwise.
     *
     * @param metadataFile the metadata file location.
     * @param logger logger for errors/warnings, etc...
     */
    @JvmStatic
    fun loadFromFile(metadataFile: File?, logger: ILogger): GenericBuiltArtifacts? {
        if (metadataFile == null || !metadataFile.exists()) {
            return null
        }
        val relativePath = metadataFile.parentFile.toPath()
        val gsonBuilder = GsonBuilder()

        gsonBuilder.registerTypeAdapter(
            GenericBuiltArtifact::class.java,
            GenericBuiltArtifactTypeAdapter()
        )

        val gson = gsonBuilder.create()
        val buildOutputs = FileReader(metadataFile).use {
            try {
                gson.fromJson<GenericBuiltArtifacts>(it, GenericBuiltArtifacts::class.java)
            } catch (e: Exception) {
                logger.quiet("Cannot parse build output metadata file, please run a clean build")
                return null
            }
        }
        // resolve the file path to the current project location.
        return GenericBuiltArtifacts(
            artifactType = buildOutputs.artifactType,
            version = buildOutputs.version,
            applicationId = buildOutputs.applicationId,
            variantName = buildOutputs.variantName,
            elements = buildOutputs.elements
                .asSequence()
                .map { builtArtifact ->
                    GenericBuiltArtifact(
                        outputFile = relativePath.resolve(builtArtifact.outputFile).toString(),
                        versionCode = builtArtifact.versionCode,
                        versionName = builtArtifact.versionName,
                        outputType = builtArtifact.outputType,
                        filters = builtArtifact.filters,
                        attributes = builtArtifact.attributes
                    )
                }
                .toList(),
            elementType = buildOutputs.elementType)
    }
}
