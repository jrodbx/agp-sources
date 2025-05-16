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
package com.android.sdklib.repository;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.ProgressIndicatorAdapter;
import com.android.utils.ILogger;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * A {@link ProgressIndicator} that wraps a {@link ILogger}.
 */
public class LoggerProgressIndicatorWrapper extends ProgressIndicatorAdapter {

    private final ILogger mWrapped;

    public LoggerProgressIndicatorWrapper(@NonNull ILogger toWrap) {
        mWrapped = toWrap;
    }

    @Override
    public void logWarning(@NonNull String s) {
        mWrapped.warning(s);
    }

    @Override
    public void logWarning(@NonNull String s, @Nullable Throwable e) {
        if (e == null) {
            logWarning(s);
            return;
        }

        mWrapped.warning("%1$s:\n%2$s", s, throwableToString(e));
    }

    @Override
    public void logError(@NonNull String s) {
        logError(s, null);
    }

    @Override
    public void logError(@NonNull String s, @Nullable Throwable e) {
        mWrapped.error(e, s);
    }

    @Override
    public void logInfo(@NonNull String s) {
        mWrapped.lifecycle(s);
    }

    @Override
    public void logVerbose(@NonNull String s) {
        mWrapped.verbose(s);
    }

    private static String throwableToString(@NonNull Throwable e) {
        StringWriter writer = new StringWriter();
        e.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
