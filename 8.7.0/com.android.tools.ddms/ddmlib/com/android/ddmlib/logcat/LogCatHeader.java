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
package com.android.ddmlib.logcat;

import com.android.annotations.NonNull;
import com.android.ddmlib.Log.LogLevel;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.Objects;

public class LogCatHeader {

    @NonNull
    private static final DateTimeFormatter
            EPOCH_TIME_FORMATTER
            = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.INSTANT_SECONDS)
            .appendFraction(ChronoField.MILLI_OF_SECOND, 3, 3, true)
            .toFormatter(Locale.ROOT);

    @NonNull private final LogLevel logLevel;

    private final int pid;

    private final int tid;

    @NonNull private final String appName;

    @NonNull private final String tag;

    @NonNull private final Instant timestamp;

    public LogCatHeader(@NonNull LogLevel level,
            int pid,
            int tid,
            @NonNull String name,
            @NonNull String tag,
            @NonNull Instant timestamp) {
        logLevel = level;
        this.pid = pid;
        this.tid = tid;
        appName = name;
        this.tag = tag;
        this.timestamp = timestamp;
    }

    @NonNull
    public LogLevel getLogLevel() {
        return logLevel;
    }

    public int getPid() {
        return pid;
    }

    public int getTid() {
        return tid;
    }

    @NonNull
    public String getAppName() {
        return appName;
    }

    @NonNull
    public String getTag() {
        return tag;
    }

    @NonNull
    public Instant getTimestamp() {
        return timestamp;
    }

    @NonNull
    public LogLevel component1() {
        return getLogLevel();
    }

    public int component2() {
        return getPid();
    }

    public int component3() {
        return getTid();
    }

    @NonNull
    public String component4() {
        return getAppName();
    }

    @NonNull
    public String component5() {
        return getTag();
    }

    @NonNull
    public Instant component6() {
        return getTimestamp();
    }

    @Override
    public String toString() {
        String epoch = EPOCH_TIME_FORMATTER.format(timestamp);
        char priority = logLevel.getPriorityLetter();
        return String.format(Locale.ROOT,
                             "%s: %c/%s(%d:%d) %s",
                             epoch,
                             priority,
                             tag,
                             pid,
                             tid,
                             appName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(logLevel, pid, tid, appName, tag, timestamp);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof LogCatHeader)) {
            return false;
        }
        LogCatHeader other = (LogCatHeader)obj;
        return logLevel == other.logLevel &&
               pid == other.pid &&
               tid == other.tid &&
               appName.equals(other.appName) &&
               tag.equals(other.tag) &&
               timestamp.equals(other.timestamp);
    }
}
