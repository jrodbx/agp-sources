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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.clientmanager.ClientManager;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public interface AndroidDebugBridgeDelegate {

    void enableFakeAdbServerMode(int port);

    void disableFakeAdbServerMode();

    void init(boolean clientSupport);

    void init(boolean clientSupport, boolean useLibusb, @NonNull Map<String, String> env);

    void init(AdbInitOptions options);

    boolean optionsChanged(
            @NonNull AdbInitOptions options,
            @NonNull String osLocation,
            boolean forceNewBridge,
            long terminateTimeout,
            long initTimeout,
            @NonNull TimeUnit unit);

    @Nullable
    AndroidDebugBridge createBridge();

    @Nullable
    AndroidDebugBridge createBridge(long timeout, @NonNull TimeUnit unit);

    @Nullable
    AndroidDebugBridge createBridge(@NonNull String osLocation, boolean forceNewBridge);

    @Nullable
    AndroidDebugBridge createBridge(
            @NonNull String osLocation,
            boolean forceNewBridge,
            long timeout,
            @NonNull TimeUnit unit);

    @Nullable
    AndroidDebugBridge getBridge();

    void initIfNeeded(boolean clientSupport);

    boolean hasInitialDeviceList();

    IDevice[] getDevices();

    boolean isConnected();

    ListenableFuture<List<AdbDevice>> getRawDeviceList();

    @Nullable
    AdbVersion getCurrentAdbVersion();

    boolean startAdb(long timeout, @NonNull TimeUnit unit);

    boolean restart();

    boolean restart(long timeout, @NonNull TimeUnit unit);

    void terminate();

    void disconnectBridge();

    boolean disconnectBridge(long timeout, @NonNull TimeUnit unit);

    void deviceConnected(@NonNull IDevice device);

    void deviceDisconnected(@NonNull IDevice device);

    void deviceChanged(@NonNull IDevice device, int mask);

    void clientChanged(@NonNull Client client, int mask);

    void addDebugBridgeChangeListener(
            @NonNull AndroidDebugBridge.IDebugBridgeChangeListener listener);

    void removeDebugBridgeChangeListener(AndroidDebugBridge.IDebugBridgeChangeListener listener);

    int getDebugBridgeChangeListenerCount();

    void addDeviceChangeListener(@NonNull AndroidDebugBridge.IDeviceChangeListener listener);

    void removeDeviceChangeListener(AndroidDebugBridge.IDeviceChangeListener listener);

    int getDeviceChangeListenerCount();

    void addClientChangeListener(AndroidDebugBridge.IClientChangeListener listener);

    void removeClientChangeListener(AndroidDebugBridge.IClientChangeListener listener);

    boolean getClientSupport();

    ClientManager getClientManager();

    InetSocketAddress getSocketAddress();

    SocketChannel openConnection() throws IOException;

    @Nullable
    IDeviceUsageTracker getiDeviceUsageTracker();

    ListenableFuture<AdbVersion> getAdbVersion(@NonNull File adb);

    ListenableFuture<String> getVirtualDeviceId(
            @NonNull ListeningExecutorService service, @NonNull File adb, @NonNull IDevice device);

    boolean isUserManagedAdbMode();
}
