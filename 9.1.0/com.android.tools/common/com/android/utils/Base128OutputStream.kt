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

import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * An output stream that uses the unsigned little endian base 128 (<a xref="https://en.wikipedia.org/wiki/LEB128">LEB128</a>)
 * variable-length encoding for integer values.
 *
 * @see Base128InputStream
 */
class Base128OutputStream(stream: OutputStream) : BufferedOutputStream(stream) {

  /**
   * Opens a stream to write to the given file.
   *
   * @param file the file to write to
   * @throws IOException if an I/O error occurs.
   */
  @Throws(IOException::class) constructor(file: Path) : this(Files.newOutputStream(file))

  /**
   * Writes a 32-bit integer to the stream. Small positive integers take less space than larger ones:
   * ```
   *  [0, 2^7) - 1 byte
   *  [2^7, 2^14) - 2 bytes
   *  [2^14, 2^21) - 3 bytes
   *  [2^21, 2^28) - 4 bytes
   *  [2^28, 2^31) - 5 bytes
   *  negative - 5 bytes
   * ```
   *
   * Avoid using this method for writing negative numbers since they take 5 bytes.
   *
   * @param value the value to write
   * @throws IOException if an I/O error occurs.
   */
  @Throws(IOException::class)
  fun writeInt(value: Int) {
    var value = value
    do {
      var b = value and 0x7F
      value = value ushr 7
      if (value != 0) {
        b = b or 0x80
      }
      super.write(b)
    } while (value != 0)
  }

  /**
   * Writes a 64-bit integer to the stream. Small positive integers take less space than larger ones:
   * ```
   *  [0, 2^7) - 1 byte
   *  [2^7, 2^14) - 2 bytes
   *  [2^14, 2^21) - 3 bytes
   *  [2^21, 2^28) - 4 bytes
   *  [2^28, 2^35) - 5 bytes
   *  [2^35, 2^42) - 6 bytes
   *  [2^42, 2^49) - 7 bytes
   *  [2^49, 2^56) - 8 bytes
   *  [2^56, 2^63) - 9 bytes
   *  negative - 10 bytes
   * ```
   *
   * Avoid using this method for writing negative numbers since they take 10 bytes.
   *
   * @param value the value to write
   * @throws IOException if an I/O error occurs.
   */
  @Throws(IOException::class)
  fun writeLong(value: Long) {
    var value = value
    do {
      var b = value.toInt() and 0x7F
      value = value ushr 7
      if (value != 0L) {
        b = b or 0x80
      }
      super.write(b)
    } while (value != 0L)
  }

  /**
   * Writes a float to the stream as a 32-bit, IEEE754-encoded floating point value.
   *
   * @param value the value to write
   * @throws IOException if an I/O error occurs.
   */
  @Throws(IOException::class)
  fun writeFloat(value: Float) {
    writeFixed32(value.toBits())
  }

  /**
   * Writes a fixed 32-bit value to the stream.
   *
   * @param value the 32-bit value, as an int
   * @throws IOException if an I/O error occurs
   */
  @Throws(IOException::class)
  fun writeFixed32(value: Int) {
    var shift = 0
    while (shift < 32) {
      super.write((value ushr shift) and 0xFF)
      shift += 8
    }
  }

  /**
   * Writes a String to the stream. The string is prefixed by its length + 1. Each character is then written using the [.writeChar] method.
   *
   * @param str the string to write or null
   * @throws IOException if an I/O error occurs.
   */
  @Throws(IOException::class)
  fun writeString(str: String?) {
    if (str == null) {
      writeInt(0)
    } else {
      val len = str.length
      writeInt(len + 1)
      for (i in 0 until len) {
        writeChar(str[i])
      }
    }
  }

  /**
   * Writes a 16-bit integer to the stream. Small positive integers take less space than larger ones:
   * ```
   *  [0, 2^7) - 1 byte
   *  [2^7, 2^14) - 2 bytes
   *  [2^14, 2^16) - 3 bytes
   * ```
   *
   * @param value the value to write
   * @throws IOException if an I/O error occurs.
   */
  @Throws(IOException::class)
  fun writeChar(value: Char) {
    writeInt(value.code and 0xFFFF)
  }

  /**
   * Writes a byte value to the stream.
   *
   * @param value the value to write
   * @throws IOException if an I/O error occurs.
   */
  @Throws(IOException::class)
  fun writeByte(value: Byte) {
    super.write(value.toInt())
  }

  /**
   * Writes an array of bytes to the stream. The bytes are prefixed by their number. Each byte is then written using the [.writeByte]
   * method.
   *
   * @param bytes the array of bytes to write
   * @throws IOException if an I/O error occurs.
   */
  @Throws(IOException::class)
  fun writeBytes(bytes: ByteArray) {
    writeInt(bytes.size)
    for (b in bytes) {
      writeByte(b)
    }
  }

  /**
   * Writes a boolean value to the stream.
   *
   * @param value the value to write
   * @throws IOException if an I/O error occurs.
   */
  @Throws(IOException::class)
  fun writeBoolean(value: Boolean) {
    writeInt(if (value) 1 else 0)
  }

  /**
   * Writes an enum value as its ordinal number to the stream.
   *
   * @throws IOException if an I/O error occurs.
   */
  @Throws(IOException::class)
  fun <T : Enum<T>> writeEnum(value: T) {
    writeInt(value.ordinal)
  }

  /** @throws UnsupportedOperationException when called. */
  @Deprecated("Use writeByte or writeInt instead.")
  override fun write(b: Int) {
    throw UnsupportedOperationException(
      "This method is disabled to prevent unintended accidental use. Please use writeByte or writeInt instead."
    )
  }
}
