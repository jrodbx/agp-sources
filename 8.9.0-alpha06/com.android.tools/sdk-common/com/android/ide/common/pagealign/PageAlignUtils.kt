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

import java.io.InputStream

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
