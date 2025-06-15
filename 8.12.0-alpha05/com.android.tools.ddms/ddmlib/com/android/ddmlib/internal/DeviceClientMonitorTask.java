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
import com.android.annotations.Nullable;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.AdbHelper;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.internal.jdwp.chunkhandler.HandleHello;
import com.android.sdklib.AndroidVersion;
import com.android.server.adb.protos.AppProcessesProto;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
 *
 * <p>Depending on the version of the device being monitored, different ADB services are used. - For
 * pre-S (API 31), "track-jdwp" is used. This service only retrieves the debuggable apps and only
 * returns the PIDs. The application ID of the app process is retrieved later when the VM sends an
 * APNM packets.
 *
 * <p>- S and post-S devices use "track-app" service. On top of the pid, the debuggable and
 * profileable status are also returned. The VM does NOT send an APNM packet to declare its
 * application ID. This information is retrieved via a separate command sent for each PID "cat
 * /proc/XXX/cmdline".
 */
class DeviceClientMonitorTask implements Runnable {

    // Every times the selector triggers, we read as much as possible from the socket and hand it
    // over to the Processor associated with it.
    private final ByteBuffer mBuffer = ByteBuffer.allocate(1 << 16);

    private volatile boolean mQuit;
    @NonNull private final Selector mSelector;
    // Note that mChannelsToRegister is not synchronized other than through the compute* interface.
    // Therefore, make sure atomic operations are done via that extended API.
    private final ConcurrentHashMap<SocketChannel, TrackServiceProcessor> mChannelsToRegister =
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
                TrackServiceProcessor processor =
                        isDeviceVersionAtLeastS(device)
                                ? new TrackAppProcessor(device)
                                : new TrackJdwpProcessor(device);
                boolean result = sendDeviceMonitoringRequest(socketChannel, processor);
                if (result) {
                    device.setClientMonitoringSocket(socketChannel);
                    socketChannel.configureBlocking(false);
                    mChannelsToRegister.put(socketChannel, processor);
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
        List<SocketChannel> channels = Collections.list(mChannelsToRegister.keys());
        for (SocketChannel channel : channels) {
            try {
                channel.register(mSelector, SelectionKey.OP_READ, mChannelsToRegister.get(channel));
            } catch (ClosedChannelException e) {
                Log.w("DeviceClientMonitorTask", "Cannot register already-closed channel.");
            } finally {
                mChannelsToRegister.keySet().remove(channel);
            }
        }
    }

    void disconnectClient(ClientImpl client) {
        MonitorThread monitorThread = MonitorThread.getInstance();
        if (monitorThread != null) {
            monitorThread.dropClient(client, true);
        }
    }

    @Override
    public void run() {
        do {
            int count = 0;
            try {
                count = mSelector.select();
            } catch (IOException e) {
                Log.e("DeviceClientMonitorTask", "Connection error while monitoring clients.");
                Log.d("DeviceClientMonitorTask", e);
                return;
            }

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

                if (!key.isValid() || !key.isReadable()) {
                    continue;
                }

                Object attachment = key.attachment();
                if (!(attachment instanceof Processor)) {
                    continue;
                }

                Processor processor = (Processor) attachment;
                SocketChannel socket = (SocketChannel) key.channel();
                if (socket == null) {
                    continue;
                }

                try {
                    // This socket has data available. Read as much as we can from it, and hand it
                    // over to the processor.
                    mBuffer.clear();
                    int read = socket.read(mBuffer);

                    // The inputstream has been closed
                    if (read == -1) {
                        closeProcessor(processor, socket);
                        continue;
                    }

                    mBuffer.flip();
                    processor.onBytesReceived(mBuffer);
                } catch (IOException ioe) {
                    Log.d(
                            "DeviceClientMonitorTask",
                            "Error reading incoming data: " + ioe.getMessage());
                    closeProcessor(processor, socket);
                }
            }
        } while (!mQuit);
        Log.d("DeviceClientMonitorTask", "Exiting loop");
    }

    public void closeProcessor(@NonNull Processor processor, @NonNull SocketChannel socket) {
        try (Processor p = processor;
                SocketChannel c = socket) {
        } catch (IOException ignored) {
            // ignored
        }

        // When does this occurs?
        if (processor instanceof TrackServiceProcessor) {
            // For TrackServiceProcessor, the socket is for "track-app" or
            // "track-jdwp". Reopen them if the device is still connected.
            mChannelsToRegister.remove(socket);
            DeviceImpl device = processor.getDevice();
            device.getClientTracker().trackDeviceToDropAndReopen(device);
        }
    }

    public void stop() {
        mQuit = true;
        // wake up the secondary loop by closing the selector.
        mSelector.wakeup();
    }

    private boolean sendDeviceMonitoringRequest(
            @NonNull SocketChannel socket, @NonNull TrackServiceProcessor processor)
            throws TimeoutException, AdbCommandRejectedException, IOException {

        try {
            AdbHelper.setDevice(socket, processor.getDevice());
            AdbHelper.write(socket, AdbHelper.formAdbRequest(processor.getCommand()));
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

    private void updateJdwpClients(@NonNull DeviceImpl device, @NonNull Set<Integer> newPids) {
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

    /** Opens and creates a new client. */
    private static void openClient(
            @NonNull DeviceImpl device, int pid, @NonNull MonitorThread monitorThread) {

        SocketChannel clientSocket;
        try {

            if (DdmPreferences.isJdwpProxyEnabled()) {
                clientSocket =
                        AdbHelper.createPassThroughConnection(
                                new InetSocketAddress(
                                        "localhost", DdmPreferences.getJdwpProxyPort()),
                                device.getSerialNumber(),
                                pid);
            } else {
                clientSocket =
                        AdbHelper.createPassThroughConnection(
                                AndroidDebugBridge.getSocketAddress(),
                                device.getSerialNumber(),
                                pid);
            }

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

    private static boolean isDeviceVersionAtLeastS(@NonNull DeviceImpl device) {
        return device.getVersion().getFeatureLevel() >= AndroidVersion.VersionCodes.S;
    }

    /**
     * A Processor instance is the entity to process data coming from a socket. It's always
     * registered with the Selector in {@link DeviceClientMonitorTask}.
     *
     * <p>For each device, there's a {@link TrackServiceProcessor}. If the device version is at
     * least S, it's for adb's track-app service through derived class {@link TrackAppProcessor};
     * otherwise, it's for the track-jdwp service through derived class {@link TrackJdwpProcessor}.
     *
     * <p>Another type of Processor is {@link CmdlineFileProcessor} which is created to run an adb
     * shell command to read the /proc/<PID>/cmdline file for the name of a given process.
     */
    private abstract static class Processor implements AutoCloseable {

        @NonNull ProcessorStream mStream;
        @NonNull final DeviceImpl mDevice;

        Processor(@NonNull DeviceImpl device) {
            mDevice = device;
            mStream = new ProcessorStream();
        }

        public void onBytesReceived(@NonNull ByteBuffer buffer) throws IOException {
            // Append the incoming byte to the Processor local stream.
            mStream.append(buffer);

            // Check if the processor has received one or more full messages. In which case,
            // request it to be processed and discard the used bytes.
            for (Optional<ByteBuffer> message = parseMessage();
                    message.isPresent();
                    message = parseMessage()) {
                onMessage(message.get());
                mStream.consume(message.get().limit());
            }
        }

        // Check if the Processor stream contains a fully usable message and wrap it in a ByteBuffer
        protected abstract Optional<ByteBuffer> parseMessage() throws IOException;

        // Process the message contained in the Bytebuffer (guaranteed to contain a single message)
        protected abstract void onMessage(ByteBuffer message) throws IOException;

        @NonNull
        DeviceImpl getDevice() {
            return mDevice;
        }

        @Nullable
        abstract SocketChannel getSocket();

        public abstract void close() throws IOException;
    }

    abstract static class TrackServiceProcessor extends Processor {

        private static final int HEADER_SIZE = 4;

        TrackServiceProcessor(@NonNull DeviceImpl device) {
            super(device);
        }

        @Override
        protected Optional<ByteBuffer> parseMessage() throws IOException {
            if (mStream.size() < HEADER_SIZE) {
                return Optional.empty();
            }

            String textSize = new String(mStream.buf(), 0, HEADER_SIZE, AdbHelper.DEFAULT_CHARSET);
            int paydloadSize;
            try {
                paydloadSize = Integer.parseInt(textSize, 16);
            } catch (NumberFormatException e) {
                throw new IOException("Bad message size =" + textSize, e);
            }

            // We don't have enough data yet.
            if (mStream.size() < HEADER_SIZE + paydloadSize) {
                return Optional.empty();
            }

            ByteBuffer message = ByteBuffer.wrap(mStream.buf(), 0, paydloadSize + HEADER_SIZE);
            message.getInt(); // Skip the first four bytes containing the length.
            return Optional.of(message);
        }

        @Override
        @Nullable
        SocketChannel getSocket() {
            return getDevice().getClientMonitoringSocket();
        }

        @NonNull
        protected abstract String getCommand();

        @Override
        public void close() {}
    }

    private class TrackAppProcessor extends TrackServiceProcessor {

        TrackAppProcessor(@NonNull DeviceImpl device) {
            super(device);
        }

        @Override
        @NonNull
        protected String getCommand() {
            return "track-app";
        }

        @Override
        protected void onMessage(ByteBuffer message) throws IOException {
            AppProcessesProto.AppProcesses processes;

            try {
                processes = AppProcessesProto.AppProcesses.parseFrom(message);
            } catch (InvalidProtocolBufferException e) {
                throw new IOException(e);
            }

            Set<Integer> newJdwpPids = new HashSet<Integer>();
            // Map from pid to the associated data.
            Map<Integer, ProfileableClientImpl> newProfileable = new HashMap<>();
            for (AppProcessesProto.ProcessEntry process : processes.getProcessList()) {
                if (process.getDebuggable()) {
                    newJdwpPids.add(Integer.valueOf((int) process.getPid()));
                }
                // A debuggable process must be profileable.
                if (process.getProfileable() || process.getDebuggable()) {
                    // Use an empty name first, will retrieve process names later.
                    ProfileableClientImpl client =
                            new ProfileableClientImpl(
                                    (int) process.getPid(),
                                    DeviceImpl.UNKNOWN_PACKAGE,
                                    process.getArchitecture());
                    newProfileable.put((int) process.getPid(), client);
                }
            }

            updateJdwpClients(getDevice(), newJdwpPids);
            updateProfileableClients(getDevice(), newProfileable);
        }

        void updateProfileableClients(
                @NonNull DeviceImpl device,
                @NonNull Map<Integer, ProfileableClientImpl> currentProfileable) {
            // Map from pid to the associated data, for profileable apps discovered before.
            Map<Integer, ProfileableClientImpl> previousProfileable = new HashMap<>();
            for (ProfileableClientImpl client : device.getProfileableClientImpls()) {
                previousProfileable.put(client.getProfileableClientData().getPid(), client);
            }
            Set<Integer> addPids =
                    Sets.difference(currentProfileable.keySet(), previousProfileable.keySet());
            Set<Integer> removePids =
                    Sets.difference(previousProfileable.keySet(), currentProfileable.keySet());
            if (addPids.isEmpty() && removePids.isEmpty()) {
                return;
            }

            Set<Integer> pidsWithoutNames = Sets.newTreeSet(addPids);
            // Populate names for profileable apps that have been discovered before
            previousProfileable.forEach(
                    (pid, client) -> {
                        if (!currentProfileable.containsKey(pid)) {
                            return;
                        }
                        String name = client.getProfileableClientData().getProcessName();
                        if (name != null && !name.isEmpty()) {
                            currentProfileable
                                    .get(pid)
                                    .getProfileableClientData()
                                    .setProcessName(name);
                        } else {
                            pidsWithoutNames.add(pid);
                        }
                    });
            findProcessJdwpNames(device, currentProfileable, pidsWithoutNames);
            device.updateProfileableClientList(Lists.newArrayList(currentProfileable.values()));
            AndroidDebugBridge.deviceChanged(device, IDevice.CHANGE_PROFILEABLE_CLIENT_LIST);

            for (Integer pid : pidsWithoutNames) {
                // Create an instance of CmdlineFileProcessor for each pid, and register the socket
                // to the Selector of DeviceClientMonitorTask.
                new CmdlineFileProcessor(device, pid).connect();
            }
        }

        /**
         * Find names for pids in {@link pidsWithoutNames} and populate {@link pidClientMap} with
         * the findings. Assume {@link pidClientMap} contains every pid from {@link
         * pidsWithoutNames}. If the name of a pid is successfully found, the pid will be removed
         * from {@link pidsWithoutNames}.
         *
         * <p>Prefer the names from jdwp channel. It's fast and accurate. May query
         * /proc/PID/cmdline file but there's a latency (e.g., tens of milliseconds) for each query.
         */
        private void findProcessJdwpNames(
                @NonNull DeviceImpl device,
                @NonNull Map<Integer, ProfileableClientImpl> pidClientMap,
                Set<Integer> pidsWithoutNames) {

            Map<Integer, String> jdwpClientNames = new HashMap<>();
            for (Client client : device.getClients()) {
                ClientData clientData = client.getClientData();
                jdwpClientNames.put(clientData.getPid(), clientData.getPackageName());
            }
            for (Integer pid : Sets.newTreeSet(pidsWithoutNames)) {
                // Prefer the name from jdwp. It's faster and more accurate as it comes from APNM
                // packet. If the debuggable app is newly launched, its name may not be set yet.
                @Nullable String name = jdwpClientNames.get(pid);
                if (name != null && !name.isEmpty()) {
                    pidClientMap.get(pid).getProfileableClientData().setProcessName(name);
                    pidsWithoutNames.remove(pid);
                }
            }
        }
    }

    private class TrackJdwpProcessor extends TrackServiceProcessor {
        TrackJdwpProcessor(@NonNull DeviceImpl device) {
            super(device);
        }

        @Override
        @NonNull
        protected String getCommand() {
            return "track-jdwp";
        }

        @Override
        protected void onMessage(@NonNull ByteBuffer message) throws IOException {
            // The [message] bytes correspond to the pids of the current set of processes on the
            // device.
            // It takes this set of pids and compares them with the existing set of clients
            // for the device. Clients that correspond to pids that are not alive anymore are
            // dropped, and new clients are created for pids that don't have a corresponding Client.

            // array for the current pids.
            Set<Integer> newPids = new HashSet<>();

            // get the string data if there are any
            String result =
                    new String(
                            message.array(),
                            message.position(),
                            message.remaining(),
                            AdbHelper.DEFAULT_CHARSET);

            // split each line in its own list and create an array of integer pid
            String[] pids = result.split("\n"); // $NON-NLS-1$

            for (String pid : pids) {
                try {
                    newPids.add(Integer.valueOf(pid));
                        } catch (NumberFormatException nfe) {
                            // looks like this pid is not really a number. Lets ignore it.
                        }
                }
                updateJdwpClients(getDevice(), newPids);
        }
    }

    private class CmdlineFileProcessor extends Processor {

        // CmdlineFileProcessor is peculiar since contrary to TrackServiceProcessor it tap into a
        // source that does not prefix its message with their size (it runs a bash command). Also
        // the source does not send several messages, it returns a single message made fo the whole
        // update of the bash command.
        //
        // To be compliant with the parseMessage/onMessage system, it always return false on
        // parseMessage() until the Processor is closed. After that parseMessage returns true once
        // and then false each subsequent calls.

        private boolean messageReceived = false;

        private final int mPid;

        private int mRetryCount; // The number of attempts left to read the cmdline file.

        @NonNull SocketChannel mSocket; // Socket to execute the adb shell command.

        boolean mSocketConnected = true;

        CmdlineFileProcessor(@NonNull DeviceImpl device, int pid) {
            // For each pid, make up to 5 attempts to read the cmdline file to get the name.
            this(device, pid, 5);
        }

        CmdlineFileProcessor(@NonNull DeviceImpl device, int pid, int retryCount) {
            super(device);
            mPid = pid;
            mRetryCount = retryCount;
        }

        @Override
        protected Optional<ByteBuffer> parseMessage() {
            if (mSocketConnected || messageReceived) {
                return Optional.empty();
            }

            messageReceived = true;
            return Optional.of(ByteBuffer.wrap(mStream.buf(), 0, mStream.size()));
        }

        void connect() {
            if (mRetryCount <= 0) {
                Log.w("DeviceClientMonitorTask", "Unexpected cmdline file for PID " + mPid);
                return;
            }

            String[] parameters = new String[1];
            parameters[0] = "/proc/" + mPid + "/cmdline";
            try {
                mSocket = getDevice().rawExec("cat", parameters);
            } catch (AdbCommandRejectedException | TimeoutException | IOException e) {
                // ignore
            }

            try {
                mSocket.register(mSelector, SelectionKey.OP_READ, this);
            } catch (ClosedChannelException e) {
                Log.w(
                        "DeviceClientMonitorTask",
                        "Cannot register already-closed channel to read the name for PID " + mPid);
            }
        }

        @Override
        @Nullable
        SocketChannel getSocket() {
            return mSocket;
        }

        @Override
        protected void onMessage(ByteBuffer message) throws IOException {
            String name =
                    new String(
                            message.array(),
                            message.position(),
                            message.remaining(),
                            AdbHelper.DEFAULT_CHARSET);

            message.position(message.remaining());
            name = name.trim();
            if (name.isEmpty()) {
                return;
            }

            if (name.equals("<pre-initialized>")) {
                // The cmdline file hasn't been initialized when it's read.
                // Create another processor to read the same file, and register its own socket
                // to the Selector of DeviceClientMonitorTask.
                new CmdlineFileProcessor(getDevice(), mPid, --mRetryCount).connect();
                return;
            } else if (name.contains("No such file or directory")) {
                // The process is already dead. Do nothing.
                // Adb's track-app service will signal the process termination.
                return;
            }
            getDevice().updateProfileableClientName(mPid, name);
            AndroidDebugBridge.deviceChanged(getDevice(), IDevice.CHANGE_PROFILEABLE_CLIENT_LIST);
        }

        @Override
        public void close() throws IOException {
            mSocketConnected = false;
            onBytesReceived(ByteBuffer.wrap(new byte[0]));
        }
    }
}

