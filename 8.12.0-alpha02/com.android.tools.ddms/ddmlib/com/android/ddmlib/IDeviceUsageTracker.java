/*
 * Copyright (C) 2023 The Android Open Source Project
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

public interface IDeviceUsageTracker {

    // Identifier of the `IDevice` method.
    // This should be kept in sync with the corresponding enum in
    // `tools/analytics-library/protos/src/main/proto/studio_stats.proto`
    enum Method {
        METHOD_UNSPECIFIED,
        GET_NAME,
        EXECUTE_SHELL_COMMAND_1,
        EXECUTE_SHELL_COMMAND_2,
        EXECUTE_SHELL_COMMAND_3,
        EXECUTE_SHELL_COMMAND_4,
        EXECUTE_SHELL_COMMAND_5,
        GET_SYSTEM_PROPERTY,
        GET_SERIAL_NUMBER,
        GET_AVD_NAME,
        GET_AVD_PATH,
        GET_AVD_DATA,
        CREATE_AVD_DATA,
        GET_STATE,
        GET_PROPERTIES,
        GET_PROPERTY_COUNT,
        GET_PROPERTY,
        ARE_PROPERTIES_SET,
        SUPPORTS_FEATURE_1,
        SUPPORTS_FEATURE_2,
        SERVICES,
        TO_STRING,
        IS_ONLINE,
        IS_EMULATOR,
        IS_OFFLINE,
        IS_BOOT_LOADER,
        GET_CLIENTS,
        GET_CLIENT,
        GET_PROFILEABLE_CLIENTS,
        CREATE_FORWARD_1,
        CREATE_FORWARD_2,
        REMOVE_FORWARD,
        GET_CLIENT_NAME,
        PUSH_FILE,
        PULL_FILE,
        INSTALL_PACKAGE_1,
        INSTALL_PACKAGE_2,
        INSTALL_PACKAGE_3,
        INSTALL_PACKAGES_1,
        INSTALL_PACKAGES_2,
        GET_LAST_INSTALL_METRICS,
        SYNC_PACKAGE_TO_DEVICE,
        INSTALL_REMOTE_PACKAGE,
        REMOVE_REMOTE_PACKAGE,
        UNINSTALL_PACKAGE,
        UNINSTALL_APP,
        ROOT,
        FORCE_STOP,
        KILL,
        IS_ROOT,
        GET_ABIS,
        GET_DENSITY,
        GET_VERSION,
        EXECUTE_REMOTE_COMMAND_1,
        EXECUTE_REMOTE_COMMAND_2,
        EXECUTE_REMOTE_COMMAND_3,
        EXECUTE_REMOTE_COMMAND_4,
        RAW_EXEC2,
        STAT_FILE,
        UNSUPPORTED_METHOD,
        CREATE_REVERSE,
        REMOVE_REVERSE
    }

    void logUsage(Method method, boolean isException);
}
