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
package com.android.ddmlib;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.annotations.VisibleForTesting;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public abstract class EmulatorConsole {

    private static final Map<String, EmulatorConsole> sTestConsoles = new HashMap<>();


    /** Disconnect the socket channel and remove self from emulator console cache. */
    public abstract void close();

    /**
     * Sends a KILL command to the emulator.
     */
    public abstract void kill();

    /**
     * The AVD name. If the command failed returns the error message after "KO: " or null.
     */
    @Nullable
    public abstract String getAvdName();

    @NonNull
    public Path getAvdNioPath() throws CommandFailedException {
        return Paths.get(getAvdPath());
    }

    /**
     * The absolute path to the virtual device in the file system. The path is operating system
     * dependent; it will have / name separators on Linux and \ separators on Windows.
     *
     * @throws CommandFailedException If the subcommand failed or if the emulator's version is older
     *     than 30.0.18
     * @deprecated Use {@link #getAvdNioPath}
     */
    @Deprecated
    @NonNull
    public abstract String getAvdPath() throws CommandFailedException;

    @Nullable
    public abstract String startEmulatorScreenRecording(@Nullable String args);

    @Nullable
    public abstract String stopScreenRecording();

    /**
     * Register a console instance corresponding to the given device to be used during testing. You
     * must call [.clearConsolesForTest] at the end of your test.
     */
    @VisibleForTesting
    public static void registerConsoleForTest(String deviceSerial, EmulatorConsole console) {
        sTestConsoles.put(deviceSerial, console);
    }

    /**
     * This must be called at the end of any test where [.registerConsoleForTest] is called.
     */
    @VisibleForTesting
    public static void clearConsolesForTest() {
        sTestConsoles.clear();
    }

    public static EmulatorConsole getConsole(IDevice d) {
        EmulatorConsole result = sTestConsoles.get(d.getSerialNumber());
        if (result == null) {
            result = EmulatorConsoleImpl.createConsole(d);
        }
        return result;
    }

    /**
     * Return port of emulator given its serial number.
     *
     * @param serialNumber the emulator's serial number
     * @return the integer port or `null` if it could not be determined
     */
    public static Integer getEmulatorPort(String serialNumber) {
        return EmulatorConsoleImpl.getEmulatorPortFromSerialNumber(serialNumber);
    }
}
