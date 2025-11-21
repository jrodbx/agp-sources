/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.utils

import com.android.io.CancellableFileIo
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.enums.enumEntries

/**
 * An output stream that uses the unsigned little endian base 128
 * (<a xref="https://en.wikipedia.org/wiki/LEB128">LEB128</a>) variable-length encoding for integer
 * values.
 * @see Base128OutputStream
 */
class Base128InputStream(stream: InputStream) : BufferedInputStream(stream) {

  private var stringCache: MutableMap<String, String>? = null

  /**
   * Opens a stream to read from the given file.
   *
   * @param file the file to read from
   * @throws NoSuchFileException if the file does not exist
   * @throws IOException if an I/O error occurs
   */
  @Throws(NoSuchFileException::class, IOException::class)
  constructor(file: Path) : this(CancellableFileIo.newInputStream(file))

  /**
   * If the `stringCache` parameter is not null, the [.readString] method will use that cache
   * to avoid returning distinct String instances that are equal to each other.
   *
   * @param stringCache the map used for storing previously encountered strings; keys and values are identical.
   */
  fun setStringCache(stringCache: MutableMap<String, String>) {
    this.stringCache = stringCache
  }

  /**
   * Reads a 16-bit integer from the stream. The integer had to be written by [Base128OutputStream.writeChar].
   *
   * @return the value read from the stream
   * @throws IOException if an I/O error occurs
   * @throws StreamFormatException if an invalid data format is detected
   */
  @Throws(IOException::class, StreamFormatException::class)
  fun readChar(): Char {
    var b = readByteAsInt()
    var value = b and 0x7F
    var shift = 7
    while ((b and 0x80) != 0) {
      b = readByteAsInt()
      if (shift == 14 && (b and 0xFC) != 0) {
        throw StreamFormatException.Companion.invalidFormat()
      }
      value = value or ((b and 0x7F) shl shift)
      shift += 7
    }
    return value.toChar()
  }

  /**
   * Reads a 32-bit integer from the stream. The integer had to be written by [Base128OutputStream.writeInt].
   *
   * @return the value read from the stream
   * @throws IOException if an I/O error occurs
   * @throws StreamFormatException if an invalid data format is detected
   */
  @Throws(IOException::class)
  fun readInt(): Int {
    var b = readByteAsInt()
    var value = b and 0x7F
    var shift = 7
    while ((b and 0x80) != 0) {
      b = readByteAsInt()
      if (shift == 28 && (b and 0xF0) != 0) {
        throw StreamFormatException.Companion.invalidFormat()
      }
      value = value or ((b and 0x7F) shl shift)
      shift += 7
    }
    return value
  }

  /**
   * Reads a 64-bit integer from the stream. The integer had to be written by [Base128OutputStream.writeLong].
   *
   * @return the value read from the stream
   * @throws IOException if an I/O error occurs
   * @throws StreamFormatException if an invalid data format is detected
   */
  @Throws(IOException::class, StreamFormatException::class)
  fun readLong(): Long {
    var b = readByteAsInt()
    var value = (b and 0x7F).toLong()
    var shift = 7
    while ((b and 0x80) != 0) {
      b = readByteAsInt()
      if (shift == 63 && (b and 0xFE) != 0) {
        throw StreamFormatException.Companion.invalidFormat()
      }
      value = value or (((b and 0x7F).toLong()) shl shift)
      shift += 7
    }
    return value
  }

  /**
   * Reads a float from the stream. The float had to be written by [Base128OutputStream.writeFloat].
   *
   * @return the value read from the stream
   * @throws IOException if an I/O error occurs
   * @throws StreamFormatException if an invalid data format is detected
   */
  @Throws(IOException::class, StreamFormatException::class)
  fun readFloat(): Float {
    return Float.fromBits(readFixed32())
  }

  /**
   * Reads a fixed 32-bit value from the stream and returns it as an int.
   *
   * @return the next 32-bits of the stream as a 4-byte int
   * @throws IOException if an I/O error occurs
   */
  @Throws(IOException::class)
  fun readFixed32(): Int {
    return readByteAsInt() or (readByteAsInt() shl 8) or (readByteAsInt() shl 16) or
        (readByteAsInt() shl 24)
  }

  /**
   * Reads a String from the stream. The String had to be written by [Base128OutputStream.writeString].
   *
   * @return the String read from the stream, or null if [Base128OutputStream.writeString] was called
   * with a null argument
   * @throws IOException if an I/O error occurs
   * @throws StreamFormatException if an invalid data format is detected
   */
  @Throws(IOException::class, StreamFormatException::class)
  fun readString(): String? {
    var len = readInt()
    if (len < 0) {
      throw StreamFormatException.Companion.invalidFormat()
    }
    if (len == 0) {
      return null
    }
    --len
    if (len == 0) {
      return ""
    }
    val buf = StringBuilder(len)
    while (--len >= 0) {
      buf.append(readChar())
    }
    val str = buf.toString()
    return stringCache?.computeIfAbsent(str) { it } ?: str
  }

  /**
   * Reads a byte value from the stream.
   *
   * @return the byte value read from the stream, or -1 if the end of the stream is reached
   * @throws IOException if an I/O error occurs
   * @throws StreamFormatException if the stream does not contain any more data
   */
  @Throws(IOException::class)
  fun readByte(): Byte {
    val b = readByteAsInt()
    return b.toByte()
  }

  /**
   * Reads an array of bytes from the stream. The bytes had to be written by [Base128OutputStream.writeBytes].
   *
   * @return the array of bytes read from the stream
   * @throws IOException if an I/O error occurs
   * @throws StreamFormatException if an invalid data format is detected
   */
  @Throws(IOException::class, StreamFormatException::class)
  fun readBytes(): ByteArray {
    val len = readInt()
    if (len < 0) {
      throw StreamFormatException.Companion.invalidFormat()
    }
    val bytes = ByteArray(len)
    for (i in 0..<len) {
      bytes[i] = readByte()
    }
    return bytes
  }

  /**
   * Reads a boolean value from the stream.
   *
   * @return the value read from the stream
   * @throws IOException if an I/O error occurs
   * @throws StreamFormatException if an invalid data format is detected
   */
  @Throws(IOException::class, StreamFormatException::class)
  fun readBoolean(): Boolean {
    val c = readInt()
    if ((c and 0x1.inv()) != 0) {
      throw StreamFormatException.Companion.invalidFormat()
    }
    return c != 0
  }

  /**
   * Reads an enum value represented by its ordinal number from the stream.
   *
   * @return the value read from the stream
   * @throws IOException if an I/O error occurs
   * @throws StreamFormatException if an invalid data format is detected
   */
  @Throws(IOException::class, StreamFormatException::class)
  inline fun <reified T : Enum<T>> readEnum(): T {
    val ordinal = readInt()
    return try {
      enumEntries<T>()[ordinal]
    } catch (_: IndexOutOfBoundsException) {
      throw StreamFormatException("Invalid ordinal value $ordinal of enum ${T::class.simpleName}")
    }
  }

  /** @throws UnsupportedOperationException when called. */
  @Deprecated("Use readByte() or readInt() instead.")
  override fun read(): Int {
    throw UnsupportedOperationException(
        "This method is disabled to prevent unintended accidental use. Please use readByte or readInt instead."
    )
  }

  /**
   * Checks if the stream contains the given bytes starting from the current position.
   * Unless the remaining part of the stream is shorter than the `expected` array,
   * exactly `expected.length` bytes are read from the stream.
   *
   * @param expected expected stream content
   * @return true if the stream content matches, false otherwise.
   * @throws IOException in case of a premature end of stream or an I/O error
   */
  @Throws(IOException::class)
  fun validateContents(expected: ByteArray): Boolean {
    var result = true
    for (b in expected) {
      if (b != readByte()) {
        result = false
      }
    }
    return result
  }

  @Throws(IOException::class)
  private fun readByteAsInt(): Int {
    val b = super.read()
    if (b < 0) {
      throw StreamFormatException.Companion.prematureEndOfFile()
    }
    return b
  }

  /**
   * Exception thrown when invalid data is encountered while reading from a stream.
   */
  class StreamFormatException(message: String) : IOException(message) {
    companion object {
      @JvmStatic
      fun prematureEndOfFile(): StreamFormatException {
        return StreamFormatException("Premature end of file")
      }

      @JvmStatic
      fun invalidFormat(): StreamFormatException {
        return StreamFormatException("Invalid file format")
      }
    }
  }
}
