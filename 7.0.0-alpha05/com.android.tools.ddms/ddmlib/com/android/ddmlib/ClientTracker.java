/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.ddmlib.internal.ClientImpl;
import com.android.ddmlib.internal.DeviceImpl;

/** Tracks device {@link ClientImpl clients} */
public interface ClientTracker {
    /**
     * Callback for when a client is disconnected. This callback is meant to notify any threads that hold a registration to
     * a clients socket to release it.
     * @param client that was dropped
     */
    void trackDisconnectedClient(@NonNull ClientImpl client);

    /**
     * Callback for indicating that a client was dropped but an attempt should be made to reopen the connection with the client.
     * @param client that was dropped.
     */
    void trackClientToDropAndReopen(@NonNull ClientImpl client);

    /**
     * Callback to indicate that a device was dropped. An attempt should be made to reopen the connection with the device and
     * reestablish connection with any of the clients running on that device.
     * @param device that was dropped.
     */
    void trackDeviceToDropAndReopen(@NonNull DeviceImpl device);
}
