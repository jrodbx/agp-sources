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

package com.android.build.gradle.internal.cxx.ninja

import com.android.build.gradle.internal.cxx.ninja.Record.Dependencies
import com.android.build.gradle.internal.cxx.ninja.Record.EOF
import com.android.build.gradle.internal.cxx.ninja.Record.Path
import com.android.build.gradle.internal.cxx.ninja.Record.Version
import com.android.build.gradle.internal.cxx.string.StringTable
import com.google.common.annotations.VisibleForTesting
import com.google.common.primitives.Longs
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.lang.RuntimeException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.CharBuffer
import java.nio.IntBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets

/**
 * Ninja dependencies file format coder/decoder.
 * This is a compact representation of C/C++ file dependencies.
 *
 * Everything is aligned to four bytes, everything is little endian.
 *
 * Header 16 bytes:
 *   MAGIC (12 bytes) "# ninjadeps\n"
 *   Version (u32): The number 3 or 4
 * Sequence of Record:
 *   Record Type and Size (u32):
 *     High Bit: The number 0 for 'Path Record' or 1 for 'Dependencies Record'.
 *     Remaining: Size of the record that follows.
 *   Path Record:
 *     File Path (UTF-8 bytes, not zero terminated, back slashes on Windows, forward elsewhere).
 *     0 to 3 NUL byt to align on four byte boundary.
 *     Checksum (u32): One's complement of file index.
 *   Dependencies Record:
 *     Target Path (u32): Path to some .o file (or other output) relative to .ninja_deps file.
 *     One of Timestamp:
 *       v1 (u32): seconds since epoch (2000 for windows, 1970 for others).
 *       v2 (u64): nanoseconds since epoch (2000 for windows, 1970 for others).
 *       (See: https://github.com/ninja-build/ninja/blob/master/src/disk_interface.cc)
 *     Sequence of Dependency:
 *       Dependency (u32): Path to .cpp and .h. When relative, relative to .ninja_deps file.
 *
 * Example,
 * CMakeFiles/main.dir/WiiUtils.cpp.o|1617220563|WiiUtils.cpp,<toolchain>sysroot/usr/include/c++/v1/string,etc.
 * CMakeFiles/main.dir/IniFile.cpp.o|1617220563|IniFile.cpp,<toolchain>sysroot/usr/include/jni.h,etc.
 *
 */
private val MAGIC = "# ninjadeps\n".toByteArray()

/**
 * Create a .ninja_deps file or open an existing one.
 * - ninjaDepsFile -- the name of the file to open or create
 */
class NinjaDepsEncoder private constructor(
    private val stringTable : StringTable,
    private val schemaVersion : Int,
    private val output: DataOutputStream
) : AutoCloseable {

    private val sizeOfTimestamp = if (schemaVersion == 3) Int.SIZE_BYTES else Long.SIZE_BYTES

    /**
     * Write a target, timestamp, and dependencies.
     */
    fun writeTarget(target: String, targetTimestamp: Long?, dependencies: List<String>) {
        val targetId = writePath(target)
        val dependencyIds = dependencies.map { writePath(it) }
        val size = Int.SIZE_BYTES + // target path
                sizeOfTimestamp + // target timestamp
                dependencies.size * Int.SIZE_BYTES // Dependencies
        writeLittleEndianUIntWithHighBitSet(size.toLong())
        writeLittleEndianUInt(targetId.toLong())
        writeTimestamp(targetTimestamp)
        dependencyIds.forEach {
            writeLittleEndianUInt(it.toLong())
        }
    }

    /**
     * Write 'path' to the string table if it hasn't been seen before.
     */
    private fun writePath(path: String): Int {
        return stringTable.getIdCreateIfAbsent(path) { id ->
            val bytes = path.toByteArray(Charsets.UTF_8)
            val padding = when (val mod = bytes.size % 4) {
                0 -> 0
                else -> 4 - mod
            }
            writeLittleEndianUInt(bytes.size + padding + 4L)
            for (element in bytes) {
                output.writeByte(element.toInt())
            }
            // Pad out to four-byte alignment.
            repeat(padding) {
                output.writeByte(0)
            }
            // Checksum is one's complement of the ID
            writeLittleEndianUIntComplement(id.toLong())
        }
    }

    /**
     * Write a timestamp.
     */
    private fun writeTimestamp(targetTimestamp: Long?) {
        if (schemaVersion == 3) {
            when (targetTimestamp) {
                null -> writeLittleEndianUInt(0)
                0L -> writeLittleEndianUInt(1)
                else -> writeLittleEndianUInt(targetTimestamp)
            }
        } else {
            when (targetTimestamp) {
                null -> writeLittleEndianLong(0)
                0L -> writeLittleEndianLong(1)
                else -> writeLittleEndianLong(targetTimestamp)
            }
        }
    }

    /**
     * Writes a 32-bit int to the [DataOutputStream] in little-endian format.
     */
    private fun writeLittleEndianUInt(l: Long) {
        val bytes = Longs.toByteArray(java.lang.Long.reverseBytes(l))
        for (i in 0 until (0 + Int.SIZE_BYTES)) {
            output.writeByte(bytes[i].toInt())
        }
    }

    /**
     * Writes a 32-bit int to the [DataOutputStream] in little-endian format with the
     * high bit set.
     */
    private fun writeLittleEndianUIntWithHighBitSet(l: Long) {
        writeLittleEndianUInt(l or (1L shl 31))
    }

    /**
     * Writes one's complement of a 32-bit int to the [DataOutputStream] in little-endian format.
     */
    private fun writeLittleEndianUIntComplement(l: Long) {
        writeLittleEndianUInt(l.inv())
    }

    /**
     * Helper function writes an a 64-bit int to the [DataOutputStream] in little-endian
     * format.
     */
    private fun writeLittleEndianLong(v: Long) {
        val bytes = Longs.toByteArray(java.lang.Long.reverseBytes(v))
        for (i in 0 until (0 + bytes.size)) {
            output.writeByte(bytes[i].toInt())
        }
    }

    override fun close() {
        output.close()
    }

    companion object {
        /**
         * Open an existing .ninja_deps file.
         */
        fun open(ninjaDepsFile : File) : NinjaDepsEncoder {
            val deps = NinjaDepsInfo.readFile(ninjaDepsFile)
            return NinjaDepsEncoder(
                deps.pathTable,
                deps.schemaVersion,
                DataOutputStream(
                    BufferedOutputStream(
                        FileOutputStream(ninjaDepsFile, true)))
            )
        }
    }
}

sealed class Record {
    class Version(val version : Int) : Record()
    class Path(val path : CharBuffer, val checkSum : Int) : Record()
    class Dependencies(val targetPath : Int, val timestamp: Long?, val dependencies : IntBuffer) : Record()
    object EOF : Record()
}

/**
 * Low-level decoder for .ninja_deps file format
 */
private class NinjaDepsDecoder(private val buffer : ByteBuffer) {
    private var schemaVersion = 0
    private var sizeOfTimestamp = 0

    init {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
    }

    private fun readHeader() : Version {
        MAGIC.forEach { expected ->
            val actual = buffer.get()
            if (actual != expected) {
                error("Was not a .ninja_deps file")
            }
        }
        schemaVersion = buffer.int
        assert(schemaVersion == 3 || schemaVersion == 4)
        sizeOfTimestamp = if (schemaVersion == 3) 4 else 8
        return Version(schemaVersion)
    }

    /**
     * Read and report a single record: either a path or dependencies of a target.
     */
    fun read() : Record {
        if (buffer.position() == 0) return readHeader()
        if (buffer.position() == buffer.capacity()) return EOF
        val typeAndSize = buffer.int
        val start = buffer.position()

        if (typeAndSize > 0) {
            // Path record
            val view = buffer.slice()
            var len = 0
            while (buffer.get().toInt() != 0 && len < (typeAndSize - 4)) ++len
            view.limit(len)
            val charBuffer: CharBuffer = StandardCharsets.UTF_8.decode(view)
            buffer.position(start + typeAndSize - 4)
            val checksum = buffer.int
            return Path(charBuffer, checksum)
        } else {
            // Dependencies record
            val size = typeAndSize - Int.MIN_VALUE
            val targetPath = buffer.int
            val timestamp: Long? =
                when (val provisional =
                    if (schemaVersion == 3) buffer.int.toLong() else buffer.long) {
                    0L -> null // 'does not exist'
                    1L -> 0L
                    else -> provisional
                }
            val pathsSize = size - 4 - sizeOfTimestamp
            val view = buffer.slice()
            view.limit(pathsSize)
            view.order(ByteOrder.LITTLE_ENDIAN)
            buffer.position(start + size)
            return Dependencies(targetPath, timestamp, view.asIntBuffer())
        }
    }
}

/**
 * Data representing a fully read .ninja_deps file.
 */
data class NinjaDepsInfo(
    internal val schemaVersion : Int,
    internal val pathTable : StringTable,
    private val dependencies : Map<Int, IntArray>
) {
    /**
     * Dependencies of a particular target file. Later dependencies for the same target file have
     * replaced earlier dependencies.
     * Returns null if [target] is not known.
     */
    fun getDependencies(target : String) : List<String>? {
        if (pathTable.containsString(target)) {
            return dependencies.getValue(pathTable.getId(target)).map { id ->
                pathTable.decode(id)
            }
        }
        return null
    }

    companion object {
        /**
         * Read a .ninja_deps file into memory. Only retain the latest dependencies if they are
         * restated in the file.
         */
        fun readFile(ninjaDepsFile : File) : NinjaDepsInfo {
            val pathIndexToDependencies = mutableMapOf<Int, IntArray>()
            val stringTable = StringTable()
            var schemaVersion = 0
            try {
                streamNinjaDepsFile(ninjaDepsFile) { record ->
                    when (record) {
                        is Version -> schemaVersion = record.version
                        is Dependencies -> {
                            val dependencies = IntArray(record.dependencies.remaining())
                            record.dependencies.get(dependencies)
                            pathIndexToDependencies[record.targetPath] = dependencies
                        }
                        is Path -> {
                            stringTable.getIdCreateIfAbsent(record.path.toString())
                        }
                        else -> {
                        }
                    }
                }
            } catch(e : Throwable) {
                throw RuntimeException("Error reading ninja dependencies file '$ninjaDepsFile'", e)
            }
            return NinjaDepsInfo(
                schemaVersion,
                stringTable,
                pathIndexToDependencies
            )
        }
    }
}

/**
 * Create an empty .ninja_deps file at [ninjaDepsFile] with schema [schemaVersion].
 */
fun createEmptyNinjaDepsFile(
    ninjaDepsFile : File,
    schemaVersion : Int) {
    ninjaDepsFile.parentFile.mkdirs()
    DataOutputStream(BufferedOutputStream(FileOutputStream(ninjaDepsFile))).use { output ->
        MAGIC.forEach { byte ->
            output.writeByte(byte.toInt())
        }
        output.writeByte(schemaVersion)
        output.writeByte(0)
        output.writeByte(0)
        output.writeByte(0)
    }
}

/**
 * Stream all ninja dependencies inside the file then close the file at the end.
 * Note that the way the ninja dependencies format works is that dependencies
 * for the same file may be repeated multiple times. In this case, the last
 * list of dependencies is the true value.
 */
@VisibleForTesting
fun streamNinjaDepsFile(ninjaDepsFile : File, consumer : (Record) -> Unit) {
    RandomAccessFile(ninjaDepsFile, "r").use { raf ->
        raf.channel.use { channel ->
            val buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.channel.size())
            val reader = NinjaDepsDecoder(buffer)

            var record = reader.read()
            while (record != EOF) {
                consumer(record)
                record = reader.read()
            }
            channel.close()
        }
    }
}


