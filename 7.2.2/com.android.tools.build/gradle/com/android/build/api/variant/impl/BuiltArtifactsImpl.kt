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
import com.android.build.api.variant.BuiltArtifact
import com.android.build.api.variant.BuiltArtifacts
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.ide.common.build.CommonBuiltArtifacts
import com.google.gson.GsonBuilder
import org.gradle.api.file.Directory
import java.io.File
import java.io.Serializable
import java.nio.file.Path
import java.nio.file.Paths

class BuiltArtifactsImpl @JvmOverloads constructor(
    override val version: Int = BuiltArtifacts.METADATA_FILE_VERSION,
    override val artifactType: Artifact<*>,
    override val applicationId: String,
    override val variantName: String,
    override val elements: Collection<BuiltArtifactImpl>,
    private val elementType: String? = null)
    : CommonBuiltArtifacts, BuiltArtifacts, Serializable {

    fun elementType():String? =
        elementType ?: initFileType(elements)

    companion object {
        const val METADATA_FILE_NAME = "output-metadata.json"
        const val REDIRECT_FILE_NAME = "redirect.props"

        private fun initFileType(elements: Collection<BuiltArtifactImpl>): String? {
            val (files, directories)  = elements
                    .asSequence()
                    .map { File(it.outputFile) }
                    .filter { it.exists() }
                    .partition { it.isFile }
            if (files.isNotEmpty() && directories.isNotEmpty()) {
                throw IllegalArgumentException("""
                You cannot store both files and directories as a single artifact.
                ${display(files, "file", "files")}
                ${display(directories, "directory", "directories")}
            """.trimIndent())
            }
            return when {
                files.isNotEmpty() -> "File"
                directories.isNotEmpty() -> "Directory"
                else -> null
            }
        }

        private fun display(files: Collection<File>, singular: String, plural: String): String {
            return if (files.size > 1)
                "${files.joinToString(",") { it.name }} are $plural"
            else "${files.first().name} is a $singular"
        }
    }

    fun addElement(element: BuiltArtifactImpl): BuiltArtifactsImpl {
        val elementsCopy = elements.toMutableList()
        elements.find {
            it.variantOutputConfiguration == element.variantOutputConfiguration
        }?.also { previous: BuiltArtifact -> elementsCopy.remove(previous) }
        elementsCopy.add(element)
        return BuiltArtifactsImpl(
            version,
            artifactType,
            applicationId,
            variantName,
            elementsCopy.toList(),
            elementType
        )
    }

    override fun save(out: Directory) {
        val outFile = File(out.asFile, METADATA_FILE_NAME)
        saveToFile(outFile)
    }

    fun getBuiltArtifact(outputType: VariantOutputConfiguration.OutputType) =
        elements.firstOrNull { it.outputType == outputType }


    fun getBuiltArtifact(variantOutputConfiguration: VariantOutputConfiguration): BuiltArtifactImpl? =
        elements.firstOrNull {
            it.outputType == variantOutputConfiguration.outputType &&
            it.filters == variantOutputConfiguration.filters }

    fun saveToDirectory(folder: File) =
        saveToFile(File(folder, METADATA_FILE_NAME))

    fun saveToFile(out: File) =
        out.writeText(persist(out.parentFile.toPath()), Charsets.UTF_8)

    private fun persist(projectPath: Path): String {
        val gsonBuilder = GsonBuilder()
        gsonBuilder.registerTypeAdapter(BuiltArtifactImpl::class.java, BuiltArtifactTypeAdapter())
        gsonBuilder.registerTypeHierarchyAdapter(Artifact::class.java, ArtifactTypeTypeAdapter())
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
                    BuiltArtifactImpl.make(
                        outputFile = projectPath.relativize(
                            Paths.get(builtArtifact.outputFile)).toString(),
                        versionCode = builtArtifact.versionCode,
                        versionName = builtArtifact.versionName,
                        variantOutputConfiguration = builtArtifact.variantOutputConfiguration,
                        attributes = builtArtifact.attributes

                    )
                }.toList(),
                elementType())
        )
    }
}
