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
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.File
import java.io.FileReader
import java.io.Reader
import java.io.StringReader
import java.nio.file.Path
import java.util.Properties

/**
 * Singleton object to load metadata file returned by the model into a [GenericBuiltArtifacts]
 * in memory model.
 */
object GenericBuiltArtifactsLoader {

    /**
     * Load a metadata file if it exists or return null otherwise.
     *
     * The provided [inputFile] can either be the metadata file which is a json file containing the
     * built artifacts information (pre 7.1 behavior) or can be a redirect file (7.1 and up).
     *
     * The function will recognize a redirect file if its first line is a [Properties] comment
     * [RedirectMarker]. If the first line is anything else, the function will consider the file
     * to be the metadata file.
     *
     * A redirect file is a simple [Properties] serialized with a single property name
     * [RedirectFilePropertyName] and the [RedirectMarker] comment. The value of that property will
     * be a relative location of the metadata file
     *
     * @param inputFile the metadata file or redirect file location.
     * @param logger logger for errors/warnings, etc...
     */
    @JvmStatic
    fun loadFromFile(inputFile: File?, logger: ILogger): GenericBuiltArtifacts? {
        val artifactsList = loadListFromFile(inputFile, logger)
        return when (artifactsList.size) {
            0 -> null
            1 -> artifactsList.single()
            else -> {
                logger.quiet("Expected a single artifact, got ${artifactsList.size}")
                null
            }
        }
    }

    @JvmStatic
    fun loadListFromFile(inputFile: File?, logger: ILogger): List<GenericBuiltArtifacts> {
        if (inputFile == null || !inputFile.exists()) {
            return emptyList()
        }

        val redirectFileContent = inputFile.readText()
        val redirectedFile =
            ListingFileRedirect.maybeExtractRedirectedFile(inputFile, redirectFileContent)
        val relativePathToUse = if (redirectedFile != null) {
            redirectedFile.parentFile.toPath()
        } else {
            inputFile.parentFile.toPath()
        }

        val reader = redirectedFile?.let { FileReader(it) } ?: StringReader(redirectFileContent)
        val buildOutputs = ArrayList<GenericBuiltArtifacts>()
        JsonReader(reader).use {
            try {
                if (it.peek() == JsonToken.BEGIN_ARRAY) {
                    it.beginArray()
                    while (it.hasNext()) {
                        buildOutputs.add(GenericBuiltArtifactsTypeAdapter.read(it))
                    }
                    it.endArray()
                } else {
                    buildOutputs.add(GenericBuiltArtifactsTypeAdapter.read(it))
                }

            } catch (e: Exception) {
                val outputFilePath= if (redirectedFile!=null) {
                    "$redirectedFile redirected from $inputFile"
                } else {
                    inputFile
                }
                logger.quiet(
                    "Cannot parse build output metadata file ($outputFilePath). " +
                            "Please run a clean build")
                return emptyList()
            }
        }
        // resolve the file path to the current project location.
        return buildOutputs.map {
            convertToRelativePath(it, relativePathToUse)
        }
    }

    private fun convertToRelativePath(
        buildOutputs: GenericBuiltArtifacts,
        relativePathToUse: Path
    ) = buildOutputs.copy(
        elements = buildOutputs.elements
            .map { builtArtifact ->
                builtArtifact.copy(
                    outputFile = relativePathToUse.resolve(builtArtifact.outputFile)
                        .normalize()
                        .toString()
                )
            })
}
