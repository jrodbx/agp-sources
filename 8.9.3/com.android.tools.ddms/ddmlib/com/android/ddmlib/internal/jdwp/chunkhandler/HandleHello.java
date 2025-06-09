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

package com.android.ddmlib.internal.jdwp.chunkhandler;

import com.android.ddmlib.ByteBufferUtil;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.internal.MonitorThread;
import com.android.ddmlib.internal.ClientImpl;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/** Handle the "hello" chunk (HELO) and feature discovery. */
public final class HandleHello extends ChunkHandler {

    public static final int CHUNK_HELO = ChunkHandler.type("HELO");
    public static final int CHUNK_FEAT = ChunkHandler.type("FEAT");

    private static final HandleHello mInst = new HandleHello();

    private HandleHello() {}

    /** Register for the packets we expect to get from the client. */
    public static void register(MonitorThread mt) {
        mt.registerChunkHandler(CHUNK_HELO, mInst);
    }

    /** Client is ready. */
    @Override
    public void clientReady(ClientImpl client) {
        Log.d("ddm-hello", "Now ready: " + client);
    }

    /** Client went away. */
    @Override
    public void clientDisconnected(ClientImpl client) {
        Log.d("ddm-hello", "Now disconnected: " + client);
    }

    /**
     * Sends HELLO-type commands to the VM after a good handshake.
     *
     * @param client
     * @param serverProtocolVersion
     * @throws IOException
     */
    public static void sendHelloCommands(ClientImpl client, int serverProtocolVersion)
            throws IOException {
        sendHELO(client, serverProtocolVersion);
        sendFEAT(client);
        HandleProfiling.sendMPRQ(client);
    }

    /** Chunk handler entry point. */
    @Override
    public void handleChunk(
            ClientImpl client, int type, ByteBuffer data, boolean isReply, int msgId) {

        Log.d("ddm-hello", "handling " + ChunkHandler.name(type));

        if (type == CHUNK_HELO) {
            assert isReply;
            handleHELO(client, data);
        } else if (type == CHUNK_FEAT) {
            handleFEAT(client, data);
        } else {
            handleUnknownChunk(client, type, data, isReply, msgId);
        }
    }

    /*
     * Handle a reply to our HELO message.
     */
    private static void handleHELO(ClientImpl client, ByteBuffer data) {
        int version, pid, vmIdentLen, appNameLen;
        String vmIdent, processName;

        version = data.getInt();
        pid = data.getInt();
        vmIdentLen = data.getInt();
        appNameLen = data.getInt();

        vmIdent = ByteBufferUtil.getString(data, vmIdentLen);
        processName = ByteBufferUtil.getString(data, appNameLen);

        Log.d(
                "ddm-hello",
                String.format(
                        "HELO: v=%d, pid=%d, vm='%s', app='%s'",
                        version, pid, vmIdent, processName));

        // Newer devices send user id in the APNM packet.
        Integer userId = null;
        if (data.hasRemaining()) {
            try {
                userId = data.getInt();
            } catch (BufferUnderflowException e) {
                // five integers + two utf-16 strings
                int expectedPacketLength = 20 + appNameLen * 2 + vmIdentLen * 2;

                Log.e("ddm-hello", "Insufficient data in HELO chunk to retrieve user id.");
                Log.e("ddm-hello", "Actual chunk length: " + data.capacity());
                Log.e("ddm-hello", "Expected chunk length: " + expectedPacketLength);
            }
        }

        // check if the VM has reported information about the ABI
        boolean validAbi = false;
        String abi = null;
        if (data.hasRemaining()) {
            try {
                int abiLength = data.getInt();
                abi = ByteBufferUtil.getString(data, abiLength);
                validAbi = true;
            } catch (BufferUnderflowException e) {
                Log.e("ddm-hello", "Insufficient data in HELO chunk to retrieve ABI.");
            }
        }

        boolean hasJvmFlags = false;
        String jvmFlags = null;
        if (data.hasRemaining()) {
            try {
                int jvmFlagsLength = data.getInt();
                jvmFlags = ByteBufferUtil.getString(data, jvmFlagsLength);
                hasJvmFlags = true;
            } catch (BufferUnderflowException e) {
                Log.e("ddm-hello", "Insufficient data in HELO chunk to retrieve JVM flags");
            }
        }

        boolean nativeDebuggable = false;
        if (data.hasRemaining()) {
            try {
                byte nativeDebuggableByte = data.get();
                nativeDebuggable = nativeDebuggableByte == 1;
            } catch (BufferUnderflowException e) {
                Log.e("ddm-hello", "Insufficient data in HELO chunk to retrieve nativeDebuggable");
            }
        }

        String packageName = IDevice.UNKNOWN_PACKAGE;
        if (data.hasRemaining()) {
            try {
                int packageNameLength = data.getInt();
                packageName = ByteBufferUtil.getString(data, packageNameLength);
                Log.d("ddm-hello", String.format("HELO: pkg='%s'", packageName));
            } catch (BufferUnderflowException e) {
                Log.e("ddm-hello", "Insufficient data in HELO chunk to retrieve packageName");
            }
        }

        ClientData cd = client.getClientData();

        if (cd.getPid() == pid) {
            cd.setVmIdentifier(vmIdent);
            cd.setNames(new ClientData.Names(processName, userId, packageName));

            if (validAbi) {
                cd.setAbi(abi);
            }

            if (hasJvmFlags) {
                cd.setJvmFlags(jvmFlags);
            }

            cd.setNativeDebuggable(nativeDebuggable);
        } else {
            Log.e(
                    "ddm-hello",
                    "Received pid (" + pid + ") does not match client pid (" + cd.getPid() + ")");
        }

        if (client != null) {
            client.update(ClientImpl.CHANGE_NAME);
        }
    }

    /** Send a HELO request to the client. */
    public static void sendHELO(ClientImpl client, int serverProtocolVersion) throws IOException {
        ByteBuffer rawBuf = allocBuffer(4);
        JdwpPacket packet = new JdwpPacket(rawBuf);
        ByteBuffer buf = getChunkDataBuf(rawBuf);

        buf.putInt(serverProtocolVersion);

        finishChunkPacket(packet, CHUNK_HELO, buf.position());
        Log.d(
                "ddm-hello",
                "Sending " + name(CHUNK_HELO) + " ID=0x" + Integer.toHexString(packet.getId()));
        client.send(packet, mInst);
    }

    /** Handle a reply to our FEAT request. */
    private static void handleFEAT(ClientImpl client, ByteBuffer data) {
        int featureCount;
        int i;

        featureCount = data.getInt();
        for (i = 0; i < featureCount; i++) {
            int len = data.getInt();
            String feature = ByteBufferUtil.getString(data, len);
            client.getClientData().addFeature(feature);

            Log.d("ddm-hello", "Feature: " + feature);
        }
    }

    /** Send a FEAT request to the client. */
    public static void sendFEAT(ClientImpl client) throws IOException {
        ByteBuffer rawBuf = allocBuffer(0);
        JdwpPacket packet = new JdwpPacket(rawBuf);
        ByteBuffer buf = getChunkDataBuf(rawBuf);

        // no data

        finishChunkPacket(packet, CHUNK_FEAT, buf.position());
        Log.d("ddm-heap", "Sending " + name(CHUNK_FEAT));
        client.send(packet, mInst);
    }
}

