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
package com.android.ddmlib;

import com.android.ddmlib.internal.ClientImpl;
import com.android.ddmlib.internal.jdwp.chunkhandler.ChunkHandler;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public abstract class DebugViewDumpHandler extends ChunkHandler {
    /** Enable/Disable tracing of OpenGL calls. */
    public static final int CHUNK_VUGL = type("VUGL");

    /** List <code>ViewRootImpl</code>'s of this process. */
    public static final int CHUNK_VULW = type("VULW");

    /** Operation on view root, first parameter in packet should be one of VURT_* constants */
    public static final int CHUNK_VURT = type("VURT");
    /**
     * Generic View Operation, first parameter in the packet should be one of the VUOP_* constants
     * below.
     */
    public static final int CHUNK_VUOP = type("VUOP");

    private final CountDownLatch mLatch = new CountDownLatch(1);
    private final int mChunkType;

    public DebugViewDumpHandler(int chunkType) {
        mChunkType = chunkType;
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

        handleViewDebugResult(data);
        mLatch.countDown();
    }

    protected abstract void handleViewDebugResult(ByteBuffer data);

    protected void waitForResult(long timeout, TimeUnit unit) {
        try {
            mLatch.await(timeout, unit);
        } catch (InterruptedException e) {
            // pass
        }
    }

    /** Utility function to copy a String out of a ByteBuffer. */
    public static String getString(ByteBuffer buf, int len) {
        return ByteBufferUtil.getString(buf, len);
    }
}
