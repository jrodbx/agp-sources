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
package com.android.ddmlib.internal;

import com.android.annotations.NonNull;
import com.android.ddmlib.AdbHelper;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

public class AdbSocketUtils {

    /**
     * Fills a ByteBuffer by reading data from a socket.
     *
     * @throws IOException if there was not enough data to fill the buffer
     */
    static void read(@NonNull SocketChannel socket, @NonNull ByteBuffer buf) throws IOException {
        while (buf.remaining() > 0) {
            int count = socket.read(buf);
            if (count < 0) {
                throw new EOFException("EOF");
            }
        }
    }

    /**
     * Reads data of given length from a socket.
     *
     * @return the content in a ByteBuffer, with the position at the beginning.
     * @throws IOException if there was not enough data to fill the buffer
     */
    @NonNull
    static ByteBuffer read(@NonNull SocketChannel socket, int length) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(length);
        read(socket, buf);
        buf.rewind();
        return buf;
    }

    /**
     * Fills a buffer by reading data from a socket.
     *
     * @return the content of the buffer as a string, or null if it failed to convert the buffer.
     * @throws IOException if there was not enough data to fill the buffer
     */
    @NonNull
    static String read(@NonNull SocketChannel socket, @NonNull byte[] buffer) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(buffer, 0, buffer.length);
        read(socket, buf);
        return new String(buffer, 0, buf.position(), AdbHelper.DEFAULT_CHARSET);
    }

    /**
     * Reads the length of the next message from a socket.
     *
     * @param socket The {@link SocketChannel} to read from.
     * @return the length, or 0 (zero) if no data is available from the socket.
     * @throws IOException if the connection failed.
     */
    static int readLength(@NonNull SocketChannel socket, @NonNull byte[] buffer)
            throws IOException {
        String msg = read(socket, buffer);

        if (msg != null) {
            try {
                return Integer.parseInt(msg, 16);
            } catch (NumberFormatException nfe) {
                // we'll throw an exception below.
            }
        }

        // we receive something we can't read. It's better to reset the connection at this point.
        throw new IOException("Unable to read length");
    }

    @NonNull
    static String readNullTerminatedString(@NonNull SocketChannel socket) throws IOException {
        return readNullTerminatedString(socket, AdbHelper.DEFAULT_CHARSET);
    }

    @NonNull
    static String readNullTerminatedString(@NonNull SocketChannel socket, @NonNull Charset charset)
            throws IOException {
        byte[] buffer = new byte[64];
        ByteBuffer buf = ByteBuffer.wrap(buffer, 0, buffer.length);
        ByteArrayDataOutput output = ByteStreams.newDataOutput(64);
        boolean end = false;
        while (!end) {
            buf.rewind();
            try {
                read(socket, buf);
            } catch (EOFException e) {
                end = true;
            }
            int nullPosition = 0;
            for (; nullPosition < buf.position(); nullPosition++) {
                if (buffer[nullPosition] == 0) {
                    end = true;
                    break;
                }
            }
            output.write(buffer, 0, nullPosition);
        }
        return new String(output.toByteArray(), charset);
    }
}
