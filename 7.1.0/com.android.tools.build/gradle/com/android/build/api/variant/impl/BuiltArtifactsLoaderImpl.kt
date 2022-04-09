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

package com.android.build.api.variant.impl

import com.android.build.api.artifact.Artifact
import com.android.build.api.variant.BuiltArtifactsLoader
import com.android.ide.common.build.ListingFileRedirect
import com.google.gson.GsonBuilder
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import java.io.File
import java.io.FileReader
import java.io.StringReader
import java.nio.file.Paths

class BuiltArtifactsLoaderImpl: BuiltArtifactsLoader {

    override fun load(folder: Directory): BuiltArtifactsImpl? {
        return load(folder as FileSystemLocation)
    }

    fun load(folder: FileSystemLocation): BuiltArtifactsImpl? {
        return loadFromFile(
            File(folder.asFile, BuiltArtifactsImpl.METADATA_FILE_NAME)
        )
    }

    override fun load(fileCollection: FileCollection): BuiltArtifactsImpl? {
        val metadataFile =
            fileCollection.asFileTree.files.find { it.name == BuiltArtifactsImpl.METADATA_FILE_NAME }
        return loadFromFile(metadataFile)
    }

    fun load(folder: Provider<Directory>): BuiltArtifactsImpl? = load(folder.get())

    companion object {
        @JvmStatic
        fun loadFromDirectory(folder: File): BuiltArtifactsImpl? =
            loadFromFile(File(folder, BuiltArtifactsImpl.METADATA_FILE_NAME))


        @JvmStatic
        fun loadFromFile(inputFile: File?): BuiltArtifactsImpl? {
            if (inputFile == null || !inputFile.exists()) {
                return null
            }
            val gsonBuilder = GsonBuilder()

            gsonBuilder.registerTypeAdapter(
                BuiltArtifactImpl::class.java,
                BuiltArtifactTypeAdapter()
            )
            gsonBuilder.registerTypeHierarchyAdapter(
                Artifact::class.java,
                ArtifactTypeTypeAdapter()
            )

            val gson = gsonBuilder.create()
            val redirectFileContent = inputFile.readText()
            val redirectedFile =
                ListingFileRedirect.maybeExtractRedirectedFile(inputFile, redirectFileContent)
            val relativePathToUse = if (redirectedFile != null) {
                redirectedFile.parentFile.toPath()
            } else {
                inputFile.parentFile.toPath()
            }

            val reader = redirectedFile?.let { FileReader(it) } ?: StringReader(redirectFileContent)
            val buildOutputs = reader.use {
                gson.fromJson(it, BuiltArtifactsImpl::class.java)
            }
            // resolve the file path to the current project location.
            return BuiltArtifactsImpl(
                artifactType = buildOutputs.artifactType,
                version = buildOutputs.version,
                applicationId = buildOutputs.applicationId,
                variantName = buildOutputs.variantName,
                elements = buildOutputs.elements
                    .asSequence()
                    .map { builtArtifact ->
                        BuiltArtifactImpl.make(
                            outputFile = relativePathToUse.resolve(
                                Paths.get(builtArtifact.outputFile)).normalize().toString(),
                            versionCode = builtArtifact.versionCode,
                            versionName = builtArtifact.versionName,
                            variantOutputConfiguration = builtArtifact.variantOutputConfiguration,
                            attributes = builtArtifact.attributes
                        )
                    }
                    .toList())
        }
    }
}
