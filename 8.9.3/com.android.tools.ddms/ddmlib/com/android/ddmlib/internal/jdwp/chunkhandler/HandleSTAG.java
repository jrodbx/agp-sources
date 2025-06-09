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

package com.android.ddmlib.internal.jdwp.chunkhandler;

import com.android.ddmlib.Log;
import com.android.ddmlib.internal.ClientImpl;
import com.android.ddmlib.internal.MonitorThread;
import java.nio.ByteBuffer;

/**
 * Handle the "STAG" chunk. These packets, introduced in API 34 are a synchronization mechanism
 * allowing ddm client to be updated as an app crosses boot milestones.
 */
public final class HandleSTAG extends ChunkHandler {

    static final int CHUNK_STAG = ChunkHandler.type("STAG");

    private static final HandleSTAG mInst = new HandleSTAG();

    private HandleSTAG() {}

    /** Register for the packets we expect to get from the client. */
    public static void register(MonitorThread mt) {
        mt.registerChunkHandler(CHUNK_STAG, mInst);
    }

    /** Client is ready. */
    @Override
    public void clientReady(ClientImpl client) {}

    /** Client went away. */
    @Override
    public void clientDisconnected(ClientImpl client) {}

    /** Chunk handler entry point. */
    @Override
    public void handleChunk(
            ClientImpl client, int type, ByteBuffer data, boolean isReply, int msgId) {

        Log.d("ddm-stag", "handling " + ChunkHandler.name(type));

        if (type == CHUNK_STAG) {
            assert !isReply;
            // Do nothing
        } else {
            handleUnknownChunk(client, type, data, isReply, msgId);
        }
    }
}
