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
 * Handle the "ART Timing" chunk (ARTT). These are sent periodically by oj-libjdwp when its timing
 * buffer is full. This data is used by jdwp-tracer so we discard it.
 */
public final class HandleARTT extends ChunkHandler {

    static final int CHUNK_ARTT = ChunkHandler.type("ARTT");

    private static final HandleARTT mInst = new HandleARTT();

    private HandleARTT() {}

    /** Register for the packets we expect to get from the client. */
    public static void register(MonitorThread mt) {
        mt.registerChunkHandler(CHUNK_ARTT, mInst);
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

        Log.d("ddm-wait", "handling " + ChunkHandler.name(type));

        if (type == CHUNK_ARTT) {
            assert !isReply;
            // Do nothing
        } else {
            handleUnknownChunk(client, type, data, isReply, msgId);
        }
    }
}
