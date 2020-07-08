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

import com.android.build.api.artifact.ArtifactType
import com.android.build.api.variant.BuiltArtifacts
import com.android.build.api.variant.BuiltArtifactsLoader
import com.google.gson.GsonBuilder
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import java.io.File
import java.io.FileReader
import java.nio.file.Path

class BuiltArtifactsLoaderImpl: BuiltArtifactsLoader {

    override fun load(folder: Directory): BuiltArtifacts? {
        return loadFromFile(
            File(folder.asFile, BuiltArtifactsImpl.METADATA_FILE_NAME),
            folder.asFile.toPath())
    }

    override fun load(fileCollection: FileCollection): BuiltArtifacts? {
        val metadataFile =
            fileCollection.asFileTree.files.find { it.name == BuiltArtifactsImpl.METADATA_FILE_NAME }
        return loadFromFile(metadataFile, metadataFile?.parentFile?.toPath())
    }

    companion object {
        @JvmStatic
        fun loadFromDirectory(folder: File): BuiltArtifacts? =
            loadFromFile(File(folder, BuiltArtifactsImpl.METADATA_FILE_NAME), folder.toPath())


        @JvmStatic
        fun loadFromFile(metadataFile: File?, relativePath: Path?): BuiltArtifacts? {
            if (metadataFile == null || relativePath == null || !metadataFile.exists()) {
                return null
            }
            val gsonBuilder = GsonBuilder()

            gsonBuilder.registerTypeAdapter(
                BuiltArtifactImpl::class.java,
                BuiltArtifactTypeAdapter()
            )
            gsonBuilder.registerTypeHierarchyAdapter(
                ArtifactType::class.java,
                ArtifactTypeTypeAdapter()
            )

            val gson = gsonBuilder.create()
            val reader = FileReader(metadataFile)
            val buildOutputs =
                gson.fromJson<BuiltArtifactsImpl>(reader, BuiltArtifactsImpl::class.java)
            // resolve the file path to the current project location.
            return BuiltArtifactsImpl(
                artifactType = buildOutputs.artifactType,
                version = buildOutputs.version,
                applicationId = buildOutputs.applicationId,
                variantName = buildOutputs.variantName,
                elements = buildOutputs.elements
                    .asSequence()
                    .map { builtArtifact ->
                        BuiltArtifactImpl(
                            outputFile = relativePath.resolve(builtArtifact.outputFile),
                            properties = mapOf(),
                            versionCode = builtArtifact.versionCode,
                            versionName = builtArtifact.versionName,
                            isEnabled = builtArtifact.isEnabled,
                            outputType = builtArtifact.outputType,
                            filters = builtArtifact.filters
                        )
                    }
                    .toList())
        }
    }
}