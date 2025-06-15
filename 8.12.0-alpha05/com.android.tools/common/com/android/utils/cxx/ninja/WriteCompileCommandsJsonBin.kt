/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.utils.cxx.ninja

import com.android.SdkConstants.CURRENT_PLATFORM
import com.android.utils.cxx.io.ProgressCallback
import com.android.utils.cxx.io.filenameEndsWithIgnoreCase
import com.android.utils.cxx.io.hasExtensionIgnoreCase
import com.android.utils.cxx.os.createOsBehavior
import com.android.utils.cxx.collections.DoubleStringBuilder
import com.android.utils.TokenizedCommandLine
import com.android.utils.allocateTokenizeCommandLineBuffer
import com.android.utils.cxx.CompileCommandsEncoder
import com.android.utils.cxx.STRIP_FLAGS_WITHOUT_ARG
import com.android.utils.cxx.STRIP_FLAGS_WITH_ARG
import com.android.utils.cxx.STRIP_FLAGS_WITH_IMMEDIATE_ARG
import com.android.utils.minimumSizeOfTokenizeCommandLineBuffer
import com.google.common.annotations.VisibleForTesting
import java.io.File
import java.util.concurrent.Executors.newFixedThreadPool
import java.util.concurrent.Executors.newSingleThreadExecutor
import java.util.concurrent.TimeUnit.MILLISECONDS


// Define the normal thread shutdown timeout. This is the amount of time allocated to
// finishing writing of compile_commands.json.bin *after* 'build.ninja' has been
// completely read and parsed. For this reason, assume the worst case and give it a
// good amount of time, just not infinite.
private const val NORMAL_SHUTDOWN_TIMEOUT_MS = 30_000L
// This is the error shutdown case. Work shouldn't really be happening since work
// items skip quickly through without processing.
private const val ERROR_SHUTDOWN_TIMEOUT_MS = 500L

/**
 * Writes source files and flags to compile_commands.json.bin.
 * The parsing of command-lines and writing to compile_commands.json.bin is multi-threaded.
 */
fun writeCompileCommandsJsonBin(
    // The 'build.ninja' file to process.
    ninjaBuildFile: File,
    // The root source folder to use when Ninja paths are relative.
    sourcesRoot: File,
    // The compile_commands.json.bin file to write.
    compileCommandsJsonBin: File,
    // The current platform: Windows, Linux, or Mac
    platform: Int = CURRENT_PLATFORM,
    // A progress callback function. If null, progress is not reported.
    progress: ProgressCallback? = null
) {

    // There are three kinds of thread used here:
    // - Main thread is used for reading and parsing build.ninja. The parsing would be difficult to
    //   parallelize since it is a single forward-only pass, so it's combined with the IO to read
    //   build.ninja.
    // - Parse threads (one per CPU core) are used to parse command-lines created during Ninja
    //   parsing.
    // - Write thread is used to write results to compile_commands.json.bin. It is a single thread
    //   because it is IO bound. This avoids the need to synchronize on the output file.
    //
    // On my macbook, the main thread is typically the bottleneck. I found only ~3 parse threads are
    // needed to keep up with the main thread. Occasionally, a backlog of up to 10 work items was
    // observed, but usually it hovered around 1. I left parse threads at number of core anyway
    // because other environments could be different.
    //
    // I never found the write thread to be the bottleneck. That is, the queue never built up past
    // 1 work item.
    val parseExecutor = newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    val writeExecutor = newSingleThreadExecutor()

    val os = createOsBehavior(platform)
    val buffers = mutableListOf<Pair<IntArray, DoubleStringBuilder>>()
    var error : Throwable? = null

    CompileCommandsEncoder(compileCommandsJsonBin).use { encoder ->
        fun writeCommand(
            compiler: File,
            sourceFile: File,
            objectFile: File,
            flags: List<String>) {
            if (error != null) return
            try {
                encoder.writeCompileCommand(
                    sourceFile = sourceFile,
                    compiler = compiler,
                    flags = flags,
                    workingDirectory = sourcesRoot,
                    outputFile = objectFile,
                    target = ""
                )
            } catch (e : Throwable) {
                synchronized(ninjaBuildFile) {
                    error = error ?: e
                }
                throw e
            }
        }

        fun popBuffer() = synchronized(buffers) {
            if (buffers.isEmpty()) intArrayOf(2048) to DoubleStringBuilder()
            else buffers.removeAt(0)
        }

        fun pushBuffer(buffer: Pair<IntArray, DoubleStringBuilder>) = synchronized(buffers) {
            buffers.add(buffer)
        }

        fun expandCommandThenWrite(unexpanded : NinjaBuildUnexpandedCommand) {
            if (error != null) return
            try {
                var (commandLineBuffer, expansionBuffer) = popBuffer()
                val expanded = unexpanded.expandWithResponseFile(expansionBuffer)
                for (subcommand in os.splitCommandLine(expanded)) {
                    // Grow the buffer if necessary
                    if (commandLineBuffer.size <= minimumSizeOfTokenizeCommandLineBuffer(
                            subcommand
                        )
                    ) {
                        commandLineBuffer = allocateTokenizeCommandLineBuffer(subcommand)
                    }
                    val tokens =
                        TokenizedCommandLine(
                            subcommand,
                            false,
                            os.platform,
                            commandLineBuffer
                        )
                    // We're looking for the command-line that invokes clang.exe (or other NDK
                    // toolchain tool).
                    // It is possible the first token is a toolchain wrapper (like those used
                    // in distcc or goma) so we look at the first two tokens and take the one
                    // that looks like a toolchain we recognize.
                    var matched = false
                    repeat(2) {
                        tokens.removeNth(0)?.let { token ->
                            if (isToolchainTool(token)) {
                                val input = deconflictSourceFiles(unexpanded.explicitInputs)
                                tokens.stripFlags(input)
                                val compiler = File(token)
                                val source = sourcesRoot.resolve(input).normalize()
                                val objectFile = File(unexpanded.explicitOutputs[0])
                                val flags = tokens.toTokenList()
                                writeExecutor.execute { writeCommand(
                                    compiler = compiler,
                                    sourceFile = source,
                                    objectFile = objectFile,
                                    flags = flags
                                ) }
                                matched = true
                            }
                        }
                    }
                    if (matched) break
                }
                pushBuffer(commandLineBuffer to expansionBuffer)
            } catch (e : Throwable) {
                synchronized(ninjaBuildFile) {
                    error = error ?: e
                }
                throw e
            }
        }

        fun checkError() {
            val error = error ?: return
            parseExecutor.shutdownNow()
            writeExecutor.shutdownNow()
            parseExecutor.awaitTermination(ERROR_SHUTDOWN_TIMEOUT_MS, MILLISECONDS)
            writeExecutor.awaitTermination(ERROR_SHUTDOWN_TIMEOUT_MS, MILLISECONDS)
            throw error
        }

        try {
            streamNinjaBuildCommands(ninjaBuildFile, progress) {
                // If it looks like a .cpp -> .o or .cpp -> .pch command then write it to
                // compile_commands.json.bin. We don't want other things like .o -> .so.
                if (explicitOutputs.isNotEmpty() &&
                    explicitOutputs[0].hasExtensionIgnoreCase("o", "pch")
                ) {
                    parseExecutor.execute { expandCommandThenWrite(this) }
                }
                checkError()
            }
            // parseExecutor adds items to writeExecutor so fully shut it down before shutting
            // down writeExecutor
            parseExecutor.shutdown()
            parseExecutor.awaitTermination(NORMAL_SHUTDOWN_TIMEOUT_MS, MILLISECONDS)
            writeExecutor.shutdown()
            writeExecutor.awaitTermination(NORMAL_SHUTDOWN_TIMEOUT_MS, MILLISECONDS)
            checkError()
        } catch (e : Throwable) {
            // Best effort to leave compile_commands.json.bin deleted if there was an exception.
            if (compileCommandsJsonBin.isFile) try {
                compileCommandsJsonBin.delete()
            } catch (e : Throwable) {
                // We're going to throw the outer error.
            }
            throw e
        }
    }
}

/**
 * Strip flags that would prevent coalescing with flags of other clang commands.
 * This is mainly:
 * - The source file
 * - The output file
 * - Flags related to #include dependency detection
 */
private fun TokenizedCommandLine.stripFlags(sourceFile : String) {
    // Remove the source file
    removeTokenGroup(
        sourceFile,
        0,
        filePathSlashAgnostic = true)
    // Remove the output file.
    removeTokenGroup("-o", 1)
    removeTokenGroup("--output=", 0, matchPrefix = true)
    removeTokenGroup("--output", 1)
    // This flag produces a lot of STDOUT which can bog down CIDR and make its diagnostics
    // difficult to interpret.
    removeTokenGroup("-fcolor-diagnostics", 1)
    for (flag in STRIP_FLAGS_WITH_ARG) {
        removeTokenGroup(flag, 1)
    }
    for (flag in STRIP_FLAGS_WITH_IMMEDIATE_ARG) {
        removeTokenGroup(flag, 0, matchPrefix = true)
    }
    for (flag in STRIP_FLAGS_WITHOUT_ARG) {
        removeTokenGroup(flag, 0)
    }
}

/**
 * Heuristic to check whether [exe] looks like a relevant tool from the NDK toolset
 */
private fun isToolchainTool(exe : String) =
    exe.filenameEndsWithIgnoreCase("clang", "clang++", "ar") &&
            exe.hasExtensionIgnoreCase("exe", "")

/**
 * Reduce a list of source files to one source file by removing a .pch if there is one.
 */
@VisibleForTesting
fun deconflictSourceFiles(sources: List<String>) =
    sources.singleOrNull { !it.hasExtensionIgnoreCase("pch") } ?: sources.first()
