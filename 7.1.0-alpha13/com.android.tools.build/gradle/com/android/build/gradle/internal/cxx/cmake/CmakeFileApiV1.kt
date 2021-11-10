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
package com.android.build.gradle.internal.cxx.cmake

import com.android.build.gradle.internal.cxx.cmake.TargetDataItem.Artifacts
import com.android.build.gradle.internal.cxx.cmake.TargetDataItem.CompileGroups
import com.android.build.gradle.internal.cxx.cmake.TargetDataItem.Dependencies
import com.android.build.gradle.internal.cxx.cmake.TargetDataItem.Link
import com.android.build.gradle.internal.cxx.cmake.TargetDataItem.Paths
import com.android.build.gradle.internal.cxx.cmake.TargetDataItem.Source
import com.android.build.gradle.internal.cxx.cmake.TargetDataItem.SourceGroups
import com.android.build.gradle.internal.cxx.cmake.TargetDataItem.Type
import com.android.build.gradle.internal.cxx.configure.CmakeProperty
import com.android.build.gradle.internal.cxx.configure.convertCMakeToCompileCommandsBin
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons.writeNativeBuildConfigValueToJsonFile
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValue
import com.android.build.gradle.internal.cxx.json.NativeLibraryValue
import com.android.build.gradle.internal.cxx.json.NativeToolchainValue
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.warnln
import com.android.utils.cxx.CxxDiagnosticCode.EXTRA_OUTPUT
import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.Closeable
import java.io.File
import java.io.FileReader

/**
 * CMake file-api reply directory is laid out as follows:
 *
 *    index-{unique}.json -- Index into other files in this reply.
 *
 *    cmakeFiles-v1-{hash}.json -- List of CMakeLists.txt and other
 *    build files.
 *
 *    cache-v2-{hash}.json -- CMake properties
 *
 *    codemodel-v2-{hash}.json -- References to projects and targets.
 *    Contains filenames of target json files.
 *
 *    target-hello-jni-Debug-{hash}.json -- Referenced by codemodel file.
 *    Contains source files and flags for one target.
 *
 * This is the V1 version of the CMake file API schema as described in
 * https://cmake.org/cmake/help/latest/manual/cmake-file-api.7.html
 */

/**
 * The index file is the entry-point for locating the real query results.
 * It is defined as the lexically greatest index*.json file
 */
private fun findCmakeQueryApiIndexFile(replyFolder: File) : File? {
    if (!replyFolder.isDirectory) error("$replyFolder was not a directory")
    val files = replyFolder.listFiles()
    if (files == null || files.isEmpty()) error("$replyFolder didn't have any files: $files")
    return files.toList()
            .filter { it.name.startsWith("index") && it.name.endsWith(".json") }
            .maxByOrNull { it.name }
}


/**
 * Information about a single source file.
 */
data class CmakeFileApiSourceFile(
    // Path to the root sources folder
    val rootSourceFolder : File,
    // Path to a particular source file, if not absolute then relative to [rootSourceFolder]
    val sourcePath : String,
    // A type for this file. For example, "Source Files" or "Header Files"
    val sourceGroup : String,
    // Contains flags, defines, and other information relevant to a single source or header file
    val compileGroup : TargetCompileGroupData?
)

/**
 * This function makes a single pass through CMake file API reply file structure returning
 * information needed to construct a native model. The source files and flags may be large
 * so they are streamed via a callback function [sourceFlagAction].
 */
fun readCmakeFileApiReply(
    replyFolder: File,
    sourceFlagAction: (CmakeFileApiSourceFile) -> Unit
) : NativeBuildConfigValue {
    val indexFile = findCmakeQueryApiIndexFile(replyFolder)!!
    val index = FileReader(indexFile).use { reader ->
        GSON.fromJson(reader, IndexData::class.java)
    }
    val config = NativeBuildConfigValue()

    // Read CMake cache properties
    val cache = index.getIndexObject("cache", replyFolder, CmakeFileApiCacheDataV2::class.java)!!
    val ld = cache.getCacheString(CmakeProperty.CMAKE_LINKER)!!
    val abi = cache.getCacheString(CmakeProperty.CMAKE_ANDROID_ARCH_ABI)
    val ninja = cache.getCacheString(CmakeProperty.CMAKE_MAKE_PROGRAM)
    val toolchain = NativeToolchainValue()
    toolchain.cCompilerExecutable = inferToolExeFromExistingTool(ld, "clang")
    toolchain.cppCompilerExecutable = inferToolExeFromExistingTool(ld, "clang++")
    config.toolchains = mapOf("toolchain" to toolchain)

    // Read CMakeFiles information
    val cmakeFiles = index.getIndexObject("cmakeFiles", replyFolder, CmakeFileApiCmakeFilesDataV1::class.java)!!
    val rootSourceFolder = File(cmakeFiles.paths.source)
    val rootBuildFolder = File(cmakeFiles.paths.build)
    config.buildFiles = cmakeFiles
            .inputs
            .map { input -> rootSourceFolder.resolve(input.path) }
            .filter { input ->
                // Keep only the files named CMakeLists.txt or files in this project
                // that end with .cmake or .cmake.in
                input.name.equals("CMakeLists.txt", ignoreCase = true)
                || (input.path.startsWith(rootSourceFolder.path, ignoreCase = true)
                    && !input.path.startsWith(rootBuildFolder.path, ignoreCase = true)
                    && (input.path.endsWith(".cmake.in", ignoreCase = true)
                        || input.path.endsWith(".cmake", ignoreCase = true)))
            }
            .distinct()
            .sorted()
    config.cleanCommandsComponents = listOf(listOf(ninja, "-C", rootBuildFolder.path, "clean"))

    // This is a compound function that collects symbols directories and also
    // issues callbacks to [sourceFlagAction] for each source file. It's compound
    // because the information for both comes from the same target json file and
    // we only want to scan it once since it can be large.
    val targetIdToOutputs = mutableMapOf<String, MutableSet<String>>()
    val targetIdToType = mutableMapOf<String, String>()
    val targetIdToNativeLibraryValue = mutableMapOf<String, NativeLibraryValue>()
    val languageToExtensionMap = mutableMapOf<String, MutableSet<String>>()
    val codeModel = index.getIndexObject("codemodel", replyFolder, CmakeFileApiCodeModelDataV2::class.java)!!
    val targetIdToLink = mutableMapOf<String, Link>()

    val targetDataFiles = codeModel.configurations
        .flatMap { it.targets }
        .map { target ->
            targetIdToNativeLibraryValue.computeIfAbsent(target.id) {
                NativeLibraryValue()
            }.artifactName = target.name
            Pair(target.id, target.jsonFile)
        }

    targetDataFiles
        .forEach { (id, jsonFile) ->
            lateinit var compileGroups : List<TargetCompileGroupData>
            lateinit var sourceGroups : List<String>
            TargetDataStream(id, replyFolder.resolve(jsonFile)).use {
                it.stream().forEach { item ->
                    when (item) {
                        is Artifacts -> {
                            targetIdToOutputs.computeIfAbsent(item.targetId) { mutableSetOf() }
                                    .addAll(
                                            item.artifacts.map { artifact ->
                                                File(cmakeFiles.paths.build)
                                                        .resolve(artifact)
                                                        .normalize()
                                                        .path
                                            }
                                    )
                        }
                        is CompileGroups -> compileGroups = item.compileGroups
                        is SourceGroups -> sourceGroups = item.sourceGroups
                        is Link -> targetIdToLink[id] = item
                        is Type -> targetIdToType[id] = item.type
                        is Source -> {
                            // This relies on "compileGroups" arriving before "sources". Without this
                            // assumption we'd need to scan source files twice, first to find compileGroups
                            // then to scan sources and join with compileGroups.
                            val sourceGroup = sourceGroups[item.sourceGroupIndex]
                            val compileGroup =
                                    if (item.compileGroupIndex != null) {
                                        compileGroups[item.compileGroupIndex]
                                    } else {
                                        // No compile group probably means the sourceGroup is "Header Files"
                                        null
                                    }
                            // Record the file extension for each language seen.
                            if (compileGroup != null) {
                                languageToExtensionMap.computeIfAbsent(compileGroup.language) {
                                    mutableSetOf()
                                }.add(File(item.path).extension)
                            }

                            sourceFlagAction(
                                    CmakeFileApiSourceFile(
                                            rootSourceFolder = rootSourceFolder,
                                            sourcePath = item.path,
                                            sourceGroup = sourceGroup,
                                            compileGroup = compileGroup
                                    )
                            )
                        }
                        else -> {
                        }
                    }
                }
            }
        }

    // Populate NativeLibraryValues#output
    targetIdToOutputs
        .filter {(id, _) -> targetIdToType.get(id) != "OBJECT_LIBRARY" }
        .forEach { (id, outputs) ->
        if (outputs.size > 1) {
            errorln(
                EXTRA_OUTPUT, "Target $id produces multiple outputs ${outputs.joinToString(", ")}"
            )
        }
        targetIdToNativeLibraryValue
                .computeIfAbsent(id) { NativeLibraryValue() }
                    .output = outputs.map(::File).firstOrNull()
    }

    // Populate NativeLibraryValues#runtimeFiles
    targetIdToLink.forEach { (id, link) ->
        targetIdToNativeLibraryValue
            .computeIfAbsent(id) { NativeLibraryValue() }
                .runtimeFiles =
                    link.compileCommandFragments
                        .filter {
                            // Just 'libraries' role
                            it.role == "libraries" &&
                            // Only accept .so
                            it.fragment.endsWith(".so") &&
                            // Ignore libraries under sysroot
                            !it.fragment.startsWith(link.sysroot)
                        }
                        .map {
                            File(cmakeFiles.paths.build)
                                .resolve(it.fragment)
                                .normalize()
                        }
                        .toList()
    }

    config.libraries = targetIdToNativeLibraryValue

    // Set toolchain
    targetIdToNativeLibraryValue.forEach {
        it.value.toolchain = "toolchain"
        it.value.abi = abi
    }

    // Set extensions
    config.cFileExtensions = languageToExtensionMap["C"] ?: listOf()
    config.cppFileExtensions = languageToExtensionMap["CXX"] ?: listOf()

    return config
}

/**
 * Unfortunately, CMake file API doesn't provide a way to get the path to the
 * compiler. This function infers a tool from an existing tool. It uses the
 * path and file extension.
 */
fun inferToolExeFromExistingTool(existingTool:String, newTool:String) : File {
    val existing = File(existingTool)
    val binFolder = existing.parentFile
    val extension = existing.extension
    return if (extension.isEmpty()) binFolder.resolve(newTool)
        else binFolder.resolve("$newTool.$extension")
}

private class TargetDataStream(
        val targetId : String,
        file : File) : Closeable {
    private val reader = JsonReader(FileReader(file))
    fun stream() : Sequence<TargetDataItem> = sequence {
        reader.beginObject()
        while (reader.hasNext()) {
            when(reader.peek()) {
                JsonToken.NAME -> {
                    when(reader.nextName()) {
                        "artifacts" -> yield(Artifacts(
                                targetId,
                                readSingleStringObjectList("path")))
                        "compileGroups" -> yield(CompileGroups(readCompileGroups()))
                        "link" -> yield(readLink())
                        "dependencies" -> yield(Dependencies(targetId,
                                readSingleStringObjectList("id")))
                        "sourceGroups" -> yield(SourceGroups(
                                readSingleStringObjectList("name")))
                        "paths" -> yield(readPaths())
                        "sources" -> yieldAll(streamSources())
                        "type" -> yield(Type(reader.nextString()))
                        else -> reader.skipValue()
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
    }

    private fun streamSources() : Sequence<Source> = sequence {
        reader.beginArray()
        while (reader.hasNext()) {
            when (reader.peek()) {
                JsonToken.BEGIN_OBJECT -> yield(readSource())
                else -> reader.skipValue()
            }
        }
        reader.endArray()
    }

    private fun readSource() : Source {
        reader.beginObject()
        var sourceGroupIndex = -1
        var compileGroupIndex : Int? = null
        lateinit var path: String
        while (reader.hasNext()) {
            when (reader.peek()) {
                JsonToken.NAME -> {
                    when(reader.nextName()) {
                        "sourceGroupIndex" -> sourceGroupIndex = reader.nextInt()
                        "compileGroupIndex" -> compileGroupIndex = reader.nextInt()
                        "path" -> path = reader.nextString()
                        else -> reader.skipValue()
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return Source(
                sourceGroupIndex = sourceGroupIndex,
                compileGroupIndex = compileGroupIndex,
                path = path
        )
    }

    private fun readCompileGroups() : List<TargetCompileGroupData> = sequence {
        reader.beginArray()
        while (reader.hasNext()) {
            when (reader.peek()) {
                JsonToken.BEGIN_OBJECT -> yield(readCompileGroup())
                else -> reader.skipValue()
            }
        }
        reader.endArray()
    }.toList()

    private fun readLink() : Link {
        reader.beginObject()
        lateinit var commandFragments : List<CommandFragmentData>
        lateinit var language : String
        lateinit var sysroot : String
        while (reader.hasNext()) {
            when(reader.nextName()) {
                "commandFragments" -> commandFragments = readCommandFragments()
                "language" -> language = reader.nextString()
                "sysroot" -> sysroot = readSingleStringObject("path")
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return Link(targetId, commandFragments, language, sysroot)
    }

    private fun readPaths() : Paths {
        reader.beginObject()
        lateinit var build : String
        lateinit var source: String
        while (reader.hasNext()) {
            when(reader.nextName()) {
                "build" -> build = reader.nextString()
                "source" -> source = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return Paths(
            build = build,
            source = source)
    }

    private fun readCommandFragments() : List<CommandFragmentData> = sequence {
        reader.beginArray()
        while (reader.hasNext()) {
            when (reader.peek()) {
                JsonToken.BEGIN_OBJECT -> yield(readCommandFragment())
                else -> reader.skipValue()
            }
        }
        reader.endArray()
    }.toList()

    private fun readCommandFragment() : CommandFragmentData {
        reader.beginObject()

        lateinit var role: String
        lateinit var fragment: String
        while (reader.hasNext()) {
            when(reader.nextName()) {
                "role" -> role = reader.nextString()
                "fragment" -> fragment = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return CommandFragmentData(
                role = role,
                fragment = fragment)
    }

    private fun readCompileGroup() : TargetCompileGroupData {
        reader.beginObject()
        // In the case of assembler code, there is no compileCommandFragments
        var compileCommandFragments = listOf<String>()
        var defines = listOf<String>()
        var includes = listOf<String>()
        var language = "Unknown Language from CMake file API"
        var sysroot = "Unknown Sysroot from CMake file API"
        while (reader.hasNext()) {
            when (reader.peek()) {
                JsonToken.NAME -> {
                    when(reader.nextName()) {
                        "compileCommandFragments" -> compileCommandFragments = readSingleStringObjectList("fragment")
                        "defines" -> defines = readSingleStringObjectList("define").toList()
                        "includes" -> includes = readSingleStringObjectList("path").toList()
                        "language" -> language = reader.nextString()
                        "sysroot" -> sysroot = readSingleStringObject("path")
                        else -> reader.skipValue()
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return TargetCompileGroupData(
                compileCommandFragments = compileCommandFragments,
                defines = defines,
                includes = includes,
                language = language,
                sysroot = sysroot
        )
    }

    private fun readSingleStringObjectList(expectedName: String) : List<String> = sequence {
        reader.beginArray()
        while (reader.hasNext()) {
            when (reader.peek()) {
                JsonToken.BEGIN_OBJECT -> yield(readSingleStringObject(expectedName))
                else -> reader.skipValue()
            }
        }
        reader.endArray()
    }.toList()

    private fun readSingleStringObject(expectedName : String) : String {
        reader.beginObject()
        var result : String? = null
        val unknownNames = mutableSetOf<String>()
        while (reader.hasNext()) {
            when (reader.peek()) {
                JsonToken.NAME ->
                    when(val name = reader.nextName()) {
                        expectedName -> result = reader.nextString()
                        else -> {
                            unknownNames += name
                            reader.skipValue()
                        }
                    }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        if (result == null) {
            error("No name $expectedName found in ${unknownNames.joinToString(",")}")
        }
        return result
    }

    override fun close() {
        reader.close()
    }
}

private val GSON = GsonBuilder()
    .setPrettyPrinting()
    .create()

/**
 * Convert a CMake file API reply folder into the metadata that
 * we need to provide Intellisense, caching, etc.
 */
fun parseCmakeFileApiReply(
    replyFolder : File,
    additionalFiles : File,
    androidGradleBuildJsonFile : File,
    compileCommandsJsonFile : File,
    compileCommandsJsonBinFile : File,
    buildTargetsCommand : List<String>) {
    try {
        val extra = mutableSetOf<String>()
        val config = readCmakeFileApiReply(replyFolder) { source ->
            if (source.sourceGroup != "Source Files") {
                extra += source.rootSourceFolder.resolve(source.sourcePath).absolutePath
            }
        }
        additionalFiles.writeText(extra.joinToString("\n"))

        // Write the ninja build command, possibly with user settings from CMakeSettings.json.
        config.buildTargetsCommandComponents = buildTargetsCommand

        writeNativeBuildConfigValueToJsonFile(
            androidGradleBuildJsonFile,
            config
        )

        // Write compile_commands.json.bin
        convertCMakeToCompileCommandsBin(
            compileCommandsJsonFile,
            compileCommandsJsonBinFile,
        )
    } catch (e : Exception) {
        // Give the user a way to open a constructive bug
        warnln("There was an error parsing CMake File API result. Please open a bug with zip of ${compileCommandsJsonFile.parentFile.absoluteFile}", e)
        throw e
    }
}
