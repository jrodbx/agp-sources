/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static com.android.ddmlib.AdbHelper.formAdbRequest;
import static com.android.ddmlib.AdbHelper.readAdbResponse;
import static com.android.ddmlib.AdbHelper.write;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.clientmanager.ClientManager;
import com.android.ddmlib.idevicemanager.IDeviceManagerFactory;
import com.android.ddmlib.internal.ClientImpl;
import com.android.ddmlib.internal.DeviceMonitor;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public abstract class AndroidDebugBridgeBase implements AndroidDebugBridgeDelegate {

    protected static final AndroidDebugBridgeChangeEvents adbChangeEvents =
            new AndroidDebugBridgeChangeEvents();

    /** Default timeout used when starting the ADB server */
    public static final long DEFAULT_START_ADB_TIMEOUT_MILLIS = 20_000;

    protected static final String ADB = "adb"; // $NON-NLS-1$

    private static final String DDMS = "ddms"; // $NON-NLS-1$

    private static final String SERVER_PORT_ENV_VAR = "ANDROID_ADB_SERVER_PORT"; // $NON-NLS-1$

    // Where to find the ADB bridge.
    public static final int DEFAULT_ADB_PORT = 5037;

    // ADB exit value when no Universal C Runtime on Windows
    private static final int STATUS_DLL_NOT_FOUND = (int)(long)0xc0000135;

    // Only set when in unit testing mode. This is a hack until we move to devicelib.
    // http://b.android.com/221925
    protected static boolean sUnitTestMode;

    /** Port where adb server will be started */
    protected int sAdbServerPort = 0;

    /** Don't automatically manage ADB server. */
    protected static boolean sUserManagedAdbMode = false;

    protected static final Object sLastKnownGoodAddressLock = new Object();

    /** Last known good {@link InetSocketAddress} to ADB. */
    protected volatile InetSocketAddress sLastKnownGoodAddress;

    protected volatile AndroidDebugBridge sThis;

    protected volatile boolean sInitialized = false;

    private static boolean sClientSupport;

    private static ClientManager sClientManager;

    private static IDeviceManagerFactory sIDeviceManagerFactory;

    private static IDeviceUsageTracker iDeviceUsageTracker;

    protected static Map<String, String> sAdbEnvVars; // env vars to set while launching adb

    /** Full path to adb. */
    protected String mAdbOsLocation = null;

    protected AdbVersion mAdbVersion;

    protected boolean mVersionCheck;

    protected boolean mStarted = false;

    /**
     * Initialized the library only if needed; deprecated for non-test usages.
     *
     * @param clientSupport Indicates whether the library should enable the monitoring and
     *                      interaction with applications running on the devices.
     * @see #init(boolean)
     */
    @Deprecated
    public synchronized void initIfNeeded(boolean clientSupport) {
        if (sInitialized) {
            return;
        }
        init(clientSupport);
    }

    /**
     * Initializes the <code>ddm</code> library.
     *
     * <p>This must be called once <b>before</b> any call to
     * {@link #createBridge(String, boolean)}.
     *
     * <p>The preferences of <code>ddmlib</code> should also be initialized with whatever default
     * values were changed from the default values.
     *
     * <p>When the application quits, {@link #terminate()} should be called.
     *
     * @param clientSupport Indicates whether the library should enable the monitoring and
     *                      interaction with applications running on the devices.
     * @see AndroidDebugBridge#createBridge(String, boolean)
     * @see DdmPreferences
     */
    public synchronized void init(boolean clientSupport) {
        init(clientSupport, false, ImmutableMap.of());
    }

    /**
     * Similar to {@link #init(boolean)}, with ability to enable libusb and pass a custom set of
     * env. variables.
     */
    public synchronized void init(
            boolean clientSupport, boolean useLibusb, @NonNull Map<String, String> env) {
        init(
                AdbInitOptions.builder()
                        .withEnv(env)
                        .setClientSupportEnabled(clientSupport)
                        .withEnv("ADB_LIBUSB", useLibusb ? "1" : "0")
                        .build());
    }

    /** Similar to {@link #init(boolean)}, with ability to pass a custom set of env. variables. */
    public synchronized void init(AdbInitOptions options) {
        Preconditions.checkState(
                !sInitialized, "AndroidDebugBridge.init() has already been called.");
        sInitialized = true;
        sIDeviceManagerFactory = options.iDeviceManagerFactory;
        iDeviceUsageTracker = options.iDeviceUsageTracker;
        sClientSupport = options.clientSupport;
        sClientManager = options.clientManager;
        if (sClientManager != null) {
            // A custom client manager is not compatible with "client support"
            sClientSupport = false;
        }
        if (sIDeviceManagerFactory != null) {
            // A custom "IDevice" manager is not compatible with a "Client" manager
            sClientManager = null;
            sClientSupport = false;
        }
        sAdbEnvVars = options.adbEnvVars;
        sUserManagedAdbMode = options.userManagedAdbMode;
        sLastKnownGoodAddress = null;
        DdmPreferences.enableJdwpProxyService(options.useJdwpProxyService);
        DdmPreferences.enableDdmlibCommandService(options.useDdmlibCommandService);
        DdmPreferences.setsJdwpMaxPacketSize(options.maxJdwpPacketSize);

        // Determine port and instantiate socket address.
        initAdbPort(options.userManagedAdbPort);
    }

    /**
     * Notify {@link AndroidDebugBridge} that options have been modified. This method will re-init
     * adb if it has already been initialized, and restart if a bridge has been connected just prior
     * to this call.
     *
     * @return true if options have been successfully changed and reflected in the
     * reinitialize/restarted state (if it was in such state prior to calling this method), or false
     * otherwise
     */
    public synchronized boolean optionsChanged(
            @NonNull AdbInitOptions options,
            @NonNull String osLocation,
            boolean forceNewBridge,
            long terminateTimeout,
            long initTimeout,
            @NonNull TimeUnit unit) {
        if (!sInitialized) {
            return true;
        }

        boolean bridgeNeedsRestart = getBridge() != null;
        if (bridgeNeedsRestart) {
            if (!disconnectBridge(terminateTimeout, unit)) {
                Log.e(DDMS, "Could not disconnect bridge prior to restart when options changed.");
                return false;
            }
        }
        terminate();
        init(options);
        if (bridgeNeedsRestart) {
            if (createBridge(osLocation, forceNewBridge, initTimeout, unit) == null) {
                Log.e(DDMS, "Could not recreate the bridge after options changed.");
                return false;
            }
        }
        return true;
    }

    @VisibleForTesting
    public void enableFakeAdbServerMode(int port) {
        Preconditions.checkState(
                !sInitialized,
                "AndroidDebugBridge.init() has already been called or "
                + "terminate() has not been called yet.");
        sUnitTestMode = true;
        sAdbServerPort = port;
    }


    @VisibleForTesting
    public void disableFakeAdbServerMode() {
        Preconditions.checkState(
                !sInitialized,
                "AndroidDebugBridge.init() has already been called or "
                + "terminate() has not been called yet.");
        sUnitTestMode = false;
        sAdbServerPort = 0;
    }

    /**
     * Returns whether the ddmlib is setup to support monitoring and interacting with
     * {@link ClientImpl}s running on the {@link IDevice}s.
     */
    public boolean getClientSupport() {
        return sClientSupport;
    }

    /**
     * Returns the current {@link ClientManager} instance if {@link Client} process tracking is
     * delegated to an external implementation, or {@code null} if {@link Client} processes are
     * monitored with the default {@link #getClientSupport()} implementation.
     */
    @Nullable
    public ClientManager getClientManager() {
        return sClientManager;
    }

    /**
     * Attempts to connect to the local android debug bridge server.
     *
     * @return a connected socket if success
     * @throws IOException should errors occur when opening the connection
     */
    public SocketChannel openConnection() throws IOException {
        SocketChannel adbChannel;
        try {
            adbChannel = SocketChannel.open(new InetSocketAddress("127.0.0.1", sAdbServerPort));
        }
        catch (IOException ipv4Exception) {
            // Fallback to IPv6.
            try {
                adbChannel = SocketChannel.open(new InetSocketAddress("::1", sAdbServerPort));
            }
            catch (IOException ipv6Exception) {
                IOException combinedException =
                        new IOException(
                                "Can't find adb server on port "
                                + sAdbServerPort
                                + ", IPv4 attempt: "
                                + ipv4Exception.getMessage()
                                + ", IPv6 attempt: "
                                + ipv6Exception.getMessage(),
                                ipv4Exception);
                combinedException.addSuppressed(ipv6Exception);
                throw combinedException;
            }
        }
        adbChannel.socket().setTcpNoDelay(true);
        return adbChannel;
    }

    /**
     * Creates a {@link AndroidDebugBridge} that is not linked to any particular executable.
     *
     * <p>This bridge will expect adb to be running. It will not be able to start/stop/restart adb.
     *
     * <p>If a bridge has already been started, it is directly returned with no changes (similar to
     * calling {@link #getBridge()}).
     *
     * @return a connected bridge, or null if there were errors while creating or connecting to the
     * bridge
     */
    @Nullable
    public AndroidDebugBridge createBridge() {
        return createBridge(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    /**
     * Creates a new debug bridge from the location of the command line tool.
     *
     * <p>Any existing server will be disconnected, unless the location is the same and <code>
     * forceNewBridge</code> is set to false.
     *
     * @param osLocation     the location of the command line tool 'adb'
     * @param forceNewBridge force creation of a new bridge even if one with the same location
     *                       already exists.
     * @return a connected bridge, or null if there were errors while creating or connecting to the
     * bridge
     * @deprecated This method may hang if ADB is not responding. Use
     * {@link #createBridge(String, boolean, long, TimeUnit)} instead.
     */
    @Deprecated
    @Nullable
    public AndroidDebugBridge createBridge(@NonNull String osLocation, boolean forceNewBridge) {
        return createBridge(osLocation, forceNewBridge, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    /** Returns the current debug bridge. Can be <code>null</code> if none were created. */
    @Nullable
    public AndroidDebugBridge getBridge() {
        return sThis;
    }

    /**
     * Disconnects the current debug bridge, and destroy the object. A new object will have to be
     * created with {@link #createBridge(String, boolean)}.
     *
     * <p>This also stops the current adb host server.
     *
     * @deprecated This method may hang if ADB is not responding. Use
     * {@link #disconnectBridge(long, TimeUnit)} instead.
     */
    @Deprecated
    public void disconnectBridge() {
        disconnectBridge(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    /**
     * Adds the listener to the collection of listeners who will be notified when a new
     * {@link AndroidDebugBridge} is connected, by sending it one of the messages defined in the
     * {@link AndroidDebugBridge.IDebugBridgeChangeListener} interface.
     *
     * @param listener The listener which should be notified.
     */
    public void addDebugBridgeChangeListener(
            @NonNull AndroidDebugBridge.IDebugBridgeChangeListener listener) {
        adbChangeEvents.addDebugBridgeChangeListener(listener);

        AndroidDebugBridge localThis = sThis;

        if (localThis != null) {
            // we attempt to catch any exception so that a bad listener doesn't kill our thread
            try {
                listener.bridgeChanged(localThis);
            }
            catch (Throwable t) {
                Log.e(DDMS, t);
            }
        }
    }

    /**
     * Removes the listener from the collection of listeners who will be notified when a new
     * {@link AndroidDebugBridge} is started.
     *
     * @param listener The listener which should no longer be notified.
     */
    public void removeDebugBridgeChangeListener(
            AndroidDebugBridge.IDebugBridgeChangeListener listener) {
        adbChangeEvents.removeDebugBridgeChangeListener(listener);
    }

    @VisibleForTesting
    public int getDebugBridgeChangeListenerCount() {
        return adbChangeEvents.debugBridgeChangeListenerCount();
    }

    /**
     * Adds the listener to the collection of listeners who will be notified when a {@link IDevice}
     * is connected, disconnected, or when its properties or its {@link ClientImpl} list changed, by
     * sending it one of the messages defined in the
     * {@link AndroidDebugBridge.IDeviceChangeListener} interface.
     *
     * @param listener The listener which should be notified.
     */
    public void addDeviceChangeListener(
            @NonNull AndroidDebugBridge.IDeviceChangeListener listener) {
        adbChangeEvents.addDeviceChangeListener(listener);
    }

    /**
     * Removes the listener from the collection of listeners who will be notified when a
     * {@link IDevice} is connected, disconnected, or when its properties or its {@link ClientImpl}
     * list changed.
     *
     * @param listener The listener which should no longer be notified.
     */
    public void removeDeviceChangeListener(AndroidDebugBridge.IDeviceChangeListener listener) {
        adbChangeEvents.removeDeviceChangeListener(listener);
    }

    @VisibleForTesting
    public int getDeviceChangeListenerCount() {
        return adbChangeEvents.deviceChangeListenerCount();
    }

    /**
     * Adds the listener to the collection of listeners who will be notified when a
     * {@link ClientImpl} property changed, by sending it one of the messages defined in the
     * {@link AndroidDebugBridge.IClientChangeListener} interface.
     *
     * @param listener The listener which should be notified.
     */
    public void addClientChangeListener(AndroidDebugBridge.IClientChangeListener listener) {
        adbChangeEvents.addClientChangeListener(listener);
    }

    /**
     * Removes the listener from the collection of listeners who will be notified when a
     * {@link ClientImpl} property changes.
     *
     * @param listener The listener which should no longer be notified.
     */
    public void removeClientChangeListener(AndroidDebugBridge.IClientChangeListener listener) {
        adbChangeEvents.removeClientChangeListener(listener);
    }

    /**
     * @return version of the ADB server if we were able to successfully retrieve it, {@code null}
     * otherwise.
     */
    public @Nullable AdbVersion getCurrentAdbVersion() {
        return mAdbVersion;
    }

    @Nullable
    public IDeviceUsageTracker getiDeviceUsageTracker() {
        return iDeviceUsageTracker;
    }

    /**
     * @deprecated Use {@link #execute} which lets you inject an executor
     */
    @Deprecated
    private static <T> ListenableFuture<T> runAdb(
            @NonNull final File adb,
            AndroidDebugBridge.AdbOutputProcessor<T> resultParser,
            String... command) {
        final SettableFuture<T> future = SettableFuture.create();
        new Thread(
                () -> {
                    List<String> args = new ArrayList<>();
                    args.add(adb.getPath());
                    args.addAll(Arrays.asList(command));
                    ProcessBuilder pb = new ProcessBuilder(args);
                    pb.redirectErrorStream(true);

                    Process p;
                    try {
                        p = pb.start();
                    }
                    catch (IOException e) {
                        future.setException(e);
                        return;
                    }

                    try (BufferedReader br =
                                 new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                        future.set(resultParser.process(p, br));
                    }
                    catch (IOException e) {
                        future.setException(e);
                        return;
                    }
                    catch (RuntimeException e) {
                        future.setException(e);
                    }
                },
                "Running adb")
                .start();
        return future;
    }

    public ListenableFuture<AdbVersion> getAdbVersion(@NonNull final File adb) {
        return runAdb(
                adb,
                (Process process, BufferedReader br) -> {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        AdbVersion version = AdbVersion.parseFrom(line);
                        if (version != AdbVersion.UNKNOWN) {
                            return version;
                        }
                        sb.append(line);
                        sb.append('\n');
                    }

                    String errorMessage = "Unable to detect adb version";

                    int exitValue = process.exitValue();
                    if (exitValue != 0) {
                        errorMessage += ", exit value: 0x" + Integer.toHexString(exitValue);

                        // Display special message if it is the STATUS_DLL_NOT_FOUND code, and
                        // ignore adb output since it's empty anyway
                        if (exitValue == STATUS_DLL_NOT_FOUND
                            && SdkConstants.currentPlatform()
                               == SdkConstants.PLATFORM_WINDOWS) {
                            errorMessage +=
                                    ". ADB depends on the Windows Universal C Runtime, which is"
                                    + " usually installed by default via Windows Update. You"
                                    + " may need to manually fetch and install the runtime"
                                    + " package here:"
                                    + " https://support.microsoft.com/en-ca/help/2999226/update-for-universal-c-runtime-in-windows";
                            throw new RuntimeException(errorMessage);
                        }
                    }
                    if (sb.length() > 0) {
                        errorMessage += ", adb output: " + sb.toString();
                    }
                    throw new RuntimeException(errorMessage);
                },
                "version");
    }

    private static ListenableFuture<List<AdbDevice>> getRawDeviceList(@NonNull final File adb) {
        return runAdb(
                adb,
                (Process process, BufferedReader br) -> {
                    // The first line of output is a header, not part of the device list. Skip it.
                    br.readLine();
                    List<AdbDevice> result = new ArrayList<>();
                    String line;
                    while ((line = br.readLine()) != null) {
                        AdbDevice device = AdbDevice.parseAdbLine(line);

                        if (device != null) {
                            result.add(device);
                        }
                    }

                    return result;
                },
                "devices",
                "-l");
    }

    @NonNull
    public ListenableFuture<String> getVirtualDeviceId(
            @NonNull ListeningExecutorService service, @NonNull File adb, @NonNull IDevice device) {
        List<String> command =
                Arrays.asList(adb.toString(), "-s", device.getSerialNumber(), "emu", "avd", "id");

        return execute(
                service, command, AndroidDebugBridgeBase::processVirtualDeviceIdCommandOutput);
    }

    /**
     * Processes the output of an adb -s serial emu avd id command. In the following example,
     * Pixel_3_API_29/snap_2019-10-29_17-06-54 is the virtual device ID. It's simply the argument to
     * the -id flag of the emulator command used to run the virtual device.
     *
     * <pre>
     * $ adb -s emulator-5554 emu avd id
     * Pixel_3_API_29/snap_2019-10-29_17-06-54
     * OK
     * </pre>
     *
     * @return the virtual device ID or the empty string if the output is unexpected
     */
    @NonNull
    private static String processVirtualDeviceIdCommandOutput(
            @NonNull Process process, @NonNull BufferedReader reader) {
        List<String> lines = reader.lines().collect(Collectors.toList());

        if (lines.size() != 2) {
            return "";
        }

        if (!lines.get(1).equals("OK")) {
            return "";
        }

        String result = lines.get(0);
        assert !result.isEmpty();

        return result;
    }

    @NonNull
    private static <T> ListenableFuture<T> execute(
            @NonNull ListeningExecutorService service,
            @NonNull List<String> command,
            @NonNull AndroidDebugBridge.AdbOutputProcessor<T> processor) {
        return service.submit(
                () -> {
                    ProcessBuilder builder = new ProcessBuilder(command);
                    builder.redirectErrorStream(true);

                    Process process = builder.start();

                    try (BufferedReader in =
                                 new BufferedReader(
                                         new InputStreamReader(
                                                 process.getInputStream(),
                                                 StandardCharsets.UTF_8))) {
                        return processor.process(process, in);
                    }
                });
    }

    /**
     * Returns the set of devices reported by the adb command-line. This is mainly intended for the
     * Connection Assistant or other diagnostic tools that need to validate the state of the
     * {@link #getDevices()} list via another channel. Code that just needs to access the list of
     * devices should call {@link #getDevices()} instead.
     */
    public ListenableFuture<List<AdbDevice>> getRawDeviceList() {
        if (mAdbOsLocation == null) {
            SettableFuture<List<AdbDevice>> result = SettableFuture.create();
            result.set(Collections.emptyList());
            return result;
        }
        File adb = new File(mAdbOsLocation);
        return getRawDeviceList(adb);
    }

    /**
     * Notify the listener of a new {@link IDevice}.
     *
     * <p>The notification of the listeners is done in a synchronized block. It is important to
     * expect the listeners to potentially access various methods of {@link IDevice} as well as
     * {@link #getDevices()} which use internal locks.
     *
     * @param device the new <code>IDevice</code>.
     */
    public void deviceConnected(@NonNull IDevice device) {
        adbChangeEvents.notifyDeviceConnected(device);
    }

    /**
     * Notify the listener of a disconnected {@link IDevice}.
     *
     * <p>The notification of the listeners is done in a synchronized block. It is important to
     * expect the listeners to potentially access various methods of {@link IDevice} as well as
     * {@link #getDevices()} which use internal locks.
     *
     * @param device the disconnected <code>IDevice</code>.
     */
    public void deviceDisconnected(@NonNull IDevice device) {
        adbChangeEvents.notifyDeviceDisconnected(device);
    }

    /**
     * Notify the listener of a modified {@link IDevice}.
     *
     * <p>The notification of the listeners is done in a synchronized block. It is important to
     * expect the listeners to potentially access various methods of {@link IDevice} as well as
     * {@link #getDevices()} which use internal locks.
     *
     * @param device the modified <code>IDevice</code>.
     */
    public void deviceChanged(@NonNull IDevice device, int changeMask) {
        // Notify the listeners
        adbChangeEvents.notifyDeviceChanged(device, changeMask);
    }

    /**
     * Notify the listener of a modified {@link ClientImpl}.
     *
     * <p>The notification of the listeners is done in a synchronized block. It is important to
     * expect the listeners to potentially access various methods of {@link IDevice} as well as
     * {@link #getDevices()} which use internal locks.
     *
     * @param client     the modified <code>Client</code>.
     * @param changeMask the mask indicating what changed in the <code>Client</code>
     */
    public void clientChanged(@NonNull Client client, int changeMask) {
        // Notify the listeners
        adbChangeEvents.notifyClientChanged(client, changeMask);
    }

    /**
     * @return If operating in user managed ADB mode where ddmlib will and should not manage the ADB
     * server.
     */
    public boolean isUserManagedAdbMode() {
        return sUserManagedAdbMode;
    }

    @Override
    public String queryFeatures(String adbFeaturesRequest)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        // TODO: implement using adblib
        try (SocketChannel adbChan = AndroidDebugBridge.openConnection()) {
            adbChan.configureBlocking(false);

            byte[] request = formAdbRequest(adbFeaturesRequest);

            write(adbChan, request);

            AdbHelper.AdbResponse resp = readAdbResponse(adbChan, true /* readDiagString */);
            if (!resp.okay) {
                Log.w("features", "Error querying features: " + resp.message);
                throw new AdbCommandRejectedException(resp.message);
            }

            return resp.message;
        }
    }

    /** Instantiates sSocketAddr with the address of the host's adb process. */
    private void initAdbPort(int userManagedAdbPort) {
        // If we're in unit test mode, we already manually set sAdbServerPort.
        if (!sUnitTestMode) {
            if (sUserManagedAdbMode) {
                sAdbServerPort = userManagedAdbPort;
            }
            else {
                sAdbServerPort = getAdbServerPort();
            }
        }
    }

    /**
     * Returns the port where adb server should be launched. This looks at:
     *
     * <ol>
     *   <li>The system property ANDROID_ADB_SERVER_PORT
     *   <li>The environment variable ANDROID_ADB_SERVER_PORT
     *   <li>Defaults to {@link #DEFAULT_ADB_PORT} if neither the system property nor the env var
     *       are set.
     * </ol>
     *
     * @return The port number where the host's adb should be expected or started.
     */
    private static int getAdbServerPort() {
        // check system property
        Integer prop = Integer.getInteger(SERVER_PORT_ENV_VAR);
        if (prop != null) {
            try {
                return validateAdbServerPort(prop.toString());
            }
            catch (IllegalArgumentException e) {
                String msg =
                        String.format(
                                "Invalid value (%1$s) for ANDROID_ADB_SERVER_PORT system property.",
                                prop);
                Log.w(DDMS, msg);
            }
        }

        // when system property is not set or is invalid, parse environment property
        try {
            String env = System.getenv(SERVER_PORT_ENV_VAR);
            if (env != null) {
                return validateAdbServerPort(env);
            }
        }
        catch (SecurityException ex) {
            // A security manager has been installed that doesn't allow access to env vars.
            // So an environment variable might have been set, but we can't tell.
            // Let's log a warning and continue with ADB's default port.
            // The issue is that adb would be started (by the forked process having access
            // to the env vars) on the desired port, but within this process, we can't figure out
            // what that port is. However, a security manager not granting access to env vars
            // but allowing to fork is a rare and interesting configuration, so the right
            // thing seems to be to continue using the default port, as forking is likely to
            // fail later on in the scenario of the security manager.
            Log.w(
                    DDMS,
                    "No access to env variables allowed by current security manager. "
                    + "If you've set ANDROID_ADB_SERVER_PORT: it's being ignored.");
        }
        catch (IllegalArgumentException e) {
            String msg =
                    String.format(
                            "Invalid value (%1$s) for ANDROID_ADB_SERVER_PORT environment variable"
                            + " (%2$s).",
                            prop, e.getMessage());
            Log.w(DDMS, msg);
        }

        // use default port if neither are set
        return DEFAULT_ADB_PORT;
    }

    /**
     * Returns the integer port value if it is a valid value for adb server port
     *
     * @param adbServerPort adb server port to validate
     * @return {@code adbServerPort} as a parsed integer
     * @throws IllegalArgumentException when {@code adbServerPort} is not bigger than 0 or it is not
     *                                  a number at all
     */
    private static int validateAdbServerPort(@NonNull String adbServerPort)
            throws IllegalArgumentException {
        try {
            // C tools (adb, emulator) accept hex and octal port numbers, so need to accept them too
            int port = Integer.decode(adbServerPort);
            if (port <= 0 || port >= 65535) {
                throw new IllegalArgumentException("Should be > 0 and < 65535");
            }
            return port;
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not a valid port number");
        }
    }

    // TODO: This class is currently unused so it could be removed, but before doing that
    //  lets figure out how to trigger `initializationError` from `AdbLibAndroidDebugBridge`
    private static class MonitorErrorHandler implements DeviceMonitor.MonitorErrorHandler {

        @Override
        public void initializationError(@NonNull Exception e) {
            adbChangeEvents.notifyBridgeInitializationError(e);
        }
    }
}
