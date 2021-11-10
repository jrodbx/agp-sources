/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.ddmlib.logcat;

import com.android.annotations.NonNull;
import com.android.ddmlib.Log.LogLevel;
import java.time.Instant;

/**
 * Model a single log message output from {@code logcat -v long}.
 *
 * Every message is furthermore associated with a {@link LogCatHeader} which contains additionally
 * meta information about the message.
 */
public final class LogCatMessage {

    @NonNull
    private final LogCatHeader mHeader;

    @NonNull
    private final String mMessage;

    /**
     * Construct an immutable log message object.
     */
    public LogCatMessage(@NonNull LogCatHeader header, @NonNull String msg) {
        mHeader = header;
        mMessage = msg;
    }

    /**
     * Helper constructor to generate a dummy message, useful if we want to add message from code
     * that matches the logcat format.
     */
    public LogCatMessage(@NonNull LogLevel logLevel, @NonNull String message) {
        this(new LogCatHeader(logLevel, -1, -1, "", "", Instant.EPOCH), message);
    }


    @NonNull
    public LogCatHeader getHeader() {
        return mHeader;
    }

    @NonNull
    public String getMessage() {
        return mMessage;
    }

    @NonNull
    public LogLevel getLogLevel() {
        return mHeader.getLogLevel();
    }

    public int getPid() {
        return mHeader.getPid();
    }

    public int getTid() {
        return mHeader.getTid();
    }

    @NonNull
    public String getAppName() {
        return mHeader.getAppName();
    }

    @NonNull
    public String getTag() {
        return mHeader.getTag();
    }

    @NonNull
    public Instant getTimestamp() {
        return mHeader.getTimestamp();
    }

    @Override
    public String toString() {
        return mHeader.toString() + ": " + mMessage;
    }
}
