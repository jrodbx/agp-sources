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


import com.android.annotations.NonNull;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.AdbHelper;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.JdwpHandshake;
import com.android.ddmlib.Log;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.internal.jdwp.chunkhandler.JdwpPacket;
import com.android.ddmlib.internal.jdwp.interceptor.ClientInitializationInterceptor;
import com.android.ddmlib.internal.jdwp.interceptor.DebuggerInterceptor;
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
    private JdwpConnectionReader mReader;
    private boolean isHandshakeComplete = false;

    public JdwpClientManager(@NonNull JdwpClientManagerId id, @NonNull Selector selector)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        this(
                AdbHelper.createPassThroughConnection(
                        AndroidDebugBridge.getSocketAddress(), id.deviceSerial, id.pid));
        mAdbSocket.configureBlocking(false);
        mAdbSocket.register(selector, SelectionKey.OP_READ, this);
    }

    @VisibleForTesting
    JdwpClientManager(@NonNull SocketChannel socket)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        mReader = new JdwpConnectionReader(socket, 1024);
        mAdbSocket = socket;
        mInterceptors.add(new NoReplyPacketInterceptor());
        mInterceptors.add(new ClientInitializationInterceptor());
        mInterceptors.add(new DebuggerInterceptor());
        // Send handshake as soon as a connection has been established.
        // Handshakes are a special packet that do not conform to the JDWP packet format.
        sendHandshake();
    }

    private void sendHandshake() throws IOException, TimeoutException {
        ByteBuffer handshake = ByteBuffer.allocate(JdwpHandshake.HANDSHAKE_LEN);
        JdwpHandshake.putHandshake(handshake);
        writeRaw(handshake);
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

    /**
     * This read loop deals only with JDWP packets. The JdwpConnectionReader is responsible for
     * reading the data from the network stream and returning a full JDWPPacket.
     * When a full packet is received the packet is tested against each of the interceptors then
     * sent onto each client as needed.
     */
    @Override
    public void read() throws IOException, TimeoutException {
        if (mAdbSocket == null) {
            return;
        }
        // Read data into the readers internal buffer.
        int length = mReader.read();
        if (length == -1) {
            shutdown();
            throw new EOFException("Client disconnected");
        }

        // Need to test for the handshake in a loop due to bug (b/178655046),
        // fixed in S+ by (aosp/1569323)
        while (!isHandshakeComplete) {
            if (mReader.isHandshake()) {
                mReader.consumeData(JdwpHandshake.HANDSHAKE_LEN);
                isHandshakeComplete = true;
                break;
            }
            if (!mReader.isAPNMPacket()) {
                Log.e("DDMLIB", "An unexpected packet was received before the handshake.");
                return;
            }
            mReader.consumePacket();
        }
        // Loop the readers buffer processing any packets returned.
        JdwpPacket packet;
        while ((packet = mReader.readPacket()) != null) {
            ByteBuffer sendBuffer = ByteBuffer.allocate(packet.getLength());
            packet.copy(sendBuffer);
            for (JdwpProxyClient client : mClients) {
                if (!filterToClient(client, packet)) {
                    // Send the data to the client.
                    client.write(sendBuffer.array(), sendBuffer.position());
                }
            }
            packet.consume();
        }
    }

    void write(JdwpProxyClient from, JdwpPacket packet) throws IOException, TimeoutException {
        if (mAdbSocket == null) {
            return;
        }
        if (!filterToDevice(from, packet)) {
            ByteBuffer sendBuffer = ByteBuffer.allocate(packet.getLength());
            packet.copy(sendBuffer);
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

    private boolean filterToClient(JdwpProxyClient client, JdwpPacket packet) throws IOException, TimeoutException {
        boolean filter = false;
        for (Interceptor interceptor : mInterceptors) {
            filter |= interceptor.filterToClient(client, packet);
        }
        return filter;
    }
}
