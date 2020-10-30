/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.ddmlib.jdwp.JdwpAgent;
import com.android.ddmlib.jdwp.JdwpExtension;
import com.android.ddmlib.jdwp.JdwpInterceptor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DdmJdwpExtension extends JdwpExtension {

    // For broadcasts to message handlers
    enum Event {
        // CLIENT_CONNECTED,
        CLIENT_READY,
        CLIENT_DISCONNECTED
    }

    @NonNull
    private final ConcurrentMap<Integer, ChunkHandler> mHandlerMap;

    public DdmJdwpExtension() {
        mHandlerMap = new ConcurrentHashMap<Integer, ChunkHandler>();
    }

    @Override
    public void intercept(@NonNull  Client client) {
        client.addJdwpInterceptor(new DdmInterceptor(client));
    }

    public void registerHandler(int type, @NonNull ChunkHandler handler) {
        mHandlerMap.putIfAbsent(type, handler);
    }

    void broadcast(Event event, @NonNull  Client client) {
        Log.d("ddms", "broadcast " + event + ": " + client);

        /*
         * The handler objects appear once in mHandlerMap for each message they
         * handle. We want to notify them once each, so we convert the HashMap
         * to a HashSet before we iterate.
         */
        HashSet<ChunkHandler> set = new HashSet<ChunkHandler>(mHandlerMap.values());
        for (ChunkHandler handler : set) {
            switch (event) {
                case CLIENT_READY:
                    try {
                        handler.clientReady(client);
                    } catch (IOException ioe) {
                        // Something failed with the client. It should
                        // fall out of the list the next time we try to
                        // do something with it, so we discard the
                        // exception here and assume cleanup will happen
                        // later. May need to propagate farther. The
                        // trouble is that not all values for "event" may
                        // actually throw an exception.
                        Log.w("ddms",
                                "Got exception while broadcasting 'ready'");
                        return;
                    }
                    break;
                case CLIENT_DISCONNECTED:
                    handler.clientDisconnected(client);
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
        }
    }

    void ddmSeen(@NonNull Client client) {
        // on first DDM packet received, broadcast a "ready" message
        if (!client.ddmSeen()) {
            broadcast(Event.CLIENT_READY, client);
        }
    }

    /**
     * Returns "true" if this JDWP packet has a JDWP command type.
     *
     * This never returns "true" for reply packets.
     * @param packet
     */
    static boolean isDdmPacket(JdwpPacket packet) {
        return !packet.isReply() && packet.is(ChunkHandler.DDMS_CMD_SET, ChunkHandler.DDMS_CMD);
    }

    public class DdmInterceptor extends JdwpInterceptor {

        @NonNull
        private final Client mClient;

        public DdmInterceptor(@NonNull  Client client) {
            mClient = client;
        }

        @Override
        public JdwpPacket intercept(@NonNull JdwpAgent agent, @NonNull JdwpPacket packet) {
            if (isDdmPacket(packet)) {
                ddmSeen(mClient);
                ByteBuffer buf = packet.getPayload();
                int type = buf.getInt(buf.position());
                ChunkHandler handler = mHandlerMap.get(type);

                if (handler == null) {
                    Log.w("ddms", "Received unsupported chunk type " + "ChunkHandler.name(type)");
                } else {
                    handler.handlePacket(mClient, packet);
                }
                return null;
            }
            return packet;
        }
    }
}
