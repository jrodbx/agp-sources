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
import com.android.utils.cxx.Sections.StringLists
import com.android.utils.cxx.Sections.StringTable
import java.io.File
import java.io.RandomAccessFile
import java.nio.Buffer
import java.nio.ByteBuffer
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
 * - version: int32 [COMPILE_COMMANDS_CODEC_VERSION]
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
 * string-lists-section [StringLists]:
 * - num-string-lists: int32
 * - string-lists (one per num-string-lists):
 *   - list-length: int32
 *   - list: int32 (one element per list-length, index into string-table-section)
 *
 * compile-commands-section [CompileCommands]:
 * - num-messages: int32
 * - num-file-messages: int32
 * - messages (one per num-messages):
 *   - message-type: byte ([COMPILE_COMMAND_CONTEXT_MESSAGE] or [COMPILE_COMMAND_FILE_MESSAGE])
 *   - file-context-message [COMPILE_COMMAND_CONTEXT_MESSAGE]:
 *     - compiler: int32 (index into string-table-section)
 *     - flags: int32 (index into string-lists-section)
 *     - working-directory: int32 (index into string-table-section)
 *     - target: int32 (index into string-table-section)
 *   - file-message [COMPILE_COMMAND_FILE_MESSAGE]:
 *     - source-file-path: int32 (index into string-table-section)
 *     - output-file-path: int32 (index into string-table-section)
 *
 *  Version history:
 *  - V3 Just bumped version number to address b/201754404
 *  - V2 added num-file-messages
 *  - V2 added file-message/output-file-path
 *  - V2 added file-context-message/target
 */
private const val MAGIC = "C/C++ Build Metadata\u001a"
const val COMPILE_COMMANDS_CODEC_VERSION = 3
private const val COMPILE_COMMAND_CONTEXT_MESSAGE : Byte = 0x00
private const val COMPILE_COMMAND_FILE_MESSAGE : Byte = 0x01

private const val BYTEBUFFER_WINDOW_SIZE = 1024 * 32

/**
 * Enum of different file section types.
 */
private enum class Sections {
    StringTable,
    StringLists,
    CompileCommands;
    companion object {
        private val map = values().associateBy { it.ordinal }
        fun getByValue(value: Int) = map[value]
    }
}

/**
 * Class that encodes compile commands into a binary format.
 */
class CompileCommandsEncoder(
    val file : File,
    initialBufferSize : Int = BYTEBUFFER_WINDOW_SIZE
) : AutoCloseable {
    private val ras = RandomAccessFile(file, "rw")
    private val channel = ras.channel
    private val lock = channel.lock()
    private var bufferStartPosition = 0L
    private var map = ByteBuffer.allocate(initialBufferSize)
    private val stringTableIndexEntry : Long
    private val stringListsIndexEntry : Long
    private val compileCommandsIndexEntry : Long
    private val countOfSourceMessagesOffset : Long
    private val stringTable = mutableMapOf<String, Int>()
    private val stringListTable = mutableMapOf<List<Int>, Int>()
    private var countOfSourceMessages = 0
    private var lastCompilerWritten = -1
    private var lastFlagsWritten = -1
    private var lastTargetWritten = -1
    private var lastWorkingDirectoryWritten = -1
    private var countOfSourceFiles = 0

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
        encodeInt(COMPILE_COMMANDS_CODEC_VERSION)
        encodeInt(3) // Number of sections
        compileCommandsIndexEntry = writeSectionIndexEntry(CompileCommands)
        stringTableIndexEntry = writeSectionIndexEntry(StringTable)
        stringListsIndexEntry = writeSectionIndexEntry(StringLists)
        countOfSourceMessagesOffset = ras.filePointer + map.position().toLong()
        encodeInt(0) // Reserve for count of source messages
        encodeInt(0) // Reserve for count of files
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
            Int.SIZE_BYTES +
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
        if (map.capacity() < map.position() + bytes) {
            flushBuffer()
            if (map.capacity() < bytes) {
                map = ByteBuffer.allocate(bytes)
            }
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
        // Type cast is required when this code is compiled by JDK 9+ and runs on JDK 8. See b/165948891 and
        // https://stackoverflow.com/questions/61267495/exception-in-thread-main-java-lang-nosuchmethoderror-java-nio-bytebuffer-flip
        (map as Buffer).clear()
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
     * Intern a string list and return it's unique index.
     */
    private fun intern(strings: List<Int>) = stringListTable.computeIfAbsent(strings) {
        stringListTable.size
    }

    /**
     * Write a section index entry with offset initially set to zero:
     *
     * section-index (one per num-sections):
     * - section-type: int32 ([Sections] StringTable, StringLists, CompileCommands)
     * - offset: int64 (offset into this file of that section)
     *
     */
    private fun writeSectionIndexEntry(section: Sections): Long {
        val result = ras.filePointer + map.position().toLong()
        encodeInt(section.ordinal)
        encodeLongZero()
        return result
    }

    /**
     * Write a new compile command. This will be one or two messages.
     *
     * First, if compiler, flags, target, or working directory has changed since
     * last time then write a [COMPILE_COMMAND_CONTEXT_MESSAGE] with that information.
     *
     * Then, write the [COMPILE_COMMAND_FILE_MESSAGE] with just the path to the source file.
     *
     * - messages (one per num-messages):
     *   - message-type: byte ([COMPILE_COMMAND_CONTEXT_MESSAGE] or [COMPILE_COMMAND_FILE_MESSAGE])
     *   - file-context-message [COMPILE_COMMAND_CONTEXT_MESSAGE]:
     *     - compiler: int32 (index into string-table-section)
     *     - flags: int32 (index into string-lists-section)
     *     - working-directory: int32 (index into string-table-section)
     *     - targets: int32 (index into string-lists-section)
     *   - file-message [COMPILE_COMMAND_FILE_MESSAGE]:
     *     - source-file-path: int32 (index into string-table-section)
     */
    fun writeCompileCommand(
        sourceFile: File,
        compiler: File,
        flags: List<String>,
        workingDirectory: File,
        outputFile: File,
        target: String
    ) {
        val compilerIndex = intern(compiler.path)
        val flagsIndex = intern(flags.map { intern(it) })
        val workingDirectoryIndex = intern(workingDirectory.path)
        val targetIndex = intern(target)
        if (compilerIndex != lastCompilerWritten ||
                flagsIndex != lastFlagsWritten ||
                workingDirectoryIndex != lastWorkingDirectoryWritten ||
                targetIndex != lastTargetWritten) {
            // Encode file context
            encodeByte(COMPILE_COMMAND_CONTEXT_MESSAGE)
            encodeInt(compilerIndex)
            encodeInt(flagsIndex)
            encodeInt(workingDirectoryIndex)
            encodeInt(targetIndex)
            lastCompilerWritten = compilerIndex
            lastFlagsWritten = flagsIndex
            lastWorkingDirectoryWritten = workingDirectoryIndex
            lastTargetWritten = targetIndex
            ++countOfSourceMessages
        }

        // Encode the file
        encodeByte(COMPILE_COMMAND_FILE_MESSAGE)
        encodeInt(intern(sourceFile.path))
        encodeInt(intern(outputFile.path)) // Version 2 and up
        ++countOfSourceMessages
        ++countOfSourceFiles
    }

    /**
     * On close, write the final information needed to complete the file.
     * - string-table-section
     * - string-lists-section
     * - File offset of compile-commands-section
     * - Number of compile-commands-section messages
     * - File offset of string-table-section
     * - File offset of string-lists-section
     */
    override fun close() {
        // Write the string table
        val offsetOfStringTable = bufferStartPosition + map.position()
        encodeInt(stringTable.size)
        stringTable.toList()
            .sortedBy { (_, index) -> index }
            .forEach { (string, _) -> encodeUTF8(string) }

        // Write the strings lists
        val offsetOfStringListsTable = bufferStartPosition + map.position()
        encodeInt(stringListTable.size)
        stringListTable.toList()
            .sortedBy { (_, index) -> index }
            .forEach { (strings, _) ->
                encodeInt(strings.size)
                strings.forEach { id -> encodeInt(id) }
            }

        // Flush remaining bytes in buffer.
        flushBuffer()

        // Here and below use random access (ras) rather than streaming write
        // so use 'ras' instead of 'map' for these sections. Set 'map' to null as a safety.
        map = null

        // Write the number of source file messages
        ras.seek(countOfSourceMessagesOffset)
        ras.writeInt(countOfSourceMessages)
        ras.writeInt(countOfSourceFiles)
        ras.seek(compileCommandsIndexEntry + Int.SIZE_BYTES)
        ras.writeLong(countOfSourceMessagesOffset)

        // Write the offset of strings table
        ras.seek(stringTableIndexEntry + Int.SIZE_BYTES)
        ras.writeLong(offsetOfStringTable)

        // Write the offset of string lists table
        ras.seek(stringListsIndexEntry + Int.SIZE_BYTES)
        ras.writeLong(offsetOfStringListsTable)

        ras.fd.sync()

        lock.close()
        channel.close()
        ras.close()
    }
}

/**
 * Read [MAGIC] and [COMPILE_COMMANDS_CODEC_VERSION] and return the position immediately after.
 * Returns Pair(0, 0) if this isn't a compile_commands.json.bin file.
 */
private fun ByteBuffer.positionAfterMagicAndVersion() : Pair<Int, Int> {
    (this as Buffer).position(0)
    MAGIC.forEach { expected ->
        if (!hasRemaining()) {
            return 0 to 0
        }
        val actual = get()
        if (actual != expected.code.toByte()) {
            return 0 to 0
        }
    }
    val version = int // Version
    return position() to version
}

/**
 * Leave the buffer pointer set at the beginning of [section]
 */
private fun ByteBuffer.seekSection(start: Int, section: Sections) {
    val buffer: Buffer = this
    buffer.position(start)
    val indexSize = int
    repeat(indexSize) {
        val type = Sections.getByValue(int)
        val offset = long
        if (type == section) {
            buffer.position(offset.toInt())
            return
        }
    }
}

/**
 * Read a UTF8 string.
 */
private fun ByteBuffer.readUTF8() : String {
    val bytes = ByteArray(int)
    get(bytes)
    return String(bytes, Charsets.UTF_8)
}

/**
 * Read the string table.
 */
private fun ByteBuffer.readStringTable(start: Int) : Array<String?> {
    seekSection(start, StringTable)
    val count = int
    return (listOf(null) + (0 until count)
            .map { readUTF8() })
            .toTypedArray()
}

/**
 * Read the string lists table.
 */
private fun ByteBuffer.readStringListsTable(
    start: Int,
    strings : Array<String?>) : Array<List<String>> {
    seekSection(start, StringLists)
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
fun streamCompileCommandsV1(file: File,
    action : (
        sourceFile:File,
        compiler:File,
        flags:List<String>,
        workingDirectory:File) -> Unit) {
    streamCompileCommands(file) {
        action(sourceFile, compiler, flags, workingDirectory)
    }
}

data class CompileCommand(
        val sourceFile:File,
        val compiler:File,
        val flags:List<String>,
        val workingDirectory:File,
        val outputFile:File,
        val target:String,
        val sourceFileIndex:Int,
        val sourceFileCount:Int)


// These are fallback placeholder values to use when a V1 file is read.
// They shouldn't be able to leak into V2. Give them easily searchable
// string values in case a bug allows them to leak.
private val VERSION_FALLBACK_OUTPUT_FILE = File("compile-commands-fallback-output-file")
private const val VERSION_FALLBACK_TARGET = "compile-commands-fallback-targets-list"

/**
 * Methods for streaming over compile commands messages and converting ordinals to Strings and
 * String lists.
 */
private class CompileCommandsInputStream(private val file: File) : AutoCloseable {
    private val fileChannel = FileChannel.open(file.toPath())
    private val size = file.length().toInt()
    private val map = ByteBuffer.allocate(size)
    private val internedFiles: MutableMap<Int, File> = mutableMapOf()
    private val start: Int
    val version: Int
    private val positionAfterLastMessage: Int
    private val strings: Array<String?>
    private val stringLists: Array<List<String>>
    val sourceMessagesCount : Int

    init {
        fileChannel.read(map)
        val startAndVersion: Pair<Int, Int> = map.positionAfterMagicAndVersion()
        start = startAndVersion.first
        version = startAndVersion.second
        if (start == 0 || version == 0) {
            error("$file is not a valid C/C++ Build Metadata file")
        }
        map.seekSection(start, StringTable)
        positionAfterLastMessage = map.position()

        strings = map.readStringTable(start)
        stringLists = map.readStringListsTable(start, strings)
        map.seekSection(start, CompileCommands)
        sourceMessagesCount = int()
    }

    /**
     * Read a Byte from the stream.
     */
    fun byte(): Byte = map.get()

    /**
     * Read an Int from the stream.
     */
    fun int(): Int = map.int

    /**
     * Read a File from the stream.
     */
    fun file(): File {
        val index = map.int
        if (index == 0) error("Null file name seen in '$file'")
        return internedFiles.computeIfAbsent(index) {
            File(strings[index]!!)
        }
    }

    /**
     * Read an Int from the stream and look it up in the string table.
     */
    fun string(): String = strings[map.int]!!

    /**
     * Read an Int from the stream and look it up in the string table. Return null if the int is
     * out-of-range of the string table.
     */
    fun stringOrNull() : String? {
        val index = map.int
        if (index == 0) return null
        if (index > strings.size) return null
        return strings[index]
    }

    /**
     * Read an Int from the stream and look it up in the string list table.
     */
    fun stringList(): List<String> = stringLists[map.int]

    /**
     * Read an Int from the stream and look it up in the string list table. Return null if the int
     * is out-of-range of the string list table.
     */
    fun stringListOrNull() : List<String>? {
        val index = map.int
        if (index > stringLists.size) return null
        return stringLists[index]
    }

    /**
     * Return true if the current position is at the position right after the list of messages.
     */
    fun isEndOfMessages() : Boolean {
        val current = map.position()
        return current == positionAfterLastMessage
    }

    override fun close() {
        fileChannel.close()
    }
}

/**
 * Open and use a [CompileCommandsInputStream] to execute action.
 * Inline and return T so that 'return' can be called inside the [action] block.
 */
private inline fun <T> indexCompileCommands(file : File, action: CompileCommandsInputStream.() -> T): T {
    return CompileCommandsInputStream(file).use { action(it) }
}

/**
 * Check for the presence of b/201754404.
 * This is a variant that version 2 the mistakenly did not have the version number bumped up.
 *
 * Differences between V2 and V2' (the one with the bug):
 *   In the header, V2 has no source file count but V2' does
 *   In the message COMPILE_COMMAND_CONTEXT_MESSAGE, V2 had 'outputFile'. In V2' it was replaced
 *     with 'target' name.
 *   In the message COMPILE_COMMAND_FILE_MESSAGE, V2' added 'outputFile'.
 *
 *   V3 and V2' are identical aside from version number written into the file.
 *
 *   This function checks for the presence of V2' by trying to step through the file's
 *   messages as if the file was V2. Once an invalid message, string, or string list is
 *   encountered the function returns 'true' indicating the bug is present.
 *
 *   Note that it isn't possible to construct a file that can be parsed as both V2 and V2'
 *   because there is an exact number of messages to be read. The COMPILE_COMMAND_FILE_MESSAGE has
 *   changed size from 4 to 8 (because output file was added).
 */
fun hasBug201754404(file: File) : Boolean {
    indexCompileCommands(file) {
        if (version != 2) return false
        repeat(sourceMessagesCount) {
            when(byte()) {
                COMPILE_COMMAND_CONTEXT_MESSAGE -> {
                    stringOrNull() ?: return true // Compiler
                    stringListOrNull() ?: return true  // Flags
                    stringOrNull() ?: return true // WorkingDirectory
                    stringOrNull() ?: return true// Output File (if V2) or Target (if V2')
                }
                COMPILE_COMMAND_FILE_MESSAGE -> {
                    stringOrNull() ?: return true // Source File
                    // Not reading OutputFile here because it is needed by V2' but not V2.
                    // This function is reading the file as if it was V2 and will return true
                    // if the file can't be read as V2.
                }
                else -> return true
            }
        }
        return !isEndOfMessages() // Make sure we reached the end of messages.
    }
}

/**
 * Implementation class that streams all information for the current version.
 * When earlier-than-current files are read in, fallback nonsense values are used.
 */
fun streamCompileCommands(file: File, action : CompileCommand.() -> Unit) {
    val hasBug201754404 = hasBug201754404(file)

    indexCompileCommands(file) {
        val sourceFilesCount = if (version > 2 || (version == 2 && hasBug201754404)) int() else -1
        lateinit var lastCompiler: File
        lateinit var lastFlags : List<String>
        lateinit var lastWorkingDirectory : File
        var lastTarget = VERSION_FALLBACK_TARGET
        var lastOutputFile = VERSION_FALLBACK_OUTPUT_FILE
        var sourceFileIndex = 0
        repeat(sourceMessagesCount) {
            when(byte()) {
                COMPILE_COMMAND_CONTEXT_MESSAGE -> {
                    lastCompiler = file()
                    lastFlags = stringList()
                    lastWorkingDirectory = file()
                    if (version > 2 || hasBug201754404) {
                        lastTarget = string()
                    } else if (version == 2) {
                        lastOutputFile = file()
                    }
                }
                COMPILE_COMMAND_FILE_MESSAGE -> {
                    val sourceFile = file()
                    if (version > 2 || hasBug201754404) {
                        lastOutputFile = file()
                    }
                    action(CompileCommand(
                        sourceFile = sourceFile,
                        compiler = lastCompiler,
                        flags = lastFlags,
                        workingDirectory = lastWorkingDirectory,
                        outputFile = lastOutputFile,
                        target = lastTarget,
                        sourceFileIndex = sourceFileIndex,
                        sourceFileCount = sourceFilesCount
                    ))
                    ++sourceFileIndex
                }
                else -> error("Unexpected")
            }
        }
    }
}

/**
 * Return true iff the given compile_commands.json.bin is the latest
 * version supported by the code in this class.
 */
fun compileCommandsFileIsCurrentVersion(file: File) : Boolean {
    return readCompileCommandsVersionNumber(file) == COMPILE_COMMANDS_CODEC_VERSION
}

/**
 * Read only the version number from a compile_commands.json.bin file.
 */
fun readCompileCommandsVersionNumber(file: File) : Int {
    FileChannel.open(file.toPath()).use { fc ->
        val map = ByteBuffer.allocate(MAGIC.length + Int.SIZE_BYTES)
        fc.read(map)
        val (_, version) = map.positionAfterMagicAndVersion()
        return version
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
            in STRIP_FLAGS_WITH_ARG_INCLUDING_OUTPUT_FILE -> i++
            else -> if (STRIP_FLAGS_WITH_IMMEDIATE_ARG_INCLUDING_OUTPUT_FILE.none { arg.startsWith(it) } && arg != sourceFile) {
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
        "-MF",
        "-MT",
        "-MQ"
    )

val STRIP_FLAGS_WITH_ARG_INCLUDING_OUTPUT_FILE =
    listOf(
        "-o",
        "--output"
    ) + STRIP_FLAGS_WITH_ARG

/** These are flags that have arguments immediate following them. */
val STRIP_FLAGS_WITH_IMMEDIATE_ARG = listOf(
    "-MF",
    "-MT",
    "-MQ"
)

val STRIP_FLAGS_WITH_IMMEDIATE_ARG_INCLUDING_OUTPUT_FILE = listOf(
    "--output="
) + STRIP_FLAGS_WITH_IMMEDIATE_ARG

/** These are flags that should be stripped and don't have a following argument. */
val STRIP_FLAGS_WITHOUT_ARG: List<String> =
    listOf( // Skip -M* flags because these govern the creation of .d files in gcc. We don't want
        // spurious files dropped by Cidr. See see b.android.com/215555 and
        // b.android.com/213429
        "-M", "-MM", "-MD", "-MG", "-MP", "-MMD",
        // -c tells the compiler to skip linking, which is always true for Android build.
        "-c"
    )

/**
 * Extract a particular flag argument. Return null if that flag doesn't exist.
 */
fun extractFlagArgument(short : String, long : String, flags : List<String>) : String? {
    var returnNext = false
    val longEquals = "$long="
    for(flag in flags) {
        if (returnNext) return flag
        when {
            flag.startsWith(longEquals) -> return flag.substringAfter(longEquals)
            flag == short || flag == long -> returnNext = true
        }
    }
    return null
}
