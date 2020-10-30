/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.ide

import com.android.build.api.artifact.ArtifactType
import com.android.build.FilterData
import com.android.build.OutputFile
import com.android.build.VariantOutput
import com.android.build.api.variant.BuiltArtifacts
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.google.common.collect.ImmutableList
import org.gradle.api.model.ObjectFactory
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Temporary class to load enough metadata to populate early model. should be deleted once
 * IDE only relies on minimalistic after build model.
 */
data class EarlySyncBuildOutput(
        val type: ArtifactType<*>,
        val apkType: VariantOutput.OutputType,
        val filtersData: Collection<FilterData>,
        val version: Int,
        val output: File) : java.io.Serializable, OutputFile {

    override fun getOutputFile(): File = output
    override fun getOutputType(): String = apkType.name

    override fun getFilterTypes(): Collection<String> =
            filtersData.asSequence()
                    .map { it.filterType }
                    .toList()

    override fun getFilters(): Collection<FilterData> = filtersData
    override fun getMainOutputFile(): OutputFile = this
    @Suppress("OverridingDeprecatedMember")
    override fun getOutputs(): MutableCollection<out OutputFile> = ImmutableList.of<OutputFile>(this)
    override fun getVersionCode(): Int = version
    fun getFilter(filterType: String): String? =
            filtersData.asSequence().find { it.filterType == filterType }?.identifier

    companion object {
        @JvmStatic
        fun load(metadaFileVersion: Int, folder: File): Collection<EarlySyncBuildOutput> {
            val metadataFile = ExistingBuildElements.getMetadataFileIfPresent(folder)
            if (metadataFile == null || !metadataFile.exists()) {
                return ImmutableList.of<EarlySyncBuildOutput>()
            }

            return try {
                if (metadaFileVersion == 1) loadVersionOneFile(metadataFile)
                else loadVersionTwoFile(metadataFile)
            } catch (e: IOException) {
                ImmutableList.of<EarlySyncBuildOutput>()
            }
        }

        private fun loadVersionOneFile(metadataFile: File): Collection<EarlySyncBuildOutput> {

            // TODO : remove use of ApkInfo and replace with EarlySyncApkInfo.
            val buildElements = ExistingBuildElements.from(metadataFile.parentFile)

            // Some produced BuildOutput's might have null apkData, mostly because
            // they're unused ones or not readable by the current adapter (b/129994596).
            if (buildElements.any { it.apkData == null }) {
                throw IllegalStateException(
                    """
                        Invalid file found (empty apk data).
                        Try to remove ${metadataFile.absolutePath} or clean your build directory.
                        If the error persists, report this issue via Help > Submit Feedback.
                    """.trimIndent())
            }

            // resolve the file path to the current project location.
            val projectPath = metadataFile.parentFile.toPath()
            return buildElements
                    .asSequence()
                    .map { buildOutput ->
                        EarlySyncBuildOutput(
                                buildOutput.type,
                                buildOutput.apkData.type,
                                buildOutput.apkData.filters,
                                buildOutput.apkData.versionCode,
                                projectPath.resolve(buildOutput.outputPath).toFile())
                    }
                    .toList()
        }

        private fun loadVersionTwoFile(metadataFile: File): Collection<EarlySyncBuildOutput> {

            val builtArtifacts = BuiltArtifactsLoaderImpl.loadFromFile(
                metadataFile, metadataFile.parentFile.toPath())
                ?: throw FileNotFoundException("$metadataFile not found")

            // resolve the file path to the current project location.
            val projectPath = metadataFile.parentFile.toPath()
            return builtArtifacts.elements
                .asSequence()
                .map { buildOutput ->

                    val filterData = buildOutput.filters.map { filterConfiguration ->
                        FilterDataImpl(
                            filterConfiguration.filterType.toString(),
                            filterConfiguration.identifier
                        )
                    }

                    EarlySyncBuildOutput(
                        builtArtifacts.artifactType,
                        if (buildOutput.outputType == VariantOutputConfiguration.OutputType.SINGLE)
                            VariantOutput.OutputType.MAIN else VariantOutput.OutputType.FULL_SPLIT,
                        filterData,
                        buildOutput.versionCode,
                        projectPath.resolve(buildOutput.outputFile).toFile())
                }
                .toList()
        }
    }
}
