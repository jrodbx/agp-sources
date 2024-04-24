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
import com.android.ddmlib.ClientData;
import com.android.ddmlib.ProfileableClientData;

/**
 * Listener for process related changes coming from {@link DeviceClientManager}
 *
 * <p>Note: Callbacks only happen on a worker thread, never on the EDT thread.
 */
@WorkerThread
public interface DeviceClientManagerListener {

    /**
     * Invoked when the {@link DeviceClientManager#getClients()} list has been updated with new
     * and/or removed processes.
     */
    void processListUpdated(
            @NonNull AndroidDebugBridge bridge, @NonNull DeviceClientManager deviceClientManager);

    /**
     * Invoked when the {@link DeviceClientManager#getProfileableClients()} list has been updated
     * with new and/or removed processes. Also invoked when the
     * {@link ProfileableClientData#getProcessName()} value of any profileable process is updated.
     */
    void profileableProcessListUpdated(
            @NonNull AndroidDebugBridge bridge, @NonNull DeviceClientManager deviceClientManager);

    /**
     * Invoked when {@link ClientData#getPackageName()} or {@link ClientData#getClientDescription()}
     * of the given {@link Client} has changed.
     */
    void processNameUpdated(
            @NonNull AndroidDebugBridge bridge,
            @NonNull DeviceClientManager deviceClientManager,
            @NonNull Client client);

    /**
     * Invoked when {@link ClientData#getDebuggerConnectionStatus()} of the given {@link Client} has
     * changed.
     */
    void processDebuggerStatusUpdated(
            @NonNull AndroidDebugBridge bridge,
            @NonNull DeviceClientManager deviceClientManager,
            @NonNull Client client);


    /**
     * Invoked when {@link Client#requestAllocationDetails()} of the given {@link Client} has
     * successfully retrieved allocation data into {@link ClientData#getAllocationsData()}.
     */
    void processHeapAllocationsUpdated(
            @NonNull AndroidDebugBridge bridge,
            @NonNull DeviceClientManager deviceClientManager,
            @NonNull Client client);

    /**
     * Invoked when the profiler state has changed (e.g. {@link Client#startMethodTracer()}).
     */
    void processMethodProfilingStatusUpdated(
            @NonNull AndroidDebugBridge bridge,
            @NonNull DeviceClientManager deviceClientManager,
            @NonNull Client client);
}
