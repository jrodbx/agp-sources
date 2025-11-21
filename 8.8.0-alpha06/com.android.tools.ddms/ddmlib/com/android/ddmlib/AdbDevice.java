/*
 * Copyright (C) 2019 The Android Open Source Project
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

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Holds the state of a device as reported by the "adb devices -l" command. */
public final class AdbDevice {
    private static Pattern SERIAL_PATTERN =
            Pattern.compile("([\\S&&[^\\(]]\\S*|\\(.*\\))\\s+(\\S+)\\s*.*$");

    /** Serial number of the device or null if none */
    private String serial;

    /** Status of the device, or null if unknown. */
    private IDevice.DeviceState state;

    public AdbDevice(String serial, IDevice.DeviceState state) {
        this.serial = serial;
        this.state = state;
    }

    public String getSerial() {
        return serial;
    }

    public IDevice.DeviceState getState() {
        return state;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AdbDevice device = (AdbDevice) o;
        return Objects.equals(serial, device.serial) && state == device.state;
    }

    @Override
    public int hashCode() {
        return Objects.hash(serial, state);
    }

    /**
     * Parse a single line returned from "adb devices -l" as an AdbDevice or null if the line
     * doesn't match the expected format.
     */
    public static AdbDevice parseAdbLine(String line) {
        Matcher matcher = SERIAL_PATTERN.matcher(line);
        if (matcher.matches()) {
            String serial = matcher.group(1);
            String stateName = matcher.group(2);

            if (serial != null && serial.startsWith("(")) {
                serial = null;
            }

            return new AdbDevice(serial, IDevice.DeviceState.getState(stateName));
        }
        return null;
    }
}
