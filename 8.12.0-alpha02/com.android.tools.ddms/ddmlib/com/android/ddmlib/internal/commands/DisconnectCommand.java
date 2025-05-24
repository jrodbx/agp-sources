/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.ddmlib.internal.commands;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.internal.DeviceMonitor;

public class DisconnectCommand implements ICommand {
    public static final String COMMAND = "disconnect";
    private DeviceMonitor myMonitor;

    public DisconnectCommand(DeviceMonitor monitor) {
        myMonitor = monitor;
    }

    /**
     * Args for disconnect command are expected to be in the format of [device id]:[process id]
     *
     * @param argsString device id and process id to disconnect. Eg ("emulator:1234")
     */
    @Override
    public CommandResult run(String argsString) {
        try {
            if (argsString == null) {
                throw new IllegalArgumentException("Expected arguments got null.");
            }
            String[] params = argsString.split(":");
            if (params.length != 2) {
                throw new IllegalArgumentException("Expected 2 parameters got " + params.length);
            }
            String deviceId = params[0];
            int pid = Integer.parseInt((params[1]));
            for (IDevice device : myMonitor.getDevices()) {
                if (device.getSerialNumber().equals(deviceId)) {
                    myMonitor.disconnectClient(device, pid);
                    return new CommandResult();
                }
            }
            Log.w("DisconnectCommand", "No client found for given args (" + argsString + ")");
            return new CommandResult("No client found for " + argsString);
        } catch (Exception ex) {
            // Failed to disconnect the client.
            Log.e("DisconnectCommand", ex);
            return new CommandResult("Unknown error: " + ex.getMessage());
        }
    }
}
