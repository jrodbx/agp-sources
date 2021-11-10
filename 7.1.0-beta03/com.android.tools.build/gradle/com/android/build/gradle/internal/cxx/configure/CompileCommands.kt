/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.configure

import com.android.SdkConstants
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.utils.TokenizedCommandLineMap
import com.android.utils.cxx.CompileCommandsEncoder
import com.android.utils.cxx.CxxDiagnosticCode.COULD_NOT_EXTRACT_OUTPUT_FILE_FROM_CLANG_COMMAND
import com.android.utils.cxx.CxxDiagnosticCode.OBJECT_FILE_CANT_BE_CONVERTED_TO_TARGET_NAME
import com.android.utils.cxx.STRIP_FLAGS_WITHOUT_ARG
import com.android.utils.cxx.STRIP_FLAGS_WITH_ARG
import com.android.utils.cxx.STRIP_FLAGS_WITH_IMMEDIATE_ARG
import com.android.utils.cxx.compileCommandsFileIsCurrentVersion
import com.google.gson.stream.JsonReader
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Convert from compile_commands.json [json] to compile_commands.json.bin
 * [bin]. Only convert if [bin] is out-of-date with respect to [json].
 *
 * This function only works for CMake-generated compile_commands.json because
 * it uses the .o output path to figure out the corresponding build target
 * name. See [extractCMakeTargetFromObjectFile] for details on how that is
 * done.
 */
fun convertCMakeToCompileCommandsBin(
    json : File,
    bin : File,
    platform: Int = SdkConstants.currentPlatform(),
    stringToFile: (String) -> File = { s:String -> File(s) } ) {

    // If bin exists but is not the current version, then delete it so that
    // it can be recreated with current version information.
    if (bin.exists()) {
        if (!compileCommandsFileIsCurrentVersion(bin)) {
            infoln("Deleting prior $bin because it was invalid")
            Files.delete(bin.toPath())
        }
    }

    // lastModified return 0L when the file doesn't exist so file existence
    // check is implied here.
    if (json.isFile) {
        if (json.lastModified() == bin.lastModified()) {
            infoln("$bin was up-to-date with respect to $json")
            return
        }
    } else {
        // The compile_commands.json.bin file may exist without there being
        // a source compile_commands.json file. When this comment was written
        // it always happens for the ndk-build metadata generator. The reason
        // is that that generator can write directly to
        // compile_commands.json.bin without needing an intermediary copy of
        // that same information in compile_commands.json.
        infoln("$bin existed but not $json")
        return
    }
    JsonReader(json.reader(StandardCharsets.UTF_8)).use { reader ->
        CompileCommandsEncoder(bin).use { encoder ->
            val tokenMap =
                TokenizedCommandLineMap<Triple<String, List<String>, String?>>(
                    raw = false,
                    platform = platform) {
                        tokens,
                        sourceFile ->
                    tokens.removeTokenGroup(
                        sourceFile,
                        0,
                        filePathSlashAgnostic = true)

                    for (flag in STRIP_FLAGS_WITH_ARG) {
                        tokens.removeTokenGroup(flag, 1)
                    }
                    for (flag in STRIP_FLAGS_WITH_IMMEDIATE_ARG) {
                        tokens.removeTokenGroup(flag, 0, matchPrefix = true)
                    }
                    for (flag in STRIP_FLAGS_WITHOUT_ARG) {
                        tokens.removeTokenGroup(flag, 0)
                    }
                }
            reader.beginArray()
            while (reader.hasNext()) {
                reader.beginObject()
                lateinit var directory: String
                lateinit var command: String
                lateinit var sourceFile: String
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "directory" -> directory = reader.nextString()
                        "command" -> command = reader.nextString()
                        "file" -> sourceFile = reader.nextString()
                        // swallow other optional fields
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                val (compiler, flags, output) = tokenMap.computeIfAbsent(command, sourceFile) {
                    // Find the output file (for example, probably something.o)
                    val outputFile =
                        it.removeTokenGroup("-o", 1, returnFirstExtra = true) ?:
                        it.removeTokenGroup("--output=", 0, matchPrefix = true, returnFirstExtra = true) ?:
                        it.removeTokenGroup("--output", 1, returnFirstExtra = true)
                    val tokenList = it.toTokenList()
                    Triple(tokenList[0], tokenList.subList(1, tokenList.size), outputFile)
                }
                if(output == null) {
                    errorln(
                        COULD_NOT_EXTRACT_OUTPUT_FILE_FROM_CLANG_COMMAND,
                        "Expected to find output file in command-line: ${flags.joinToString()}")
                    return
                }

                encoder.writeCompileCommand(
                    sourceFile = stringToFile(sourceFile),
                    compiler = stringToFile(compiler),
                    flags = flags,
                    workingDirectory = stringToFile(directory),
                    outputFile = stringToFile(output),
                    target = extractCMakeTargetFromObjectFile(stringToFile(output))
                )
            }
            reader.endArray()
        }
    }
    // Set timestamp of bin file to exactly timestamp of compile_commands.json
    bin.setLastModified(json.lastModified())
    infoln("Exiting generation of $bin normally")
}

/**
 * This function extracts a build target from a CMake object file
 * path.
 *
 * CMake .o files are stored in a directory structure like:
 *
 *   BasePath/CMakeFiles/hello-jni.dir/src/main/cxx/hello-jni.c.o
 *
 * Where "hello-jni" from "hello-jni.dir" is the associated build
 * target.
 */
private fun extractCMakeTargetFromObjectFile(objectFile : File) : String {
    var current = objectFile.parentFile
    while(current != null) {
        if (current.extension == "dir") {
            val targetCandidate = current.nameWithoutExtension
            // Also check that the next segment is CMakeFiles to ensure it
            // wasn't a user folder that happened to end in .dir
            current = current.parentFile
            if (current == null || current.name != "CMakeFiles") {
                continue
            }
            return targetCandidate
        }
        current = current.parentFile
    }
    errorln(
        OBJECT_FILE_CANT_BE_CONVERTED_TO_TARGET_NAME,
        "Could not determine target from [$objectFile]")
    return "unknown-target"
}
