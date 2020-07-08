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

import com.android.utils.tokenizeCommandLineToEscaped
import com.google.gson.stream.JsonReader
import java.nio.file.Paths

/**
 * This is a visitor over clang compilation database json file. It builds a string table of
 * the flags. Certain compilation flags are stripped to make it more likely that multiple flags
 * will collapse with each-other. Specifically, those flags are -o and -c which contain the name
 * of the source file within.
 */
class CompilationDatabaseIndexingVisitor(private val strings: StringTable) :
    CompilationDatabaseStreamingVisitor() {
    private var compiler = ""
    private var flags = ""
    private var file = "."
    private val map = mutableMapOf<String, Int>()

    override fun beginCommand() {
        compiler = ""
        flags = ""
        file = "."
    }

    override fun visitFile(file: String) {
        this.file = file
    }

    /**
     * Intern each command after stripping -o and -c flags
     */
    override fun visitCommand(command: String) {
        val tokens = command.tokenizeCommandLineToEscaped()
        compiler = tokens.first()
        val stripped = mutableListOf<String>()
        var skipNext = true // Initially true to remove the actual clang++.exe
        for (token in tokens) {
            if (skipNext) {
                skipNext = false
            } else {
                when (token) {
                    "-o", "-c" -> skipNext = true
                    else -> stripped += token
                }
            }
        }
        val recombined = stripped.joinToString(" ")
        this.flags = recombined
    }

    override fun endCommand() {
        // Use normalized path for consistency.
        var filePath = Paths.get(file).normalize().toString()
        if (filePath.isEmpty()) {
            // If the normalized path is empty string, it's better to use the non-normalized path.
            filePath = Paths.get(file).toString()
        }
        map[filePath] = strings.intern(flags)
    }

    fun mappings(): Map<String, Int> = map
}

/**
 * Given a clang compilation database build a map of file name to flags for that file.
 * The flags are stripped of -o and -c to make them more unique and then interned into a string
 * table.
 */
fun indexCompilationDatabase(compilationDatabase: JsonReader, strings: StringTable):
        Map<String, Int> {
    val visitor = CompilationDatabaseIndexingVisitor(strings)
    CompilationDatabaseStreamingParser(compilationDatabase, visitor).use {
        it.parse()
    }
    return visitor.mappings()
}
