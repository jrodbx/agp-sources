/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.sdklib;

import com.android.annotations.NonNull;

/**
 * Information on the layoutlib version.
 *
 * @deprecated Only the api level is relevant, and so should be represented by a single int.
 */
public class LayoutlibVersion implements Comparable<LayoutlibVersion> {

    private final int mApi;

    private final int mRevision;

    public static final int NOT_SPECIFIED = 0;

    public LayoutlibVersion(int api, int revision) {
        mApi = api;
        mRevision = revision;
    }

    public int getApi() {
        return mApi;
    }

    public int getRevision() {
        return mRevision;
    }

    @Override
    public int compareTo(@NonNull LayoutlibVersion rhs) {
        boolean useRev = this.mRevision > NOT_SPECIFIED && rhs.mRevision > NOT_SPECIFIED;
        int lhsValue = (this.mApi << 16) + (useRev ? this.mRevision : 0);
        int rhsValue = (rhs.mApi << 16) + (useRev ? rhs.mRevision : 0);
        return lhsValue - rhsValue;
    }
}
