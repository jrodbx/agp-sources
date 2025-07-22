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
package com.android.ddmlib.idevicemanager;

import com.android.annotations.NonNull;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import java.util.List;

/**
 * An extension point to {@code ddmlib} that allows {@link AndroidDebugBridge} to delegate {@link
 * IDevice} device tracking to an external component. See {@link AndroidDebugBridge#getDevices()}.
 */
public interface IDeviceManager extends AutoCloseable {

    /** Returns the current of list of {@link IDevice} */
    @NonNull
    List<IDevice> getDevices();

    /** Returns true if the device list data was built at least once */
    boolean hasInitialDeviceList();
}
