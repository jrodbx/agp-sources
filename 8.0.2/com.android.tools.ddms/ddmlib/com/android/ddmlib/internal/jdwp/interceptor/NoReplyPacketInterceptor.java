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

import com.android.annotations.NonNull;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.internal.jdwp.JdwpProxyClient;
import com.android.ddmlib.internal.jdwp.chunkhandler.JdwpPacket;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This interceptor captures packets sent by the device that are not in response to a request. An
 * example of this type of packet is the APNM packet. The APNM packet will be sent when the device
 * wants to inform clients of an updated app package name. This information is not requested by
 * clients first so this interceptor needs to cache the response and send the data to new
 * connections after the handshake is completed.
 * <p>
 * Example:
 * DDMLIB_1 -> JDWP-Handshake -> Proxy -> JDWP-Handshake -> Device
 * DDMLIB_2 Connects to proxy
 * Device -> JDWP-Handshake -> Proxy -> DDMLIB_1
 * Device -> APNM -> Proxy -> APNM -> DDMLIB_1
 * DDMLIB_2 -> JDWP-Handshake -> Proxy (Send handshake as well as cached no-reply packets)
 *                                      -> JDWP-Handshake -> DDMLIB_2
 *                                      -> APNM -> DDMLIB_2
 *
 * Note: If these packets are sent to the client before the handshake they are discarded as invalid packets.
 * </p>
 */
public class NoReplyPacketInterceptor implements Interceptor {
  private List<ByteBuffer> mCachedPackets = new ArrayList<>();
  private Set<JdwpProxyClient> mClientsSentCacheTo = new HashSet<>();

  @VisibleForTesting
  List<ByteBuffer> getCachedPackets() {
    return mCachedPackets;
  }

  @VisibleForTesting
  Set<JdwpProxyClient> getClientsSentCacheTo() {
    return mClientsSentCacheTo;
  }

  @Override
  public boolean filterToClient(@NonNull JdwpProxyClient to, @NonNull JdwpPacket packetToSend) throws IOException, TimeoutException {
    if (to.isHandshakeComplete() && !mClientsSentCacheTo.contains(to)) {
      sendCacheToClient(to);
    }
    if (packetToSend.isEmpty() || packetToSend.isError() || packetToSend.isReply()) {
      return false;
    }
    ByteBuffer buffer = ByteBuffer.allocate(packetToSend.getLength());
    buffer.order(CHUNK_ORDER);
    packetToSend.copy(buffer);
    mCachedPackets.add(buffer);
    return !to.isHandshakeComplete();
  }

  private void sendCacheToClient(JdwpProxyClient client) throws IOException, TimeoutException {
    for (ByteBuffer packet : mCachedPackets) {
      client.write(packet.array(), packet.position());
    }
    mClientsSentCacheTo.add(client);
    mClientsSentCacheTo.removeIf(c -> !c.isConnected());
  }
}
