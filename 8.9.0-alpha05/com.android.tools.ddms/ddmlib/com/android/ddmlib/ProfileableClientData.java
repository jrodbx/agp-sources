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
import com.android.ddmlib.internal.ProfileableClientImpl;

/** Contains the data of a {@link ProfileableClientImpl}. */
public class ProfileableClientData {
    // the client's process ID
    private final int mPid;

    // client's underlying package name.
    @NonNull private String mProcessName;

    // client's ABI
    @NonNull private String mAbi;

    /** Generic constructor. */
    public ProfileableClientData(int pid, @NonNull String processName, @NonNull String abi) {
        mPid = pid;
        mProcessName = processName;
        mAbi = abi;
    }

    /** Returns the process ID. */
    public int getPid() {
        return mPid;
    }

    /** Returns the application's process name. */
    @NonNull
    public String getProcessName() {
        return mProcessName;
    }

    /** Returns the abi flavor (32-bit or 64-bit) of the application, null if unknown or not set. */
    @NonNull
    public String getAbi() {
        return mAbi;
    }

    public void setProcessName(@NonNull String name) {
        mProcessName = name;
    }
}
