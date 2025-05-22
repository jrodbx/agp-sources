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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.ILogger;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.io.Closeables;
import java.io.InputStream;

/** Class providing access to an embedded list of default devices. */
public class DefaultDevices {
    @NonNull private final ILogger mLog;

    private Table<String, String, Device> mDefaultDevices;

    public DefaultDevices(@NonNull ILogger log) {
        mLog = log;
    }

    /**
     * Initializes the {@link Device}s packaged with the SDK.
     *
     * @return True if the list has changed.
     */
    public synchronized boolean init() {
        if (mDefaultDevices != null) {
            return false;
        }
        InputStream stream = DefaultDevices.class.getResourceAsStream(SdkConstants.FN_DEVICES_XML);
        try {
            assert stream != null : SdkConstants.FN_DEVICES_XML + " not bundled in sdklib.";
            mDefaultDevices = DeviceParser.parse(stream);
            return true;
        } catch (IllegalStateException e) {
            // The device builders can throw IllegalStateExceptions if
            // build gets called before everything is properly setup
            mLog.error(e, null);
            mDefaultDevices = HashBasedTable.create();
        } catch (Exception e) {
            mLog.error(e, "Error reading default devices");
            mDefaultDevices = HashBasedTable.create();
        } finally {
            Closeables.closeQuietly(stream);
        }
        return false;
    }

    @Nullable
    public Device getDevice(@NonNull String id, @NonNull String manufacturer) {
        return mDefaultDevices.get(id, manufacturer);
    }

    @Nullable
    public Table<String, String, Device> getDevices() {
        return mDefaultDevices;
    }
}
