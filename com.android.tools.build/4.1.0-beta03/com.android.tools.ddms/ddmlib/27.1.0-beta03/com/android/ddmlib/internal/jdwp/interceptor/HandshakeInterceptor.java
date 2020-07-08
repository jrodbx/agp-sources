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

import com.android.annotations.NonNull;
import com.android.ddmlib.JdwpHandshake;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.internal.jdwp.JdwpProxyClient;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

/**
 * This interceptor listens for the {@link JdwpHandshake}.
 *
 * Both the device and clients are in an uninitialized state until the handshake is complete. This interceptor helps ensure that no packets
 * are sent until a valid handshake between clients and devices are completed.
 *
 * This also means after a handshake is completed a new handshake request can put the client or the device into an undefined state. This
 * means when a new instance of DDMLIB connects and request a handshake for this client we return immediately and do not allow the request
 * to be sent to the device.
 * Example:
 * DDMLIB_1 -> JDWP-Handshake -> Proxy (First time for this client) -> JDWP-Handshake -> Device
 * Device -> JDWP-Handshake -> Proxy (Send to all clients expecting a handshake) -> JDWP-Handshake -> DDMLIB_1
 * DDMLIB_2 -> JDWP-Handshake -> Proxy (We have already done a handshake, reply with handshake) -> JDWP-Handshake -> DDMLIB_2
 *
 * The 2nd handshake only goes to the client that requested it. Also only one handshake was established with the device.
 */
public class HandshakeInterceptor implements Interceptor {
  private boolean mIsHandshakeSent = false;
  private boolean mIsExpectingHandshakeResponse = false;
  private final ByteBuffer mHandShakeResponse = ByteBuffer.allocate(JdwpHandshake.HANDSHAKE_LEN);
  private Set<JdwpProxyClient> mPendingClients = new HashSet<>();

  public HandshakeInterceptor() {
    JdwpHandshake.putHandshake(mHandShakeResponse);
  }

  @Override
  public boolean filterToDevice(@NonNull JdwpProxyClient from, @NonNull byte[] bufferToSend, int length)
    throws IOException, TimeoutException {
    ByteBuffer buffer = ByteBuffer.wrap(bufferToSend, 0, length);
    buffer.position(length);
    if (JdwpHandshake.findHandshake(buffer) != JdwpHandshake.HANDSHAKE_GOOD) {
      // Only filter handshake packets
      return false;
    }
    if (mIsHandshakeSent && !mIsExpectingHandshakeResponse) {
      // Reply to client the same as our initial handshake.
      from.setHandshakeComplete();
      from.write(mHandShakeResponse.array(), mHandShakeResponse.position());
      return true;
    }
    else if (mIsHandshakeSent) {
      mPendingClients.add(from);
      return true;
    }
    else {
      mIsHandshakeSent = true;
      mIsExpectingHandshakeResponse = true;
    }
    mPendingClients.add(from);
    return false;
  }

  @Override
  public boolean filterToClient(@NonNull JdwpProxyClient to, @NonNull byte[] bufferToSend, int length) {
    if (mPendingClients.isEmpty()) {
      return !to.isHandshakeComplete();
    }
    ByteBuffer buffer = ByteBuffer.wrap(bufferToSend, 0, length);
    buffer.position(length);
    if (mIsExpectingHandshakeResponse && JdwpHandshake.findHandshake(buffer) == JdwpHandshake.HANDSHAKE_GOOD) {
      mIsExpectingHandshakeResponse = false;
      // If the pending list contains this client then we don't filter the response.
      boolean filter = !mPendingClients.remove(to);
      to.setHandshakeComplete();
      return filter;
    }
    return false;
  }
}
