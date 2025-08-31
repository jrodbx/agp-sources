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
import com.android.annotations.Nullable;
import com.android.ddmlib.IDevice;
import com.google.common.annotations.VisibleForTesting;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Class to parse raw output of {@code adb logcat -v long} to {@link LogCatMessage} objects.
 *
 * <p>TODO(187522636): Remove this class?
 */
public class LogCatMessageParser {

    @Nullable
    LogCatHeader mPrevHeader;

    @NonNull private final LogCatHeaderParser mHeaderParser;

    public LogCatMessageParser() {
        this(ZonedDateTime.now().getYear(), ZoneId.systemDefault());
    }

    @VisibleForTesting
    LogCatMessageParser(int year, @NonNull ZoneId zoneId) {
        mHeaderParser = new LogCatHeaderParser(year, zoneId);
    }

    /**
     * Parse a header line into a {@link LogCatHeader} object, or {@code null} if the input line
     * doesn't match the expected format.
     *
     * @param line raw text that should be the header line from logcat -v long
     * @param device device from which these log messages have been received
     * @return a {@link LogCatHeader} which represents the passed in text
     */
    @Nullable
    private LogCatHeader processLogHeader(@NonNull String line, @Nullable IDevice device) {
        LogCatHeader header = mHeaderParser.parseHeader(line, device);
        if (header == null) {
            return null;
        }
        mPrevHeader = header;
        return header;
    }


    /**
     * Parse a list of strings into {@link LogCatMessage} objects. This method maintains state from
     * previous calls regarding the last seen header of logcat messages.
     *
     * @param lines  list of raw strings obtained from logcat -v long
     * @param device device from which these log messages have been received
     * @return list of LogMessage objects parsed from the input
     * @throws IllegalStateException if given text before ever parsing a header
     */
    @NonNull
    public List<LogCatMessage> processLogLines(@NonNull String[] lines, @Nullable IDevice device) {
        List<LogCatMessage> messages = new ArrayList<>(lines.length);

        for (String line : lines) {
            if (line.isEmpty()) {
                continue;
            }

            if (processLogHeader(line, device) == null) {
                // If not a header line, this is a message line
                if (mPrevHeader == null) {
                    // If we are fed a log line without a header, there's nothing we can do with
                    // it - the header metadata is very important! So, we have no choice but to drop
                    // this line.
                    //
                    // This should rarely happen, if ever - for example, perhaps we're running over
                    // old logs where some earlier lines have been truncated.
                    continue;
                }
                messages.add(new LogCatMessage(mPrevHeader, line));
            }
        }

        return messages;
    }
}
