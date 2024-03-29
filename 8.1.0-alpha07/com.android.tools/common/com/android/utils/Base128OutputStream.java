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
package com.android.utils;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An output stream that uses the unsigned little endian base 128 (<a xref="https://en.wikipedia.org/wiki/LEB128">LEB128</a>)
 * variable-length encoding for integer values.
 * @see Base128InputStream
 */
public final class Base128OutputStream extends BufferedOutputStream {
  public Base128OutputStream(@NotNull OutputStream stream) {
    super(stream);
  }

  public Base128OutputStream(@NotNull Path file) throws IOException {
    super(Files.newOutputStream(file));
  }

  /**
   * Writes a 32-bit integer to the stream. Small positive integers take less space than larger ones:
   * <ul>
   *   <li>[0, 2^7) - 1 byte</li>
   *   <li>[2^7, 2^14) - 2 bytes</li>
   *   <li>[2^14, 2^21) - 3 bytes</li>
   *   <li>[2^21, 2^28) - 4 bytes</li>
   *   <li>[2^28, 2^31) - 5 bytes</li>
   *   <li>negative - 5 bytes</li>
   * </ul>
   * Avoid using this method for writing negative numbers since they take 5 bytes.
   *
   * @param value the value to write
   * @throws IOException if an I/O error occurs.
   */
  public void writeInt(int value) throws IOException {
    do {
      int b = value & 0x7F;
      value >>>= 7;
      if (value != 0) {
        b |= 0x80;
      }
      super.write(b);
    } while (value != 0);
  }

  /**
   * Writes a 64-bit integer to the stream. Small positive integers take less space than larger ones:
   * <ul>
   *   <li>[0, 2^7) - 1 byte</li>
   *   <li>[2^7, 2^14) - 2 bytes</li>
   *   <li>[2^14, 2^21) - 3 bytes</li>
   *   <li>[2^21, 2^28) - 4 bytes</li>
   *   <li>[2^28, 2^35) - 5 bytes</li>
   *   <li>[2^35, 2^42) - 6 bytes</li>
   *   <li>[2^42, 2^49) - 7 bytes</li>
   *   <li>[2^49, 2^56) - 8 bytes</li>
   *   <li>[2^56, 2^63) - 9 bytes</li>
   *   <li>negative - 10 bytes</li>
   * </ul>
   * Avoid using this method for writing negative numbers since they take 10 bytes.
   *
   * @param value the value to write
   * @throws IOException if an I/O error occurs.
   */
  public void writeLong(long value) throws IOException {
    do {
      int b = (int) value & 0x7F;
      value >>>= 7;
      if (value != 0) {
        b |= 0x80;
      }
      super.write(b);
    } while (value != 0);
  }

  /**
   * Writes a float to the stream as a 32-bit, IEEE754-encoded floating point value.
   *
   * @param value the value to write
   * @throws IOException if an I/O error occurs.
   */
  public void writeFloat(float value) throws IOException {
    writeFixed32(Float.floatToIntBits(value));
  }

  /**
   * Writes a fixed 32-bit value to the stream.
   *
   * @param value the 32-bit value, as an int
   * @throws IOException if an I/O error occurs
   */
  public void writeFixed32(int value) throws IOException {
    for (int shift = 0; shift < 32; shift += 8) {
      super.write((value >>> shift) & 0xFF);
    }
  }

  /**
   * Writes a String to the stream. The string is prefixed by its length + 1.
   * Each character is then written using the {@link #writeChar} method.
   *
   * @param str the string to write or null
   * @throws IOException if an I/O error occurs.
   */
  public void writeString(@Nullable String str) throws IOException {
    if (str == null) {
      writeInt(0);
    }
    else {
      int len = str.length();
      writeInt(len + 1);
      for (int i = 0; i < len; i++) {
        writeChar(str.charAt(i));
      }
    }
  }

  /**
   * Writes a 16-bit integer to the stream. Small positive integers take less space than larger ones:
   * <ul>
   *   <li>[0, 2^7) - 1 byte</li>
   *   <li>[2^7, 2^14) - 2 bytes</li>
   *   <li>[2^14, 2^16) - 3 bytes</li>
   * </ul>
   *
   * @param value the value to write
   * @throws IOException if an I/O error occurs.
   */
  public void writeChar(char value) throws IOException {
    writeInt(value & 0xFFFF);
  }

  /**
   * Writes a byte value to the stream.
   *
   * @param value the value to write
   * @throws IOException if an I/O error occurs.
   */
  public void writeByte(byte value) throws IOException {
    super.write(value);
  }

  /**
   * Writes an array of bytes to the stream. The bytes are prefixed by their number.
   * Each byte is then written using the {@link #writeByte} method.
   *
   * @param bytes the array of bytes to write
   * @throws IOException if an I/O error occurs.
   */
  public void writeBytes(@NotNull byte[] bytes) throws IOException {
    writeInt(bytes.length);
    for (byte b : bytes) {
      writeByte(b);
    }
  }

  /**
   * Writes a boolean value to the stream.
   *
   * @param value the value to write
   * @throws IOException if an I/O error occurs.
   */
  public void writeBoolean(boolean value) throws IOException {
    writeInt(value ? 1 : 0);
  }

  /**
   * @deprecated Use {@link #writeByte(byte)} or {@link #writeInt(int)} instead.
   * @throws UnsupportedOperationException when called
   */
  @Deprecated
  @Override
  public void write(int b) {
    throw new UnsupportedOperationException(
        "This method is disabled to prevent unintended accidental use. Please use writeByte or writeInt instead.");
  }
}
