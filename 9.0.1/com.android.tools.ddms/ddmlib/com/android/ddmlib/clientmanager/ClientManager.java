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
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;

/**
 * An extension point to {@code ddmlib} that allows {@link AndroidDebugBridge} to delegate {@link
 * Client} process tracking to an external component. See {@link
 * AndroidDebugBridge#getClientManager()}.
 */
public interface ClientManager {

    /**
     * Creates a {@link DeviceClientManager} instance for the given {@link IDevice}, where {@link
     * DeviceClientManagerListener} is a callback that is invoked when events related to {@link
     * Client processes} occur.
     *
     * <p>The returned {@link DeviceClientManager} instance should be discarded when the {@link
     * IDevice} instance is invalidated, e.g. when the device becomes offline.
     *
     * <p>This method is used only in tests
     */
    @NonNull
    DeviceClientManager createDeviceClientManager(
            @NonNull AndroidDebugBridge bridge,
            @NonNull IDevice device,
            @NonNull DeviceClientManagerListener listener);

    /**
     * Creates a {@link DeviceClientManager} instance for the given {@link IDevice}, where the
     * default {@link DeviceClientManagerListener} callback is used when events related to {@link
     * Client processes} occur.
     *
     * <p>The returned {@link DeviceClientManager} instance should be discarded when the {@link
     * IDevice} instance is invalidated, e.g. when the device becomes offline.
     */
    @NonNull
    DeviceClientManager createDeviceClientManager(
            @NonNull AndroidDebugBridge bridge, @NonNull IDevice device);
}
