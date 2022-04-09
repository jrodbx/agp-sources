/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.ddmlib.clientmanager;

import com.android.annotations.NonNull;
import com.android.annotations.concurrency.WorkerThread;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import org.jetbrains.annotations.NotNull;

public class DeviceClientManagerUtils {

    /**
     * Creates a fully initialized {@link DeviceClientManager} instance for the given {@link
     * AndroidDebugBridge} instance and {@link IDevice}.
     *
     * <p>This is a utility method that should only be called by {@link AndroidDebugBridge} when
     * {@link AndroidDebugBridge#getClientManager()} is not {@code null}.
     */
    @NonNull
    public static DeviceClientManager createDeviceClientManager(
            @NonNull AndroidDebugBridge bridge, @NonNull IDevice device) {
        if (bridge.getClientManager() == null) {
            throw new IllegalStateException(
                    "AndroidDebugBridge does not have a ClientManager configured");
        }

        // Listener that notifies AndroidDebugBridge of changes to processes (clients)
        DeviceClientManagerListener listener =
                new DeviceClientManagerListener() {
                    @Override
                    @WorkerThread
                    public void processListUpdated(
                            @NotNull AndroidDebugBridge bridge,
                            @NotNull DeviceClientManager deviceClientManager) {
                        if (bridge == AndroidDebugBridge.getBridge()) {
                            AndroidDebugBridge.deviceChanged(
                                    deviceClientManager.getDevice(), IDevice.CHANGE_CLIENT_LIST);
                        }
                    }

                    @Override
                    @WorkerThread
                    public void processNameUpdated(
                            @NotNull AndroidDebugBridge bridge,
                            @NotNull DeviceClientManager deviceClientManager,
                            @NotNull Client client) {
                        if (bridge == AndroidDebugBridge.getBridge()) {
                            AndroidDebugBridge.clientChanged(client, Client.CHANGE_NAME);
                        }
                    }

                    @Override
                    @WorkerThread
                    public void processDebuggerStatusUpdated(
                            @NotNull AndroidDebugBridge bridge,
                            @NotNull DeviceClientManager deviceClientManager,
                            @NotNull Client client) {
                        if (bridge == AndroidDebugBridge.getBridge()) {
                            AndroidDebugBridge.clientChanged(client, Client.CHANGE_DEBUGGER_STATUS);
                        }
                    }
                };

        return bridge.getClientManager().createDeviceClientManager(bridge, device, listener);
    }
}
