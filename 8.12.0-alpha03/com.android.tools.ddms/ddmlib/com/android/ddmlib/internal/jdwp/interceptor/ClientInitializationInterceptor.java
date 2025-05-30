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

package com.android.ddmlib.internal.jdwp.interceptor;

import static com.android.ddmlib.internal.jdwp.chunkhandler.ChunkHandler.CHUNK_ORDER;
import static com.android.ddmlib.internal.jdwp.chunkhandler.HandleHeap.CHUNK_HPIF;
import static com.android.ddmlib.internal.jdwp.chunkhandler.HandleHeap.CHUNK_REAQ;
import static com.android.ddmlib.internal.jdwp.chunkhandler.HandleHello.CHUNK_FEAT;
import static com.android.ddmlib.internal.jdwp.chunkhandler.HandleHello.CHUNK_HELO;
import static com.android.ddmlib.internal.jdwp.chunkhandler.HandleProfiling.CHUNK_MPRQ;

import com.android.annotations.NonNull;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.internal.jdwp.JdwpProxyClient;
import com.android.ddmlib.internal.jdwp.chunkhandler.JdwpPacket;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Interceptor that is responsible for caching and fixing reply packet ids.
 *
 * <p>Because the device manages internal state specific packets being sent at an unexpected time
 * may cause undefined behavior. This interceptor listens for various request from clients and
 * either forwards the request on to the device or replies with a cached response previously
 * captured from device.
 *
 * <p>This allows the device to talk to the proxy as a single client. While at the same time
 * allowing multiple instances of DDMLIB to treat the proxy as a single device client. Example
 * DDMLIB_1 -> HELO -> ClientInterceptor (First time seeing HELO) -> HELO -> DEVICE DDMLIB_2 -> HELO
 * -> ClientInterceptor (I am waiting on a response do nothing) DEVICE -> HELO -> ClientInterceptor
 * (2 instances are waiting for a response) -> HELO -> DDMLIB_1 -> HELO -> DDMLIB_2 ... Some time
 * later ... DDMLIB_3 -> HELO -> ClientInterceptor (I have a response already send cache) -> HELO ->
 * DDMLIB_3
 *
 * <p>Some things to note in the example above, the device only sees HELO 1 time no matter how many
 * instances connect. Responses to all request tracked by this interceptor are only sent back as
 * replies to instances that request them.
 */
public class ClientInitializationInterceptor implements Interceptor {
    private static int PACKET_ID_OFFSET = 0x04;
    // This set contains the packet type of packets to capture and cache the device response.
    private final Set<Integer> mCachePacketFilter = new HashSet<>();
    // This set contains the packet type of packets to capture and fix the packet id before sending.
    private final Set<Integer> mReplyPacketFilter = new HashSet<>();
    private final Map<Integer, byte[]> mCachedPackets = new HashMap<>();
    private final HashMap<Integer, Set<ClientRequestId>> mPendingPackets = new HashMap<>();

    private static class ClientRequestId {
        public JdwpProxyClient client; // Client making the request.
        public int requestId; // Id of the requested packet.

        ClientRequestId(JdwpProxyClient client, int requestId) {
            this.client = client;
            this.requestId = requestId;
        }
    }

    public ClientInitializationInterceptor() {
        // Note: mCachePacketFilter and mReplyPacketFilter should not have overlapping entries
        // if an entry does overlap the behavior of the mCachePacketFilter will win.
        mCachePacketFilter.add(CHUNK_HELO);
        mCachePacketFilter.add(CHUNK_FEAT);
        // The following commands are status request for a client and do not need to be
        // cached to support a multi-client workflow.
        mReplyPacketFilter.add(CHUNK_MPRQ);
        mReplyPacketFilter.add(CHUNK_HPIF);
        mReplyPacketFilter.add(CHUNK_REAQ);

    }

    @Override
    public boolean filterToDevice(@NonNull JdwpProxyClient from, @NonNull JdwpPacket packet) throws IOException, TimeoutException {
        if (packet.isEmpty() || packet.isError() || packet.getLength() < JdwpPacket.JDWP_HEADER_LEN + 4) {
            return false;
        }
        ByteBuffer payload = packet.getPayload();
        int type = payload.getInt();
        if (!(mCachePacketFilter.contains(type) || mReplyPacketFilter.contains(type))) {
            return false;
        }
        if (mCachedPackets.containsKey(type)) {
            sendCachedPacket(from, type, packet.getId());
            return true;
        }
        boolean alreadyPending = mPendingPackets.containsKey(type);
        mPendingPackets
                .computeIfAbsent(type, (key) -> new HashSet<>())
                .add(new ClientRequestId(from, packet.getId()));
        return alreadyPending;
    }

    @Override
    public boolean filterToClient(@NonNull JdwpProxyClient to, @NonNull JdwpPacket packet)
            throws IOException, TimeoutException {
        // Note: reply packets don't have the command / command set values initialized.
        if (!packet.isReply()
                || packet.isEmpty()
                || packet.isError()
                || packet.getLength() < JdwpPacket.JDWP_HEADER_LEN + 4) {
            return false;
        }

        ByteBuffer payload = packet.getPayload();
        int type = payload.getInt();
        if (!mPendingPackets.containsKey(type)) {
            return false;
        }
        if (mCachePacketFilter.contains(type)) {
            ByteBuffer buffer = ByteBuffer.allocate(packet.getLength());
            buffer.order(CHUNK_ORDER);
            packet.copy(buffer);
            mCachedPackets.put(type, buffer.array());
            for (ClientRequestId pending : mPendingPackets.get(type)) {
                sendCachedPacket(pending.client, type, pending.requestId);
            }
        } else if (mReplyPacketFilter.contains(type)) {
            for (ClientRequestId pending : mPendingPackets.get(type)) {
                sendPacketWithUpdatedPacketId(pending.client, pending.requestId, packet);
            }
        }
        mPendingPackets.remove(type);
        return true;
    }

    // Helper function to send cached packets. This helper replaces the packet id with the reply
    // packet id expected by the ddmlib client.
    // Packet ids are used as a mapping between request/reply packets.
    private void sendCachedPacket(JdwpProxyClient to, int type, int id)
            throws IOException, TimeoutException {
        ByteBuffer buffer = ByteBuffer.wrap(mCachedPackets.get(type));
        buffer.order(CHUNK_ORDER);
        buffer.putInt(PACKET_ID_OFFSET, id);
        to.write(buffer.array(), buffer.limit());
    }
    // Helper function to reply to specific clients with the expected packet id. Packet ids are used
    // to map reply packets
    // to a request. This is needed because a reply packet does not have the SET / CMD_SET flags of
    // a packet set.
    private static void sendPacketWithUpdatedPacketId(JdwpProxyClient to, int id, JdwpPacket packet)
            throws IOException, TimeoutException {
        ByteBuffer buffer = ByteBuffer.allocate(packet.getLength());
        buffer.order(CHUNK_ORDER);
        packet.copy(buffer);
        buffer.putInt(PACKET_ID_OFFSET, id);
        to.write(buffer.array(), buffer.limit());
    }
}
