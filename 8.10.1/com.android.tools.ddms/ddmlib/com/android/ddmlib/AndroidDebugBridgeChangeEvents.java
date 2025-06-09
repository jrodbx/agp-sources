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

import com.google.common.collect.Sets;

import java.util.Set;

public class AndroidDebugBridgeChangeEvents {

    private static final String ADB = "adb";

    private final Set<AndroidDebugBridge.IDebugBridgeChangeListener> bridgeListeners =
            Sets.newCopyOnWriteArraySet();

    private final Set<AndroidDebugBridge.IDeviceChangeListener> deviceListeners =
            Sets.newCopyOnWriteArraySet();

    private final Set<AndroidDebugBridge.IClientChangeListener> clientListeners =
            Sets.newCopyOnWriteArraySet();

    public void addDebugBridgeChangeListener(
            @NonNull AndroidDebugBridge.IDebugBridgeChangeListener listener) {
        bridgeListeners.add(listener);
    }

    public void removeDebugBridgeChangeListener(
            @NonNull AndroidDebugBridge.IDebugBridgeChangeListener listener) {
        bridgeListeners.remove(listener);
    }

    public int debugBridgeChangeListenerCount() {
        return bridgeListeners.size();
    }

    public void notifyBridgeChanged(@Nullable AndroidDebugBridge bridge) {
        for (AndroidDebugBridge.IDebugBridgeChangeListener listener : bridgeListeners) {
            // we attempt to catch any exception so that a bad listener doesn't kill our thread
            try {
                listener.bridgeChanged(bridge);
            } catch (Throwable t) {
                Log.e(ADB, t);
            }
        }
    }

    public void notifyBridgeRestartInitiated() {
        for (AndroidDebugBridge.IDebugBridgeChangeListener listener : bridgeListeners) {
            // we attempt to catch any exception so that a bad listener doesn't kill our thread
            try {
                listener.restartInitiated();
            } catch (Throwable t) {
                Log.e(ADB, t);
            }
        }
    }

    public void notifyBridgeRestartCompleted(boolean isSuccessful) {
        for (AndroidDebugBridge.IDebugBridgeChangeListener listener : bridgeListeners) {
            // we attempt to catch any exception so that a bad listener doesn't kill our thread
            try {
                listener.restartCompleted(isSuccessful);
            } catch (Throwable t) {
                Log.e(ADB, t);
            }
        }
    }

    public void notifyBridgeInitializationError(@NonNull Exception e) {
        for (AndroidDebugBridge.IDebugBridgeChangeListener listener : bridgeListeners) {
            // we attempt to catch any exception so that a bad listener doesn't kill our thread
            try {
                listener.initializationError(e);
            } catch (Throwable t) {
                Log.e(ADB, t);
            }
        }
    }

    public void addDeviceChangeListener(
            @NonNull AndroidDebugBridge.IDeviceChangeListener listener) {
        deviceListeners.add(listener);
    }

    public void removeDeviceChangeListener(
            @NonNull AndroidDebugBridge.IDeviceChangeListener listener) {
        deviceListeners.remove(listener);
    }

    public int deviceChangeListenerCount() {
        return deviceListeners.size();
    }

    public void notifyDeviceChanged(@NonNull IDevice device, int changeMask) {
        for (AndroidDebugBridge.IDeviceChangeListener listener : deviceListeners) {
            // we attempt to catch any exception so that a bad listener doesn't kill our thread
            try {
                listener.deviceChanged(device, changeMask);
            } catch (Throwable t) {
                Log.e(ADB, t);
            }
        }
    }

    public void notifyDeviceConnected(@NonNull IDevice device) {
        for (AndroidDebugBridge.IDeviceChangeListener listener : deviceListeners) {
            // we attempt to catch any exception so that a bad listener doesn't kill our thread
            try {
                listener.deviceConnected(device);
            } catch (Throwable t) {
                Log.e(ADB, t);
            }
        }
    }

    public void notifyDeviceDisconnected(@NonNull IDevice device) {
        for (AndroidDebugBridge.IDeviceChangeListener listener : deviceListeners) {
            // we attempt to catch any exception so that a bad listener doesn't kill our thread
            try {
                listener.deviceDisconnected(device);
            } catch (Throwable t) {
                Log.e(ADB, t);
            }
        }
    }

    public void addClientChangeListener(
            @NonNull AndroidDebugBridge.IClientChangeListener listener) {
        clientListeners.add(listener);
    }

    public void removeClientChangeListener(
            @NonNull AndroidDebugBridge.IClientChangeListener listener) {
        clientListeners.remove(listener);
    }

    public void notifyClientChanged(@NonNull Client client, int changeMask) {
        for (AndroidDebugBridge.IClientChangeListener listener : clientListeners) {
            // we attempt to catch any exception so that a bad listener doesn't kill our thread
            try {
                listener.clientChanged(client, changeMask);
            } catch (Throwable t) {
                Log.e(ADB, t);
            }
        }
    }
}
