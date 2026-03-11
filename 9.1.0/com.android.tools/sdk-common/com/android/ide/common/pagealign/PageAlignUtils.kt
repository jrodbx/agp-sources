/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.ide.common.pagealign

import com.android.ide.common.pagealign.AlignmentProblem.LoadSectionNotAligned
import com.android.ide.common.pagealign.AlignmentProblem.RelroEndNotAligned
import com.android.ide.common.pagealign.AlignmentProblem.RelroStartNotAligned
import com.android.ide.common.pagealign.AlignmentProblem.ZipEntryNotAligned
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream

// ELF Constants
private const val ELF_64BIT = 2.toByte()
private const val ELF_LITTLE_ENDIAN = 1.toByte()
const val ELF_PT_LOAD = 1
const val ELF_PT_GNU_RELRO = 0x6474e552

const val PAGE_ALIGNMENT_16K = 16L * 1024

/** Represents an ELF Program Header. */
data class ProgramHeader(val type: Int, val vaddr: Long, val memsz: Long, val align: Long) {
  /** The computed end address. */
  val endVaddr: Long
    get() = vaddr + memsz

  /** String representation of the program header type. */
  val programHeaderType: String
    get() =
      when (type) {
        ELF_PT_LOAD -> "PT_LOAD"
        ELF_PT_GNU_RELRO -> "PT_GNU_RELRO"
        else -> "UNKNOWN[$type]"
      }

  override fun toString() = "$programHeaderType start: ${vaddr.toHex()} end: ${endVaddr.toHex()} align: ${align.toHex()}"
}

/** Represents a simplified ELF Section Header. Used to verify strict containment within Segments. */
data class SectionHeader(val addr: Long, val size: Long) : Comparable<SectionHeader> {
  override fun compareTo(other: SectionHeader): Int {
    // Sort by the address for consistent list comparison
    return this.addr.compareTo(other.addr)
  }
}

/** Internal context containing parsed ELF structures. */
private data class ElfContext(val programHeaders: List<ProgramHeader>, val sectionHeaders: List<SectionHeader>)

/** Check whether [InputStream] has the ELF magic number. Consumes the first 4 bytes. */
fun hasElfMagicNumber(input: InputStream): Boolean {
  try {
    val ident = ByteArray(4)
    val read = input.read(ident)
    if (read != ident.size) return false
    return ident[0] == 0x7f.toByte() && ident[1] == 'E'.code.toByte() && ident[2] == 'L'.code.toByte() && ident[3] == 'F'.code.toByte()
  } catch (_: UnsupportedZipFeatureException) {
    // This can happen when a zip entry is encrypted.
    return false
  }
}

/** Verifies a raw ELF input stream for 16KB alignment compatibility. */
fun readElfAlignmentProblems(input: InputStream): List<AlignmentProblem>? {
  val context = parseElfStructure(input) ?: return null
  return verifyElfSectionAlignment(context)
}

/** Examines program and section headers to verify 16KB alignment compatibility. */
private fun verifyElfSectionAlignment(context: ElfContext): List<AlignmentProblem> {
  val problems = mutableListOf<AlignmentProblem>()
  val headers = context.programHeaders
  val sections = context.sectionHeaders

  // Check Load Section Alignment
  val loadHeaders = headers.filter { it.type == ELF_PT_LOAD }
  val minLoadAlign = loadHeaders.minByOrNull { it.align }

  if (minLoadAlign != null && !is16kAligned(minLoadAlign.align)) {
    problems.add(LoadSectionNotAligned(minLoadAlign))
  }

  // Check Relro Section Alignment
  val relroHeaders = headers.filter { it.type == ELF_PT_GNU_RELRO }

  // Sort LOAD headers by virtual address to easily find the "next" segment for gap checks
  val sortedLoadHeaders = loadHeaders.sortedBy { it.vaddr }

  for (relro in relroHeaders) {
    // Find the containing LOAD section (the last one starting before or at the RELRO start)
    val loadIndex = sortedLoadHeaders.indexOfLast { load -> load.vaddr <= relro.vaddr }

    if (loadIndex == -1) {
      // Cannot find containing LOAD section
      problems.add(RelroStartNotAligned(relro))
      continue
    }

    val containingLoad = sortedLoadHeaders[loadIndex]

    // Ensure RELRO does not bleed into the *Next* LOAD segment.
    val nextLoad = sortedLoadHeaders.getOrNull(loadIndex + 1)
    val limitVaddr = nextLoad?.vaddr ?: Long.MAX_VALUE

    if (relro.endVaddr > limitVaddr) {
      // RELRO extends into the memory space of the next segment.
      problems.add(RelroEndNotAligned(relro))
      continue
    }

    // Match aac: Check if RELRO contains exactly the same sections as the LOAD segment.
    // If the section lists are identical, RELRO effectively equals LOAD and alignment
    // is guaranteed by the LOAD segment's alignment (which is checked separately).
    if (relroIsEntireLoadSection(relro, containingLoad, sections)) {
      continue
    }

    // Prefix Rule: RELRO must start on a page boundary OR at the exact start of the LOAD segment.
    val startValid = is16kAligned(relro.vaddr) || (relro.vaddr == containingLoad.vaddr)
    if (!startValid) {
      problems.add(RelroStartNotAligned(relro))
    }

    // Suffix Rule: RELRO must end on a page boundary OR at the exact end of the LOAD segment.
    val endValid = is16kAligned(relro.endVaddr) || (relro.endVaddr == containingLoad.endVaddr)
    if (!endValid) {
      problems.add(RelroEndNotAligned(relro))
    }
  }

  return problems
}

/**
 * Replicates aac's RelroIsEntireLoadSection logic.
 *
 * It calculates the list of Sections contained within the RELRO segment and the list of Sections contained within the LOAD segment. If the
 * lists are identical, RELRO effectively covers the entire LOAD segment logic.
 */
private fun relroIsEntireLoadSection(relro: ProgramHeader, load: ProgramHeader, sections: List<SectionHeader>): Boolean {
  // Helper to find sections strictly inside a segment
  fun getContainedSections(segment: ProgramHeader): List<SectionHeader> {
    return sections
      .filter { sh -> sh.addr >= segment.vaddr && (sh.addr + sh.size) <= segment.endVaddr }
      .sorted() // Ensure consistent ordering for equality check
  }

  val relroSections = getContainedSections(relro)
  val loadSections = getContainedSections(load)

  // Strict equality check of the lists
  return relroSections == loadSections
}

/** Parses Program Headers and Section Headers. */
/** Parses Program Headers and Section Headers. */
private fun parseElfStructure(input: InputStream): ElfContext? {
  val ident = ByteArray(2)
  val read = input.read(ident)
  if (read != 2 || ident[0] != ELF_64BIT || ident[1] != ELF_LITTLE_ENDIAN) return null

  input.skipFully(26) ?: return null // Skip to PH Off

  val phoff = input.readLongLittleEndian() ?: return null
  val shoff = input.readLongLittleEndian() ?: return null

  input.skipFully(4) ?: return null // Flags

  /* ehsize */ input.skipFully(2) ?: return null
  val phentsize = input.readShortLittleEndian() ?: return null
  val phnum = input.readShortLittleEndian() ?: return null
  val shentsize = input.readShortLittleEndian() ?: return null
  val shnum = input.readShortLittleEndian() ?: return null
  /* shstrndx */ input.skipFully(2) ?: return null

  var currentOffset = 64L

  val phHeaders = mutableListOf<ProgramHeader>()
  val shHeaders = mutableListOf<SectionHeader>()

  // Define tasks to run at specific offsets
  data class ReadParse(val offset: Long, val run: () -> Unit)
  val readers = mutableListOf<ReadParse>()

  // Task: Parse Program Headers
  if (phoff >= currentOffset && phnum > 0) {
    readers.add(
      ReadParse(phoff) {
        repeat(phnum.toInt()) {
          val type = input.readIntLittleEndian() ?: return@ReadParse
          input.skipFully(4) ?: return@ReadParse // flags
          input.skipFully(8) ?: return@ReadParse // offset
          val vaddr = input.readLongLittleEndian() ?: return@ReadParse
          input.skipFully(8) ?: return@ReadParse // paddr
          input.skipFully(8) ?: return@ReadParse // filesz
          val memsz = input.readLongLittleEndian() ?: return@ReadParse
          val align = input.readLongLittleEndian() ?: return@ReadParse

          phHeaders.add(ProgramHeader(type, vaddr, memsz, align))

          val readBytes = 56
          if (phentsize > readBytes) {
            input.skipFully((phentsize - readBytes).toLong())
          }
        }
        currentOffset += (phnum * phentsize)
      }
    )
  }

  // Task: Parse Section Headers
  if (shoff >= currentOffset && shnum > 0) {
    readers.add(
      ReadParse(shoff) {
        repeat(shnum.toInt()) {
          // Elf64_Shdr (64 bytes)
          // 0x00 name(4), 0x04 type(4), 0x08 flags(8) -> skip 16
          input.skipFully(16) ?: return@ReadParse
          val shAddr = input.readLongLittleEndian() ?: return@ReadParse
          input.skipFully(8) ?: return@ReadParse // offset
          val shSize = input.readLongLittleEndian() ?: return@ReadParse

          // 0x28 link(4), 0x2C info(4), 0x30 align(8), 0x38 entsize(8) -> skip 24
          input.skipFully(24) ?: return@ReadParse

          // shentsize is at least 64. Skip padding if larger.
          val readBytes = 64
          if (shentsize > readBytes) {
            input.skipFully((shentsize - readBytes).toLong()) ?: return@ReadParse
          }

          // Do not filter by size. aac considers zero-sized sections
          // (like .bss markers) when checking for containment.
          shHeaders.add(SectionHeader(shAddr, shSize))
        }
        currentOffset += (shnum * shentsize)
      }
    )
  }

  readers.sortBy { it.offset }

  for (reader in readers) {
    if (reader.offset < currentOffset) continue
    input.skipFully(reader.offset - currentOffset) ?: continue
    currentOffset = reader.offset
    reader.run()
  }

  return ElfContext(phHeaders, shHeaders)
}

/**
 * Skip n bytes, even if the underlying stream provides only partial reads. The caller is required to check the result because that's the
 * way to know the end of the stream has arrived.
 */
private fun InputStream.skipFully(n: Long): Long? {
  var remaining = n
  while (remaining > 0) {
    val skipped = skip(remaining)
    if (skipped <= 0) {
      // Fallback: try reading if skip isn't supported or returns 0 unexpectedly
      if (this.read() == -1) {
        return null
      }
      remaining--
    } else {
      remaining -= skipped
    }
  }
  return n
}

/** Read the stream into the given byte array even if the underlying stream provides only partial reads. */
private fun InputStream.readFully(bytes: ByteArray): Boolean {
  var offset = 0
  while (offset < bytes.size) {
    val read = read(bytes, offset, bytes.size - offset)
    if (read == -1) return false // EOF before filling buffer
    offset += read
  }
  return true
}

/** Read little-endian short (16 bits). */
private fun InputStream.readShortLittleEndian(): Short? {
  val bytes = ByteArray(2)
  if (!readFully(bytes)) return null
  return ((bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)).toShort()
}

/** Read little-endian int (32 bits). */
private fun InputStream.readIntLittleEndian(): Int? {
  val bytes = ByteArray(4)
  if (!readFully(bytes)) return null
  return (bytes[0].toInt() and 0xFF) or
    ((bytes[1].toInt() and 0xFF) shl 8) or
    ((bytes[2].toInt() and 0xFF) shl 16) or
    ((bytes[3].toInt() and 0xFF) shl 24)
}

/** Read little-endian long (64 bits). */
private fun InputStream.readLongLittleEndian(): Long? {
  val bytes = ByteArray(8)
  if (!readFully(bytes)) return null
  var res = 0L
  for (i in 0..7) res = res or ((bytes[i].toLong() and 0xFF) shl (i * 8))
  return res
}

private fun Long.toHex(): String = "0x" + toString(16).padStart(8, '0')

/** Defines the type of problem that can be reported. */
sealed class AlignmentProblem {
  data class ZipEntryNotAligned(val zipAlignment: Long) : AlignmentProblem() {
    override fun toString() = "${getHumanReadablePageSize(zipAlignment)} zip alignment, but 16 KB is required"
  }

  data class LoadSectionNotAligned(val ph: ProgramHeader) : AlignmentProblem() {
    override fun toString() = "${getHumanReadablePageSize(ph.align)} LOAD section alignment, but 16 KB is required"
  }

  data class RelroStartNotAligned(val ph: ProgramHeader) : AlignmentProblem() {
    override fun toString() = "RELRO is not a prefix and its start is not 16 KB aligned"
  }

  data class RelroEndNotAligned(val ph: ProgramHeader) : AlignmentProblem() {
    override fun toString() = "RELRO is not a suffix and its end is not 16 KB aligned"
  }
}

/**
 * Give a human-readable page alignment. If the value >= 1024L and is a multiple of 1024L then return in units of KB. Otherwise, return in
 * units of B. In practice, bytes will be a power of 2 greater than 8 due to the way clang works.
 */
fun getHumanReadablePageSize(sizeInBytes: Long): String {
  if (sizeInBytes == -1L) return ""
  if (sizeInBytes >= 1024L && (sizeInBytes % 1024L) == 0L) return "${sizeInBytes/1024} KB"
  return "$sizeInBytes B"
}

/** Result of processing a zip (apk, aab, aar) for alignment problems. */
data class PageAlignmentInfo(
  /** When true, the zip has at least one ELF file. */
  val hasElfFiles: Boolean,

  /** Key is the path to the ELF file within the zip, value is a list of alignment problems. */
  val alignmentProblems: Map<String, List<AlignmentProblem>>,

  /** Holds an exception if one was thrown while processing the zip. */
  val zipException: Exception?,
)

internal fun findElfFile16kAlignmentInfo(input: ZipArchiveInputStream): PageAlignmentInfo {
  val alignmentProblems = mutableMapOf<String, MutableList<AlignmentProblem>>()
  var hasElfFiles = false
  var zipException: Exception? = null

  fun tolerantNext() =
    try {
      input.nextZipEntry
    } catch (e: ZipException) {
      // This can happen if the file wasn't a zip or was corrupted.
      zipException = e
      null
    } catch (e: IOException) {
      // This can happen if the file wasn't a zip or was corrupted.
      zipException = e
      null
    }

  var entry = tolerantNext()
  while (entry != null) {
    val currentEntryAligned = is16kAligned(input.bytesRead)
    if (hasElfMagicNumber(input)) {
      val elfProblems = readElfAlignmentProblems(input)
      if (elfProblems != null) {
        hasElfFiles = true

        elfProblems.forEach { alignmentProblems.computeIfAbsent(entry.name) { mutableListOf() }.add(it) }

        if (entry.method == ZipEntry.STORED && !currentEntryAligned) {
          alignmentProblems.computeIfAbsent(entry.name) { mutableListOf() }.add(ZipEntryNotAligned(input.bytesRead))
        }
      }
    }
    entry = tolerantNext()
  }
  return PageAlignmentInfo(hasElfFiles, alignmentProblems, zipException)
}

fun findElfFile16kAlignmentInfo(file: File): PageAlignmentInfo {
  return FileInputStream(file).use { fis -> ZipArchiveInputStream(fis).use { findElfFile16kAlignmentInfo(it) } }
}

fun is16kAligned(value: Long) = (value % PAGE_ALIGNMENT_16K) == 0L
