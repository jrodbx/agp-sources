/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.annotations.Nullable;
import com.android.ddmlib.Log.LogLevel;
import java.time.Instant;
import java.util.Objects;

/**
 * Data class for message header information which gets reported by logcat.
 */
public final class LogCatHeader {

    @NonNull
    private final LogLevel mLogLevel;

    private final int mPid;

    private final int mTid;

    @NonNull
    private final String mAppName;

    @NonNull
    private final String mTag;

    @NonNull private final Instant mTimestamp;

    public LogCatHeader(
            @NonNull LogLevel logLevel,
            int pid,
            int tid,
            @NonNull String appName,
            @NonNull String tag,
            @NonNull Instant timestamp) {
        mLogLevel = logLevel;
        mPid = pid;
        mTid = tid;
        mAppName = appName;
        mTag = tag;
        mTimestamp = timestamp;
    }

    @NonNull
    public LogLevel getLogLevel() {
        return mLogLevel;
    }

    public int getPid() {
        return mPid;
    }

    public int getTid() {
        return mTid;
    }

    @NonNull
    public String getAppName() {
        return mAppName;
    }

    @NonNull
    public String getTag() {
        return mTag;
    }

    @NonNull
    public Instant getTimestamp() {
        return mTimestamp;
    }

    @Override
    public boolean equals(@Nullable Object object) {
        if (!(object instanceof LogCatHeader)) {
            return false;
        }

        LogCatHeader header = (LogCatHeader) object;

        return mLogLevel.equals(header.mLogLevel)
                && mPid == header.mPid
                && mTid == header.mTid
                && mAppName.equals(header.mAppName)
                && mTag.equals(header.mTag)
                && Objects.equals(mTimestamp, header.mTimestamp);
    }

    @Override
    public int hashCode() {
        int hashCode = 17;

        hashCode = 31 * hashCode + mLogLevel.hashCode();
        hashCode = 31 * hashCode + mPid;
        hashCode = 31 * hashCode + mTid;
        hashCode = 31 * hashCode + mAppName.hashCode();
        hashCode = 31 * hashCode + mTag.hashCode();
        hashCode = 31 * hashCode + Objects.hashCode(mTimestamp);

        return hashCode;
    }

    @Override
    public String toString() {
        return LogCatLongEpochMessageParser.EPOCH_TIME_FORMATTER.format(mTimestamp)
                + ": "
                + mLogLevel.getPriorityLetter()
                + '/'
                + mTag
                + '('
                + mPid
                + ')';
    }
}
