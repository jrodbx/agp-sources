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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

/** Wrapper for a {@link SocketChannel} that supports read/write with timeouts */
public class SocketChannelWithTimeouts implements SimpleConnectedSocket {
    private static final String LOG_TAG = "SocketChannelWithTimeouts";

    private final SocketChannel channel;
    private Selector readSelector;
    private Selector writeSelector;

    public static SocketChannelWithTimeouts wrap(SocketChannel channel) throws IOException {
        SocketChannelWithTimeouts wrappedChannel = new SocketChannelWithTimeouts(channel);
        try {
            wrappedChannel.init();
        } catch (IOException e) {
            wrappedChannel.close();
            throw e;
        }
        return wrappedChannel;
    }

    private SocketChannelWithTimeouts(SocketChannel channel) {
        this.channel = channel;
    }

    private void init() throws IOException {
        if (channel.isBlocking()) {
            Log.d(LOG_TAG, "SocketChannel is a blocking channel. Changing it to non-blocking");
            channel.configureBlocking(false);
        }

        readSelector = Selector.open();
        channel.register(readSelector, SelectionKey.OP_READ);

        writeSelector = Selector.open();
        channel.register(writeSelector, SelectionKey.OP_WRITE);
    }

    @Override
    public int read(@NonNull ByteBuffer dst, long timeoutMs) throws IOException {
        readSelector.select(timeoutMs);
        return channel.read(dst);
    }

    @Override
    public int write(@NonNull ByteBuffer dst, long timeoutMs) throws IOException {
        writeSelector.select(timeoutMs);
        return channel.write(dst);
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public void close() throws IOException {
        try (Channel c = channel;
                Selector r = readSelector;
                Selector w = writeSelector) {}
    }
}
