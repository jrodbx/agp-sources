/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.incfs.install;

import com.android.annotations.NonNull;

/** A request sent from the device for a block of data from an APK being streamed. */
class ReadRequest {
    enum RequestType {
        /** All pages have been successfully received and streaming is complete. */
        SERVING_COMPLETE,
        /** The device is requesting a block to be served so it can be read immediately. */
        BLOCK_MISSING,
        /**
         * The device is requesting a block to be served because it predicts the block will be
         * needed soon.
         */
        PREFETCH,
        /** The device is terminating connection with the host for some unexpected reason. */
        DESTROY
    }

    @NonNull final RequestType requestType;
    final short apkId;
    final int blockIndex;

    ReadRequest(@NonNull RequestType requestType, short apkId, int blockIndex) {
        this.requestType = requestType;
        this.apkId = apkId;
        this.blockIndex = blockIndex;
    }

    @Override
    public String toString() {
        return "ReadRequest{"
                + "requestType=" + requestType
                + ", apkId=" + apkId
                + ", blockIndex=" + blockIndex
                + '}';
    }
}
