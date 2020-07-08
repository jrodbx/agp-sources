/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.json

import com.google.gson.stream.JsonReader
import java.io.File
import java.io.FileReader

/**
 * Visitor over clang compilation database that finds the toolchain executable for C and C++.
 * Call sequence for CMake-generated compile_commands.json is:
 *
 *   beginCommand
 *     visitDirectory -- contains the working directory for the compile
 *     visitCommand -- contains flags
 *     visitFile -- contains the source file that is being compiled
 *   endCommand
 *   repeat once per source file...
 *
 * Note that the order of visit* calls is not guaranteed, so visitors should not depend on order.
 */
class CompilationDatabaseToolchainVisitor(
    private val cppExtensions: Collection<String>,
    private val cExtensions: Collection<String>) : CompilationDatabaseStreamingVisitor() {
    private var cppCompilerExecutable : File? = null
    private var cCompilerExecutable : File? = null
    private lateinit var command : String
    private lateinit var directory : String
    private lateinit var file : File

    override fun beginCommand() {
        command = ""
        directory = ""
    }

    override fun visitCommand(command: String) {
        this.command = command
    }

    override fun visitDirectory(directory: String) {
        this.directory = directory
    }

    override fun visitFile(file: String) {
        this.file = File(file)
    }

    override fun endCommand() {
        if (command.isEmpty()) {
            return
        }

        val extension = file.extension
        val executable = File(command.substring(0, command.indexOf(' ')))

        if (cppCompilerExecutable == null && cppExtensions.contains(extension)) {
            cppCompilerExecutable = executable
        }
        if (cCompilerExecutable == null && cExtensions.contains(extension)) {
            cCompilerExecutable = executable
        }
    }

    fun result() = CompilationDatabaseToolchain(cppCompilerExecutable, cCompilerExecutable)
}

/**
 * Given a compilation database file, figure out the C++ and C toolchains (the executable for
 * the compiler).
 */
fun populateCompilationDatabaseToolchains(
    compilationDatabase: File,
    cppExtensions: Collection<String>,
    cExtensions: Collection<String>)  : CompilationDatabaseToolchain {
    val visitor = CompilationDatabaseToolchainVisitor(cppExtensions, cExtensions)
    CompilationDatabaseStreamingParser(JsonReader(FileReader(compilationDatabase)), visitor).use {
        it.parse()
    }

    return visitor.result()
}