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

public interface AdbDelegateUsageTracker {

    // Identifier of the `AndroidDebugBridgeDelegate` method.
    // This should be kept in sync with the corresponding enum in
    // `tools/analytics-library/protos/src/main/proto/studio_stats.proto`
    enum Method {
        METHOD_UNSPECIFIED,
        ADD_CLIENT_CHANGE_LISTENER,
        ADD_DEBUG_BRIDGE_CHANGE_LISTENER,
        ADD_DEVICE_CHANGE_LISTENER,
        CLIENT_CHANGED,
        CREATE_BRIDGE_1,
        CREATE_BRIDGE_2,
        CREATE_BRIDGE_3,
        CREATE_BRIDGE_4,
        DEVICE_CHANGED,
        DEVICE_CONNECTED,
        DEVICE_DISCONNECTED,
        DISABLE_FAKE_ADB_SERVER_MODE,
        DISCONNECT_BRIDGE_1,
        DISCONNECT_BRIDGE_2,
        ENABLE_FAKE_ADB_SERVER_MODE,
        GET_ADB_VERSION,
        GET_BRIDGE,
        GET_CLIENT_MANAGER,
        GET_CLIENT_SUPPORT,
        GET_CURRENT_ADB_VERSION,
        GET_DEBUG_BRIDGE_CHANGE_LISTENER_COUNT,
        GET_DEVICE_CHANGE_LISTENER_COUNT,
        GET_DEVICES,
        GET_IDEVICE_USAGE_TRACKER,
        GET_RAW_DEVICE_LIST,
        GET_SOCKET_ADDRESS,
        GET_VIRTUAL_DEVICE_ID,
        HAS_INITIAL_DEVICE_LIST,
        INIT_1,
        INIT_2,
        INIT_3,
        INIT_IF_NEEDED,
        IS_CONNECTED,
        IS_USER_MANAGED_ADB_MODE,
        OPEN_CONNECTION,
        OPTIONS_CHANGED,
        REMOVE_CLIENT_CHANGE_LISTENER,
        REMOVE_DEBUG_BRIDGE_CHANGE_LISTENER,
        REMOVE_DEVICE_CHANGE_LISTENER,
        RESTART_1,
        RESTART_2,
        START_ADB,
        TERMINATE
    }

    void logUsage(AdbDelegateUsageTracker.Method method, boolean isException);
}
