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
import static com.android.ddmlib.AndroidDebugBridge.MIN_ADB_VERSION;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.clientmanager.ClientManager;
import com.android.ddmlib.idevicemanager.IDeviceManager;
import com.android.ddmlib.idevicemanager.IDeviceManagerFactory;
import com.android.ddmlib.idevicemanager.IDeviceManagerUtils;
import com.android.ddmlib.internal.ClientImpl;
import com.android.ddmlib.internal.DeviceMonitor;
import com.android.ddmlib.internal.MonitorThread;
import com.android.ddmlib.internal.jdwp.chunkhandler.HandleARTT;
import com.android.ddmlib.internal.jdwp.chunkhandler.HandleAppName;
import com.android.ddmlib.internal.jdwp.chunkhandler.HandleHeap;
import com.android.ddmlib.internal.jdwp.chunkhandler.HandleHello;
import com.android.ddmlib.internal.jdwp.chunkhandler.HandleNativeHeap;
import com.android.ddmlib.internal.jdwp.chunkhandler.HandleProfiling;
import com.android.ddmlib.internal.jdwp.chunkhandler.HandleSTAG;
import com.android.ddmlib.internal.jdwp.chunkhandler.HandleTest;
import com.android.ddmlib.internal.jdwp.chunkhandler.HandleThread;
import com.android.ddmlib.internal.jdwp.chunkhandler.HandleViewDebug;
import com.android.ddmlib.internal.jdwp.chunkhandler.HandleWait;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closeables;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class AndroidDebugBridgeImpl extends AndroidDebugBridgeBase {
    /** Default timeout used when starting the ADB server */
    public static final int DEFAULT_START_ADB_TIMEOUT_MILLIS = 20_000;

    private static final String ADB = "adb"; // $NON-NLS-1$
    private static final String DDMS = "ddms"; // $NON-NLS-1$
    private static final String SERVER_PORT_ENV_VAR = "ANDROID_ADB_SERVER_PORT"; // $NON-NLS-1$

    // Where to find the ADB bridge.
    public static final int DEFAULT_ADB_PORT = 5037;

    // ADB exit value when no Universal C Runtime on Windows
    private static final int STATUS_DLL_NOT_FOUND = (int) (long) 0xc0000135;

    // Only set when in unit testing mode. This is a hack until we move to devicelib.
    // http://b.android.com/221925
    private static boolean sUnitTestMode;

    /** Port where adb server will be started */
    private static int sAdbServerPort = 0;

    /** Don't automatically manage ADB server. */
    private static boolean sUserManagedAdbMode = false;

    private static final Object sLastKnownGoodAddressLock = new Object();

    /** Last known good {@link InetSocketAddress} to ADB. */
    private static InetSocketAddress sLastKnownGoodAddress;

    private volatile AndroidDebugBridge sThis;
    private static boolean sInitialized = false;
    private static boolean sClientSupport;
    private static ClientManager sClientManager;
    private static IDeviceManagerFactory sIDeviceManagerFactory;
    private static IDeviceUsageTracker iDeviceUsageTracker;
    private static AdbDelegateUsageTracker adbDelegateUsageTracker;
    private static Map<String, String> sAdbEnvVars; // env vars to set while launching adb

    /** Full path to adb. */
    private String mAdbOsLocation = null;

    private AdbVersion mAdbVersion;
    private boolean mVersionCheck;

    private boolean mStarted = false;

    private DeviceMonitor mDeviceMonitor;

    private IDeviceManager mIDeviceManager;

    // lock object for synchronization
    private static final Object sLock = new Object();

    /**
     * Initialized the library only if needed; deprecated for non-test usages.
     *
     * @param clientSupport Indicates whether the library should enable the monitoring and
     *     interaction with applications running on the devices.
     * @see #init(boolean)
     */
    @Deprecated
    public synchronized void initIfNeeded(boolean clientSupport) {
        logRun(
                AdbDelegateUsageTracker.Method.INIT_IF_NEEDED,
                () -> {
                    if (sInitialized) {
                        return;
                    }
                    init(clientSupport);
                });
    }

    /**
     * Initializes the <code>ddm</code> library.
     *
     * <p>This must be called once <b>before</b> any call to {@link #createBridge(String, boolean)}.
     *
     * <p>The preferences of <code>ddmlib</code> should also be initialized with whatever default
     * values were changed from the default values.
     *
     * <p>When the application quits, {@link #terminate()} should be called.
     *
     * @param clientSupport Indicates whether the library should enable the monitoring and
     *     interaction with applications running on the devices.
     * @see AndroidDebugBridge#createBridge(String, boolean)
     * @see DdmPreferences
     */
    public synchronized void init(boolean clientSupport) {
        logRun(
                AdbDelegateUsageTracker.Method.INIT_1,
                () -> init(clientSupport, false, ImmutableMap.of()));
    }

    /**
     * Similar to {@link #init(boolean)}, with ability to enable libusb and pass a custom set of
     * env. variables.
     */
    public synchronized void init(
            boolean clientSupport, boolean useLibusb, @NonNull Map<String, String> env) {
        logRun(
                AdbDelegateUsageTracker.Method.INIT_2,
                () ->
                        init(
                                AdbInitOptions.builder()
                                        .withEnv(env)
                                        .setClientSupportEnabled(clientSupport)
                                        .withEnv("ADB_LIBUSB", useLibusb ? "1" : "0")
                                        .build()));
    }

    /** Similar to {@link #init(boolean)}, with ability to pass a custom set of env. variables. */
    public synchronized void init(AdbInitOptions options) {
        logRun(
                AdbDelegateUsageTracker.Method.INIT_3,
                () -> {
                    Preconditions.checkState(
                            !sInitialized, "AndroidDebugBridge.init() has already been called.");
                    sInitialized = true;
                    sIDeviceManagerFactory = options.iDeviceManagerFactory;
                    iDeviceUsageTracker = options.iDeviceUsageTracker;
                    adbDelegateUsageTracker = options.adbDelegateUsageTracker;
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
                    HandleARTT.register(monitorThread);
                    HandleSTAG.register(monitorThread);
                });
    }

    /**
     * Notify {@link AndroidDebugBridge} that options have been modified. This method will re-init
     * adb if it has already been initialized, and restart if a bridge has been connected just prior
     * to this call.
     *
     * @return true if options have been successfully changed and reflected in the
     *     reinitialize/restarted state (if it was in such state prior to calling this method), or
     *     false otherwise
     */
    public synchronized boolean optionsChanged(
            @NonNull AdbInitOptions options,
            @NonNull String osLocation,
            boolean forceNewBridge,
            long terminateTimeout,
            long initTimeout,
            @NonNull TimeUnit unit) {
        return logCall(
                AdbDelegateUsageTracker.Method.OPTIONS_CHANGED,
                () -> {
                    if (!sInitialized) {
                        return true;
                    }

                    boolean bridgeNeedsRestart = getBridge() != null;
                    if (bridgeNeedsRestart) {
                        if (!disconnectBridge(terminateTimeout, unit)) {
                            Log.e(
                                    DDMS,
                                    "Could not disconnect bridge prior to restart when options"
                                            + " changed.");
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
                });
    }

    @VisibleForTesting
    public void enableFakeAdbServerMode(int port) {
        logRun(
                AdbDelegateUsageTracker.Method.ENABLE_FAKE_ADB_SERVER_MODE,
                () -> {
                    Preconditions.checkState(
                            !sInitialized,
                            "AndroidDebugBridge.init() has already been called or "
                                    + "terminate() has not been called yet.");
                    sUnitTestMode = true;
                    sAdbServerPort = port;
                });
    }

    @VisibleForTesting
    public void disableFakeAdbServerMode() {
        logRun(
                AdbDelegateUsageTracker.Method.DISABLE_FAKE_ADB_SERVER_MODE,
                () -> {
                    Preconditions.checkState(
                            !sInitialized,
                            "AndroidDebugBridge.init() has already been called or "
                                    + "terminate() has not been called yet.");
                    sUnitTestMode = false;
                    sAdbServerPort = 0;
                });
    }

    /** Terminates the ddm library. This must be called upon application termination. */
    public synchronized void terminate() {
        logRun(
                AdbDelegateUsageTracker.Method.TERMINATE,
                () -> {
                    if (sThis != null) {
                        killMonitoringServices();
                    }

                    MonitorThread monitorThread = MonitorThread.getInstance();
                    if (monitorThread != null) {
                        monitorThread.quit();
                    }

                    sInitialized = false;
                    sThis = null;
                    sLastKnownGoodAddress = null;
                });
    }

    /**
     * Returns whether the ddmlib is setup to support monitoring and interacting with {@link
     * ClientImpl}s running on the {@link IDevice}s.
     */
    public boolean getClientSupport() {
        return logCall(AdbDelegateUsageTracker.Method.GET_CLIENT_SUPPORT, () -> sClientSupport);
    }

    /**
     * Returns the current {@link ClientManager} instance if {@link Client} process tracking is
     * delegated to an external implementation, or {@code null} if {@link Client} processes are
     * monitored with the default {@link #getClientSupport()} implementation.
     */
    @Nullable
    public ClientManager getClientManager() {
        return logCall(AdbDelegateUsageTracker.Method.GET_CLIENT_MANAGER, () -> sClientManager);
    }

    /**
     * Returns the socket address of the ADB server on the host.
     *
     * <p>This method will try to return a socket address that's known to work by opening a socket
     * channel to the ADB server. Both IPv4 and IPv6 loopback-address will be attempted. In the
     * event where neither could connect, this method will fallback to returning the
     * loopback-address preferred by the JVM. This fallback logic is required to prevent API
     * breakage.
     *
     * <p>If fake ADB server mode is enabled, this method will automatically fallback to legacy
     * implementation without attempting to connect to ADB.
     *
     * @deprecated This method returns a loopback server address that may not match what's used by
     *     the ADB server. i.e. the JVM may be in IPv4 mode while the ADB server is hosted on the
     *     IPv6 loopback address. Prefer {@link #openConnection()} instead when opening a connection
     *     to the ADB server.
     */
    @Deprecated
    public InetSocketAddress getSocketAddress() {
        // Not using `logCall()` since this method is called too often
        if (!sUnitTestMode) {
            // Use synchronized access to ensure we only ever open one connection to ADB when we
            // need to check which local address to use.
            synchronized (sLastKnownGoodAddressLock) {
                if (sLastKnownGoodAddress != null) {
                    return sLastKnownGoodAddress;
                }
                try (SocketChannel adbChannel = openConnection()) {
                    // SocketAddress from adbChannel is created by openConnection and should always
                    // be an InetSocketAddress.
                    sLastKnownGoodAddress = (InetSocketAddress) adbChannel.getRemoteAddress();
                    return sLastKnownGoodAddress;
                } catch (IOException e) {
                    // Ignore the failure and fallback to old implementation.
                }
            }
        }
        return new InetSocketAddress(InetAddress.getLoopbackAddress(), sAdbServerPort);
    }

    /**
     * Attempts to connect to the local android debug bridge server.
     *
     * @return a connected socket if success
     * @throws IOException should errors occur when opening the connection
     */
    public SocketChannel openConnection() throws IOException {
        return logCall1(
                AdbDelegateUsageTracker.Method.OPEN_CONNECTION,
                () -> {
                    SocketChannel adbChannel;
                    try {
                        adbChannel =
                                SocketChannel.open(
                                        new InetSocketAddress("127.0.0.1", sAdbServerPort));
                    } catch (IOException ipv4Exception) {
                        // Fallback to IPv6.
                        try {
                            adbChannel =
                                    SocketChannel.open(
                                            new InetSocketAddress("::1", sAdbServerPort));
                        } catch (IOException ipv6Exception) {
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
                });
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
     *     bridge
     */
    @Nullable
    public AndroidDebugBridge createBridge() {
        return logCall(
                AdbDelegateUsageTracker.Method.CREATE_BRIDGE_1,
                () -> createBridge(Long.MAX_VALUE, TimeUnit.MILLISECONDS));
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
     *     bridge
     */
    @Nullable
    public AndroidDebugBridge createBridge(long timeout, @NonNull TimeUnit unit) {
        return logCall(
                AdbDelegateUsageTracker.Method.CREATE_BRIDGE_2,
                () -> {
                    AndroidDebugBridge localThis;
                    synchronized (sLock) {
                        if (sThis != null) {
                            return sThis;
                        }

                        try {
                            localThis = new AndroidDebugBridge();
                            if (!start(localThis, timeout, unit)) {
                                // We return without notifying listeners, since there were no
                                // changes
                                return null;
                            }
                        } catch (InvalidParameterException e) {
                            // We return without notifying listeners, since there were no changes
                            return null;
                        }

                        // Success, store static instance
                        sThis = localThis;
                    }

                    // Notify the listeners of the change (outside of the lock to decrease the
                    // likelihood of deadlocks)
                    adbChangeEvents.notifyBridgeChanged(localThis);

                    return localThis;
                });
    }

    /**
     * Creates a new debug bridge from the location of the command line tool.
     *
     * <p>Any existing server will be disconnected, unless the location is the same and <code>
     * forceNewBridge</code> is set to false.
     *
     * @param osLocation the location of the command line tool 'adb'
     * @param forceNewBridge force creation of a new bridge even if one with the same location
     *     already exists.
     * @return a connected bridge, or null if there were errors while creating or connecting to the
     *     bridge
     * @deprecated This method may hang if ADB is not responding. Use {@link #createBridge(String,
     *     boolean, long, TimeUnit)} instead.
     */
    @Deprecated
    @Nullable
    public AndroidDebugBridge createBridge(@NonNull String osLocation, boolean forceNewBridge) {
        return logCall(
                AdbDelegateUsageTracker.Method.CREATE_BRIDGE_3,
                () -> {
                    return createBridge(osLocation,
                                        forceNewBridge,
                                        Long.MAX_VALUE,
                                        TimeUnit.MILLISECONDS);
                });
    }

    /**
     * Creates a new debug bridge from the location of the command line tool.
     *
     * <p>Any existing server will be disconnected, unless the location is the same and <code>
     * forceNewBridge</code> is set to false.
     *
     * @param osLocation the location of the command line tool 'adb'
     * @param forceNewBridge force creation of a new bridge even if one with the same location
     *     already exists.
     * @param timeout the maximum time to wait
     * @param unit the time unit of the {@code timeout} argument
     * @return a connected bridge, or null if there were errors while creating or connecting to the
     *     bridge
     */
    @Nullable
    public AndroidDebugBridge createBridge(
            @NonNull String osLocation,
            boolean forceNewBridge,
            long timeout,
            @NonNull TimeUnit unit) {
        return logCall(
                AdbDelegateUsageTracker.Method.CREATE_BRIDGE_4,
                () -> {
                    AndroidDebugBridge localThis;
                    synchronized (sLock) {
                        TimeoutRemainder rem = new TimeoutRemainder(timeout, unit);
                        if (!sUnitTestMode) {
                            if (sThis != null) {
                                if (mAdbOsLocation != null
                                    && mAdbOsLocation.equals(osLocation)
                                    && !forceNewBridge) {
                                    // We return without notifying listeners, since there were no
                                    // changes
                                    return sThis;
                                } else {
                                    // stop the current server
                                    if (!stop(rem.getRemainingNanos(), TimeUnit.NANOSECONDS)) {
                                        // We return without notifying listeners, since there were
                                        // no changes
                                        return null;
                                    }
                                }

                                // We are successfully stopped. We need to notify listeners in all
                                // code paths past this point.
                                sThis = null;
                            }
                        }

                        try {
                            localThis = new AndroidDebugBridge();
                            initOsLocationAndCheckVersion(osLocation);
                            if (!start(localThis, rem.getRemainingNanos(), TimeUnit.NANOSECONDS)) {
                                // Note: Don't return here, as we want to notify listeners
                                localThis = null;
                            }
                        } catch (InvalidParameterException e) {
                            // Note: Don't return here, as we want to notify listeners
                            localThis = null;
                        }

                        // Success, store static instance
                        sThis = localThis;
                    }

                    // Notify the listeners of the change (outside of the lock to decrease the
                    // likelihood of deadlocks)
                    adbChangeEvents.notifyBridgeChanged(localThis);

                    return localThis;
                });
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
     * @deprecated This method may hang if ADB is not responding. Use {@link #disconnectBridge(long,
     *     TimeUnit)} instead.
     */
    @Deprecated
    public void disconnectBridge() {
        logRun(
                AdbDelegateUsageTracker.Method.DISCONNECT_BRIDGE_1,
                () -> disconnectBridge(Long.MAX_VALUE, TimeUnit.MILLISECONDS));
    }

    /**
     * Disconnects the current debug bridge, and destroy the object. A new object will have to be
     * created with {@link #createBridge(String, boolean)}.
     *
     * <p>This also stops the current adb host server.
     *
     * @return {@code true} if the method succeeds within the specified timeout.
     */
    public boolean disconnectBridge(long timeout, @NonNull TimeUnit unit) {
        return logCall(
                AdbDelegateUsageTracker.Method.DISCONNECT_BRIDGE_2,
                () -> {
                    synchronized (sLock) {
                        if (sThis != null) {
                            if (!stop(timeout, unit)) {
                                // We could not stop ADB. Assume we are still running.
                                return false;
                            }
                            // Success, store our local instance
                            sThis = null;
                        }
                    }

                    // Notify the listeners of the change (outside of the lock to decrease the
                    // likelihood of deadlocks)
                    adbChangeEvents.notifyBridgeChanged(null);

                    return true;
                });
    }

    /**
     * Adds the listener to the collection of listeners who will be notified when a new {@link
     * AndroidDebugBridge} is connected, by sending it one of the messages defined in the {@link
     * AndroidDebugBridge.IDebugBridgeChangeListener} interface.
     *
     * @param listener The listener which should be notified.
     */
    public void addDebugBridgeChangeListener(
            @NonNull AndroidDebugBridge.IDebugBridgeChangeListener listener) {
        logRun(
                AdbDelegateUsageTracker.Method.ADD_DEBUG_BRIDGE_CHANGE_LISTENER,
                () -> {
                    adbChangeEvents.addDebugBridgeChangeListener(listener);

                    AndroidDebugBridge localThis = sThis;

                    if (localThis != null) {
                        // we attempt to catch any exception so that a bad listener doesn't kill our
                        // thread
                        try {
                            listener.bridgeChanged(localThis);
                        } catch (Throwable t) {
                            Log.e(DDMS, t);
                        }
                    }
                });
    }

    /**
     * Removes the listener from the collection of listeners who will be notified when a new {@link
     * AndroidDebugBridge} is started.
     *
     * @param listener The listener which should no longer be notified.
     */
    public void removeDebugBridgeChangeListener(
            AndroidDebugBridge.IDebugBridgeChangeListener listener) {
        logRun(
                AdbDelegateUsageTracker.Method.REMOVE_DEBUG_BRIDGE_CHANGE_LISTENER,
                () -> adbChangeEvents.removeDebugBridgeChangeListener(listener));
    }

    @VisibleForTesting
    public int getDebugBridgeChangeListenerCount() {
        return logCall(
                AdbDelegateUsageTracker.Method.GET_DEBUG_BRIDGE_CHANGE_LISTENER_COUNT,
                adbChangeEvents::debugBridgeChangeListenerCount);
    }

    /**
     * Adds the listener to the collection of listeners who will be notified when a {@link IDevice}
     * is connected, disconnected, or when its properties or its {@link ClientImpl} list changed, by
     * sending it one of the messages defined in the {@link
     * AndroidDebugBridge.IDeviceChangeListener} interface.
     *
     * @param listener The listener which should be notified.
     */
    public void addDeviceChangeListener(
            @NonNull AndroidDebugBridge.IDeviceChangeListener listener) {
        logRun(
                AdbDelegateUsageTracker.Method.ADD_DEVICE_CHANGE_LISTENER,
                () -> adbChangeEvents.addDeviceChangeListener(listener));
    }

    /**
     * Removes the listener from the collection of listeners who will be notified when a {@link
     * IDevice} is connected, disconnected, or when its properties or its {@link ClientImpl} list
     * changed.
     *
     * @param listener The listener which should no longer be notified.
     */
    public void removeDeviceChangeListener(AndroidDebugBridge.IDeviceChangeListener listener) {
        logRun(
                AdbDelegateUsageTracker.Method.REMOVE_DEVICE_CHANGE_LISTENER,
                () -> adbChangeEvents.removeDeviceChangeListener(listener));
    }

    @VisibleForTesting
    public int getDeviceChangeListenerCount() {
        return logCall(
                AdbDelegateUsageTracker.Method.GET_DEVICE_CHANGE_LISTENER_COUNT,
                adbChangeEvents::deviceChangeListenerCount);
    }

    /**
     * Adds the listener to the collection of listeners who will be notified when a {@link
     * ClientImpl} property changed, by sending it one of the messages defined in the {@link
     * AndroidDebugBridge.IClientChangeListener} interface.
     *
     * @param listener The listener which should be notified.
     */
    public void addClientChangeListener(AndroidDebugBridge.IClientChangeListener listener) {
        logRun(
                AdbDelegateUsageTracker.Method.ADD_CLIENT_CHANGE_LISTENER,
                () -> adbChangeEvents.addClientChangeListener(listener));
    }

    /**
     * Removes the listener from the collection of listeners who will be notified when a {@link
     * ClientImpl} property changes.
     *
     * @param listener The listener which should no longer be notified.
     */
    public void removeClientChangeListener(AndroidDebugBridge.IClientChangeListener listener) {
        logRun(
                AdbDelegateUsageTracker.Method.REMOVE_CLIENT_CHANGE_LISTENER,
                () -> adbChangeEvents.removeClientChangeListener(listener));
    }

    /**
     * @return version of the ADB server if we were able to successfully retrieve it, {@code null}
     *     otherwise.
     */
    public @Nullable AdbVersion getCurrentAdbVersion() {
        return logCall(AdbDelegateUsageTracker.Method.GET_CURRENT_ADB_VERSION, () -> mAdbVersion);
    }

    /**
     * Returns the devices.
     *
     * @see #hasInitialDeviceList()
     */
    @NonNull
    public IDevice[] getDevices() {
        return logCall(
                AdbDelegateUsageTracker.Method.GET_DEVICES,
                () -> {
                    if (mIDeviceManager != null) {
                        return mIDeviceManager.getDevices().toArray(new IDevice[0]);
                    }
                    synchronized (sLock) {
                        if (mDeviceMonitor != null) {
                            return mDeviceMonitor.getDevices();
                        }
                    }

                    return new IDevice[0];
                });
    }

    /**
     * Returns whether the bridge has acquired the initial list from adb after being created.
     *
     * <p>Calling {@link #getDevices()} right after {@link #createBridge(String, boolean)} will
     * generally result in an empty list. This is due to the internal asynchronous communication
     * mechanism with <code>adb</code> that does not guarantee that the {@link IDevice} list has
     * been built before the call to {@link #getDevices()}.
     *
     * <p>The recommended way to get the list of {@link IDevice} objects is to create a {@link
     * AndroidDebugBridge.IDeviceChangeListener} object.
     */
    public boolean hasInitialDeviceList() {
        return logCall(
                AdbDelegateUsageTracker.Method.HAS_INITIAL_DEVICE_LIST,
                () -> {
                    if (mIDeviceManager != null) {
                        return mIDeviceManager.hasInitialDeviceList();
                    }

                    if (mDeviceMonitor != null) {
                        return mDeviceMonitor.hasInitialDeviceList();
                    }

                    return false;
                });
    }

    /**
     * Returns whether the {@link AndroidDebugBridge} object is still connected to the adb daemon.
     */
    public boolean isConnected() {
        // Not using `logCall()` since this method is called too often
        MonitorThread monitorThread = MonitorThread.getInstance();
        if (mDeviceMonitor != null && monitorThread != null) {
            return mDeviceMonitor.isMonitoring()
                    && monitorThread.getState() != Thread.State.TERMINATED;
        }
        return false;
    }

    @Nullable
    public IDeviceUsageTracker getiDeviceUsageTracker() {
        return iDeviceUsageTracker;
    }

    private void initOsLocationAndCheckVersion(String osLocation) {
        if (osLocation == null || osLocation.isEmpty()) {
            throw new InvalidParameterException();
        }
        mAdbOsLocation = osLocation;

        try {
            mAdbVersion = fetchAdbVersion();
            mVersionCheck = checkAdbVersion(mAdbVersion);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Queries adb for its version number.
     *
     * @return a {@link AdbVersion} if adb responds correctly, or null otherwise
     */
    private @Nullable AdbVersion fetchAdbVersion() throws IOException {
        if (mAdbOsLocation == null) {
            return null;
        }

        File adb = new File(mAdbOsLocation);
        ListenableFuture<AdbVersion> future = getAdbVersion(adb);
        try {
            return future.get(DEFAULT_START_ADB_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return null;
        } catch (java.util.concurrent.TimeoutException e) {
            String msg = "Unable to obtain result of 'adb version'";
            Log.logAndDisplay(Log.LogLevel.ERROR, ADB, msg);
            return null;
        } catch (ExecutionException e) {
            Log.logAndDisplay(Log.LogLevel.ERROR, ADB, e.getCause().getMessage());
            Throwables.propagateIfInstanceOf(e.getCause(), IOException.class);
            return null;
        }
    }

    /**
     * Checks if given AdbVersion is at least {@link AndroidDebugBridge#MIN_ADB_VERSION}.
     *
     * @return true if given version is at least minimum, false otherwise
     */
    private static boolean checkAdbVersion(@Nullable AdbVersion adbVersion) {
        // default is bad check
        boolean passes = false;

        if (adbVersion == null) {
            Log.logAndDisplay(Log.LogLevel.ERROR, ADB, "Could not determine adb version.");
        } else if (adbVersion.compareTo(MIN_ADB_VERSION) > 0) {
            passes = true;
        } else {
            String message =
                    String.format(
                            "Required minimum version of adb: %1$s." + "Current version is %2$s",
                            MIN_ADB_VERSION, adbVersion);
            Log.logAndDisplay(Log.LogLevel.ERROR, ADB, message);
        }
        return passes;
    }

    interface AdbOutputProcessor<T> {
        T process(Process process, BufferedReader r) throws IOException;
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

    public ListenableFuture<AdbVersion> getAdbVersion(@NonNull final File adb) {
        return logCall(
                AdbDelegateUsageTracker.Method.GET_ADB_VERSION,
                () ->
                        runAdb(
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
                                        errorMessage +=
                                                ", exit value: 0x" + Integer.toHexString(exitValue);

                                        // Display special message if it is the STATUS_DLL_NOT_FOUND
                                        // code, and ignore adb output since it's empty anyway
                                        if (exitValue == STATUS_DLL_NOT_FOUND
                                                && SdkConstants.currentPlatform()
                                                        == SdkConstants.PLATFORM_WINDOWS) {
                                            errorMessage +=
                                                    ". ADB depends on the Windows Universal C"
                                                        + " Runtime, which is usually installed by"
                                                        + " default via Windows Update. You may"
                                                        + " need to manually fetch and install the"
                                                        + " runtime package here:"
                                                        + " https://support.microsoft.com/en-ca/help/2999226/update-for-universal-c-runtime-in-windows";
                                            throw new RuntimeException(errorMessage);
                                        }
                                    }
                                    if (sb.length() > 0) {
                                        errorMessage += ", adb output: " + sb.toString();
                                    }
                                    throw new RuntimeException(errorMessage);
                                },
                                "version"));
    }

    private ListenableFuture<List<AdbDevice>> getRawDeviceList(@NonNull final File adb) {
        return logCall(
                AdbDelegateUsageTracker.Method.GET_RAW_DEVICE_LIST,
                () ->
                        runAdb(
                                adb,
                                (Process process, BufferedReader br) -> {
                                    // The first line of output is a header, not part of the device
                                    // list. Skip it.
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
                                "-l"));
    }

    @NonNull
    public ListenableFuture<String> getVirtualDeviceId(
            @NonNull ListeningExecutorService service, @NonNull File adb, @NonNull IDevice device) {
        return logCall(
                AdbDelegateUsageTracker.Method.GET_VIRTUAL_DEVICE_ID,
                () -> {
                    List<String> command =
                            Arrays.asList(
                                    adb.toString(),
                                    "-s",
                                    device.getSerialNumber(),
                                    "emu",
                                    "avd",
                                    "id");

                    return execute(
                            service,
                            command,
                            AndroidDebugBridgeImpl::processVirtualDeviceIdCommandOutput);
                });
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
        return logCall(
                AdbDelegateUsageTracker.Method.GET_RAW_DEVICE_LIST,
                () -> {
                    if (mAdbOsLocation == null) {
                        SettableFuture<List<AdbDevice>> result = SettableFuture.create();
                        result.set(Collections.emptyList());
                        return result;
                    }
                    File adb = new File(mAdbOsLocation);
                    return getRawDeviceList(adb);
                });
    }

    /**
     * Starts the debug bridge.
     *
     * @return true if success.
     */
    private boolean start(AndroidDebugBridge localThis, long timeout, @NonNull TimeUnit unit) {
        // Skip server start check if using user managed ADB server
        if (!sUserManagedAdbMode) {
            // If we are configured correctly, check if we need to start ADB
            if (mAdbOsLocation != null && sAdbServerPort != 0) {
                // If we don't have a valid ADB version (or if we have not checked successfully), we
                // can't start
                if (!mVersionCheck) {
                    return false;
                }
                // Try to start adb
                if (!startAdb(timeout, unit)) {
                    return false;
                }
            }
        }

        mStarted = true;

        // Start the underlying services.
        startMonitoringServices(localThis);

        return true;
    }

    private void startMonitoringServices(AndroidDebugBridge localThis) {
        assert (localThis != null);
        if (sIDeviceManagerFactory != null) {
            mIDeviceManager =
                    sIDeviceManagerFactory.createIDeviceManager(
                            localThis, IDeviceManagerUtils.createIDeviceManagerListener());
        }
        // If `IDeviceManager` is available then it is used to process the device updates and
        // `DeviceMonitor` is used only to establish and track adb server connection, etc.
        boolean emitDeviceListUpdates = mIDeviceManager == null;
        mDeviceMonitor =
                new DeviceMonitor(localThis, new MonitorErrorHandler(), emitDeviceListUpdates);
        mDeviceMonitor.start();
    }

    /**
     * Kills the debug bridge, and the adb host server.
     *
     * @return {@code true} if success within the specified timeout
     */
    private boolean stop(long timeout, @NonNull TimeUnit unit) {
        // if we haven't started we return true (i.e. success)
        if (!mStarted) {
            return true;
        }

        TimeoutRemainder rem = new TimeoutRemainder(timeout, unit);
        killMonitoringServices();

        // Don't stop ADB when using user managed ADB server.
        if (sUserManagedAdbMode) {
            Log.i(DDMS, "User managed ADB mode: Not stopping ADB server");
        } else if (!stopAdb(rem.getRemainingNanos(), TimeUnit.NANOSECONDS)) {
            return false;
        }

        mStarted = false;
        return true;
    }

    private void killMonitoringServices() {
        if (mDeviceMonitor != null) {
            mDeviceMonitor.stop();
            mDeviceMonitor = null;
        }
        if (mIDeviceManager != null) {
            try {
                mIDeviceManager.close();
            } catch (Exception e) {
                Log.e(DDMS, "Could not close IDeviceManager:");
                Log.e(DDMS, e);
            } finally {
                mIDeviceManager = null;
            }
        }
    }

    /**
     * Restarts adb, but not the services around it.
     *
     * @return true if success.
     * @deprecated This method may hang if ADB is not responding. Use {@link #restart(long,
     *     TimeUnit)} instead.
     */
    @Deprecated
    public boolean restart() {
        return logCall(
                AdbDelegateUsageTracker.Method.RESTART_1,
                () -> restart(Long.MAX_VALUE, TimeUnit.MILLISECONDS));
    }

    /**
     * Restarts adb, but not the services around it.
     *
     * @return true if success.
     */
    public boolean restart(long timeout, @NonNull TimeUnit unit) {
        return logCall(
                AdbDelegateUsageTracker.Method.RESTART_2,
                () -> {
                    if (sUserManagedAdbMode) {
                        Log.e(
                                ADB,
                                "Cannot restart adb when using user managed ADB"
                                        + " server."); // $NON-NLS-1$
                        return false;
                    }

                    if (mAdbOsLocation == null) {
                        Log.e(
                                ADB,
                                "Cannot restart adb when AndroidDebugBridge is created without the"
                                        + " location of adb."); //$NON-NLS-1$
                        return false;
                    }

                    if (sAdbServerPort == 0) {
                        Log.e(
                                ADB,
                                "ADB server port for restarting AndroidDebugBridge"
                                        + " is not set."); //$NON-NLS-1$
                        return false;
                    }

                    if (!mVersionCheck) {
                        Log.logAndDisplay(
                                Log.LogLevel.ERROR,
                                ADB,
                                "Attempting to restart adb, but version check"
                                        + " failed!"); //$NON-NLS-1$
                        return false;
                    }

                    TimeoutRemainder rem = new TimeoutRemainder(timeout, unit);
                    // Notify the listeners of the change (outside of the lock to decrease the
                    // likelihood of deadlocks)
                    adbChangeEvents.notifyBridgeRestartInitiated();

                    boolean isSuccessful;
                    synchronized (this) {
                        isSuccessful = stopAdb(rem.getRemainingNanos(), TimeUnit.NANOSECONDS);
                        if (!isSuccessful) {
                            Log.w(ADB, "Error stopping ADB without specified timeout");
                        }

                        if (isSuccessful) {
                            isSuccessful = startAdb(rem.getRemainingNanos(), TimeUnit.NANOSECONDS);
                        }

                        if (isSuccessful && mDeviceMonitor == null && mIDeviceManager == null) {
                            assert (sThis != null);
                            startMonitoringServices(sThis);
                        }
                    }

                    // Notify the listeners of the change (outside of the lock to decrease the
                    // likelihood of deadlocks)
                    adbChangeEvents.notifyBridgeRestartCompleted(isSuccessful);

                    return isSuccessful;
                });
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
        logRun(
                AdbDelegateUsageTracker.Method.DEVICE_CONNECTED,
                () -> adbChangeEvents.notifyDeviceConnected(device));
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
        logRun(
                AdbDelegateUsageTracker.Method.DEVICE_DISCONNECTED,
                () -> adbChangeEvents.notifyDeviceDisconnected(device));
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
        logRun(
                AdbDelegateUsageTracker.Method.DEVICE_CHANGED,
                () -> adbChangeEvents.notifyDeviceChanged(device, changeMask));
    }

    /**
     * Notify the listener of a modified {@link ClientImpl}.
     *
     * <p>The notification of the listeners is done in a synchronized block. It is important to
     * expect the listeners to potentially access various methods of {@link IDevice} as well as
     * {@link #getDevices()} which use internal locks.
     *
     * @param client the modified <code>Client</code>.
     * @param changeMask the mask indicating what changed in the <code>Client</code>
     */
    public void clientChanged(@NonNull Client client, int changeMask) {
        logRun(
                AdbDelegateUsageTracker.Method.CLIENT_CHANGED,
                () -> adbChangeEvents.notifyClientChanged(client, changeMask));
    }

    /**
     * @return If operating in user managed ADB mode where ddmlib will and should not manage the ADB
     *     server.
     */
    public boolean isUserManagedAdbMode() {
        return sUserManagedAdbMode;
    }

    @Override
    public String queryFeatures(String adbFeaturesRequest)
            throws TimeoutException, AdbCommandRejectedException, IOException {
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

    /**
     * Starts the adb host side server. This method should not be used when using user managed ADB
     * server as the server lifecycle should be managed by the user, not ddmlib.
     *
     * @return true if success
     */
    public synchronized boolean startAdb(long timeout, @NonNull TimeUnit unit) {
        return logCall(
                AdbDelegateUsageTracker.Method.START_ADB,
                () -> {
                    if (sUserManagedAdbMode) {
                        Log.e(
                                ADB,
                                "startADB should never be called when using user managed ADB"
                                        + " server.");
                        return false;
                    }

                    if (sUnitTestMode) {
                        // in this case, we assume the FakeAdbServer was already setup by the test
                        // code
                        return true;
                    }

                    if (mAdbOsLocation == null) {
                        Log.e(
                                ADB,
                                "Cannot start adb when AndroidDebugBridge is created without the"
                                        + " location of adb."); //$NON-NLS-1$
                        return false;
                    }

                    if (sAdbServerPort == 0) {
                        Log.w(
                                ADB,
                                "ADB server port for starting AndroidDebugBridge is"
                                        + " not set."); //$NON-NLS-1$
                        return false;
                    }

                    Process proc;
                    int status = -1;

                    String[] command = getAdbLaunchCommand("start-server");
                    String commandString = Joiner.on(' ').join(command);
                    try {
                        Log.d(
                                DDMS,
                                String.format(
                                        "Launching '%1$s' to ensure ADB is running.",
                                        commandString));
                        ProcessBuilder processBuilder = new ProcessBuilder(command);
                        Map<String, String> env = processBuilder.environment();
                        sAdbEnvVars.forEach(env::put);
                        if (DdmPreferences.getUseAdbHost()) {
                            String adbHostValue = DdmPreferences.getAdbHostValue();
                            if (adbHostValue != null && !adbHostValue.isEmpty()) {
                                // TODO : check that the String is a valid IP address
                                env.put("ADBHOST", adbHostValue);
                            }
                        }
                        processBuilder.directory(new File(mAdbOsLocation).getParentFile());
                        proc = processBuilder.start();

                        ArrayList<String> errorOutput = new ArrayList<String>();
                        ArrayList<String> stdOutput = new ArrayList<String>();
                        status =
                                grabProcessOutput(
                                        proc,
                                        errorOutput,
                                        stdOutput,
                                        false /* waitForReaders */,
                                        timeout,
                                        unit);
                    } catch (IOException | InterruptedException ioe) {
                        Log.e(DDMS, "Unable to run 'adb': " + ioe.getMessage()); // $NON-NLS-1$
                        // we'll return false;
                    }

                    if (status != 0) {
                        Log.e(
                                DDMS,
                                String.format(
                                        "'%1$s' failed -- run manually if necessary",
                                        commandString)); //$NON-NLS-1$
                        return false;
                    } else {
                        Log.d(
                                DDMS,
                                String.format("'%1$s' succeeded", commandString)); // $NON-NLS-1$
                        return true;
                    }
                });
    }

    private String[] getAdbLaunchCommand(String option) {
        List<String> command = new ArrayList<String>(4);
        command.add(mAdbOsLocation);
        if (sAdbServerPort != DEFAULT_ADB_PORT) {
            command.add("-P"); // $NON-NLS-1$
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
    public synchronized boolean stopAdb(long timeout, @NonNull TimeUnit unit) {
        if (sUserManagedAdbMode) {
            Log.e(ADB, "stopADB should never be called when using user managed ADB server.");
            return false;
        }

        if (mAdbOsLocation == null) {
            Log.e(
                    ADB,
                    "Cannot stop adb when AndroidDebugBridge is created without the location of"
                            + " adb.");
            return false;
        }

        if (sAdbServerPort == 0) {
            Log.e(ADB, "ADB server port for restarting AndroidDebugBridge is not set");
            return false;
        }

        Process proc;
        int status = -1;

        String[] command = getAdbLaunchCommand("kill-server"); // $NON-NLS-1$
        try {
            proc = Runtime.getRuntime().exec(command);
            if (proc.waitFor(timeout, unit)) {
                status = proc.exitValue();
            } else {
                proc.destroy();
                status = -1;
            }
        } catch (IOException ioe) {
            // we'll return false;
        } catch (InterruptedException ie) {
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
     * Get the stderr/stdout outputs of a process and return when the process is done. Both
     * <b>must</b> be read or the process will block on windows.
     *
     * @param process The process to get the output from
     * @param errorOutput The array to store the stderr output. cannot be null.
     * @param stdOutput The array to store the stdout output. cannot be null.
     * @param waitForReaders if true, this will wait for the reader threads.
     * @return the process return code.
     * @throws InterruptedException
     */
    private static int grabProcessOutput(
            final Process process,
            final ArrayList<String> errorOutput,
            final ArrayList<String> stdOutput,
            boolean waitForReaders,
            long timeout,
            @NonNull TimeUnit unit)
            throws InterruptedException {
        assert errorOutput != null;
        assert stdOutput != null;

        TimeoutRemainder rem = new TimeoutRemainder(timeout, unit);

        // read the lines as they come. if null is returned, it's
        // because the process finished
        Thread t1 = new Thread("adb:stderr reader") { // $NON-NLS-1$
                    @Override
                    public void run() {
                        // create a buffer to read the stderr output
                        InputStreamReader is =
                                new InputStreamReader(process.getErrorStream(), Charsets.UTF_8);
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

        Thread t2 = new Thread("adb:stdout reader") { // $NON-NLS-1$
                    @Override
                    public void run() {
                        InputStreamReader is =
                                new InputStreamReader(process.getInputStream(), Charsets.UTF_8);
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
        long remMillis;
        if (waitForReaders) {
            try {
                remMillis = rem.getRemainingUnits(TimeUnit.MILLISECONDS);
                if (remMillis > 0) {
                    t1.join(remMillis);
                }
            } catch (InterruptedException ignored) {
            }
            remMillis = rem.getRemainingUnits(TimeUnit.MILLISECONDS);
            try {
                if (remMillis > 0) {
                    t2.join(remMillis);
                }
            } catch (InterruptedException ignored) {
            }
        }

        // get the return code from the process
        if (process.waitFor(rem.getRemainingNanos(), TimeUnit.NANOSECONDS)) {
            return process.exitValue();
        } else {
            Log.w(ADB, "Process did not terminate within specified timeout, killing it");
            process.destroyForcibly();
            return -1;
        }
    }

    /** Instantiates sSocketAddr with the address of the host's adb process. */
    private static void initAdbPort(int userManagedAdbPort) {
        // If we're in unit test mode, we already manually set sAdbServerPort.
        if (!sUnitTestMode) {
            if (sUserManagedAdbMode) {
                sAdbServerPort = userManagedAdbPort;
            } else {
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
            } catch (IllegalArgumentException e) {
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
            Log.w(
                    DDMS,
                    "No access to env variables allowed by current security manager. "
                            + "If you've set ANDROID_ADB_SERVER_PORT: it's being ignored.");
        } catch (IllegalArgumentException e) {
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
     *     a number at all
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

    private static class MonitorErrorHandler implements DeviceMonitor.MonitorErrorHandler {
        @Override
        public void initializationError(@NonNull Exception e) {
            adbChangeEvents.notifyBridgeInitializationError(e);
        }
    }

    private void logRun(AdbDelegateUsageTracker.Method method, Runnable block) {
        try {
            block.run();
            maybeLogUsage(method, false);
        } catch (Throwable t) {
            maybeLogUsage(method, true);
            throw t;
        }
    }

    private <R> R logCall(AdbDelegateUsageTracker.Method method, Supplier<R> block) {
        R result;
        try {
            result = block.get();
            maybeLogUsage(method, false);
            return result;
        } catch (Throwable t) {
            maybeLogUsage(method, true);
            throw t;
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier1<T> {
        T get() throws IOException;
    }

    private <R> R logCall1(AdbDelegateUsageTracker.Method method, ThrowingSupplier1<R> block)
            throws IOException {
        R result;
        try {
            result = block.get();
            maybeLogUsage(method, false);
            return result;
        } catch (Throwable t) {
            maybeLogUsage(method, true);
            throw t;
        }
    }

    private void maybeLogUsage(AdbDelegateUsageTracker.Method method, boolean isException) {
        if (adbDelegateUsageTracker != null) {
            adbDelegateUsageTracker.logUsage(method, isException);
        }
    }
}
