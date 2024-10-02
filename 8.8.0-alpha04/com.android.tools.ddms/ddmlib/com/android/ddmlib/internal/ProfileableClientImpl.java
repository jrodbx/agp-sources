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
package com.android.ddmlib.internal;

import com.android.annotations.NonNull;
import com.android.ddmlib.ProfileableClient;
import com.android.ddmlib.ProfileableClientData;

/**
 * This represents a single profileable client, usually a Dalvik VM process.
 *
 * <p>This class gives access to basic client information.
 */
public class ProfileableClientImpl implements ProfileableClient {
    // chunk handlers stash state data in here
    private ProfileableClientData mClientData;

    /** Create an object for a new profileable client. */
    ProfileableClientImpl(int pid, @NonNull String processName, @NonNull String abi) {
        mClientData = new ProfileableClientData(pid, processName, abi);
    }

    /** Returns the {@link ProfileableClientData} object containing this client information. */
    @Override
    @NonNull
    public ProfileableClientData getProfileableClientData() {
        return mClientData;
    }
}
