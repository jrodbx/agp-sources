/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.ide.common.pagealign

import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import com.android.ide.common.pagealign.AlignmentProblems.ElfLoadSectionsNot16kAligned
import com.android.ide.common.pagealign.AlignmentProblems.ElfNot16kAlignedInZip

// Define some ELF constants we'll need for parsing ELF file.
private const val ELF_64BIT = 2.toByte()
private const val ELF_LITTLE_ENDIAN = 1.toByte()
private const val ELF_PT_LOAD = 1.toShort()
const val PAGE_ALIGNMENT_16K = 16L * 1024

/**
 * Check whether [InputStream] has the ELF magic number.
 */
fun hasElfMagicNumber(input: InputStream): Boolean {
    val ident = ByteArray(4)
    val read = input.read(ident)
    if (read != ident.size) return false
    return ident[0] == 0x7f.toByte() && ident[1] == 'E'.code.toByte() &&
        ident[2] == 'L'.code.toByte() && ident[3] == 'F'.code.toByte()
}

/**
 * Return the minimum value of alignment of all LOAD sections (Program Headers).
 *
 * This function expects [input] to be at the first byte after the ELF magic number.
 * This will be the case when [hasElfMagicNumber] has been called right before.
 *
 * Will return -1L if this turns out not to be an ELF file or if there are no
 * LOAD sections.
 */
fun readElfMinimumLoadSectionAlignment(input : InputStream) : Long {
    val ident = ByteArray(2)
    val read = input.read(ident)
    // Must be ELF format
    if (read != ident.size) return -1L
    // Must be 64 bit
    if (ident[0] != ELF_64BIT) return -1L
    // Must be little endian
    if (ident[1] != ELF_LITTLE_ENDIAN) return -1L

    // Read what we need from the ELF header.
    // https://docs.oracle.com/cd/E23824_01/html/819-0690/chapter6-43405.html
    input.skip(26) // Skip rest of ident plus type, machine, version, entry
    val phoff = input.readLongLittleEndian() ?: return -1L
    input.skip(12) // Skip shoff, flags
    val ehsize = input.readShortLittleEndian() ?: return -1L
    input.skip(2) // Skip phentsize
    val phnum = input.readShortLittleEndian() ?: return -1L
    input.skip(6) // shentsize, shnum, shstrndx

    // Jump to program header offset
    if (phoff >= ehsize) {
        input.skip(phoff - ehsize)
    } else {
        // This won't happen in a well-formed ELF file, but handle it anyway. We assume the caller
        // will treat -1 as "not an ELF file" which is better than the IllegalArgumentException
        // we'd throw otherwise.
        return -1L
    }

    // Find the minimum page alignment from the set of Program Headers.
    // https://docs.oracle.com/cd/E23824_01/html/819-0690/chapter6-83432.html
    var minAlign: Long? = null
    repeat(phnum.toInt()) {
        val type = input.readShortLittleEndian() ?: return -1L
        input.skip(46) // Skip rest of Program Header
        val align = input.readLongLittleEndian() ?: return -1L
        if (type == ELF_PT_LOAD) {
            minAlign = minAlign?.coerceAtMost(align) ?: align
        }
    }

    return minAlign ?: -1L
}

private fun InputStream.readShortLittleEndian(): Short? {
    val bytes = ByteArray(2)
    val bytesRead = read(bytes)
    if (bytesRead != 2) return null
    return ((bytes[0].toInt() and 0xFF) or
            ((bytes[1].toInt() and 0xFF) shl 8)).toShort()
}

private fun InputStream.readLongLittleEndian(): Long? {
    val bytes = ByteArray(8)
    val bytesRead = read(bytes)
    if (bytesRead != 8) return null
    return (bytes[0].toLong() and 0xFF) or
            ((bytes[1].toLong() and 0xFF) shl 8) or
            ((bytes[2].toLong() and 0xFF) shl 16) or
            ((bytes[3].toLong() and 0xFF) shl 24) or
            ((bytes[4].toLong() and 0xFF) shl 32) or
            ((bytes[5].toLong() and 0xFF) shl 40) or
            ((bytes[6].toLong() and 0xFF) shl 48) or
            ((bytes[7].toLong() and 0xFF) shl 56)
}

enum class AlignmentProblems {
    ElfNot16kAlignedInZip,
    ElfLoadSectionsNot16kAligned;
}

/**
 * [input] is an [InputStream] that points to an APK zip input stream.
 * This function detects issues that would cause this file to fail Play Store 16k alignment checks.
 *
 * Those problems:
 * - ELF_COMPRESSED -- the Elf file must be stored uncompressed in the APK. If it isn't, then this value is returned.
 * - ELF_NOT_16KB_ALIGNED_IN_ZIP -- the Elf file must be at a 16k boundary within the APK.
 * - ELF_LOAD_SECTIONS_NOT_16KB_ALIGNED -- each of the LOAD sections of the Elf file must be aligned on a 16k boundary.
 *
 * This function doesn't assume [input] is a well-formed Elf file. If it isn't, then no problem will be reported.
 */
fun findElfFile16kAlignmentProblems(input: ZipArchiveInputStream) : Map<String, Set<AlignmentProblems>> {
    val problems = mutableMapOf<String, MutableSet<AlignmentProblems>>()
    var entry = input.getNextZipEntry()
    fun addProblem(name : String, problem: AlignmentProblems) {
        problems.computeIfAbsent(name) { mutableSetOf() } .add(problem)
    }
    while (entry != null) {
        try {
            val currentEntryAlignedAt16kbBoundaryInZip = is16kAligned(input.bytesRead)

            if (hasElfMagicNumber(input)) {
                val minimumLoadSectionAlignment = readElfMinimumLoadSectionAlignment(input)
                if (minimumLoadSectionAlignment == -1L) continue // Not a well-formed Elf or not 64-bit
                if (!is16kAligned(minimumLoadSectionAlignment)) {
                    addProblem(entry.name, ElfLoadSectionsNot16kAligned)
                }
                if (entry.method == ZipEntry.STORED) {
                    if (!currentEntryAlignedAt16kbBoundaryInZip) {
                        addProblem(entry.name, ElfNot16kAlignedInZip)
                    }
                }
            }
        } finally {
            entry = input.getNextZipEntry()
        }
    }
    return problems
}

/**
 * Same as [findElfFile16kAlignmentProblems] except that it accepts a [File] rather than a [ZipArchiveInputStream].
 */
fun findElfFile16kAlignmentProblems(file: File) : Map<String, Set<AlignmentProblems>> {
    FileInputStream(file).use { input ->
        ZipArchiveInputStream(input).use { zipInput ->
            return findElfFile16kAlignmentProblems(zipInput)
        }
    }
}

private fun is16kAligned(value : Long) = (value % PAGE_ALIGNMENT_16K) == 0L
