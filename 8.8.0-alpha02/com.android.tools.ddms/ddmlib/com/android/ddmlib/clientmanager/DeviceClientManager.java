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
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ProfileableClient;
import java.util.List;

/** An abstraction that allows tracking to {@link Client} instances of a given {@link IDevice}. */
public interface DeviceClientManager {

    @NonNull
    IDevice getDevice();

    /**
     * Provides access to the list of {@link Client} instances for this {@link IDevice}.
     *
     * <p>Note: This method should only be used by {@link IDevice#getClients()} implementation that
     * want to expose {@link Client} instances without relying on the internal client tracking
     * mechanism of ddmlib.
     */
    @NonNull
    List<Client> getClients();

    @NonNull
    List<ProfileableClient> getProfileableClients();
}
