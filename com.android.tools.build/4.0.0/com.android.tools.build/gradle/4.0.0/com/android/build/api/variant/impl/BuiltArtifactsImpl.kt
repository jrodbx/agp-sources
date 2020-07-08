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
import com.android.build.api.variant.BuiltArtifact
import com.android.build.api.variant.BuiltArtifacts
import com.android.build.gradle.internal.scope.BuildElements
import com.google.gson.GsonBuilder
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import java.io.File
import java.nio.file.Path

class BuiltArtifactsImpl(
    val version: Int = BuiltArtifacts.METADATA_FILE_VERSION,
    override val artifactType: ArtifactType<*>,
    override val applicationId: String,
    override val variantName: String,
    override val elements: Collection<BuiltArtifactImpl>) : BuiltArtifacts {

    companion object {
        const val METADATA_FILE_NAME = "output.json"
    }

    override fun transform(
        newArtifactType: ArtifactType<*>,
        transformer: (input: File) -> File
    ): BuiltArtifacts =
        BuiltArtifactsImpl(
            version,
            newArtifactType,
            applicationId,
            variantName,
            elements.map {
                it.newOutput(transformer(it.outputFile.toFile()).toPath())
            })

    override fun save(out: Directory) {
        val outFile = File(out.asFile, METADATA_FILE_NAME)
        save(outFile)
    }

    private fun save(out: File) {
        out.writeText(persist(out.parentFile.toPath()), Charsets.UTF_8)
    }

    private fun persist(projectPath: Path): String {
        val gsonBuilder = GsonBuilder()
        gsonBuilder.registerTypeAdapter(BuiltArtifactImpl::class.java, BuiltArtifactTypeAdapter())
        gsonBuilder.registerTypeHierarchyAdapter(ArtifactType::class.java, ArtifactTypeTypeAdapter())
        val gson = gsonBuilder
            .enableComplexMapKeySerialization()
            .setPrettyPrinting()
            .create()

        // flatten and relativize the file paths to be persisted.
        return gson.toJson(BuiltArtifactsImpl(
            version,
            artifactType,
            applicationId,
            variantName,
            elements
                .asSequence()
                .map { builtArtifact ->
                    BuiltArtifactImpl(
                        projectPath.relativize(builtArtifact.outputFile),
                        builtArtifact.properties,
                        builtArtifact.versionCode,
                        builtArtifact.versionName,
                        builtArtifact.isEnabled,
                        builtArtifact.outputType,
                        builtArtifact.filters
                    )
                }
            .toList()))
    }
}