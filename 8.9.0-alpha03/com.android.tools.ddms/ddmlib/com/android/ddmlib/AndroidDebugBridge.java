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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.clientmanager.ClientManager;
import com.android.ddmlib.internal.ClientImpl;
import com.android.ddmlib.internal.DefaultJdwpProcessorFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A connection to the host-side android debug bridge (adb)
 *
 * <p>This is the central point to communicate with any devices, emulators, or the applications
 * running on them.
 *
 * <p><b>{@link #init} must be called before anything is done.</b>
 */
public class AndroidDebugBridge {

    private static volatile boolean delegateIsUsed = false;
    private static volatile AndroidDebugBridgeDelegate delegate = new AndroidDebugBridgeImpl();

    /**
     * Minimum and maximum version of adb supported. This correspond to ADB_SERVER_VERSION found in
     * //device/tools/adb/adb.h
     */
    public static final AdbVersion MIN_ADB_VERSION = AdbVersion.parseFrom("1.0.20");

    /** Default timeout used when starting the ADB server */
    public static final int DEFAULT_START_ADB_TIMEOUT_MILLIS = 20_000;

    private static JdwpTracerFactory sJdwpTracerFactory = new DefaultJdwpTracerFactory();

    private static JdwpProcessorFactory sJdwpProcessorFactory = new DefaultJdwpProcessorFactory();

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
        default void restartCompleted(boolean isSuccessful) {}

        /**
         * Sent when an error occurred during initialization.
         *
         * @param exception the exception that occurred.
         */
        default void initializationError(@NonNull Exception exception) {}
    }

    /**
     * Classes which implement this interface provide methods that deal with {@link IDevice}
     * addition, deletion, and changes.
     */
    public interface IDeviceChangeListener {
        /**
         * Sent when a device is connected to the {@link AndroidDebugBridge}.
         *
         * <p>This is sent from a non UI thread.
         *
         * @param device the new device.
         */
        void deviceConnected(@NonNull IDevice device);

        /**
         * Sent when a device is connected to the {@link AndroidDebugBridge}.
         *
         * <p>This is sent from a non UI thread.
         *
         * @param device the new device.
         */
        void deviceDisconnected(@NonNull IDevice device);

        /**
         * Sent when a device data changed, or when clients are started/terminated on the device.
         *
         * <p>This is sent from a non UI thread.
         *
         * @param device the device that was updated.
         * @param changeMask the mask describing what changed. It can contain any of the following
         *     values: {@link IDevice#CHANGE_BUILD_INFO}, {@link IDevice#CHANGE_STATE}, {@link
         *     IDevice#CHANGE_CLIENT_LIST}
         */
        void deviceChanged(@NonNull IDevice device, int changeMask);
    }

    /**
     * Classes which implement this interface provide methods that deal with {@link ClientImpl}
     * changes.
     */
    public interface IClientChangeListener {
        /**
         * Sent when an existing client information changed.
         *
         * <p>This is sent from a non UI thread.
         *
         * @param client the updated client.
         * @param changeMask the bit mask describing the changed properties. It can contain any of
         *     the following values: {@link ClientImpl#CHANGE_INFO}, {@link
         *     ClientImpl#CHANGE_DEBUGGER_STATUS}, {@link ClientImpl#CHANGE_THREAD_MODE}, {@link
         *     ClientImpl#CHANGE_THREAD_DATA}, {@link ClientImpl#CHANGE_HEAP_MODE}, {@link
         *     ClientImpl#CHANGE_HEAP_DATA}, {@link ClientImpl#CHANGE_NATIVE_HEAP_DATA}
         */
        void clientChanged(@NonNull Client client, int changeMask);
    }

    /**
     * Call this method if the default implementation needs to be overridden. This method is
     * introduced to enable a full migration of functionality in this class to from ddmlib to
     * adblib.
     */
    public static void preInit(AndroidDebugBridgeDelegate delegate) {
        if (delegateIsUsed) {
            Log.w("ddmlib", "AndroidDebugBridgeDelegate assignment after its use");
        }
        if (AndroidDebugBridge.delegate.getBridge() != null) {
            throw new IllegalStateException(
                    "preInit() called after `AndroidDebugBridge` instance was created");
        }
        AndroidDebugBridge.delegate = delegate;
    }

    /**
     * Initialized the library only if needed; deprecated for non-test usages.
     *
     * @param clientSupport Indicates whether the library should enable the monitoring and
     *     interaction with applications running on the devices.
     * @see #init(boolean)
     */
    @Deprecated
    public static void initIfNeeded(boolean clientSupport) {
        delegateIsUsed = true;
        delegate.initIfNeeded(clientSupport);
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
    public static void init(boolean clientSupport) {
        delegateIsUsed = true;
        delegate.init(clientSupport);
    }

    /**
     * Similar to {@link #init(boolean)}, with ability to enable libusb and pass a custom set of
     * env. variables.
     */
    public static void init(
            boolean clientSupport, boolean useLibusb, @NonNull Map<String, String> env) {
        delegateIsUsed = true;
        delegate.init(
                AdbInitOptions.builder()
                        .withEnv(env)
                        .setClientSupportEnabled(clientSupport)
                        .withEnv("ADB_LIBUSB", useLibusb ? "1" : "0")
                        .build());
    }

    /** Similar to {@link #init(boolean)}, with ability to pass a custom set of env. variables. */
    public static void init(AdbInitOptions options) {
        delegateIsUsed = true;
        delegate.init(options);
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
    public static boolean optionsChanged(
            @NonNull AdbInitOptions options,
            @NonNull String osLocation,
            boolean forceNewBridge,
            long terminateTimeout,
            long initTimeout,
            @NonNull TimeUnit unit) {
        delegateIsUsed = true;
        return delegate.optionsChanged(
                options, osLocation, forceNewBridge, terminateTimeout, initTimeout, unit);
    }

    @VisibleForTesting
    public static void enableFakeAdbServerMode(int port) {
        delegateIsUsed = true;
        delegate.enableFakeAdbServerMode(port);
    }

    @VisibleForTesting
    public static void disableFakeAdbServerMode() {
        delegateIsUsed = true;
        delegate.disableFakeAdbServerMode();
    }

    /** Terminates the ddm library. This must be called upon application termination. */
    public static void terminate() {
        delegateIsUsed = true;
        delegate.terminate();
    }

    /**
     * Returns whether the ddmlib is setup to support monitoring and interacting with {@link
     * ClientImpl}s running on the {@link IDevice}s.
     */
    public static boolean getClientSupport() {
        delegateIsUsed = true;
        return delegate.getClientSupport();
    }

    /**
     * Returns the current {@link ClientManager} instance if {@link Client} process tracking is
     * delegated to an external implementation, or {@code null} if {@link Client} processes are
     * monitored with the default {@link #getClientSupport()} implementation.
     */
    @Nullable
    public ClientManager getClientManager() {
        delegateIsUsed = true;
        return delegate.getClientManager();
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
    public static InetSocketAddress getSocketAddress() {
        delegateIsUsed = true;
        return delegate.getSocketAddress();
    }

    /**
     * Attempts to connect to the local android debug bridge server.
     *
     * @return a connected socket if success
     * @throws IOException should errors occur when opening the connection
     */
    public static SocketChannel openConnection() throws IOException {
        delegateIsUsed = true;
        return delegate.openConnection();
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
     * @deprecated This method may hang if ADB is not responding. Use {@link #createBridge(long,
     *     TimeUnit)} instead.
     */
    @Deprecated
    @Nullable
    public static AndroidDebugBridge createBridge() {
        delegateIsUsed = true;
        return delegate.createBridge();
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
    public static AndroidDebugBridge createBridge(long timeout, @NonNull TimeUnit unit) {
        delegateIsUsed = true;
        return delegate.createBridge(timeout, unit);
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
    public static AndroidDebugBridge createBridge(
            @NonNull String osLocation, boolean forceNewBridge) {
        delegateIsUsed = true;
        return delegate.createBridge(osLocation, forceNewBridge);
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
    public static AndroidDebugBridge createBridge(
            @NonNull String osLocation,
            boolean forceNewBridge,
            long timeout,
            @NonNull TimeUnit unit) {
        delegateIsUsed = true;
        return delegate.createBridge(osLocation, forceNewBridge, timeout, unit);
    }

    /**
     * Returns the current debug bridge. Can be <code>null</code> if none were created.
     */
    @Nullable
    public static AndroidDebugBridge getBridge() {
        delegateIsUsed = true;
        return delegate.getBridge();
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
    public static void disconnectBridge() {
        delegateIsUsed = true;
        delegate.disconnectBridge();
    }

    /**
     * Disconnects the current debug bridge, and destroy the object. A new object will have to be
     * created with {@link #createBridge(String, boolean)}.
     *
     * <p>This also stops the current adb host server.
     *
     * @return {@code true} if the method succeeds within the specified timeout.
     */
    public static boolean disconnectBridge(long timeout, @NonNull TimeUnit unit) {
        delegateIsUsed = true;
        return delegate.disconnectBridge(timeout, unit);
    }

    /**
     * Adds the listener to the collection of listeners who will be notified when a new
     * {@link AndroidDebugBridge} is connected, by sending it one of the messages defined
     * in the {@link IDebugBridgeChangeListener} interface.
     * @param listener The listener which should be notified.
     */
    public static void addDebugBridgeChangeListener(@NonNull IDebugBridgeChangeListener listener) {
        delegateIsUsed = true;
        delegate.addDebugBridgeChangeListener(listener);
    }

    /**
     * Removes the listener from the collection of listeners who will be notified when a new
     * {@link AndroidDebugBridge} is started.
     * @param listener The listener which should no longer be notified.
     */
    public static void removeDebugBridgeChangeListener(IDebugBridgeChangeListener listener) {
        delegateIsUsed = true;
        delegate.removeDebugBridgeChangeListener(listener);
    }

    @VisibleForTesting
    public static int getDebugBridgeChangeListenerCount() {
        delegateIsUsed = true;
        return delegate.getDebugBridgeChangeListenerCount();
    }

    /**
     * Adds the listener to the collection of listeners who will be notified when a {@link IDevice}
     * is connected, disconnected, or when its properties or its {@link ClientImpl} list changed, by
     * sending it one of the messages defined in the {@link IDeviceChangeListener} interface.
     *
     * @param listener The listener which should be notified.
     */
    public static void addDeviceChangeListener(@NonNull IDeviceChangeListener listener) {
        // Ok to use this delegate even before preInit, since it just stores a listener in a
        // static list.
        delegate.addDeviceChangeListener(listener);
    }

    /**
     * Removes the listener from the collection of listeners who will be notified when a {@link
     * IDevice} is connected, disconnected, or when its properties or its {@link ClientImpl} list
     * changed.
     *
     * @param listener The listener which should no longer be notified.
     */
    public static void removeDeviceChangeListener(IDeviceChangeListener listener) {
        // Ok to use this delegate even before preInit, since it just stores a listener in a
        // static list.
        delegate.removeDeviceChangeListener(listener);
    }

    @VisibleForTesting
    public static int getDeviceChangeListenerCount() {
        delegateIsUsed = true;
        return delegate.getDeviceChangeListenerCount();
    }

    /**
     * Adds the listener to the collection of listeners who will be notified when a {@link
     * ClientImpl} property changed, by sending it one of the messages defined in the {@link
     * IClientChangeListener} interface.
     *
     * @param listener The listener which should be notified.
     */
    public static void addClientChangeListener(IClientChangeListener listener) {
        // Ok to use this delegate even before preInit, since it just stores a listener in a
        // static list.
        delegate.addClientChangeListener(listener);
    }

    /**
     * Removes the listener from the collection of listeners who will be notified when a {@link
     * ClientImpl} property changes.
     *
     * @param listener The listener which should no longer be notified.
     */
    public static void removeClientChangeListener(IClientChangeListener listener) {
        // Ok to use this delegate even before preInit, since it just stores a listener in a
        // static list.
        delegate.removeClientChangeListener(listener);
    }

    /**
     * @return version of the ADB server if we were able to successfully retrieve it, {@code null}
     *     otherwise.
     */
    public @Nullable AdbVersion getCurrentAdbVersion() {
        delegateIsUsed = true;
        return delegate.getCurrentAdbVersion();
    }

    /**
     * Returns the devices.
     *
     * @see #hasInitialDeviceList()
     */
    @NonNull
    public IDevice[] getDevices() {
        delegateIsUsed = true;
        return delegate.getDevices();
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
     * IDeviceChangeListener} object.
     */
    public boolean hasInitialDeviceList() {
        delegateIsUsed = true;
        return delegate.hasInitialDeviceList();
    }

    /**
     * Returns whether the {@link AndroidDebugBridge} object is still connected to the adb daemon.
     */
    public boolean isConnected() {
        delegateIsUsed = true;
        return delegate.isConnected();
    }

    @Nullable
    public IDeviceUsageTracker getiDeviceUsageTracker() {
        delegateIsUsed = true;
        return delegate.getiDeviceUsageTracker();
    }

    /** Creates a new bridge not linked to any particular adb executable. */
    protected AndroidDebugBridge() {}

    interface AdbOutputProcessor<T> {
        T process(Process process, BufferedReader r) throws IOException;
    }

    public static ListenableFuture<AdbVersion> getAdbVersion(@NonNull final File adb) {
        delegateIsUsed = true;
        return delegate.getAdbVersion(adb);
    }

    @NonNull
    public static ListenableFuture<String> getVirtualDeviceId(
            @NonNull ListeningExecutorService service, @NonNull File adb, @NonNull IDevice device) {
        delegateIsUsed = true;
        return delegate.getVirtualDeviceId(service, adb, device);
    }

    /**
     * Returns the set of devices reported by the adb command-line. This is mainly intended for the
     * Connection Assistant or other diagnostic tools that need to validate the state of the {@link
     * #getDevices()} list via another channel. Code that just needs to access the list of devices
     * should call {@link #getDevices()} instead.
     */
    public ListenableFuture<List<AdbDevice>> getRawDeviceList() {
        delegateIsUsed = true;
        return delegate.getRawDeviceList();
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
        delegateIsUsed = true;
        return delegate.restart();
    }

    /**
     * Restarts adb, but not the services around it.
     *
     * @return true if success.
     */
    public boolean restart(long timeout, @NonNull TimeUnit unit) {
        delegateIsUsed = true;
        return delegate.restart(timeout, unit);
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
    public static void deviceConnected(@NonNull IDevice device) {
        delegateIsUsed = true;
        delegate.deviceConnected(device);
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
    public static void deviceDisconnected(@NonNull IDevice device) {
        delegateIsUsed = true;
        delegate.deviceDisconnected(device);
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
    public static void deviceChanged(@NonNull IDevice device, int changeMask) {
        delegateIsUsed = true;
        delegate.deviceChanged(device, changeMask);
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
    public static void clientChanged(@NonNull Client client, int changeMask) {
        delegateIsUsed = true;
        delegate.clientChanged(client, changeMask);
    }

    /**
     * @return If operating in user managed ADB mode where ddmlib will and should not manage the ADB server.
     */
    public static boolean isUserManagedAdbMode() {
        delegateIsUsed = true;
        return delegate.isUserManagedAdbMode();
    }

    /**
     * Starts the adb host side server. This method should not be used when using user managed ADB
     * server as the server lifecycle should be managed by the user, not ddmlib.
     *
     * @return true if success
     */
    public boolean startAdb(long timeout, @NonNull TimeUnit unit) {
        delegateIsUsed = true;
        return delegate.startAdb(timeout, unit);
    }

    public static void setJdwpTracerFactory(@NonNull JdwpTracerFactory factory) {
        sJdwpTracerFactory = factory;
    }

    @NonNull
    public static DDMLibJdwpTracer newJdwpTracer() {
        return sJdwpTracerFactory.newJwpTracer();
    }

    public static void setJdwpProcessorFactory(@NonNull JdwpProcessorFactory factory) {
        sJdwpProcessorFactory = factory;
    }

    @NonNull
    public static JdwpProcessor newProcessor() {
        return sJdwpProcessorFactory.newProcessor();
    }
}
