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
import com.android.ddmlib.internal.jdwp.JdwpProxyClient;
import com.android.ddmlib.internal.jdwp.chunkhandler.JdwpPacket;
import com.android.ddmlib.jdwp.JdwpCommands;

/**
 * This interceptor attempts to learn when a debugger attaches to the device. When a debugger is attached it is assumed that request from
 * other clients are invalid as such all data being sent to the device from clients outside the one with a debugger attached are ignored.
 * Likewise all data from the device is sent too only the client that has the debugger attached.
 */
public class DebuggerInterceptor implements Interceptor {
  private JdwpProxyClient mAttachedClient;

  @Override
  public boolean filterToDevice(@NonNull JdwpProxyClient from, @NonNull JdwpPacket packet) {
    if (packet.is(JdwpCommands.SET_VM, JdwpCommands.CMD_VM_IDSIZES)) {
      // New debugger attached, block traffic to/from other clients
      mAttachedClient = from;
    }
    if (mAttachedClient != null && !mAttachedClient.isConnected()) {
      mAttachedClient = null;
    }
    return mAttachedClient != null && mAttachedClient != from;
  }

  @Override
  public boolean filterToClient(@NonNull JdwpProxyClient to, @NonNull JdwpPacket packet) {
    return !(mAttachedClient == null || mAttachedClient == to);
  }
}
