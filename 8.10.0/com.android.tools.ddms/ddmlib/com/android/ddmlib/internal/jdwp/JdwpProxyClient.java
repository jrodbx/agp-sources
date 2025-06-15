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
import com.android.ddmlib.AdbHelper;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.JdwpHandshake;
import com.android.ddmlib.Log;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.internal.jdwp.chunkhandler.JdwpPacket;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

/**
 * This class represents the connection between {@link com.android.ddmlib.internal.ClientImpl}. and
 * ADB. Constructed by the {@link JdwpProxyServer} each time a new connection is made by DDMLIB.
 * When DDMLIB issues track device and track-jdwp calls for specific PID's this class is responsible
 * for sending and receiving data between DDMLIB and ADB.
 *
 * <p>Because multiple instances of DDMLIB can be running at the same time each connected to the
 * {@link JdwpProxyServer} it is important that the client doesn't interfear with the presumed jdwp
 * state of the client on the device. To manage this a key is created from the track device and
 * track jdwp calls. That is is used to construct or get a {@link JdwpClientManager} unique to
 * managing all JdwpProxyClient's tracking the same pid and the same device.
 */
public class JdwpProxyClient implements JdwpSocketHandler {

    private SocketChannel mClientImplSocket;

    private String mDeviceId = null;

    private int mPid = 0;

    private JdwpClientManager mConnection;

    private boolean mHandshakeComplete = false;

    private JdwpConnectionReader mReader;

    private final JdwpClientManagerFactory mFactory;

    JdwpProxyClient(@NonNull SocketChannel socket, @NonNull JdwpClientManagerFactory factory) {
        mClientImplSocket = socket;
        mFactory = factory;
        mReader = new JdwpConnectionReader(mClientImplSocket, 1024);
    }

    public boolean isConnected() {
        return mClientImplSocket != null;
    }

    @Override
    public void shutdown() throws IOException {
        if (mClientImplSocket != null) {
            mClientImplSocket.close();
            mClientImplSocket = null;
        }
        if (mConnection != null) {
            mConnection.removeListener(this);
            mConnection = null;
        }
    }

    @Override
    public void read() throws IOException, TimeoutException {
        if (mClientImplSocket == null) {
            return;
        }
        int length = mReader.read();
        if (length == -1) {
            shutdown();
            throw new EOFException("Client Disconnected");
        }
        // If we are tracking a new host grab the device id and return OKAY.
        // Else if we are tracking a new client, grab the pid and register with an
        // associated JdwpClientManager
        // Else send packets to the JdwpClientManager and let it sort it out.
        if (mDeviceId == null && mReader.isHostTransport()) {
            DdmCommandPacket packet = mReader.parseCommandPacket();
            mDeviceId = packet.getCommand().substring(AdbHelper.HOST_TRANSPORT.length());
            write("OKAY");
            mReader.consumeData(packet.getTotalSize());
        } else if (mPid == 0 && mReader.isJdwpPid()) {
            DdmCommandPacket packet = mReader.parseCommandPacket();
            mPid = Integer.parseInt(packet.getCommand().substring("jdwp:".length()));
            JdwpClientManagerId key = new JdwpClientManagerId(mDeviceId, mPid);
            try {
                mConnection = mFactory.createConnection(key);
                mConnection.addListener(this);
                write("OKAY");
            } catch (Exception ex) {
                writeFailHelper(ex.getMessage());
                shutdown();
            }
            mReader.consumeData(packet.getTotalSize());
        } else if (mReader.isDisconnect()) {
            DdmCommandPacket packet = mReader.parseCommandPacket();
            try {
                String[] params = packet.getCommand().split(":");
                JdwpClientManager clientManager =
                        mFactory.getConnection(params[1], Integer.parseInt(params[2]));
                if (clientManager == null) {
                    writeFailHelper(
                            "Unable to find client matching: " + params[1] + " / " + params[2]);
                } else {
                    write("OKAY");
                    clientManager.shutdown();
                }
            } catch (Exception ex) {
                writeFailHelper(ex.getMessage());
            }
            mReader.consumeData(packet.getTotalSize());
        } else if (mReader.isHandshake()) {
            ByteBuffer handshake = ByteBuffer.allocate(JdwpHandshake.HANDSHAKE_LEN);
            JdwpHandshake.putHandshake(handshake);
            write(handshake.array(), handshake.position());
            setHandshakeComplete();
            mReader.consumeData(JdwpHandshake.HANDSHAKE_LEN);
        } else if (!isHandshakeComplete()) {
            // If we get here it means we read data from the buffer, however that data was not
            // the handshake. We consume whatever packet read and throw it away until we establish
            // the handshake.
            Log.e("DDMLIB", "An unexpected packet was received before the handshake.");
            mReader.consumePacket();
        } else if (mConnection != null) {
            JdwpPacket packet;
            while ((packet = mReader.readPacket()) != null) {
                mConnection.write(this, packet);
                packet.consume();
            }
        }
    }

    private void writeFailHelper(String message) throws IOException, TimeoutException {
        write("FAIL");
        byte[] reason = AdbHelper.formAdbRequest(message);
        write(reason, reason.length);
    }

    public boolean isHandshakeComplete() {
        return mHandshakeComplete;
    }

    /**
     * The handshake is special, APNM packets before the handshake get discarded. So the {@link
     * com.android.ddmlib.internal.jdwp.interceptor.NoReplyPacketInterceptor} needs to know when it
     * is safe to send the appname and other no reply packets. This is only safe when the handshake
     * is complete.
     */
    public void setHandshakeComplete() {
        mHandshakeComplete = true;
    }

    public void write(byte[] data, int length) throws IOException, TimeoutException {
        JdwpLoggingUtils.log("CLIENT", "WRITE", data, length);
        AdbHelper.write(mClientImplSocket, data, length, DdmPreferences.getTimeOut());
    }

    private void write(String value) throws IOException, TimeoutException {
        write(value.getBytes(Charset.defaultCharset()), value.length());
    }
}
