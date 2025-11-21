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
package com.android.incfs.install;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

/**
 * Interface used to display warnings/errors. Pulled into IncFS package to break the dependency from
 * the rest of ddmlib.
 *
 * @see com.android.utils.ILogger
 */
public interface ILogger {
    void error(@Nullable Throwable t, @Nullable String msgFormat, Object... args);

    void warning(@NonNull String msgFormat, Object... args);

    void info(@NonNull String msgFormat, Object... args);

    void verbose(@NonNull String msgFormat, Object... args);
}
