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
package com.android.incfs.install.adb.ddmlib;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

/** Bridge class to use utils.ILogger for Incremental. */
public class DeviceLogger implements com.android.incfs.install.ILogger {
    private @NonNull com.android.utils.ILogger mLogger;

    public DeviceLogger(@NonNull com.android.utils.ILogger logger) {
        mLogger = logger;
    }

    @Override
    public void error(@Nullable Throwable t, @Nullable String msgFormat, Object... args) {
        mLogger.error(t, msgFormat, args);
    }

    @Override
    public void warning(@NonNull String msgFormat, Object... args) {
        mLogger.warning(msgFormat, args);
    }

    @Override
    public void info(@NonNull String msgFormat, Object... args) {
        mLogger.info(msgFormat, args);
    }

    @Override
    public void verbose(@NonNull String msgFormat, Object... args) {
        mLogger.verbose(msgFormat, args);
    }
}
