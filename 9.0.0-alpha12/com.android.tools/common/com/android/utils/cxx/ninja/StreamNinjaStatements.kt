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

package com.android.utils.cxx.ninja

import com.android.utils.cxx.io.ProgressCallback
import com.android.utils.cxx.io.progressReader
import com.android.utils.cxx.ninja.FileState.EXPLICIT
import com.android.utils.cxx.ninja.FileState.IMPLICIT
import com.android.utils.cxx.ninja.FileState.ORDER_ONLY
import com.android.utils.cxx.ninja.NinjaBuildTokenType.DoublePipeType
import com.android.utils.cxx.ninja.NinjaBuildTokenType.EOFType
import com.android.utils.cxx.ninja.NinjaBuildTokenType.EOLType
import com.android.utils.cxx.ninja.NinjaBuildTokenType.IndentType
import com.android.utils.cxx.ninja.NinjaBuildTokenType.PipeType
import com.android.utils.cxx.ninja.NinjaBuildTokenType.TextType
import com.android.utils.cxx.ninja.NinjaStatement.Assignment
import com.android.utils.cxx.ninja.NinjaStatement.BuildDef
import com.android.utils.cxx.ninja.NinjaStatement.Default
import com.android.utils.cxx.ninja.NinjaStatement.Include
import com.android.utils.cxx.ninja.NinjaStatement.PoolDef
import com.android.utils.cxx.ninja.NinjaStatement.RuleDef
import com.android.utils.cxx.ninja.NinjaStatement.SubNinja
import com.android.utils.cxx.ninja.State.BUILD_EXPECT_INPUTS_OR_EOL
import com.android.utils.cxx.ninja.State.BUILD_EXPECT_OUTPUT_OR_COLON
import com.android.utils.cxx.ninja.State.BUILD_EXPECT_RULE
import com.android.utils.cxx.ninja.State.DONE
import com.android.utils.cxx.ninja.State.EXPECT_EQUALS_THEN_VALUE
import com.android.utils.cxx.ninja.State.EXPECT_FILE
import com.android.utils.cxx.ninja.State.EXPECT_LIST
import com.android.utils.cxx.ninja.State.EXPECT_NAME_THEN_PROPERTIES
import com.android.utils.cxx.ninja.State.EXPECT_PROPERTIES
import com.android.utils.cxx.ninja.State.EXPECT_PROPERTY_EQUALS
import com.android.utils.cxx.ninja.State.EXPECT_PROPERTY_IDENTIFIER
import com.android.utils.cxx.ninja.State.EXPECT_PROPERTY_VALUE
import com.android.utils.cxx.ninja.State.EXPECT_VALUE
import com.android.utils.cxx.ninja.State.START
import com.android.utils.cxx.ninja.State.SYNTAX_ERROR
import com.google.common.annotations.VisibleForTesting
import java.io.File
import java.io.Reader

/**
 * Exception indicating a syntax
 */
class NinjaStatementSyntaxException(val token : String)  : Exception(token)

/**
 * This function builds on and is one level higher than [streamNinjaBuildTokens]. Its purpose is to
 * create a stream of Ninja statements (called [NinjaStatement]) like compile 'build', 'rule',
 * 'subninja', and 'include' statements.
 *
 * The 'subninja' and 'include' statements refer to another file. These statements are not expanded
 * by this function. Instead, an outer function that knows how to locate those files should be
 * used.
 */
@VisibleForTesting
fun Reader.streamNinjaStatements(action:(NinjaStatement) -> Unit) {
    var state = START
    var fileState = EXPLICIT
    val stack = mutableListOf<Any>()
    fun syntax(token : CharSequence) : State {
        throw NinjaStatementSyntaxException(token.toString())
    }
    streamNinjaBuildTokens { type, value ->
        var done: Boolean
        do {
            done = true
            when (state) {
                START -> {
                    stack.clear()
                    fileState = EXPLICIT
                    when (type) {
                        EOLType, EOFType -> { }
                        IndentType -> syntax(value)
                        TextType -> state = when (value) {
                            "include" -> {
                                stack.push(Include(""))
                                EXPECT_FILE
                            }
                            "subninja" -> {
                                stack.push(SubNinja(""))
                                EXPECT_FILE
                            }
                            "build" -> {
                                stack.push(mutableMapOf<FileState, MutableList<String>>())
                                BUILD_EXPECT_OUTPUT_OR_COLON
                            }
                            "rule" -> {
                                stack.push(RuleDef("", mapOf()))
                                EXPECT_NAME_THEN_PROPERTIES
                            }
                            "pool" -> {
                                stack.push(PoolDef("", mapOf()))
                                EXPECT_NAME_THEN_PROPERTIES
                            }
                            "default" -> {
                                stack.push(Default(listOf()))
                                EXPECT_LIST
                            }
                            else -> {
                                stack.push(value.toString())
                                EXPECT_EQUALS_THEN_VALUE
                            }
                        }
                        PipeType, DoublePipeType -> syntax(value)
                        else -> error(value)
                    }
                }
                EXPECT_EQUALS_THEN_VALUE -> state = when (value) {
                    "=" -> EXPECT_VALUE
                    else -> syntax(value)
                }
                EXPECT_VALUE -> {
                    val identifier = stack.pop<String>()
                    when(type) {
                        TextType -> action(Assignment(identifier, value.toString()))
                        EOLType -> action(Assignment(identifier, ""))
                        else -> error(type.toString())
                    }
                    state = START
                }
                EXPECT_FILE -> {
                    action(when(val node = stack.pop<Any>()) {
                        is Include -> node.copy(file = value.toString())
                        is SubNinja ->node.copy(file = value.toString())
                        else -> error("${node.javaClass}")
                    })
                    state = START
                }
                BUILD_EXPECT_OUTPUT_OR_COLON -> {
                    state = when (type) {
                        PipeType -> {
                            fileState = IMPLICIT
                            BUILD_EXPECT_OUTPUT_OR_COLON
                        }
                        DoublePipeType -> {
                            fileState = ORDER_ONLY
                            BUILD_EXPECT_OUTPUT_OR_COLON
                        }
                        TextType -> {
                            if (value == ":") {
                                fileState = EXPLICIT
                                BUILD_EXPECT_RULE
                            } else {
                                val outputs = stack.pop<MutableMap<FileState, MutableList<String>>>()
                                outputs.computeIfAbsent(fileState) { mutableListOf() }.add(value.toString())
                                stack.push(outputs)
                                BUILD_EXPECT_OUTPUT_OR_COLON
                            }
                        }
                        EOLType, EOFType -> syntax(value)
                        else -> error(value)
                    }
                }
                BUILD_EXPECT_RULE -> state = when(type) {
                    TextType -> {
                        stack.push(value.toString())
                        stack.push(mutableMapOf<FileState, MutableList<String>>())
                        BUILD_EXPECT_INPUTS_OR_EOL
                    }
                    EOFType, EOLType -> syntax(value)
                    else -> error(value)
                }
                BUILD_EXPECT_INPUTS_OR_EOL -> state = when (type) {
                    PipeType -> {
                        fileState = IMPLICIT
                        BUILD_EXPECT_INPUTS_OR_EOL
                    }
                    DoublePipeType -> {
                        fileState = ORDER_ONLY
                        BUILD_EXPECT_INPUTS_OR_EOL
                    }
                    EOFType,
                    EOLType -> {
                        val inputs = stack.pop<MutableMap<FileState, MutableList<String>>>()
                        val rule = stack.pop<String>()
                        val outputs = stack.pop<MutableMap<FileState, MutableList<String>>>()
                        stack.push(BuildDef(
                            explicitOutputs = outputs[EXPLICIT] ?: listOf(),
                            implicitOutputs = outputs[IMPLICIT] ?: listOf(),
                            rule = rule,
                            explicitInputs = inputs[EXPLICIT] ?: listOf(),
                            implicitInputs = inputs[IMPLICIT] ?: listOf(),
                            orderOnlyInputs = inputs[ORDER_ONLY] ?: listOf(),
                            properties = mapOf()
                        ))
                        stack.push(mutableMapOf<String, String>())
                        EXPECT_PROPERTIES
                    }
                    TextType -> {
                        // An input
                        val outputs = stack.pop<MutableMap<FileState, MutableList<String>>>()
                        outputs.computeIfAbsent(fileState) { mutableListOf() }.add(value.toString())
                        stack.push(outputs)
                        BUILD_EXPECT_INPUTS_OR_EOL
                    }
                    else -> error(value)
                }
                EXPECT_PROPERTIES -> state = when (type) {
                    IndentType -> EXPECT_PROPERTY_IDENTIFIER
                    EOLType -> EXPECT_PROPERTIES
                    EOFType,
                    TextType -> {
                        val properties = stack.pop<MutableMap<String, String>>()
                        action(when(val receiver = stack.pop<NinjaStatement>()) {
                            is BuildDef -> receiver.copy(properties = properties)
                            is RuleDef -> receiver.copy(properties = properties)
                            is PoolDef -> receiver.copy(properties = properties)
                            else -> error("${receiver.javaClass}")
                        })
                        // The property list was terminated by a new statement.
                        // Set 'done' to false so that this new statement can be evaluated.
                        done = false
                        START
                    }
                    else -> error(value)
                }
                EXPECT_PROPERTY_IDENTIFIER -> state = when(type) {
                    IndentType, EOLType, EOFType -> {
                        done = false
                        EXPECT_PROPERTIES
                    }
                    TextType -> {
                        stack.push(value.toString())
                        EXPECT_PROPERTY_EQUALS
                    }
                    else -> error(value)
                }
                EXPECT_PROPERTY_EQUALS -> {
                    state = if (value != "=") error(value)
                    else EXPECT_PROPERTY_VALUE
                }
                EXPECT_PROPERTY_VALUE -> {
                    val identifier = stack.pop<String>()
                    val properties = stack.pop<MutableMap<String, String>>()
                    when(type) {
                        TextType -> properties[identifier] = value.toString()
                        EOLType -> properties[identifier] = ""
                        else -> error(type)
                    }
                    stack.push(properties)
                    state = EXPECT_PROPERTIES
                }
                EXPECT_LIST -> {
                    state = when (type) {
                        EOLType, EOFType -> {
                            action(stack.pop<Default>())
                            START
                        }
                        TextType -> {
                            val default = stack.pop<Default>()
                            stack.push(default.copy(targets = default.targets + value.toString()))
                            EXPECT_LIST
                        }
                        else -> error(value)
                    }
                }
                EXPECT_NAME_THEN_PROPERTIES -> {
                    stack.push(when(val node = stack.pop<Any>()) {
                        is RuleDef -> node.copy(name = value.toString())
                        is PoolDef -> node.copy(name = value.toString())
                        else -> error("${node.javaClass}")
                    })
                    stack.push(mutableMapOf<String, String>())
                    state = EXPECT_PROPERTIES
                }
                SYNTAX_ERROR ->
                    return@streamNinjaBuildTokens
                DONE -> { }
                else -> error("$state")
            }
        } while(!done)
    }
    if (stack.isNotEmpty() && state != SYNTAX_ERROR) {
        // There should be nothing left on the stack unless a syntax error has been issued.
        throw RuntimeException("$state")
    }
}

/**
 * This function streams [NinjaStatement] like Reader.streamNinjaStatements(...) above but it
 * also knows how to locate external files references from 'include' statements and expands them
 * inline in the [NinjaStatement] stream.
 */
fun streamNinjaStatements(
    file : File,
    progress: ProgressCallback?,
    action:(NinjaStatement) -> Unit) {
    file.progressReader().use { reader ->
        reader.streamNinjaStatements { expression ->
            when (expression) {
                is Include -> {
                    val include = file.parentFile.resolve(expression.file)
                    streamNinjaStatements(include, progress, action)
                }
                else -> action(expression)
            }
            if (progress != null) {
                reader.postProgress(progress)
            }
        }
    }
}

/**
 * Stack-like helper functions over a [MutableList<Any>].
 * The main purpose is [pop] which can be used when the caller knows the expected type to be
 * popped from the stack. The [push] function is here to making the calling code look consistent
 * with respect to stack semantics.
 */
private fun MutableList<Any>.push(node : Any) = add(0, node)
private inline fun <reified T : Any> MutableList<Any>.pop() : T = removeAt(0) as T

sealed class NinjaStatement {
    /**
     * Defines a build of specific build output(s) according to a defined 'rule'.
     *
     * For example,
     *
     * build Externals/soundtouch/CMakeFiles/SoundTouch.dir/AAFilter.cpp.o:
     *      CXX_COMPILER__SoundTouch
     *      ../ext/soundtouch/AAFilter.cpp
     *      || cmake_object_order_depends_target_SoundTouch
     *   DEFINES = -DANDROID -DDATA_DIR=\"/usr/local/share/dolphin-emu/\" -DHAVE_EGL=1
     *             -DUSE_ANALYTICS=1 -DUSE_MEMORYWATCHER=1 -DUSE_PIPES=1 -D_ARCH_64=1
     *             -D_DEFAULT_SOURCE -D_FILE_OFFSET_BITS=64 -D_LARGEFILE_SOURCE -D_M_ARM=1
     *             -D_M_ARM_64=1
     *   DEP_FILE = Externals/soundtouch/CMakeFiles/SoundTouch.dir/AAFilter.cpp.o.d
     *   FLAGS = -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables
     *           -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat
     *           -Werror=format-security   -O2 -g -DNDEBUG -fPIC   -w -march=armv8-a+crc
     *           -fdiagnostics-color -Wall -Wtype-limits -Wsign-compare -Wignored-qualifiers
     *           -Wuninitialized -Wshadow -Winit-self -Wmissing-declarations
     *           -Wmissing-variable-declarations -fno-strict-aliasing -fno-exceptions
     *           -fvisibility-inlines-hidden -fvisibility=hidden -fomit-frame-pointer
     *   INCLUDES = -I../Core -I../../../../../ -I../ext/enet/include -I../ext/minizip
     *   OBJECT_DIR = Externals/soundtouch/CMakeFiles/SoundTouch.dir
     *   OBJECT_FILE_DIR = Externals/soundtouch/CMakeFiles/SoundTouch.dir
     *
     * Above,
     * - the build output is 'Externals/soundtouch/CMakeFiles/SoundTouch.dir/AAFilter.cpp.o'
     * - the rule is 'CXX_COMPILER__SoundTouch'
     *
     */
    data class BuildDef(
        val explicitOutputs: List<String>,
        val implicitOutputs: List<String>,
        val rule: String,
        val explicitInputs: List<String>,
        val implicitInputs: List<String>,
        val orderOnlyInputs: List<String>,
        val properties: Map<String, String>
    ) : NinjaStatement()

    /**
     * A Ninja rule definition
     * (https://github.com/ninja-build/ninja/blob/master/doc/manual.asciidoc#rule-variables)
     *
     * rule CXX_COMPILER__SoundTouch
     *   depfile = $DEP_FILE
     *   deps = gcc
     *   command = /path/to/clang++ --target=aarch64-none-linux-android21
     *     --gcc-toolchain=/path/to/ndk/toolchains/llvm/prebuilt/darwin-x86_64
     *     --sysroot=/path/to/ndk/toolchains/llvm/prebuilt/darwin-x86_64/sysroot
     *     $DEFINES $INCLUDES $FLAGS -MD -MT $out -MF $DEP_FILE -o $out -c $in
     *   description = Building CXX object $out
     *
     * The only required property is 'command'. This defines the command to execute after variables
     * are expanded.
     */
    data class RuleDef(
        val name: String,
        val properties: Map<String, String>
    ) : NinjaStatement()

    /**
     * Pools allow you to allocate one or more rules or edges a finite number of concurrent jobs
     * which is more tightly restricted than the default parallelism.
     *
     *   # No more than 4 links at a time.
     *   pool link_pool
     *     depth = 4
     *
     *   rule link
     *     ...
     *     pool = link_pool
     *
     * see: https://ninja-build.org/manual.html
     */
    data class PoolDef(
        val name: String,
        val properties: Map<String, String>
    ) : NinjaStatement()

    /**
     * Lexical include into the current folder. Relative to the folder that ninja.exe was invoked
     * in.
     *
     * Assignments can affect the including scope.
     *
     * include rules.ninja
     */
    data class Include(val file: String): NinjaStatement()

    /**
     * A ninja submodule.
     *
     * Assignments cannot affect the including scope.
     *
     * subninja rules.ninja
     */
    data class SubNinja(val file: String) : NinjaStatement()

    /**
     * ninja_required_version = 1.5
     */
    data class Assignment(val name: String, val value: String) : NinjaStatement()

    /**
     * Sets the default targets if none are specified from the command-line
     *
     * default all
     */
    data class Default(val targets: List<String>) : NinjaStatement()
}

/**
 * Private enum to track the current state of statement parser.
 */
private enum class State {
    START,
    DONE,
    BUILD_EXPECT_OUTPUT_OR_COLON,
    BUILD_EXPECT_RULE,
    BUILD_EXPECT_INPUTS_OR_EOL,
    EXPECT_VALUE,
    EXPECT_EQUALS_THEN_VALUE,
    EXPECT_FILE,
    EXPECT_PROPERTIES,
    EXPECT_PROPERTY_IDENTIFIER,
    EXPECT_PROPERTY_EQUALS,
    EXPECT_PROPERTY_VALUE,
    EXPECT_LIST,
    EXPECT_NAME_THEN_PROPERTIES,
    SYNTAX_ERROR,
}

/**
 * Private enum to track the current type of file that is expected next.
 */
private enum class FileState {
    EXPLICIT,
    IMPLICIT,
    ORDER_ONLY
}
