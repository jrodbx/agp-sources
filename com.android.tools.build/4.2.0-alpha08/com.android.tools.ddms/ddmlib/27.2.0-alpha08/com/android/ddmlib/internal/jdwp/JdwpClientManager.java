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

package com.android.ddmlib.internal.jdwp;

import static com.android.ddmlib.internal.jdwp.chunkhandler.JdwpPacket.JDWP_HEADER_LEN;

import com.android.annotations.NonNull;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.AdbHelper;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.internal.jdwp.chunkhandler.BadPacketException;
import com.android.ddmlib.internal.jdwp.chunkhandler.JdwpPacket;
import com.android.ddmlib.internal.jdwp.interceptor.ClientInitializationInterceptor;
import com.android.ddmlib.internal.jdwp.interceptor.DebuggerInterceptor;
import com.android.ddmlib.internal.jdwp.interceptor.HandshakeInterceptor;
import com.android.ddmlib.internal.jdwp.interceptor.Interceptor;
import com.android.ddmlib.internal.jdwp.interceptor.NoReplyPacketInterceptor;
import com.google.common.annotations.VisibleForTesting;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manager for multiple {@link JdwpProxyClient}'s connected to a single device and client.
 * This class is responsible for running {@link Interceptor}s and determining which data will be
 * send to the device and which data will be sent to each client.
 */
public class JdwpClientManager implements JdwpSocketHandler {
    /**
     * Callback for when the connection is terminated and the manager is shutdown.
     */
    interface ShutdownListener {
        void shutdown();
    }

    private SocketChannel mAdbSocket;
    private final Set<JdwpProxyClient> mClients = new HashSet<>();
    private final List<Interceptor> mInterceptors = new ArrayList<>();
    private final List<ShutdownListener> mShutdownListeners = new ArrayList<>();
    private final byte[] mBuffer;
    private final byte[] mSendBuffer = new byte[1024 * 1024];

    public JdwpClientManager(@NonNull JdwpClientManagerId id, @NonNull Selector selector, @NonNull byte[] readBuffer)
      throws TimeoutException, AdbCommandRejectedException, IOException {
        this.mBuffer = readBuffer;
        mAdbSocket = AdbHelper.createPassThroughConnection(AndroidDebugBridge.getSocketAddress(), id.deviceSerial, id.pid);
        mAdbSocket.configureBlocking(false);
        mAdbSocket.register(selector, SelectionKey.OP_READ, this);
        mInterceptors.add(new NoReplyPacketInterceptor());
        mInterceptors.add(new HandshakeInterceptor());
        mInterceptors.add(new ClientInitializationInterceptor());
        mInterceptors.add(new DebuggerInterceptor());
    }

    void addListener(JdwpProxyClient client) {
        mClients.add(client);
    }

    void removeListener(JdwpProxyClient client) {
        mClients.remove(client);
    }

    void addShutdownListener(ShutdownListener listener) {
        mShutdownListeners.add(listener);
    }

    @VisibleForTesting
    void addInterceptor(Interceptor interceptor) {
        mInterceptors.add(interceptor);
    }

    @Override
    public void shutdown() throws IOException {
        for (ShutdownListener listener : mShutdownListeners) {
            listener.shutdown();
        }
        mShutdownListeners.clear();

        while (!mClients.isEmpty()) {
            // Can't foreach due to concurrency modification exception.
            // Shutdown removes the client from the list.
            JdwpProxyClient client = mClients.iterator().next();
            client.shutdown();
            // Some test use mocks. So when shutdown is called the client isn't removed properly. This ensures that even in the test
            // case clients are removed from the client list.
            if (mClients.contains(client)) {
                mClients.remove(client);
            }
        }
        if (mAdbSocket != null) {
            mAdbSocket.close();
            mAdbSocket = null;
        }
    }

    @Override
    public void read() throws IOException, TimeoutException {
        if (mAdbSocket == null) {
            return;
        }
        ByteBuffer buffer = ByteBuffer.wrap(mBuffer);
        int length = mAdbSocket.read(buffer);
        if (length == -1) {
            shutdown();
            throw new EOFException("Client disconnected");
        }
        JdwpLoggingUtils.log("DEVICE", "READ", mBuffer, length);
        // First determine if we have a jdwp packet
        boolean isJdwpPacket = true;
        for (JdwpProxyClient client : mClients) {
            if (filterToClient(client, mBuffer, length)) {
                continue;
            }

            try {
                JdwpPacket packet;
                buffer = ByteBuffer.wrap(mBuffer, 0, length);
                buffer.position(length);
                if (isJdwpPacket && length >= JDWP_HEADER_LEN && (packet = JdwpPacket.findPacket(buffer)) != null) {
                    ByteBuffer sendBuffer = ByteBuffer.wrap(mSendBuffer);
                    do {
                        if (!filterToClient(client, packet)) {
                            packet.move(sendBuffer);
                        }
                        packet.consume();
                    }
                    while ((packet = JdwpPacket.findPacket(buffer)) != null);
                    // We didn't loop through the entire buffer this is probably not a jdwp packet
                    // buffer.
                    // This can happen when the debugger is loading symbols as some symbols can look
                    // like a packet.
                    if (buffer.position() != 0) {
                        isJdwpPacket = false;
                    } else if (sendBuffer.position() != 0) {
                        client.write(mSendBuffer, sendBuffer.position());
                    }
                }
                else {
                    isJdwpPacket = false;
                }
            }
            catch (BadPacketException ex) {
                isJdwpPacket = false;
            }
            if (!isJdwpPacket) {
                client.write(mBuffer, length);
            }
        }
    }

    void write(JdwpProxyClient from, byte[] value, int length) throws IOException, TimeoutException {
        if (mAdbSocket == null || filterToDevice(from, value, length)) {
            return;
        }

        ByteBuffer buffer = ByteBuffer.wrap(value, 0, length);
        buffer.position(length);
        boolean isJdwpPacket;
        ByteBuffer sendBuffer = ByteBuffer.wrap(mSendBuffer);
        try {
            JdwpPacket packet;
            if ((packet = JdwpPacket.findPacket(buffer)) != null) {
                isJdwpPacket = true;
                do {
                    if (!filterToDevice(from, packet)) {
                        packet.move(sendBuffer);
                    }
                    // Consume packet and read next packet if one exists
                    // We always read from end of buffer.
                    packet.consume();
                }
                while ((packet = JdwpPacket.findPacket(buffer)) != null);
            }
            else {
                isJdwpPacket = false;
            }
        }
        catch (BadPacketException ex) {
            isJdwpPacket = false;
        }
        if (!isJdwpPacket) {
            // If we don't have a valid jdwp packet just send all the data.
            sendBuffer.put(value, 0, length);
        }
        if (sendBuffer.position() != 0) {
            writeRaw(sendBuffer);
        }
    }

    @VisibleForTesting
    void writeRaw(ByteBuffer sendBuffer) throws IOException, TimeoutException {
        JdwpLoggingUtils.log("DEVICE", "WRITE", sendBuffer.array(), sendBuffer.position());
        AdbHelper.write(mAdbSocket, sendBuffer.array(), sendBuffer.position(), DdmPreferences.getTimeOut());
    }

    private boolean filterToDevice(JdwpProxyClient client, JdwpPacket packet) throws IOException, TimeoutException {
        boolean filter = false;
        for (Interceptor interceptor : mInterceptors) {
            filter |= interceptor.filterToDevice(client, packet);
        }
        return filter;
    }

    private boolean filterToDevice(JdwpProxyClient client, byte[] value, int length) throws IOException, TimeoutException {
        boolean filter = false;
        for (Interceptor interceptor : mInterceptors) {
            filter |= interceptor.filterToDevice(client, value, length);
        }
        return filter;
    }

    private boolean filterToClient(JdwpProxyClient client, JdwpPacket packet) throws IOException, TimeoutException {
        boolean filter = false;
        for (Interceptor interceptor : mInterceptors) {
            filter |= interceptor.filterToClient(client, packet);
        }
        return filter;
    }

    private boolean filterToClient(JdwpProxyClient client, byte[] value, int length) throws IOException, TimeoutException {
        boolean filter = false;
        for (Interceptor interceptor : mInterceptors) {
            filter |= interceptor.filterToClient(client, value, length);
        }
        return filter;
    }
}
