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

/** Controls whether a block of data that must be delivered to the device should be served. */
public interface IBlockFilter {

    /**
     * Callback invoked by the associated {@link IncrementalInstallSession} to determine whether a
     * block of data requested by the device should be served to the device.
     *
     * @return true if the block of data should be served; otherwise, false.
     */
    boolean shouldServeBlock(@NonNull PendingBlock block);
}
