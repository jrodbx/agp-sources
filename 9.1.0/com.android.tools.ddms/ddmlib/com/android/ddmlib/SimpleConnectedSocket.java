/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.ddmlib;

import com.android.annotations.NonNull;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * This interface is used by classes that wrap a channel (SocketChannel or AdbChannel) and use it to
 * perform reads and writes with a timeout.
 */
public interface SimpleConnectedSocket extends Closeable {

    /**
     * Reads from the channel
     *
     * @return the number of bytes read, possibly zero, or -1 if the channel has reached
     *     end-of-stream. If the timeout is reached the method will return zero (no exception will
     *     be thrown in this case).
     */
    int read(@NonNull ByteBuffer dst, long timeoutMs) throws IOException;

    /**
     * Writes to the channel
     *
     * @return number of bytes written possibly zero. If the timeout is reached the method will
     *     return zero (no exception will be thrown in this case).
     */
    int write(@NonNull ByteBuffer dst, long timeoutMs) throws IOException;

    /** Returns true if the underlying channel is open */
    boolean isOpen();
}
