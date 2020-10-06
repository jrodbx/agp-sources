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

package com.android.utils.cxx

import com.android.utils.cxx.Sections.CompileCommands
import com.android.utils.cxx.Sections.FlagLists
import com.android.utils.cxx.Sections.StringTable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Encodes and decodes a file format that represents the same information
 * as in compile_commands.json.
 *
 * When decoding, strings are naturally interned. It means that a string
 * doesn't need to be allocated to determine whether it exists already
 * in an intern table.
 *
 * For this reason, deserialization is as much as 200x faster than GSON
 * for highly redundant cases (which is the usual). Also, the on-disk
 * representation is up to 190x smaller (138MB vs 720KB in HugeProject*3
 * without distinct per-file flag).
 *
 * File format
 * -----------
 * header:
 * - magic: C/C++ Build Metadata{EOF} [MAGIC]
 * - version: int32 [VERSION]
 * - num-sections: int32
 *
 * section-index (one per num-sections):
 * - section-type: int32 ([Sections] StringTable, FlagLists, CompileCommands)
 * - offset: int64 (offset into this file of that section)
 *
 * string-table-section [StringTable]:
 * - num-strings: int32
 * - strings (one per num-strings):
 *   - string-length: int32
 *   - content: utf8-bytes (one byte per string-length)
 *
 * flag-lists-section [FlagLists]:
 * - num-flag-lists: int32
 * - flag-lists (one per num-flag-lists):
 *   - list-length: int32
 *   - list: int32 (one element per list-length, index into string-table-section)
 *
 * compile-commands-section [CompileCommands]:
 * - num-messages: int32
 * - messages (one per num-messages):
 *   - message-type: byte ([COMPILE_COMMAND_CONTEXT_MESSAGE] or [COMPILE_COMMAND_FILE_MESSAGE])
 *   - file-context-message [COMPILE_COMMAND_CONTEXT_MESSAGE]:
 *     - compiler: int32 (index into string-table-section)
 *     - flags: int32 (index into flag-lists-section)
 *     - working-directory: int32 (index into string-table-section)
 *   - file-message [COMPILE_COMMAND_FILE_MESSAGE]:
 *     - source-file-path: int32 (index into string-table-section)
 *
 */
private const val MAGIC = "C/C++ Build Metadata\u001a"
private const val VERSION = 1
private const val COMPILE_COMMAND_CONTEXT_MESSAGE : Byte = 0x00
private const val COMPILE_COMMAND_FILE_MESSAGE : Byte = 0x01

private const val BYTEBUFFER_WINDOW_SIZE = 1024 * 32 // Grow by 32K each time growth is needed

/**
 * Enum of different file section types.
 */
private enum class Sections {
    StringTable,
    FlagLists,
    CompileCommands;
    companion object {
        private val map = values().map { Pair(it.ordinal, it) }.toMap()
        fun getByValue(value: Int) = map[value]
    }
}

/**
 * Class that encodes compile commands into a binary format.
 */
class CompileCommandsEncoder(val file : File) : AutoCloseable {
    private val ras = RandomAccessFile(file, "rw")
    private val channel = ras.channel
    private val lock = channel.lock()
    private var bufferStartPosition = 0L
    private var map = ByteBuffer.allocate(BYTEBUFFER_WINDOW_SIZE)
    private val stringTableIndexEntry : Long
    private val flagListsIndexEntry : Long
    private val compileCommandsIndexEntry : Long
    private val countOfSourceMessagesOffset : Long
    private val stringTable = mutableMapOf<String, Int>()
    private val flagsTable = mutableMapOf<List<Int>, Int>()
    private var countOfSourceMessages = 0
    private var lastCompilerWritten = -1
    private var lastFlagsWritten = -1
    private var lastWorkingDirectoryWritten = -1

    init {

        /**
         * Write the header and section index. Offsets into the file are not known
         * yet, so they are left as zero for now
         *
         * header:
         * - magic: C/C++ Build Metadata{EOF}
         * - version: int32
         * - num-sections: int32
         *
         * section-index (one per num-sections):
         * - section-type: int32 (StringTable, FlagLists, CompileCommands)
         * - offset: int64 (offset into this file of that section)
         *
         */
        encodeMagic()
        encodeInt(VERSION)
        encodeInt(3) // Number of sections
        compileCommandsIndexEntry = writeSectionIndexEntry(CompileCommands)
        stringTableIndexEntry = writeSectionIndexEntry(StringTable)
        flagListsIndexEntry = writeSectionIndexEntry(FlagLists)
        countOfSourceMessagesOffset = map.position().toLong()
        encodeInt(0) // Reserve for count of source messages
    }

    /**
     * Write [MAGIC] including the embedded EOF.
     */
    private fun encodeMagic() {
        val bytes = MAGIC.toByteArray(Charsets.UTF_8)
        val map = ensureAtLeast(bytes.size)
        map.put(bytes)
    }

    /**
     * Write a string as length followed by bytes of UTF8 encoding
     */
    private fun encodeUTF8(string : String) {
        val bytes = string.toByteArray(Charsets.UTF_8)
        val map = ensureAtLeast(
            Short.SIZE_BYTES +
            bytes.size
        )
        map.putInt(bytes.size)
        map.put(bytes)
    }

    private fun encodeByte(byte : Byte) = ensureAtLeast(1).put(byte)
    private fun encodeInt(int : Int) = ensureAtLeast(Int.SIZE_BYTES).putInt(int)
    private fun encodeLongZero() = ensureAtLeast(Long.SIZE_BYTES).putLong(0L)

    /**
     * Check the size of [map] and grow if necessary.
     */
    private fun ensureAtLeast(bytes: Int) : ByteBuffer {
        if (BYTEBUFFER_WINDOW_SIZE <= map.position() + bytes) {
            flushBuffer()
        }
        return map
    }

    /**
     * Write the buffer out and reset to the start.
     */
    private fun flushBuffer() {
        if (map.position() == 0) return
        ras.seek(bufferStartPosition)
        ras.write(map.array(), 0, map.position())
        bufferStartPosition += map.position()
        map.position(0)
    }

    /**
     * Intern a string and return it's unique index number.
     * Index 0 is reserved for null.
     */
    private fun intern(string: String?) =
        if (string == null) 0
        else stringTable.computeIfAbsent(string) {
            stringTable.size + 1 // +1 to reserve 0 for 'null'
        }

    /**
     * Intern a flags list and return it's unique index.
     */
    private fun intern(flags: List<Int>) = flagsTable.computeIfAbsent(flags) {
        flagsTable.size
    }

    /**
     * Write a section index entry with offset initially set to zero:
     *
     * section-index (one per num-sections):
     * - section-type: int32 ([Sections] StringTable, FlagLists, CompileCommands)
     * - offset: int64 (offset into this file of that section)
     *
     */
    private fun writeSectionIndexEntry(section: Sections): Long {
        val result = map.position().toLong()
        encodeInt(section.ordinal)
        encodeLongZero()
        return result
    }

    /**
     * Write a new compile command. This will be one or two messages.
     *
     * First, if compiler, flags, or working directory has changed since last time then write a
     * [COMPILE_COMMAND_CONTEXT_MESSAGE] with that information.
     *
     * Then, write the [COMPILE_COMMAND_FILE_MESSAGE] with just the path to the source file.
     *
     * - messages (one per num-messages):
     *   - message-type: byte ([COMPILE_COMMAND_CONTEXT_MESSAGE] or [COMPILE_COMMAND_FILE_MESSAGE])
     *   - file-context-message [COMPILE_COMMAND_CONTEXT_MESSAGE]:
     *     - compiler: int32 (index into string-table-section)
     *     - flags: int32 (index into flag-lists-section)
     *     - working-directory: int32 (index into string-table-section)
     *   - file-message [COMPILE_COMMAND_FILE_MESSAGE]:
     *     - source-file-path: int32 (index into string-table-section)
     */
    fun writeCompileCommand(
        sourceFile: File,
        compiler: File,
        flags: List<String>,
        workingDirectory: File) {
        val compilerIndex = intern(compiler.path)
        val flagsIndex = intern(flags.map { intern(it) })
        val workingDirectoryIndex = intern(workingDirectory.path)
        if (compilerIndex != lastCompilerWritten ||
                flagsIndex != lastFlagsWritten ||
                workingDirectoryIndex != lastWorkingDirectoryWritten) {
            // Encode file context
            encodeByte(COMPILE_COMMAND_CONTEXT_MESSAGE)
            encodeInt(compilerIndex)
            encodeInt(flagsIndex)
            encodeInt(workingDirectoryIndex)
            lastCompilerWritten = compilerIndex
            lastFlagsWritten = flagsIndex
            lastWorkingDirectoryWritten = workingDirectoryIndex
            ++countOfSourceMessages
        }

        // Encode the file
        encodeByte(COMPILE_COMMAND_FILE_MESSAGE)
        encodeInt(intern(sourceFile.path))
        ++countOfSourceMessages
    }

    /**
     * On close, write the final information needed to complete the file.
     * - string-table-section
     * - flag-lists-section
     * - File offset of compile-commands-section
     * - Number of compile-commands-section messages
     * - File offset of string-table-section
     * - File offset of flag-lists-section
     */
    override fun close() {
        // Write the string table
        val offsetOfStringTable = bufferStartPosition + map.position()
        encodeInt(stringTable.size)
        stringTable.toList()
            .sortedBy { (_, index) -> index }
            .forEach { (string, _) -> encodeUTF8(string) }

        // Write the flags lists
        val offsetOfFlagLists = bufferStartPosition + map.position()
        encodeInt(flagsTable.size)
        flagsTable.toList()
            .sortedBy { (_, index) -> index }
            .forEach { (flags, _) ->
                encodeInt(flags.size)
                flags.forEach { flag -> encodeInt(flag) }
            }

        // Flush remaining bytes in buffer.
        flushBuffer()

        // Here and below use random access (ras) rather than streaming write
        // so use 'ras' instead of 'map' for these sections. Set 'map' to null as a safety.
        map = null

        // Write the number of source file messages
        ras.seek(countOfSourceMessagesOffset)
        ras.writeInt(countOfSourceMessages)
        ras.seek(compileCommandsIndexEntry + Int.SIZE_BYTES)
        ras.writeLong(countOfSourceMessagesOffset)

        // Write the offset of strings table
        ras.seek(stringTableIndexEntry + Int.SIZE_BYTES)
        ras.writeLong(offsetOfStringTable)

        // Write the offset of flags table
        ras.seek(flagListsIndexEntry + Int.SIZE_BYTES)
        ras.writeLong(offsetOfFlagLists)

        lock.close()
        channel.close()
        ras.close()
    }
}

/**
 * Read [MAGIC] and [VERSION] and return the position immediately after.
 */
private fun MappedByteBuffer.positionAfterMagicAndVersion(file: File) : Int {
    position(0)
    MAGIC.forEach { expected ->
        val actual = get()
        if (actual != expected.toByte()) {
            error("$file is not a valid C/C++ Build Metadata file")
        }
    }
    int // Version. Don't error if not our current version. It may be backward compatible.
    return position()
}

/**
 * Leave the buffer pointer set at the beginning of [section]
 */
private fun MappedByteBuffer.seekSection(start: Int, section: Sections) {
    position(start)
    val indexSize = int
    repeat(indexSize) {
        val type = Sections.getByValue(int)
        val offset = long
        if (type == section) {
            position(offset.toInt())
            return
        }
    }
}

/**
 * Read a UTF8 string.
 */
private fun MappedByteBuffer.readUTF8() : String {
    val bytes = ByteArray(int)
    get(bytes)
    return String(bytes, Charsets.UTF_8)
}

/**
 * Read the string table.
 */
private fun MappedByteBuffer.readStringTable(start: Int) : Array<String?> {
    seekSection(start, StringTable)
    val count = int
    return (listOf(null) + (0 until count)
            .map { readUTF8() })
            .toTypedArray()
}

/**
 * Read the flags table.
 */
private fun MappedByteBuffer.readFlagsTable(start: Int, strings : Array<String?>) : Array<List<String>> {
    seekSection(start, FlagLists)
    val count = int
    return (0 until count)
            .map {
                val elements = int
                (0 until elements).map { strings[int]!! }
            }.toTypedArray()
}

/**
 * Stream compile commands from [file]. Commands arrive in the form of a callback [action].
 *
 * The callback [action] takes four parameters:
 * - sourceFile: File
 * - compiler: the path to the compiler executable
 * - flags: List<String>
 * - workingDirectory: File
 *
 * All of these parameters are interned and do not need to be re-interned on the receiving side.
 */
fun streamCompileCommands(file: File,
        action : (sourceFile:File, compiler:File, flags:List<String>, workingDirectory:File) -> Unit) {
    RandomAccessFile(file, "r").use { ras ->
        val map = ras.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
        val start = map.positionAfterMagicAndVersion(file)
        val strings = map.readStringTable(start)
        val flags = map.readFlagsTable(start, strings)
        val internedFiles = mutableMapOf<Int, File>()
        fun internFile(index: Int) : File? {
            return if (index == 0) null
            else internedFiles.computeIfAbsent(index) {
                File(strings[index]!!)
            }
        }
        map.seekSection(start, CompileCommands)
        val sourceMessagesCount = map.int
        lateinit var lastCompiler: File
        var lastFlags = listOf<String>()
        var lastWorkingDirectory = File("")
        repeat(sourceMessagesCount) {
            when(map.get()) {
                COMPILE_COMMAND_CONTEXT_MESSAGE -> {
                    lastCompiler = File(strings[map.int]!!)
                    lastFlags = flags[map.int]
                    lastWorkingDirectory = internFile(map.int)!!
                }
                COMPILE_COMMAND_FILE_MESSAGE -> {
                    action(
                        internFile(map.int)!!,
                        lastCompiler,
                        lastFlags,
                        lastWorkingDirectory
                    )
                }
                else -> error("Unexpected")
            }
        }
    }
}

/**
 * Strips off arguments that don't affect IDE functionalities from the given list of arguments
 * passed to clang.
 *
 * @param sourceFile the source file to compile
 * @param args the raw args passed to the compiler
 * @param scratchSpace the list used to build the final result. This is useful if caller would like
 * to save some object allocations when stripping a large amount of commands. Caller don't need to
 * clear the scratch space.
 */
fun stripArgsForIde(
    sourceFile: String,
    args: List<String>,
    scratchSpace: MutableList<String> = ArrayList()
): List<String> {
    scratchSpace.clear()
    var i = 0
    while (i < args.size) {
        when (val arg = args[i]) {
            in STRIP_FLAGS_WITHOUT_ARG -> {
            }
            in STRIP_FLAGS_WITH_ARG -> i++
            else -> if (STRIP_FLAGS_WITH_IMMEDIATE_ARG.none { arg.startsWith(it) } && arg != sourceFile) {
                // Skip args that starts with flags that we should strip. Also skip source file.
                scratchSpace += arg
            }
        }
        i++
    }
    return scratchSpace
}

// Skip -M* flags because these govern the creation of .d files in gcc. We don't want
// spurious files dropped by Cidr. See see b.android.com/215555 and
// b.android.com/213429.
// Also, removing these flags reduces the number of Settings groups that have to be
// passed to Android Studio.

/** These are flags that should be stripped and have a following argument. */
val STRIP_FLAGS_WITH_ARG =
    listOf(
        "-o",
        "--output",
        "-MF",
        "-MT",
        "-MQ"
    )

/** These are flags that have arguments immediate following them. */
val STRIP_FLAGS_WITH_IMMEDIATE_ARG = listOf(
    "--output=",
    "-MF",
    "-MT",
    "-MQ"
)

/** These are flags that should be stripped and don't have a following argument. */
val STRIP_FLAGS_WITHOUT_ARG: List<String> =
    listOf( // Skip -M* flags because these govern the creation of .d files in gcc. We don't want
        // spurious files dropped by Cidr. See see b.android.com/215555 and
        // b.android.com/213429
        "-M", "-MM", "-MD", "-MG", "-MP", "-MMD",
        // -c tells the compiler to skip linking, which is always true for Android build.
        "-c"
    )
