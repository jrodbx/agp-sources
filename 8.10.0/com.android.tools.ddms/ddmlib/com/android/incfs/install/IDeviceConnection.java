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
package com.android.incfs.install;

import com.android.annotations.NonNull;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Represents a connection to a device that can be read from and written to. */
public interface IDeviceConnection extends AutoCloseable {
    interface Factory {
        /**
         * Connects to the Android {@code service} on device with the specified parameters, and
         * returns a connection to the service.
         */
        IDeviceConnection connectToService(@NonNull String service, @NonNull String[] parameters)
                throws IOException;
    }

    /**
     * Reads a sequence of bytes from this connection into the given buffer.
     *
     * <p>An attempt is made to read up to r bytes to the device, where r is the number of bytes
     * remaining in the buffer, that is, dst.remaining(), at the moment this method is invoked.
     *
     * @param buffer where to store data read from the socket
     * @param timeOutMs timeout in milliseconds (for the full operation to complete)
     * @return The number of bytes read, possibly zero, or -1 if the command has ended.
     * @see {@link java.nio.channels.Selector#select(long timeoutMs)}
     * @see {@link java.nio.channels.SocketChannel#read(ByteBuffer buffer)}
     */
    int read(@NonNull ByteBuffer buffer, long timeOutMs) throws IOException;

    /**
     * Writes a sequence of bytes to the device from the given buffer.
     *
     * <p>An attempt is made to write up to r bytes to the device, where r is the number of bytes
     * remaining in the buffer, that is, src.remaining(), at the moment this method is invoked. It's
     *
     * @param buffer data to be sent
     * @param timeOutMs timeout in milliseconds (for the full operation to complete)
     * @return The number of bytes written, possibly zero, or -1 if the command has ended.
     * @see {@link java.nio.channels.Selector#select(long timeoutMs)}
     * @see {@link java.nio.channels.SocketChannel#write(ByteBuffer buffer)}
     */
    int write(@NonNull ByteBuffer buffer, long timeOutMs) throws IOException;
}
