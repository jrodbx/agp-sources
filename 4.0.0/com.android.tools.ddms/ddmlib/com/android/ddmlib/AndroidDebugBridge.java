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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.GuardedBy;
import com.android.ddmlib.Log.LogLevel;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.io.Closeables;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.Thread.State;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * A connection to the host-side android debug bridge (adb)
 *
 * <p>This is the central point to communicate with any devices, emulators, or the applications
 * running on them.
 *
 * <p><b>{@link #init(boolean)} must be called before anything is done.</b>
 */
public class AndroidDebugBridge {
    /*
     * Minimum and maximum version of adb supported. This correspond to
     * ADB_SERVER_VERSION found in //device/tools/adb/adb.h
     */
    public static final AdbVersion MIN_ADB_VERSION = AdbVersion.parseFrom("1.0.20");

    private static final String ADB = "adb"; //$NON-NLS-1$
    private static final String DDMS = "ddms"; //$NON-NLS-1$
    private static final String SERVER_PORT_ENV_VAR = "ANDROID_ADB_SERVER_PORT"; //$NON-NLS-1$
    private static final String USER_MANAGED_ADB_ENV_VAR = "ANDROID_ADB_USER_MANAGED_MODE"; //$NON-NLS-1$

    // Where to find the ADB bridge.
    static final int DEFAULT_ADB_PORT = 5037;

    // ADB exit value when no Universal C Runtime on Windows
    private static int STATUS_DLL_NOT_FOUND = (int) (long) 0xc0000135;

    // Only set when in unit testing mode. This is a hack until we move to devicelib.
    // http://b.android.com/221925
    private static boolean sUnitTestMode;

    /** Port where adb server will be started **/
    private static int sAdbServerPort = 0;

    /** Don't automatically manage ADB server. */
    private static boolean sUserManagedAdbMode = false;

    private static InetAddress sHostAddr;
    private static InetSocketAddress sSocketAddr;

    private static AndroidDebugBridge sThis;
    private static boolean sInitialized = false;
    private static boolean sClientSupport;
    private static boolean sUseLibusb;
    private static Map<String, String> sEnv; // env vars to set while launching adb

    /** Full path to adb. */
    private String mAdbOsLocation = null;

    private boolean mVersionCheck;

    private boolean mStarted = false;

    private DeviceMonitor mDeviceMonitor;

    // lock object for synchronization
    private static final Object sLock = new Object();

    @GuardedBy("sLock")
    private static final Set<IDebugBridgeChangeListener> sBridgeListeners =
            Sets.newCopyOnWriteArraySet();

    private static final Set<IDeviceChangeListener> sDeviceListeners =
            Sets.newCopyOnWriteArraySet();
    private static final Set<IClientChangeListener> sClientListeners =
            Sets.newCopyOnWriteArraySet();

    /**
     * Classes which implement this interface provide a method that deals with {@link
     * AndroidDebugBridge} changes (including restarts).
     */
    public interface IDebugBridgeChangeListener {
        /**
         * Sent when a new {@link AndroidDebugBridge} is connected.
         * <p>
         * This is sent from a non UI thread.
         * @param bridge the new {@link AndroidDebugBridge} object, null if there were errors while
         *               initializing the bridge
         */
        void bridgeChanged(@Nullable AndroidDebugBridge bridge);

        /**
         * Sent before trigger a restart.
         *
         * <p>Note: Callback is inside a synchronized block so handler should be fast.
         */
        default void restartInitiated() {}

        /**
         * Sent when a restarted is finished.
         *
         * <p>Note: Callback is inside a synchronized block so handler should be fast.
         *
         * @param isSuccessful if the bridge is successfully restarted.
         */
        default void restartCompleted(boolean isSuccessful) {};
    }

    /**
     * Classes which implement this interface provide methods that deal
     * with {@link IDevice} addition, deletion, and changes.
     */
    public interface IDeviceChangeListener {
        /**
         * Sent when the a device is connected to the {@link AndroidDebugBridge}.
         * <p>
         * This is sent from a non UI thread.
         * @param device the new device.
         */
        void deviceConnected(@NonNull IDevice device);

        /**
         * Sent when the a device is connected to the {@link AndroidDebugBridge}.
         * <p>
         * This is sent from a non UI thread.
         * @param device the new device.
         */
        void deviceDisconnected(@NonNull IDevice device);

        /**
         * Sent when a device data changed, or when clients are started/terminated on the device.
         * <p>
         * This is sent from a non UI thread.
         * @param device the device that was updated.
         * @param changeMask the mask describing what changed. It can contain any of the following
         * values: {@link IDevice#CHANGE_BUILD_INFO}, {@link IDevice#CHANGE_STATE},
         * {@link IDevice#CHANGE_CLIENT_LIST}
         */
        void deviceChanged(@NonNull IDevice device, int changeMask);
    }

    /**
     * Classes which implement this interface provide methods that deal
     * with {@link Client}  changes.
     */
    public interface IClientChangeListener {
        /**
         * Sent when an existing client information changed.
         * <p>
         * This is sent from a non UI thread.
         * @param client the updated client.
         * @param changeMask the bit mask describing the changed properties. It can contain
         * any of the following values: {@link Client#CHANGE_INFO},
         * {@link Client#CHANGE_DEBUGGER_STATUS}, {@link Client#CHANGE_THREAD_MODE},
         * {@link Client#CHANGE_THREAD_DATA}, {@link Client#CHANGE_HEAP_MODE},
         * {@link Client#CHANGE_HEAP_DATA}, {@link Client#CHANGE_NATIVE_HEAP_DATA}
         */
        void clientChanged(@NonNull Client client, int changeMask);
    }

    /**
     * Initialized the library only if needed.
     *
     * @param clientSupport Indicates whether the library should enable the monitoring and
     *                      interaction with applications running on the devices.
     *
     * @see #init(boolean)
     */
    public static synchronized void initIfNeeded(boolean clientSupport) {
        if (sInitialized) {
            return;
        }

        init(clientSupport);
    }

    /**
     * Initializes the <code>ddm</code> library.
     * <p>This must be called once <b>before</b> any call to
     * {@link #createBridge(String, boolean)}.
     * <p>The library can be initialized in 2 ways:
     * <ul>
     * <li>Mode 1: <var>clientSupport</var> == <code>true</code>.<br>The library monitors the
     * devices and the applications running on them. It will connect to each application, as a
     * debugger of sort, to be able to interact with them through JDWP packets.</li>
     * <li>Mode 2: <var>clientSupport</var> == <code>false</code>.<br>The library only monitors
     * devices. The applications are left untouched, letting other tools built on
     * <code>ddmlib</code> to connect a debugger to them.</li>
     * </ul>
     * <p><b>Only one tool can run in mode 1 at the same time.</b>
     * <p>Note that mode 1 does not prevent debugging of applications running on devices. Mode 1
     * lets debuggers connect to <code>ddmlib</code> which acts as a proxy between the debuggers and
     * the applications to debug. See {@link Client#getDebuggerListenPort()}.
     * <p>The preferences of <code>ddmlib</code> should also be initialized with whatever default
     * values were changed from the default values.
     * <p>When the application quits, {@link #terminate()} should be called.
     * @param clientSupport Indicates whether the library should enable the monitoring and
     *                      interaction with applications running on the devices.
     * @see AndroidDebugBridge#createBridge(String, boolean)
     * @see DdmPreferences
     */
    public static synchronized void init(boolean clientSupport) {
        init(clientSupport, false, ImmutableMap.of());
    }

    public static synchronized void init(
            boolean clientSupport, boolean useLibusb, @NonNull Map<String, String> env) {
        Preconditions.checkState(
                !sInitialized, "AndroidDebugBridge.init() has already been called.");
        sInitialized = true;
        sClientSupport = clientSupport;
        sUseLibusb = useLibusb;
        sEnv = env;
        String userManagedAdbModeSetting = System.getenv(USER_MANAGED_ADB_ENV_VAR);
        if (userManagedAdbModeSetting != null) {
            sUserManagedAdbMode = Boolean.parseBoolean(userManagedAdbModeSetting);
        }

        // Determine port and instantiate socket address.
        initAdbSocketAddr();

        MonitorThread monitorThread = MonitorThread.createInstance();
        monitorThread.start();

        HandleHello.register(monitorThread);
        HandleAppName.register(monitorThread);
        HandleTest.register(monitorThread);
        HandleThread.register(monitorThread);
        HandleHeap.register(monitorThread);
        HandleWait.register(monitorThread);
        HandleProfiling.register(monitorThread);
        HandleNativeHeap.register(monitorThread);
        HandleViewDebug.register(monitorThread);
    }

    @VisibleForTesting
    public static void enableFakeAdbServerMode(int port) {
        Preconditions.checkState(
                !sInitialized,
                "AndroidDebugBridge.init() has already been called or "
                        + "terminate() has not been called yet.");
        sUnitTestMode = true;
        sAdbServerPort = port;
    }

    @VisibleForTesting
    public static void disableFakeAdbServerMode() {
        Preconditions.checkState(
                !sInitialized,
                "AndroidDebugBridge.init() has already been called or "
                        + "terminate() has not been called yet.");
        sUnitTestMode = false;
        sAdbServerPort = 0;
    }

    /**
     * Terminates the ddm library. This must be called upon application termination.
     */
    public static synchronized void terminate() {
        // kill the monitoring services
        if (sThis != null && sThis.mDeviceMonitor != null) {
            sThis.mDeviceMonitor.stop();
            sThis.mDeviceMonitor = null;
        }

        MonitorThread monitorThread = MonitorThread.getInstance();
        if (monitorThread != null) {
            monitorThread.quit();
        }

        sInitialized = false;
        sThis = null;
    }

    /**
     * Returns whether the ddmlib is setup to support monitoring and interacting with
     * {@link Client}s running on the {@link IDevice}s.
     */
    static boolean getClientSupport() {
        return sClientSupport;
    }

    /**
     * Returns the socket address of the ADB server on the host.
     */
    public static InetSocketAddress getSocketAddress() {
        return sSocketAddr;
    }

    /**
     * Creates a {@link AndroidDebugBridge} that is not linked to any particular executable.
     * <p>This bridge will expect adb to be running. It will not be able to start/stop/restart
     * adb.
     * <p>If a bridge has already been started, it is directly returned with no changes (similar
     * to calling {@link #getBridge()}).
     * @return a connected bridge.
     */
    public static AndroidDebugBridge createBridge() {
        synchronized (sLock) {
            if (sThis != null) {
                return sThis;
            }

            try {
                sThis = new AndroidDebugBridge();
                sThis.start();
            } catch (InvalidParameterException e) {
                sThis = null;
            }

            // notify the listeners of the change
            for (IDebugBridgeChangeListener listener : sBridgeListeners) {
                // we attempt to catch any exception so that a bad listener doesn't kill our thread
                try {
                    listener.bridgeChanged(sThis);
                } catch (Exception e) {
                    Log.e(DDMS, e);
                }
            }

            return sThis;
        }
    }


    /**
     * Creates a new debug bridge from the location of the command line tool. <p>
     * Any existing server will be disconnected, unless the location is the same and
     * <code>forceNewBridge</code> is set to false.
     * @param osLocation the location of the command line tool 'adb'
     * @param forceNewBridge force creation of a new bridge even if one with the same location
     * already exists.
     * @return a connected bridge, or null if there were errors while creating or connecting
     * to the bridge
     */
    @Nullable
    public static AndroidDebugBridge createBridge(@NonNull String osLocation,
                                                  boolean forceNewBridge) {
        synchronized (sLock) {
            if (!sUnitTestMode) {
                if (sThis != null) {
                    if (sThis.mAdbOsLocation != null
                            && sThis.mAdbOsLocation.equals(osLocation)
                            && !forceNewBridge) {
                        return sThis;
                    } else {
                        // stop the current server
                        sThis.stop();
                    }
                }
            }

            try {
                sThis = new AndroidDebugBridge(osLocation);
                if (!sThis.start()) {
                    return null;
                }
            } catch (InvalidParameterException e) {
                sThis = null;
            }

            // notify the listeners of the change
            for (IDebugBridgeChangeListener listener : sBridgeListeners) {
                // we attempt to catch any exception so that a bad listener doesn't kill our thread
                try {
                    listener.bridgeChanged(sThis);
                } catch (Exception e) {
                    Log.e(DDMS, e);
                }
            }

            return sThis;
        }
    }

    /**
     * Returns the current debug bridge. Can be <code>null</code> if none were created.
     */
    @Nullable
    public static AndroidDebugBridge getBridge() {
        return sThis;
    }

    /**
     * Disconnects the current debug bridge, and destroy the object.
     * <p>This also stops the current adb host server.
     * <p>
     * A new object will have to be created with {@link #createBridge(String, boolean)}.
     */
    public static void disconnectBridge() {
        synchronized (sLock) {
            if (sThis != null) {
                sThis.stop();
                sThis = null;

                // notify the listeners.
                for (IDebugBridgeChangeListener listener : sBridgeListeners) {
                    // we attempt to catch any exception so that a bad listener doesn't kill our
                    // thread
                    try {
                        listener.bridgeChanged(sThis);
                    } catch (Exception e) {
                        Log.e(DDMS, e);
                    }
                }
            }
        }
    }

    /**
     * Adds the listener to the collection of listeners who will be notified when a new
     * {@link AndroidDebugBridge} is connected, by sending it one of the messages defined
     * in the {@link IDebugBridgeChangeListener} interface.
     * @param listener The listener which should be notified.
     */
    public static void addDebugBridgeChangeListener(@NonNull IDebugBridgeChangeListener listener) {
        synchronized (sLock) {
            sBridgeListeners.add(listener);

            if (sThis != null) {
                // we attempt to catch any exception so that a bad listener doesn't kill our thread
                try {
                    listener.bridgeChanged(sThis);
                } catch (Exception e) {
                    Log.e(DDMS, e);
                }
            }
        }
    }

    /**
     * Removes the listener from the collection of listeners who will be notified when a new
     * {@link AndroidDebugBridge} is started.
     * @param listener The listener which should no longer be notified.
     */
    public static void removeDebugBridgeChangeListener(IDebugBridgeChangeListener listener) {
        synchronized (sLock) {
            sBridgeListeners.remove(listener);
        }
    }

    /**
     * Adds the listener to the collection of listeners who will be notified when a {@link IDevice}
     * is connected, disconnected, or when its properties or its {@link Client} list changed, by
     * sending it one of the messages defined in the {@link IDeviceChangeListener} interface.
     *
     * @param listener The listener which should be notified.
     */
    public static void addDeviceChangeListener(@NonNull IDeviceChangeListener listener) {
        sDeviceListeners.add(listener);
    }

    /**
     * Removes the listener from the collection of listeners who will be notified when a {@link
     * IDevice} is connected, disconnected, or when its properties or its {@link Client} list
     * changed.
     *
     * @param listener The listener which should no longer be notified.
     */
    public static void removeDeviceChangeListener(IDeviceChangeListener listener) {
        sDeviceListeners.remove(listener);
    }

    /**
     * Adds the listener to the collection of listeners who will be notified when a {@link Client}
     * property changed, by sending it one of the messages defined in the {@link
     * IClientChangeListener} interface.
     *
     * @param listener The listener which should be notified.
     */
    public static void addClientChangeListener(IClientChangeListener listener) {
        sClientListeners.add(listener);
    }

    /**
     * Removes the listener from the collection of listeners who will be notified when a {@link
     * Client} property changes.
     *
     * @param listener The listener which should no longer be notified.
     */
    public static void removeClientChangeListener(IClientChangeListener listener) {
        sClientListeners.remove(listener);
    }


    /**
     * Returns the devices.
     * @see #hasInitialDeviceList()
     */
    @NonNull
    public IDevice[] getDevices() {
        synchronized (sLock) {
            if (mDeviceMonitor != null) {
                return mDeviceMonitor.getDevices();
            }
        }

        return new IDevice[0];
    }

    /**
     * Returns whether the bridge has acquired the initial list from adb after being created.
     * <p>Calling {@link #getDevices()} right after {@link #createBridge(String, boolean)} will
     * generally result in an empty list. This is due to the internal asynchronous communication
     * mechanism with <code>adb</code> that does not guarantee that the {@link IDevice} list has been
     * built before the call to {@link #getDevices()}.
     * <p>The recommended way to get the list of {@link IDevice} objects is to create a
     * {@link IDeviceChangeListener} object.
     */
    public boolean hasInitialDeviceList() {
        if (mDeviceMonitor != null) {
            return mDeviceMonitor.hasInitialDeviceList();
        }

        return false;
    }

    /**
     * Sets the client to accept debugger connection on the custom "Selected debug port".
     * @param selectedClient the client. Can be null.
     */
    public void setSelectedClient(Client selectedClient) {
        MonitorThread monitorThread = MonitorThread.getInstance();
        if (monitorThread != null) {
            monitorThread.setSelectedClient(selectedClient);
        }
    }

    /**
     * Returns whether the {@link AndroidDebugBridge} object is still connected to the adb daemon.
     */
    public boolean isConnected() {
        MonitorThread monitorThread = MonitorThread.getInstance();
        if (mDeviceMonitor != null && monitorThread != null) {
            return mDeviceMonitor.isMonitoring() && monitorThread.getState() != State.TERMINATED;
        }
        return false;
    }

    /**
     * Returns the number of times the {@link AndroidDebugBridge} object attempted to connect
     * to the adb daemon.
     */
    public int getConnectionAttemptCount() {
        if (mDeviceMonitor != null) {
            return mDeviceMonitor.getConnectionAttemptCount();
        }
        return -1;
    }

    /**
     * Returns the number of times the {@link AndroidDebugBridge} object attempted to restart
     * the adb daemon.
     */
    public int getRestartAttemptCount() {
        if (mDeviceMonitor != null) {
            return mDeviceMonitor.getRestartAttemptCount();
        }
        return -1;
    }

    /**
     * Creates a new bridge.
     * @param osLocation the location of the command line tool
     * @throws InvalidParameterException
     */
    private AndroidDebugBridge(String osLocation) throws InvalidParameterException {
        if (osLocation == null || osLocation.isEmpty()) {
            throw new InvalidParameterException();
        }
        mAdbOsLocation = osLocation;

        try {
            checkAdbVersion();
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Creates a new bridge not linked to any particular adb executable.
     */
    private AndroidDebugBridge() {
    }

    /**
     * Queries adb for its version number and checks that it is atleast {@link #MIN_ADB_VERSION}.
     */
    private void checkAdbVersion() throws IOException {
        // default is bad check
        mVersionCheck = false;

        if (mAdbOsLocation == null) {
            return;
        }

        File adb = new File(mAdbOsLocation);
        ListenableFuture<AdbVersion> future = getAdbVersion(adb);
        AdbVersion version;
        try {
            version = future.get(DdmPreferences.getTimeOut(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return;
        } catch (java.util.concurrent.TimeoutException e) {
            String msg = "Unable to obtain result of 'adb version'";
            Log.logAndDisplay(LogLevel.ERROR, ADB, msg);
            return;
        } catch (ExecutionException e) {
            Log.logAndDisplay(LogLevel.ERROR, ADB, e.getCause().getMessage());
            Throwables.propagateIfInstanceOf(e.getCause(), IOException.class);
            return;
        }

        if (version.compareTo(MIN_ADB_VERSION) > 0) {
            mVersionCheck = true;
        } else {
            String message = String.format(
                    "Required minimum version of adb: %1$s."
                            + "Current version is %2$s", MIN_ADB_VERSION, version);
            Log.logAndDisplay(LogLevel.ERROR, ADB, message);
        }
    }

    interface AdbOutputProcessor<T> {
        T process(Process process, BufferedReader r) throws IOException;
    }

    /** @deprecated Use {@link #execute} which lets you inject an executor */
    @Deprecated
    private static <T> ListenableFuture<T> runAdb(
            @NonNull final File adb, AdbOutputProcessor<T> resultParser, String... command) {
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
                            } catch (IOException e) {
                                future.setException(e);
                                return;
                            }

                            try (BufferedReader br =
                                    new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                                future.set(resultParser.process(p, br));
                            } catch (IOException e) {
                                future.setException(e);
                                return;
                            } catch (RuntimeException e) {
                                future.setException(e);
                            }
                        },
                        "Running adb")
                .start();
        return future;
    }

    public static ListenableFuture<AdbVersion> getAdbVersion(@NonNull final File adb) {
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

                        // Display special message if it is the STATUS_DLL_NOT_FOUND code, and ignore adb output since it's empty anyway
                        if (exitValue == STATUS_DLL_NOT_FOUND
                                && SdkConstants.currentPlatform()
                                        == SdkConstants.PLATFORM_WINDOWS) {
                            errorMessage +=
                                    ". ADB depends on the Windows Universal C Runtime,"
                                            + " which is usually installed by default via Windows Update."
                                            + " You may need to manually fetch and install the runtime package here:"
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
    public static ListenableFuture<String> getVirtualDeviceId(
            @NonNull ListeningExecutorService service, @NonNull File adb, @NonNull IDevice device) {
        List<String> command =
                Arrays.asList(adb.toString(), "-s", device.getSerialNumber(), "emu", "avd", "id");

        return execute(service, command, AndroidDebugBridge::processVirtualDeviceIdCommandOutput);
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
            @NonNull AdbOutputProcessor<T> processor) {
        return service.submit(
                () -> {
                    ProcessBuilder builder = new ProcessBuilder(command);
                    builder.redirectErrorStream(true);

                    Process process = builder.start();

                    try (BufferedReader in =
                            new BufferedReader(
                                    new InputStreamReader(
                                            process.getInputStream(), StandardCharsets.UTF_8))) {
                        return processor.process(process, in);
                    }
                });
    }

    /**
     * Returns the set of devices reported by the adb command-line. This is mainly intended for the
     * Connection Assistant or other diagnostic tools that need to validate the state of the {@link
     * #getDevices()} list via another channel. Code that just needs to access the list of devices
     * should call {@link #getDevices()} instead.
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
     * Starts the debug bridge.
     *
     * @return true if success.
     */
    boolean start() {
        // Skip server start check if using user managed ADB server
        if (!sUserManagedAdbMode) {
            if (mAdbOsLocation != null && sAdbServerPort != 0 && (!mVersionCheck || !startAdb())) {
                return false;
            }
        }

        mStarted = true;

        // Start the underlying services.
        mDeviceMonitor = new DeviceMonitor(this);
        mDeviceMonitor.start();

        return true;
    }

   /**
     * Kills the debug bridge, and the adb host server.
     * @return true if success
     */
    boolean stop() {
        // if we haven't started we return false;
        if (!mStarted) {
            return false;
        }

        // kill the monitoring services
        if (mDeviceMonitor != null) {
            mDeviceMonitor.stop();
            mDeviceMonitor = null;
        }

        // Don't stop ADB when using user managed ADB server.
        if (sUserManagedAdbMode) {
            Log.i(DDMS, "User managed ADB mode: Not stopping ADB server");
        } else if (!stopAdb()) {
            return false;
        }

        mStarted = false;
        return true;
    }

    /**
     * Restarts adb, but not the services around it.
     * @return true if success.
     */
    public boolean restart() {
        if (sUserManagedAdbMode) {
            Log.e(ADB, "Cannot restart adb when using user managed ADB server."); //$NON-NLS-1$
            return false;
        }

        if (mAdbOsLocation == null) {
            Log.e(ADB,
                    "Cannot restart adb when AndroidDebugBridge is created without the location of adb."); //$NON-NLS-1$
            return false;
        }

        if (sAdbServerPort == 0) {
            Log.e(ADB, "ADB server port for restarting AndroidDebugBridge is not set."); //$NON-NLS-1$
            return false;
        }

        if (!mVersionCheck) {
            Log.logAndDisplay(LogLevel.ERROR, ADB,
                    "Attempting to restart adb, but version check failed!"); //$NON-NLS-1$
            return false;
        }

        synchronized (sLock) {
            for (IDebugBridgeChangeListener listener : sBridgeListeners) {
                listener.restartInitiated();
            }
        }
        boolean restart;
        synchronized (this) {
            stopAdb();

            restart = startAdb();

            if (restart && mDeviceMonitor == null) {
                mDeviceMonitor = new DeviceMonitor(this);
                mDeviceMonitor.start();
            }
        }

        synchronized (sLock) {
            for (IDebugBridgeChangeListener listener : sBridgeListeners) {
                listener.restartCompleted(restart);
            }
        }

        return restart;
    }

    /**
     * Notify the listener of a new {@link IDevice}.
     * <p>
     * The notification of the listeners is done in a synchronized block. It is important to
     * expect the listeners to potentially access various methods of {@link IDevice} as well as
     * {@link #getDevices()} which use internal locks.
     * <p>
     * For this reason, any call to this method from a method of {@link DeviceMonitor},
     * {@link IDevice} which is also inside a synchronized block, should first synchronize on
     * the {@link AndroidDebugBridge} lock. Access to this lock is done through {@link #getLock()}.
     * @param device the new <code>IDevice</code>.
     * @see #getLock()
     */
    static void deviceConnected(@NonNull IDevice device) {
        for (IDeviceChangeListener listener : sDeviceListeners) {
            // we attempt to catch any exception so that a bad listener doesn't kill our thread
            try {
                listener.deviceConnected(device);
            } catch (Exception e) {
                Log.e(DDMS, e);
            }
        }
    }

    /**
     * Notify the listener of a disconnected {@link IDevice}.
     * <p>
     * The notification of the listeners is done in a synchronized block. It is important to
     * expect the listeners to potentially access various methods of {@link IDevice} as well as
     * {@link #getDevices()} which use internal locks.
     * <p>
     * For this reason, any call to this method from a method of {@link DeviceMonitor},
     * {@link IDevice} which is also inside a synchronized block, should first synchronize on
     * the {@link AndroidDebugBridge} lock. Access to this lock is done through {@link #getLock()}.
     * @param device the disconnected <code>IDevice</code>.
     * @see #getLock()
     */
    static void deviceDisconnected(@NonNull IDevice device) {
        for (IDeviceChangeListener listener : sDeviceListeners) {
            // we attempt to catch any exception so that a bad listener doesn't kill our
            // thread
            try {
                listener.deviceDisconnected(device);
            } catch (Exception e) {
                Log.e(DDMS, e);
            }
        }
    }

    /**
     * Notify the listener of a modified {@link IDevice}.
     * <p>
     * The notification of the listeners is done in a synchronized block. It is important to
     * expect the listeners to potentially access various methods of {@link IDevice} as well as
     * {@link #getDevices()} which use internal locks.
     * <p>
     * For this reason, any call to this method from a method of {@link DeviceMonitor},
     * {@link IDevice} which is also inside a synchronized block, should first synchronize on
     * the {@link AndroidDebugBridge} lock. Access to this lock is done through {@link #getLock()}.
     * @param device the modified <code>IDevice</code>.
     * @see #getLock()
     */
    static void deviceChanged(@NonNull IDevice device, int changeMask) {
        // Notify the listeners
        for (IDeviceChangeListener listener : sDeviceListeners) {
            // we attempt to catch any exception so that a bad listener doesn't kill our
            // thread
            try {
                listener.deviceChanged(device, changeMask);
            } catch (Exception e) {
                Log.e(DDMS, e);
            }
        }
    }

    /**
     * Notify the listener of a modified {@link Client}.
     * <p>
     * The notification of the listeners is done in a synchronized block. It is important to
     * expect the listeners to potentially access various methods of {@link IDevice} as well as
     * {@link #getDevices()} which use internal locks.
     * <p>
     * For this reason, any call to this method from a method of {@link DeviceMonitor},
     * {@link IDevice} which is also inside a synchronized block, should first synchronize on
     * the {@link AndroidDebugBridge} lock. Access to this lock is done through {@link #getLock()}.
     * @param client the modified <code>Client</code>.
     * @param changeMask the mask indicating what changed in the <code>Client</code>
     * @see #getLock()
     */
    static void clientChanged(@NonNull Client client, int changeMask) {
        // Notify the listeners
        for (IClientChangeListener listener : sClientListeners) {
            // we attempt to catch any exception so that a bad listener doesn't kill our
            // thread
            try {
                listener.clientChanged(client, changeMask);
            } catch (Exception e) {
                Log.e(DDMS, e);
            }
        }
    }

    /**
     * Returns the {@link DeviceMonitor} object.
     */
    DeviceMonitor getDeviceMonitor() {
        return mDeviceMonitor;
    }

    /**
     * @return If operating in user managed ADB mode where ddmlib will and should not manage the ADB server.
     */
    public static boolean isUserManagedAdbMode() {
        return sUserManagedAdbMode;
    }

    /**
     * Starts the adb host side server.  This method should not be used when using user managed
     * ADB server as the server lifecycle should be managed by the user, not ddmlib.
     *
     * @return true if success
     */
    synchronized boolean startAdb() {
        if (sUserManagedAdbMode) {
            Log.e(ADB, "startADB should never be called when using user managed ADB server.");
            return false;
        }

        if (sUnitTestMode) {
            // in this case, we assume the FakeAdbServer was already setup by the test code
            return true;
        }

        if (mAdbOsLocation == null) {
            Log.e(ADB,
                "Cannot start adb when AndroidDebugBridge is created without the location of adb."); //$NON-NLS-1$
            return false;
        }

        if (sAdbServerPort == 0) {
            Log.w(ADB, "ADB server port for starting AndroidDebugBridge is not set."); //$NON-NLS-1$
            return false;
        }

        Process proc;
        int status = -1;

        String[] command = getAdbLaunchCommand("start-server");
        String commandString = Joiner.on(' ').join(command);
        try {
            Log.d(DDMS, String.format("Launching '%1$s' to ensure ADB is running.", commandString));
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Map<String, String> env = processBuilder.environment();
            env.put("ADB_LIBUSB", sUseLibusb ? "1" : "0");
            sEnv.forEach(env::put);
            if (DdmPreferences.getUseAdbHost()) {
                String adbHostValue = DdmPreferences.getAdbHostValue();
                if (adbHostValue != null && !adbHostValue.isEmpty()) {
                    //TODO : check that the String is a valid IP address
                    env.put("ADBHOST", adbHostValue);
                }
            }
            proc = processBuilder.start();

            ArrayList<String> errorOutput = new ArrayList<String>();
            ArrayList<String> stdOutput = new ArrayList<String>();
            status = grabProcessOutput(proc, errorOutput, stdOutput, false /* waitForReaders */);
        } catch (IOException ioe) {
            Log.e(DDMS, "Unable to run 'adb': " + ioe.getMessage()); //$NON-NLS-1$
            // we'll return false;
        } catch (InterruptedException ie) {
            Log.e(DDMS, "Unable to run 'adb': " + ie.getMessage()); //$NON-NLS-1$
            // we'll return false;
        }

        if (status != 0) {
            Log.e(DDMS,
                String.format("'%1$s' failed -- run manually if necessary", commandString)); //$NON-NLS-1$
            return false;
        } else {
            Log.d(DDMS, String.format("'%1$s' succeeded", commandString)); //$NON-NLS-1$
            return true;
        }
    }

    private String[] getAdbLaunchCommand(String option) {
        List<String> command = new ArrayList<String>(4);
        command.add(mAdbOsLocation);
        if (sAdbServerPort != DEFAULT_ADB_PORT) {
            command.add("-P"); //$NON-NLS-1$
            command.add(Integer.toString(sAdbServerPort));
        }
        command.add(option);
        return command.toArray(new String[0]);
    }

    /**
     * Stops the adb host side server.
     *
     * @return true if success
     */
    private synchronized boolean stopAdb() {
        if (sUserManagedAdbMode) {
            Log.e(ADB, "stopADB should never be called when using user managed ADB server.");
            return false;
        }

        if (mAdbOsLocation == null) {
            Log.e(ADB,
                "Cannot stop adb when AndroidDebugBridge is created without the location of adb.");
            return false;
        }

        if (sAdbServerPort == 0) {
            Log.e(ADB, "ADB server port for restarting AndroidDebugBridge is not set");
            return false;
        }

        Process proc;
        int status = -1;

        String[] command = getAdbLaunchCommand("kill-server"); //$NON-NLS-1$
        try {
            proc = Runtime.getRuntime().exec(command);
            status = proc.waitFor();
        }
        catch (IOException ioe) {
            // we'll return false;
        }
        catch (InterruptedException ie) {
            // we'll return false;
        }

        String commandString = Joiner.on(' ').join(command);
        if (status != 0) {
            Log.w(DDMS, String.format("'%1$s' failed -- run manually if necessary", commandString));
            return false;
        } else {
            Log.d(DDMS, String.format("'%1$s' succeeded", commandString));
            return true;
        }
    }

    /**
     * Get the stderr/stdout outputs of a process and return when the process is done.
     * Both <b>must</b> be read or the process will block on windows.
     * @param process The process to get the output from
     * @param errorOutput The array to store the stderr output. cannot be null.
     * @param stdOutput The array to store the stdout output. cannot be null.
     * @param waitForReaders if true, this will wait for the reader threads.
     * @return the process return code.
     * @throws InterruptedException
     */
    private static int grabProcessOutput(final Process process, final ArrayList<String> errorOutput,
      final ArrayList<String> stdOutput, boolean waitForReaders)
            throws InterruptedException {
        assert errorOutput != null;
        assert stdOutput != null;
        // read the lines as they come. if null is returned, it's
        // because the process finished
        Thread t1 = new Thread("adb:stderr reader") { //$NON-NLS-1$
            @Override
            public void run() {
                // create a buffer to read the stderr output
                InputStreamReader is = new InputStreamReader(process.getErrorStream(),
                  Charsets.UTF_8);
                BufferedReader errReader = new BufferedReader(is);

                try {
                    while (true) {
                        String line = errReader.readLine();
                        if (line != null) {
                            Log.e(ADB, line);
                            errorOutput.add(line);
                        } else {
                            break;
                        }
                    }
                } catch (IOException e) {
                    // do nothing.
                } finally {
                    Closeables.closeQuietly(errReader);
                }
            }
        };

        Thread t2 = new Thread("adb:stdout reader") { //$NON-NLS-1$
            @Override
            public void run() {
                InputStreamReader is = new InputStreamReader(process.getInputStream(),
                  Charsets.UTF_8);
                BufferedReader outReader = new BufferedReader(is);

                try {
                    while (true) {
                        String line = outReader.readLine();
                        if (line != null) {
                            Log.d(ADB, line);
                            stdOutput.add(line);
                        } else {
                            break;
                        }
                    }
                } catch (IOException e) {
                    // do nothing.
                } finally {
                    Closeables.closeQuietly(outReader);
                }
            }
        };

        t1.start();
        t2.start();

        // it looks like on windows process#waitFor() can return
        // before the thread have filled the arrays, so we wait for both threads and the
        // process itself.
        if (waitForReaders) {
            try {
                t1.join();
            } catch (InterruptedException e) {
            }
            try {
                t2.join();
            } catch (InterruptedException e) {
            }
        }

        // get the return code from the process
        return process.waitFor();
    }

    /**
     * Returns the singleton lock used by this class to protect any access to the listener.
     * <p>
     * This includes adding/removing listeners, but also notifying listeners of new bridges,
     * devices, and clients.
     */
    private static Object getLock() {
        return sLock;
    }

    /**
     * Instantiates sSocketAddr with the address of the host's adb process.
     */
    private static void initAdbSocketAddr() {
        // If we're in unit test mode, we already manually set sAdbServerPort.
        if (!sUnitTestMode) {
            sAdbServerPort = getAdbServerPort();
        }
        sHostAddr = InetAddress.getLoopbackAddress();
        sSocketAddr = new InetSocketAddress(sHostAddr, sAdbServerPort);
    }

    /**
     * Returns the port where adb server should be launched. This looks at:
     * <ol>
     *     <li>The system property ANDROID_ADB_SERVER_PORT</li>
     *     <li>The environment variable ANDROID_ADB_SERVER_PORT</li>
     *     <li>Defaults to {@link #DEFAULT_ADB_PORT} if neither the system property nor the env var
     *     are set.</li>
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
            } catch (IllegalArgumentException e) {
                String msg = String.format(
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
        } catch (SecurityException ex) {
            // A security manager has been installed that doesn't allow access to env vars.
            // So an environment variable might have been set, but we can't tell.
            // Let's log a warning and continue with ADB's default port.
            // The issue is that adb would be started (by the forked process having access
            // to the env vars) on the desired port, but within this process, we can't figure out
            // what that port is. However, a security manager not granting access to env vars
            // but allowing to fork is a rare and interesting configuration, so the right
            // thing seems to be to continue using the default port, as forking is likely to
            // fail later on in the scenario of the security manager.
            Log.w(DDMS,
                    "No access to env variables allowed by current security manager. "
                            + "If you've set ANDROID_ADB_SERVER_PORT: it's being ignored.");
        } catch (IllegalArgumentException e) {
            String msg = String.format(
                    "Invalid value (%1$s) for ANDROID_ADB_SERVER_PORT environment variable (%2$s).",
                    prop, e.getMessage());
            Log.w(DDMS, msg);
        }

        // use default port if neither are set
        return DEFAULT_ADB_PORT;
    }

    /**
     * Returns the integer port value if it is a valid value for adb server port
     * @param adbServerPort adb server port to validate
     * @return {@code adbServerPort} as a parsed integer
     * @throws IllegalArgumentException when {@code adbServerPort} is not bigger than 0 or it is
     * not a number at all
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
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not a valid port number");
        }
    }
}
