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
import com.google.gson.stream.JsonToken
import java.io.Closeable

/**
 * Parser for a clang compilation database JSON file.
 * Accepts a CompilationDatabaseStreamingVisitor which can be customized to operate on individual
 * pieces of the database.
 */
open class CompilationDatabaseStreamingParser(
    private val reader : JsonReader,
    private val visitor : CompilationDatabaseStreamingVisitor) : Closeable {
    override fun close() {
        reader.close()
    }

    /** Main entry point to the streaming parser.  */
    fun parse() {
        reader.beginArray()
        while (reader.hasNext()) {
            when (reader.peek()) {
                JsonToken.BEGIN_OBJECT -> parseCompilationEntry()
                else -> parseUnknown()
            }
        }
        reader.endArray()
    }

    private fun parseCompilationEntry() {
        reader.beginObject()
        visitor.beginCommand()
        while (reader.hasNext()) {
            val name = reader.nextName()
            when (name) {
                "directory" -> visitor.visitDirectory(reader.nextString())
                "command" -> visitor.visitCommand(reader.nextString())
                "file" -> visitor.visitFile(reader.nextString())
                else -> throw RuntimeException("'$name' is not a recognized field")
            }
        }
        visitor.endCommand()
        reader.endObject()
    }

    private fun parseUnknown() {
        throw RuntimeException(reader.peek().toString())
    }
}