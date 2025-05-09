/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.ddmlib.Client;
import com.android.ddmlib.DebugViewDumpHandler;
import com.android.ddmlib.internal.jdwp.chunkhandler.ChunkHandler;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Delegate a {@link ChunkHandler} to a {@link DebugViewDumpHandler} */
public class DebugViewChunkHandler extends ChunkHandler {
    private final int mChunkType;

    private final DebugViewDumpHandler mHandler;

    public DebugViewChunkHandler(int chunkType, DebugViewDumpHandler handler) {
        mChunkType = chunkType;
        mHandler = handler;
    }

    @Override
    public void clientReady(ClientImpl client) throws IOException {}

    @Override
    public void clientDisconnected(ClientImpl client) {}

    @Override
    public void handleChunk(
            ClientImpl client, int type, ByteBuffer data, boolean isReply, int msgId) {
        if (type != mChunkType) {
            handleUnknownChunk(client, type, data, isReply, msgId);
            return;
        }

        handleViewDebugResult(client, type, data, isReply, msgId);
    }

    private void handleViewDebugResult(
            Client client, int type, ByteBuffer data, boolean isReply, int msgId) {
        mHandler.handleChunk(client, type, data, isReply, msgId);
    }
}
