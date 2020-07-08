/*
 * Copyright (C) 2007 The Android Open Source Project
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

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * Handle the "app name" chunk (APNM).
 */
final class HandleAppName extends ChunkHandler {

    public static final int CHUNK_APNM = ChunkHandler.type("APNM");

    private static final HandleAppName mInst = new HandleAppName();


    private HandleAppName() {}

    /**
     * Register for the packets we expect to get from the client.
     */
    public static void register(MonitorThread mt) {
        mt.registerChunkHandler(CHUNK_APNM, mInst);
    }

    /**
     * Client is ready.
     */
    @Override
    public void clientReady(Client client) throws IOException {}

    /**
     * Client went away.
     */
    @Override
    public void clientDisconnected(Client client) {}

    /**
     * Chunk handler entry point.
     */
    @Override
    public void handleChunk(Client client, int type, ByteBuffer data,
            boolean isReply, int msgId) {

        Log.d("ddm-appname", "handling " + ChunkHandler.name(type));

        if (type == CHUNK_APNM) {
            assert !isReply;
            handleAPNM(client, data);
        } else {
            handleUnknownChunk(client, type, data, isReply, msgId);
        }
    }

    /*
     * Handle a reply to our APNM message.
     */
    private static void handleAPNM(Client client, ByteBuffer data) {
        int appNameLen;
        String appName;

        appNameLen = data.getInt();
        appName = ByteBufferUtil.getString(data, appNameLen);

        // Newer devices send user id in the APNM packet.
        Integer userId = null;
        if (data.hasRemaining()) {
            int dataRemaining = data.remaining();
            try {
                userId = data.getInt();
            } catch (BufferUnderflowException e) {
                Log.e("ddm-appname", "Insufficient data in APNM chunk to retrieve user id.");
                Log.e("ddm-appname", "Actual chunk length: " + dataRemaining);
                Log.e("ddm-appname", "Expected chunk length: 4"); // 4 bytes for userId int
            }
        }

        // Newer devices (newer than user id support) send the package names associated with the app.
        String packageName = Device.UNKNOWN_PACKAGE;
        if (data.hasRemaining()) {
            int dataRemaining = data.remaining();
            int packageNameLength = 0;
            try {
                packageNameLength = data.getInt();
                packageName = ByteBufferUtil.getString(data, packageNameLength);
            } catch (BufferUnderflowException e) {
                // one integer + utf-16 string
                int expectedChunkLength = 4 + packageNameLength * 2;

                Log.e("ddm-appname", "Insufficient data in APNM chunk to retrieve package name.");
                Log.e("ddm-appname", "Actual chunk length: " + dataRemaining);
                Log.e("ddm-appname", "Expected chunk length: " + expectedChunkLength);
            }
        }

        Log.d("ddm-appname", "APNM: app='" + appName + "'");

        ClientData.Names names = new ClientData.Names(appName, userId, packageName);

        ClientData cd = client.getClientData();
        cd.setNames(names);

        client = checkDebuggerPortForAppName(client, names.mProcessName);

        if (client != null) {
            client.update(Client.CHANGE_NAME);
        }
    }
 }

