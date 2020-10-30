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
package com.android.build.gradle.internal.cxx.gradle.generator

import com.android.Version
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsonCompositeVisitor
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsonStatsBuildingVisitor
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsonStreamingParser
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsonStreamingVisitor
import com.android.build.gradle.internal.cxx.json.CompileCommandFlagListInterner
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeArtifact
import com.android.builder.model.NativeFile
import com.android.builder.model.NativeSettings
import com.android.builder.model.NativeToolchain
import com.android.builder.model.NativeVariantAbi
import com.android.builder.model.NativeVariantInfo
import com.google.gson.stream.JsonReader
import com.google.wireless.android.sdk.stats.GradleBuildVariant.NativeBuildConfigInfo
import java.io.File
import java.util.UUID

/** Builder class for [NativeAndroidProject] or [NativeVariantAbi]. */
class NativeAndroidProjectBuilder {
    private val projectName: String
    private val selectedAbiName: String?
    private val buildFiles: MutableSet<File> = mutableSetOf()
    private val variantInfos: MutableMap<String, NativeVariantInfo> = mutableMapOf()
    private val extensions: MutableMap<String, String> = mutableMapOf()
    private val artifacts: MutableList<NativeArtifact> = mutableListOf()
    private val toolChains: MutableList<NativeToolchain> = mutableListOf()
    private val interner = CompileCommandFlagListInterner()
    private val settingsMap: MutableMap<List<String>, NativeSettings> = mutableMapOf()
    private val buildSystems: MutableSet<String> = mutableSetOf()

    constructor(projectName: String) {
        this.projectName = projectName
        selectedAbiName = null
    }

    constructor(projectName: String, selectedAbiName: String) {
        this.projectName = projectName
        this.selectedAbiName = selectedAbiName
    }

    fun addBuildSystem(buildSystem : String) {
        buildSystems.add(buildSystem)
    }

    /** Add information about a particular variant.  */
    fun addVariantInfo(
        variantName: String,
        abiNames: List<String>,
        buildRootFolderMap: Map<String, File>) {
        variantInfos[variantName] = NativeVariantInfoImpl(abiNames, buildRootFolderMap)
    }

    /**
     * Add a per-variant Json to builder. JSon is streamed so it is not read into memory all at
     * once. Simultaneously updates stats in NativeBuildConfigInfo.Builder.
     */
    fun addJson(
        reader: JsonReader,
        variantName: String,
        config: NativeBuildConfigInfo.Builder) {
        val modelBuildingVisitor = JsonStreamingVisitor(this, variantName, selectedAbiName)
        val statsVisitor = AndroidBuildGradleJsonStatsBuildingVisitor(config)
        val composite = AndroidBuildGradleJsonCompositeVisitor(statsVisitor, modelBuildingVisitor)
        AndroidBuildGradleJsonStreamingParser(reader, composite).use { parser -> parser.parse() }
    }

    /**
     * Add a per-variant Json to builder. Json is streamed so it is not read into memory all at
     * once.
     */
    fun addJson(
        reader: JsonReader,
        variantName: String) {
        val modelBuildingVisitor = JsonStreamingVisitor(this, variantName, selectedAbiName)
        AndroidBuildGradleJsonStreamingParser(reader, modelBuildingVisitor).use { 
                parser -> parser.parse() 
        }
    }

    /** Build the final [NativeAndroidProject].  */
    fun buildNativeAndroidProject(): NativeAndroidProject? {
        // If there are no build files and no build variant configurations then return null
        // to indicate there is no C++ in this project.
        return if (buildFiles.isEmpty() && variantInfos.isEmpty()) {
            null
        } else NativeAndroidProjectImpl(
            Version.ANDROID_GRADLE_PLUGIN_VERSION,
            projectName,
            buildFiles,
            variantInfos,
            artifacts,
            toolChains,
            settingsMap.values.toList(), // Makes a copy
            extensions,
            buildSystems,
            Version.BUILDER_MODEL_API_VERSION
        )
    }

    /** Build a [NativeVariantAbi] which is partial information about a project.  */
    fun buildNativeVariantAbi(variantName: String): NativeVariantAbi? {
        // If there are no build files (therefore no native configurations) don't return a model
        return if (buildFiles.isEmpty()) {
            null
        } else NativeVariantAbiImpl(
            variantName,
            selectedAbiName!!,
            buildFiles,
            artifacts,
            toolChains,
            settingsMap.values.toList(),
            extensions
        )
    }

    /**
     * Json streaming parser that converts a series of JSon files to [NativeAndroidProject]
     */
    internal class JsonStreamingVisitor(
        private val builder: NativeAndroidProjectBuilder,
        private val variantName: String,
        private val selectedAbiName: String?) : AndroidBuildGradleJsonStreamingVisitor() {
        private val stringTable: MutableMap<Int, String> = mutableMapOf()
        private var currentToolchain: String? = null
        private var currentCExecutable: String? = null
        private var currentCppExecutable: String? = null
        private var currentLibraryName: String? = null
        private var currentLibraryToolchain: String? = null
        private var currentLibraryOutput: String? = null
        private var currentLibraryAbi: String? = null
        private var currentLibraryArtifactName: String? = null
        private var currentLibraryRuntimeFiles: MutableList<File>? = null
        private var currentLibrarySourceFiles: MutableList<NativeFile>? = null
        private var currentLibraryFileSettingsName: String? = null
        private var currentLibraryFilePath: String? = null
        private var currentLibraryFileWorkingDirectory: String? = null
        
        override fun visitStringTableEntry(
            index: Int,
            value: String) {
            stringTable[index] = value
        }

        public override fun visitBuildFile(buildFile: String) {
            builder.buildFiles.add(File(buildFile))
        }

        public override fun beginLibrary(libraryName: String) {
            currentLibraryName = libraryName
            currentLibraryRuntimeFiles = mutableListOf()
            currentLibrarySourceFiles = mutableListOf()
        }

        public override fun endLibrary() {
            if (isCurrentAbiAcceptable) {
                builder.artifacts.add(
                    NativeArtifactImpl(
                        currentLibraryName!!,
                        currentLibraryToolchain!!,
                        variantName,
                        "",
                        currentLibrarySourceFiles!!,
                        listOf(),
                        currentLibraryOutput?.let { File(it) },
                        currentLibraryRuntimeFiles!!,
                        currentLibraryAbi!!,
                        currentLibraryArtifactName!!
                    )
                )
            }
            currentLibraryName = null
            currentLibraryToolchain = null
            currentLibraryOutput = null
            currentLibraryAbi = null
            currentLibraryArtifactName = null
            currentLibraryRuntimeFiles = null
            currentLibrarySourceFiles = null
        }

        private val isCurrentAbiAcceptable get() =
            selectedAbiName == null || selectedAbiName == currentLibraryAbi

        public override fun beginToolchain(toolchain: String) {
            currentToolchain = toolchain
        }

        public override fun endToolchain() {
            builder.toolChains.add(
                NativeToolchainImpl(
                    currentToolchain!!,
                    currentCExecutable?.let { File(it) },
                    currentCppExecutable?.let { File(it) }
                )
            )
            currentToolchain = null
            currentCExecutable = null
            currentCppExecutable = null
        }

        public override fun visitLibraryAbi(abi: String) {
            currentLibraryAbi = abi
        }

        public override fun visitLibraryArtifactName(artifact: String) {
            currentLibraryArtifactName = artifact
        }

        override fun visitLibraryOutput(output: String?) {
            currentLibraryOutput = output
        }

        public override fun visitLibraryToolchain(toolchain: String) {
            currentLibraryToolchain = toolchain
        }

        public override fun visitToolchainCCompilerExecutable(executable: String) {
            currentCExecutable = executable
        }

        public override fun visitToolchainCppCompilerExecutable(executable: String) {
            currentCppExecutable = executable
        }

        public override fun visitLibraryFileFlags(flags: String) {
            if (isCurrentAbiAcceptable) {
                currentLibraryFileSettingsName = getSettingsName(flags)
            }
        }

        override fun visitLibraryFileFlagsOrdinal(flagsOrdinal: Int) {
            visitLibraryFileFlags(stringTable[flagsOrdinal]!!)
        }

        public override fun visitLibraryFileSrc(src: String) {
            currentLibraryFilePath = src
        }

        public override fun visitLibraryFileWorkingDirectory(workingDirectory: String) {
            currentLibraryFileWorkingDirectory = workingDirectory
        }

        override fun visitLibraryFileWorkingDirectoryOrdinal(
            workingDirectoryOrdinal: Int
        ) {
            visitLibraryFileWorkingDirectory(stringTable[workingDirectoryOrdinal]!!)
        }

        public override fun visitCFileExtensions(extension: String) {
            builder.extensions[extension] = "c"
        }

        public override fun visitCppFileExtensions(extension: String) {
            builder.extensions[extension] = "c++"
        }

        public override fun visitLibraryRuntimeFile(runtimeFile: String) {
            currentLibraryRuntimeFiles!!.add(File(runtimeFile))
        }

        public override fun endLibraryFile() {
            if (currentLibraryFileSettingsName != null) {
                // See https://issuetracker.google.com/73122455
                // In the case where there is no flags field we don't generate a settings key
                // Just skip this library source file. Since we don't have flags we can't tell
                // if it is even an Android-targeting build.
                currentLibrarySourceFiles!!.add(
                    NativeFileImpl(
                        File(currentLibraryFilePath!!),
                        currentLibraryFileSettingsName!!,
                        currentLibraryFileWorkingDirectory?.let { File(it) }
                    )
                )
            }
            currentLibraryFilePath = null
            currentLibraryFileSettingsName = null
            currentLibraryFileWorkingDirectory = null
        }

        private fun getSettingsName(flags: String): String {
            val tokens = builder.interner.internFlags(flags)
            var setting = builder.settingsMap[tokens]
            if (setting == null) {
                // Settings needs to be unique so that AndroidStudio can combine settings
                // from multiple NativeAndroidAbi without worrying about collision.
                setting = NativeSettingsImpl("setting" + UUID.randomUUID(), tokens)
                builder.settingsMap[tokens] = setting
            }
            return setting.name
        }

    }
}