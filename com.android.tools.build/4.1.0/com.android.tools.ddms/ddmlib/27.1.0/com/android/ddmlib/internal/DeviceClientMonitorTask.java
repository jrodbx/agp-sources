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

package com.android.ddmlib.internal;

import com.android.annotations.NonNull;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.AdbHelper;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.internal.jdwp.chunkhandler.HandleHello;
import com.google.common.util.concurrent.Uninterruptibles;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * This class is responsible for managing debuggable clients for a registered device. When a device
 * is registered a connection is established to the adb host then command {@link
 * ADB_TRACK_JDWP_COMMAND} is sent. This command informs adb to monitor and send state about each
 * debuggable client. This service then creates a ClientImpl that represents a debuggable process on
 * the device establishing a handshake and sending a {@link HandleHello.CHUNK_HELO} packet.
 *
 * <p>Note that this class tracks {@link com.android.ddmlib.Client}s for all devices tied to an adb
 * host. Devices are keyed off of the given {@link Socketchannel} connection.
 */
class DeviceClientMonitorTask implements Runnable {
    private static final String ADB_TRACK_JDWP_COMMAND = "track-jdwp";
    private volatile boolean mQuit;
    @NonNull private final Selector mSelector;
    // Note that mChannelsToRegister is not synchronized other than through the compute* interface.
    // Therefore, make sure atomic operations are done via that extended API.
    private final ConcurrentHashMap<SocketChannel, DeviceImpl> mChannelsToRegister =
            new ConcurrentHashMap<>();
    private final Set<ClientImpl> mClientsToReopen = new HashSet<>();

    DeviceClientMonitorTask() throws IOException {
        mSelector = Selector.open();
    }

    /**
     * Starts monitoring for debuggable clients on a given device.
     *
     * @param device the device to monitor.
     * @return true if success.
     */
    boolean register(@NonNull DeviceImpl device) {
        SocketChannel socketChannel;
        try {
            socketChannel = AndroidDebugBridge.openConnection();
        } catch (IOException exception) {
            Log.e(
                    "DeviceClientMonitorTask",
                    "Unable to open connection to ADB server: " + exception);
            return false;
        }
        if (socketChannel != null) {
            try {
                boolean result = sendDeviceMonitoringRequest(socketChannel, device);
                if (result) {
                    device.setClientMonitoringSocket(socketChannel);
                    socketChannel.configureBlocking(false);
                    mChannelsToRegister.put(socketChannel, device);
                    mSelector.wakeup();

                    return true;
                }
            } catch (TimeoutException e) {
                try {
                    // attempt to close the socket if needed.
                    socketChannel.close();
                } catch (IOException e1) {
                    // we can ignore that one. It may already have been closed.
                }
                Log.d(
                        "DeviceClientMonitorTask",
                        "Connection Failure when starting to monitor device '"
                                + device
                                + "' : timeout");
            } catch (AdbCommandRejectedException e) {
                try {
                    // attempt to close the socket if needed.
                    socketChannel.close();
                } catch (IOException e1) {
                    // we can ignore that one. It may already have been closed.
                }
                Log.d(
                        "DeviceClientMonitorTask",
                        "Adb refused to start monitoring device '"
                                + device
                                + "' : "
                                + e.getMessage());
            } catch (IOException e) {
                try {
                    // attempt to close the socket if needed.
                    socketChannel.close();
                } catch (IOException e1) {
                    // we can ignore that one. It may already have been closed.
                }
                Log.d(
                        "DeviceClientMonitorTask",
                        "Connection Failure when starting to monitor device '"
                                + device
                                + "' : "
                                + e.getMessage());
            }
        }

        return false;
    }

    void registerClientToDropAndReopen(ClientImpl client) {
        synchronized (mClientsToReopen) {
            Log.d(
              "DeviceClientMonitorTask",
              "Adding " + client + " to list of client to reopen (" + client.getDebuggerListenPort() + ").");
            mClientsToReopen.add(client);
        }
        mSelector.wakeup();
    }

    void free(@NonNull ClientImpl client) { }

    private void processDropAndReopenClients() {
        synchronized (mClientsToReopen) {
            MonitorThread monitorThread = MonitorThread.getInstance();
            for (ClientImpl client : mClientsToReopen) {
                DeviceImpl device = (DeviceImpl) client.getDevice();
                int pid = client.getClientData().getPid();

                monitorThread.dropClient(client, false /* notify */);

                // This is kinda bad, but if we don't wait a bit, the client
                // will never answer the second handshake!
                Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
                Log.d("DeviceClientMonitorTask", "Reopening " + client);
                openClient(device, pid, monitorThread);
                device.update(IDevice.CHANGE_CLIENT_LIST);
            }
            mClientsToReopen.clear();
        }
    }

    /** Registers track-jdwp key with the corresponding device's socket channel's selector. */
    void processChannelsToRegister() {
        // register any new channels
        mChannelsToRegister
                .entrySet()
                .removeIf(
                        entry -> {
                            try {
                                entry.getKey()
                                        .register(
                                                mSelector, SelectionKey.OP_READ, entry.getValue());
                            } catch (ClosedChannelException e) {
                                // We'll remove the channel if there's an error. We most likely
                                // won't be able to recover from this.
                                Log.e(
                                        "DeviceClientMonitorTask",
                                        "Connection error while monitoring clients.");
                            }
                            return true;
                        });
    }

    @Override
    public void run() {
        final byte[] lengthBuffer = new byte[4];
        do {
            try {
                int count = mSelector.select();

                if (mQuit) {
                    return;
                }

                processChannelsToRegister();
                processDropAndReopenClients();

                if (count == 0) {
                    continue;
                }

                Set<SelectionKey> keys = mSelector.selectedKeys();
                Iterator<SelectionKey> iter = keys.iterator();

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();

                    if (key.isValid() && key.isReadable()) {
                        Object attachment = key.attachment();

                        if (attachment instanceof DeviceImpl) {
                            DeviceImpl device = (DeviceImpl) attachment;

                            SocketChannel socket = device.getClientMonitoringSocket();

                            if (socket != null) {
                                try {
                                    int length = AdbSocketUtils.readLength(socket, lengthBuffer);
                                    processIncomingJdwpData(device, socket, length);
                                } catch (IOException ioe) {
                                    Log.d(
                                            "DeviceClientMonitorTask",
                                            "Error reading jdwp list: " + ioe.getMessage());
                                    socket.close();
                                    mChannelsToRegister.remove(socket);
                                    device.getClientTracker().trackDeviceToDropAndReopen(device);
                                }
                            }
                        }
                    }
                }

            } catch (IOException ex) {
                Log.e("DeviceClientMonitorTask", "Connection error while monitoring clients.");
            }
        } while (!mQuit);
    }

    public void stop() {
        mQuit = true;
        // wake up the secondary loop by closing the selector.
        mSelector.wakeup();
    }

    private static boolean sendDeviceMonitoringRequest(
            @NonNull SocketChannel socket, @NonNull DeviceImpl device)
            throws TimeoutException, AdbCommandRejectedException, IOException {

        try {
            AdbHelper.setDevice(socket, device);
            AdbHelper.write(socket, AdbHelper.formAdbRequest(ADB_TRACK_JDWP_COMMAND));
            AdbHelper.AdbResponse resp = AdbHelper.readAdbResponse(socket, false);

            if (!resp.okay) {
                // request was refused by adb!
                Log.e("DeviceClientMonitorTask", "adb refused request: " + resp.message);
            }

            return resp.okay;
        } catch (TimeoutException e) {
            Log.e("DeviceClientMonitorTask", "Sending jdwp tracking request timed out!");
            throw e;
        } catch (IOException e) {
            Log.e("DeviceClientMonitorTask", "Sending jdwp tracking request failed!");
            throw e;
        }
    }

    private void processIncomingJdwpData(
            @NonNull DeviceImpl device, @NonNull SocketChannel monitorSocket, int length)
            throws IOException {

        // This methods reads @length bytes from the @monitorSocket channel.
        // These bytes correspond to the pids of the current set of processes on the device.
        // It takes this set of pids and compares them with the existing set of clients
        // for the device. Clients that correspond to pids that are not alive anymore are
        // dropped, and new clients are created for pids that don't have a corresponding Client.

        if (length >= 0) {
            // array for the current pids.
            Set<Integer> newPids = new HashSet<Integer>();

            // get the string data if there are any
            if (length > 0) {
                byte[] buffer = new byte[length];
                String result = AdbSocketUtils.read(monitorSocket, buffer);

                // split each line in its own list and create an array of integer pid
                String[] pids = result.split("\n"); // $NON-NLS-1$

                for (String pid : pids) {
                    try {
                        newPids.add(Integer.valueOf(pid));
                    } catch (NumberFormatException nfe) {
                        // looks like this pid is not really a number. Lets ignore it.
                    }
                }
            }

            MonitorThread monitorThread = MonitorThread.getInstance();

            List<ClientImpl> clients = device.getClientList();
            Map<Integer, ClientImpl> existingClients = new HashMap<>();

            synchronized (clients) {
                for (ClientImpl c : clients) {
                    existingClients.put(c.getClientData().getPid(), c);
                }
            }

            Set<ClientImpl> clientsToRemove = new HashSet<>();
            for (Integer pid : existingClients.keySet()) {
                if (!newPids.contains(pid)) {
                    clientsToRemove.add(existingClients.get(pid));
                }
            }

            Set<Integer> pidsToAdd = new HashSet<Integer>(newPids);
            pidsToAdd.removeAll(existingClients.keySet());
            monitorThread.dropClients(clientsToRemove, false);

            // at this point whatever pid is left in the list needs to be converted into Clients.
            for (int newPid : pidsToAdd) {
                openClient(device, newPid, monitorThread);
            }

            if (!pidsToAdd.isEmpty() || !clientsToRemove.isEmpty()) {
                AndroidDebugBridge.deviceChanged(device, IDevice.CHANGE_CLIENT_LIST);
            }
        }
    }

    /** Opens and creates a new client. */
    private static void openClient(
            @NonNull DeviceImpl device, int pid, @NonNull MonitorThread monitorThread) {

        SocketChannel clientSocket;
        try {
          clientSocket =
            AdbHelper.createPassThroughConnection(
              new InetSocketAddress("localhost", DdmPreferences.DEFAULT_PROXY_SERVER_PORT), device.getSerialNumber(), pid);

            // required for Selector
            clientSocket.configureBlocking(false);
        } catch (UnknownHostException uhe) {
            Log.d("DeviceClientMonitorTask", "Unknown Jdwp pid: " + pid);
            return;
        } catch (TimeoutException e) {
            Log.w("DeviceClientMonitorTask", "Failed to connect to client '" + pid + "': timeout");
            return;
        } catch (AdbCommandRejectedException e) {
            Log.d(
                    "DeviceClientMonitorTask",
                    "Adb rejected connection to client '" + pid + "': " + e.getMessage());
            return;
        } catch (IOException ioe) {
            Log.w(
                    "DeviceClientMonitorTask",
                    "Failed to connect to client '" + pid + "': " + ioe.getMessage());
            return;
        }

        createClient(device, pid, clientSocket, monitorThread);
    }

    /** Creates a client and register it to the monitor thread */
    private static void createClient(
            @NonNull DeviceImpl device,
            int pid,
            @NonNull SocketChannel socket,
            @NonNull MonitorThread monitorThread) {

        /*
         * Successfully connected to something. Create a Client object, add
         * it to the list, and initiate the JDWP handshake.
         */

        ClientImpl client = new ClientImpl(device, socket, pid);

        if (client.sendHandshake()) {
            try {
                if (AndroidDebugBridge.getClientSupport()) {
                    client.listenForDebugger();
                    String msg =
                            String.format(
                                    Locale.US,
                                    "Opening a debugger listener at port %1$d for client with pid %2$d",
                                    client.getDebuggerListenPort(),
                                    pid);
                    Log.d("ddms", msg);
                }
            } catch (IOException ioe) {
                client.getClientData().setDebuggerConnectionStatus(ClientData.DebuggerStatus.ERROR);
                Log.e("ddms", "Can't bind to local " + client.getDebuggerListenPort() + " for debugger");
                // oh well
            }

            client.requestAllocationStatus();
        } else {
            Log.e("ddms", "Handshake with " + client + " failed!");
            /*
             * The handshake send failed. We could remove it now, but if the
             * failure is "permanent" we'll just keep banging on it and
             * getting the same result. Keep it in the list with its "error"
             * state so we don't try to reopen it.
             */
        }

        if (client.isValid()) {
            device.addClient(client);
            monitorThread.addClient(client);
        }
    }
}
