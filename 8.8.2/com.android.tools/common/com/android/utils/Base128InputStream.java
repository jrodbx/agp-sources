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

import com.android.io.CancellableFileIo;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An output stream that uses the unsigned little endian base 128 (<a xref="https://en.wikipedia.org/wiki/LEB128">LEB128</a>)
 * variable-length encoding for integer values.
 * @see Base128OutputStream
 */
public final class Base128InputStream extends BufferedInputStream {
  @Nullable private Map<String, String> myStringCache;

  /**
   * Wraps a given input stream.
   */
  public Base128InputStream(@NotNull InputStream stream) {
    super(stream);
  }

  /**
   * Opens a stream to read from the given file.
   *
   * @param file the file to read from
   * @throws NoSuchFileException if the file does not exist
   * @throws IOException if any other error occurs
   */
  public Base128InputStream(@NotNull Path file) throws IOException {
    super(CancellableFileIo.newInputStream(file));
  }

  /**
   * If the {@code stringCache} parameter is not null, the {@link #readString()} method will use that cache
   * to avoid returning distinct String instances that are equal to each other.
   *
   * @param stringCache the map used for storing previously encountered strings; keys and values are identical.
   */
  public void setStringCache(@Nullable Map<String, String> stringCache) {
    myStringCache = stringCache;
  }

  /**
   * Reads a 16-bit integer from the stream. The integer had to be written by {@link Base128OutputStream#writeChar(char)}.
   *
   * @return the value read from the stream
   * @throws IOException if an I/O error occurs
   * @throws StreamFormatException if an invalid data format is detected
   */
  public char readChar() throws IOException, StreamFormatException {
    int b = readByteAsInt();
    int value = b & 0x7F;
    for (int shift = 7; (b & 0x80) != 0; shift += 7) {
      b = readByteAsInt();
      if (shift == 14 && (b & 0xFC) != 0) {
        throw StreamFormatException.invalidFormat();
      }
      value |= (b & 0x7F) << shift;
    }
    return (char)value;
  }

  /**
   * Reads a 32-bit integer from the stream. The integer had to be written by {@link Base128OutputStream#writeInt(int)}.
   *
   * @return the value read from the stream
   * @throws IOException if an I/O error occurs
   * @throws StreamFormatException if an invalid data format is detected
   */
  public int readInt() throws IOException {
    int b = readByteAsInt();
    int value = b & 0x7F;
    for (int shift = 7; (b & 0x80) != 0; shift += 7) {
      b = readByteAsInt();
      if (shift == 28 && (b & 0xF0) != 0) {
        throw StreamFormatException.invalidFormat();
      }
      value |= (b & 0x7F) << shift;
    }
    return value;
  }

  /**
   * Reads a 64-bit integer from the stream. The integer had to be written by {@link Base128OutputStream#writeLong(long)}.
   *
   * @return the value read from the stream
   * @throws IOException if an I/O error occurs
   * @throws StreamFormatException if an invalid data format is detected
   */
  public long readLong() throws IOException, StreamFormatException {
    int b = readByteAsInt();
    long value = b & 0x7F;
    for (int shift = 7; (b & 0x80) != 0; shift += 7) {
      b = readByteAsInt();
      if (shift == 63 && (b & 0xFE) != 0) {
        throw StreamFormatException.invalidFormat();
      }
      value |= ((long) (b & 0x7F)) << shift;
    }
    return value;
  }

  /**
   * Reads a float from the stream. The float had to be written by {@link Base128OutputStream#writeFloat(float)}.
   *
   * @return the value read from the stream
   * @throws IOException if an I/O error occurs
   * @throws StreamFormatException if an invalid data format is detected
   */
  public float readFloat() throws IOException, StreamFormatException {
    return Float.intBitsToFloat(readFixed32());
  }

  /**
   * Reads a fixed 32-bit value from the stream and returns it as an int.
   *
   * @return the next 32-bits of the stream as a 4-byte int
   * @throws IOException if an I/O error occurs
   */
  public int readFixed32() throws IOException {
    return readByteAsInt() | (readByteAsInt() << 8) | (readByteAsInt() << 16) |
           (readByteAsInt() << 24);
  }

  /**
   * Reads a String from the stream. The String had to be written by {@link Base128OutputStream#writeString(String)}.
   *
   * @return the String read from the stream, or null if {@link Base128OutputStream#writeString(String)} was called
   *     with a null argument
   * @throws IOException if an I/O error occurs
   * @throws StreamFormatException if an invalid data format is detected
   */
  @Nullable
  public String readString() throws IOException, StreamFormatException {
    int len = readInt();
    if (len < 0) {
      throw StreamFormatException.invalidFormat();
    }
    if (len == 0) {
      return null;
    }
    --len;
    if (len == 0) {
      return "";
    }
    StringBuilder buf = new StringBuilder(len);
    for (int i = 0; i < len; i++) {
      buf.append(readChar());
    }
    String str = buf.toString();
    return myStringCache == null ? str : myStringCache.computeIfAbsent(str, Function.identity());
  }

  /**
   * Reads a byte value from the stream.
   *
   * @return the byte value read from the stream, or -1 if the end of the stream is reached
   * @throws IOException if an I/O error occurs
   * @throws StreamFormatException if the stream does not contain any more data
   */
  public byte readByte() throws IOException {
    int b = readByteAsInt();
    return (byte)b;
  }

  /**
   * Reads an array of bytes from the stream. The bytes had to be written by {@link Base128OutputStream#writeBytes}.
   *
   * @return the array of bytes read from the stream
   * @throws IOException if an I/O error occurs
   * @throws StreamFormatException if an invalid data format is detected
   */
  public @NotNull byte[] readBytes() throws IOException, StreamFormatException {
    int len = readInt();
    if (len < 0) {
      throw StreamFormatException.invalidFormat();
    }
    byte[] bytes = new byte[len];
    for (int i = 0; i < len; i++) {
      bytes[i] = readByte();
    }
    return bytes;
  }

  /**
   * Reads a boolean value from the stream.
   *
   * @return the value read from the stream
   * @throws IOException if an I/O error occurs
   * @throws StreamFormatException if an invalid data format is detected
   */
  public boolean readBoolean() throws IOException, StreamFormatException {
    int c = readInt();
    if ((c & ~0x1) != 0) {
      throw StreamFormatException.invalidFormat();
    }
    return c != 0;
  }

  /**
   * @deprecated Use {@link #readByte()} or {@link #readInt()} instead.
   * @throws UnsupportedOperationException when called
   */
  @Deprecated
  @SuppressWarnings("NonSynchronizedMethodOverridesSynchronizedMethod")
  @Override
  public int read() {
    throw new UnsupportedOperationException(
        "This method is disabled to prevent unintended accidental use. Please use readByte or readInt instead.");
  }

  /**
   * Checks if the stream contains the given bytes starting from the current position.
   * Unless the remaining part of the stream is shorter than the {@code expected} array,
   * exactly {@code expected.length} bytes are read from the stream.
   *
   * @param expected expected stream content
   * @return true if the stream content matches, false otherwise.
   * @throws IOException in case of a premature end of stream or an I/O error
   */
  public boolean validateContents(@NotNull byte[] expected) throws IOException {
    boolean result = true;
    for (byte b : expected) {
      if (b != readByte()) {
        result = false;
      }
    }
    return result;
  }

  private int readByteAsInt() throws IOException {
    int b = super.read();
    if (b < 0) {
      throw StreamFormatException.prematureEndOfFile();
    }
    return b;
  }

  /**
   * Exception thrown when invalid data is encountered while reading from a stream.
   */
  public static class StreamFormatException extends IOException {
    public StreamFormatException(@NotNull String message) {
      super(message);
    }

    public static StreamFormatException prematureEndOfFile() {
      return new StreamFormatException("Premature end of file");
    }

    public static StreamFormatException invalidFormat() {
      return new StreamFormatException("Invalid file format");
    }
  }
}
