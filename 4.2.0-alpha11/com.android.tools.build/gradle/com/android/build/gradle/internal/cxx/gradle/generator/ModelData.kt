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

import com.android.build.gradle.internal.cxx.configure.ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeArtifact
import com.android.builder.model.NativeFile
import com.android.builder.model.NativeSettings
import com.android.builder.model.NativeToolchain
import com.android.builder.model.NativeVariantAbi
import com.android.builder.model.NativeVariantInfo
import java.io.File
import java.io.Serializable

data class NativeAndroidProjectImpl(
    private val modelVersion: String,
    private val name: String,
    private val buildFiles: Collection<File>,
    private val variantInfos: Map<String, NativeVariantInfo>,
    private val artifacts: Collection<NativeArtifact>,
    private val toolChains: Collection<NativeToolchain>,
    private val settings: Collection<NativeSettings>,
    private val fileExtensions: Map<String, String>,
    private val buildSystems: Collection<String>,
    private val apiVersion: Int
) : NativeAndroidProject, Serializable {
    override fun getApiVersion() = apiVersion
    override fun getModelVersion() = modelVersion
    override fun getName() = name
    override fun getVariantInfos() = variantInfos
    override fun getBuildFiles() = buildFiles
    override fun getArtifacts() = artifacts
    override fun getToolChains() = toolChains
    override fun getSettings() = settings
    override fun getFileExtensions() = fileExtensions
    override fun getBuildSystems() = buildSystems
    override fun getDefaultNdkVersion() = ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION
    companion object {
        private const val serialVersionUID = 5L
    }
}

data class NativeArtifactImpl(
    private val name: String,
    private val toolChain: String,
    private val groupName: String,
    private val assembleTaskName: String,
    private val sourceFiles: List<NativeFile>,
    private val exportedHeaders: List<File>,
    private val outputFile: File?,
    private val runtimeFiles: List<File>,
    private val abi: String,
    private val targetName: String) : NativeArtifact, Serializable {
    override fun getName() = name
    override fun getToolChain() = toolChain
    override fun getGroupName() = groupName
    override fun getAssembleTaskName() = assembleTaskName
    override fun getSourceFiles() = sourceFiles
    override fun getExportedHeaders() = exportedHeaders
    override fun getAbi() = abi
    override fun getTargetName() = targetName
    override fun getOutputFile() = outputFile
    override fun getRuntimeFiles() = runtimeFiles
    companion object {
        private const val serialVersionUID = 5L
    }
}

data class NativeFileImpl(
    private val filePath : File,
    private val settingsName : String,
    private val workingDirectory : File?
) : NativeFile, Serializable  {
    override fun getWorkingDirectory() = workingDirectory
    override fun getFilePath() = filePath
    override fun getSettingsName() = settingsName
    companion object {
        private const val serialVersionUID = 5L
    }
}

data class NativeToolchainImpl(
    private val name : String,
    private val cCompilerExecutable : File?,
    private val cppCompilerExecutable : File?) : NativeToolchain, Serializable  {
    override fun getName() = name
    override fun getCCompilerExecutable() = cCompilerExecutable
    override fun getCppCompilerExecutable() = cppCompilerExecutable
    companion object {
        private const val serialVersionUID = 5L
    }
}

data class NativeVariantAbiImpl(
    private val variantName : String,
    private val abi : String,
    private val buildFiles : Collection<File>,
    private val artifacts : Collection<NativeArtifact>,
    private val toolchains : Collection<NativeToolchain>,
    private val settings : Collection<NativeSettings>,
    private val fileExtensions : Map<String, String>) : NativeVariantAbi, Serializable  {
    override fun getVariantName() = variantName
    override fun getAbi() = abi
    override fun getBuildFiles() = buildFiles
    override fun getArtifacts() = artifacts
    override fun getToolChains() = toolchains
    override fun getSettings() = settings
    override fun getFileExtensions() = fileExtensions
    companion object {
        private const val serialVersionUID = 5L
    }
}

data class NativeVariantInfoImpl(
    private val abiNames : List<String>,
    private val buildRootFolderMap : Map<String, File>) : NativeVariantInfo, Serializable {
    override fun getAbiNames() = abiNames
    override fun getBuildRootFolderMap() = buildRootFolderMap
    companion object {
        private const val serialVersionUID = 5L
    }
}

data class NativeSettingsImpl(
    private val name: String,
    private val compilerFlags: List<String>
) : NativeSettings, Serializable {
    override fun getName() = name
    override fun getCompilerFlags() = compilerFlags
    companion object {
        private const val serialVersionUID = 5L
    }
}
