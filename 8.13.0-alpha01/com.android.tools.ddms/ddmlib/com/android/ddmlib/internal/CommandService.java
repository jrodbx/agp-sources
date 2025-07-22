/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.ddmlib.internal;

import com.android.ddmlib.Log;
import com.android.ddmlib.internal.commands.CommandResult;
import com.android.ddmlib.internal.commands.ICommand;
import java.io.EOFException;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Class to accept external connections and issue commands on a running ddmlib service. The format
 * of the incoming data is expected to match the format of adb commands String formatted as follows:
 * [Size(in hex 4 chars)][command]:[args] 001Cdisconnect Example:device-id:1234
 */
public class CommandService implements Runnable {

    private ServerSocketChannel listenChannel = null;
    private InetSocketAddress serverAddress = null;
    private boolean quit = true;
    private Thread runThread = null;
    private Timer startTimer = null;
    private final Map<String, ICommand> commandMap = new HashMap<>();
    private static final Long JOIN_TIMEOUT_MS = 5000L; // 5 seconds
    private static final Long RETRY_SERVER_MILLIS = 30_1000L; // 30 seconds

    public Integer getBoundPort() {
        if (listenChannel == null || listenChannel.socket() == null) {
            return -1;
        }
        return listenChannel.socket().getLocalPort();
    }

    private final Integer mListenPort;

    public CommandService(Integer mListenPort) {
        this.mListenPort = mListenPort;
    }

    public void addCommand(String command, ICommand handler) {
        commandMap.put(command, handler);
    }

    public void stop() {
        quit = true;

        if (listenChannel != null) {
            try {
                listenChannel.close();
                listenChannel.socket().close();
            } catch (IOException ex) {
                // Failed to close server socket.
                Log.w("CommandService", ex);
            }
        }
        // Wait until our run thread exits. This guarantees we can clean up all data used by
        // this thread without risk of threading issues.
        if (runThread != null) {
            try {
                runThread.join(JOIN_TIMEOUT_MS);
                if (runThread.isAlive()) {
                    Log.e(
                            "CommandService",
                            "Run thread still alive after " + JOIN_TIMEOUT_MS + "ms");
                }
            } catch (InterruptedException ex) {
                // Failed to wait for thread to stop.
                Log.w("CommandService", ex);
            }
        }

        listenChannel = null;
        runThread = null;
    }

    public void start() {
        if (startTimer == null) {
            startTimer = new Timer("CommandServiceTimer");
            startTimer.schedule(new ServerHostTimer(), 0, RETRY_SERVER_MILLIS);
        }
    }

    @Override
    public void run() {
        while (!quit) {
            try {
                if (listenChannel != null) {
                    try (SocketChannel client = listenChannel.accept()) {
                        if (client != null) {
                            processOneCommand(client);
                        }
                    }
                }
            } catch (IOException ex) {
                // threw an exception why calling select, log error and exit
                Log.e("CommandService", ex);
                return;
            }
        }
    }

    private void processOneCommand(SocketChannel client) throws IOException {
        ByteBuffer buffer = readExactly(client, 4);
        Integer cmdSize = Integer.parseInt(StandardCharsets.UTF_8.decode(buffer).toString(), 16);
        buffer = readExactly(client, cmdSize);
        String data = StandardCharsets.UTF_8.decode(buffer).toString();
        // Check if this command has any arguments, if not run command if
        // one matches.
        int commandTerminator = data.indexOf(":");
        if (commandTerminator == -1 && commandMap.containsKey(data)) {
            write(commandMap.get(data).run(null), client);
        } else if (commandTerminator != -1) {
            String command = data.substring(0, commandTerminator);
            String argsString = data.substring(commandTerminator + 1);
            if (!commandMap.containsKey(command)) {
                Log.w("CommandService", "Unknown command received");
                return;
            }
            try {
                write(commandMap.get(command).run(argsString), client);
            } catch (Throwable t) {
                Log.w("CommandService", t);
            }
        } else {
            Log.w("CommandService", "Failed to find command");
        }
    }

    private ByteBuffer readExactly(SocketChannel client, Integer amount) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(amount);
        while (buffer.hasRemaining()) {
            int count = client.read(buffer);
            if (count == -1) {
                throw new EOFException("Unexpected end of channel");
            }
        }
        buffer.position(0);
        return buffer;
    }

    private void write(CommandResult result, SocketChannel client) throws IOException {
        if (result.getSuccess()) {
            client.write(wrapString("OKAY"));
        } else {
            client.write(
                    wrapString(
                            String.format(
                                    "FAIL%04x%s",
                                    result.getMessage().length(), result.getMessage())));
        }
    }

    private ByteBuffer wrapString(String str) {
        return ByteBuffer.wrap(str.getBytes(StandardCharsets.UTF_8));
    }

    private class ServerHostTimer extends TimerTask {
        @Override
        public void run() {
            try {
                serverAddress =
                        new InetSocketAddress(
                                InetAddress.getByName("localhost"), // $NON-NLS-1$
                                mListenPort);
                listenChannel = ServerSocketChannel.open();
                listenChannel.socket().setReuseAddress(true); // enable SO_REUSEADDR
                listenChannel.socket().bind(serverAddress);
            } catch (BindException ex) {
                // A server is already running, setup timer to retry in X seconds.
                Log.i("CommandService", "Port is already bound");
                return;
            } catch (IOException ex) {
                // Failed to open server for unknown reason
                Log.e("CommandService", ex);
                return;
            }
            quit = false;
            runThread = new Thread(CommandService.this, "CommandServiceConnection");
            runThread.start();
            startTimer.cancel();
            startTimer = null;
        }
    }
}
