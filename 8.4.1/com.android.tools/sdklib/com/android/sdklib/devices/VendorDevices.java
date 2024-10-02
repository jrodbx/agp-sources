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

package com.android.sdklib.devices;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.ILogger;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.io.Closeables;
import java.io.InputStream;

/** Class providing access to a embedded java resource list of vendor devices. */
public class VendorDevices {
    private static final String[] DEVICE_FILES = {"nexus", "wear", "tv", "automotive", "desktop"};

    @NonNull private final ILogger mLog;

    private Table<String, String, Device> mVendorDevices;

    private final Object mLock = new Object();

    public VendorDevices(@NonNull ILogger log) {
        mLog = log;
    }

    /**
     * Initializes all vendor-provided {@link Device}s: the bundled nexus.xml devices as well as all
     * those coming from extra packages.
     *
     * @return True if the list has changed.
     */
    public boolean init() {
        synchronized (mLock) {
            if (mVendorDevices != null) {
                return false;
            }

            mVendorDevices = HashBasedTable.create();

            boolean hasChanged = false;
            for (String deviceFile : DEVICE_FILES) {
                InputStream stream = VendorDevices.class.getResourceAsStream(deviceFile + ".xml");
                try {
                    mVendorDevices.putAll(DeviceParser.parse(stream));
                    hasChanged = true;
                } catch (Exception e) {
                    mLog.error(e, "Could not load " + deviceFile + " devices");
                } finally {
                    Closeables.closeQuietly(stream);
                }
            }

            return hasChanged;
        }
    }

    @Nullable
    public Device getDevice(@NonNull String id, @NonNull String manufacturer) {
        synchronized (mLock) {
            return mVendorDevices.get(id, manufacturer);
        }
    }

    @Nullable
    public Table<String, String, Device> getDevices() {
        synchronized (mLock) {
            return mVendorDevices;
        }
    }
}
