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
import com.android.ddmlib.Log.LogLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Class to parse raw output of {@code adb logcat -v long} to {@link LogCatMessage} objects. */
public class LogCatMessageParser {
    private static final Pattern DATE_TIME =
            Pattern.compile("\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d\\d\\d");

    static final Pattern PROCESS_ID = Pattern.compile("\\d+");
    static final Pattern THREAD_ID = Pattern.compile("\\w+");
    static final Pattern PRIORITY = Pattern.compile("[VDIWEAF]");
    static final Pattern TAG = Pattern.compile(".*?");

    /**
     * Pattern for logcat -v long header ([ MM-DD HH:MM:SS.mmm PID:TID LEVEL/TAG ]). Example:
     *
     * <pre>[ 08-18 16:39:11.760  2977: 2988 D/PhoneInterfaceManager ]</pre>
     *
     * <p>Group 1: Date + Time<br>
     * Group 2: PID<br>
     * Group 3: TID (hex on some systems!)<br>
     * Group 4: Log Level character<br>
     * Group 5: Tag
     */
    private static final Pattern HEADER =
            Pattern.compile(
                    "^\\[ ("
                            + DATE_TIME
                            + ") +("
                            + PROCESS_ID
                            + "): *("
                            + THREAD_ID
                            + ") ("
                            + PRIORITY
                            + ")/("
                            + TAG
                            + ") +]$");

    @Nullable
    LogCatHeader mPrevHeader;

    /**
     * Parse a header line into a {@link LogCatHeader} object, or {@code null} if the input line
     * doesn't match the expected format.
     *
     * @param line   raw text that should be the header line from logcat -v long
     * @param device device from which these log messages have been received
     * @return a {@link LogCatHeader} which represents the passed in text
     */
    @Nullable
    public LogCatHeader processLogHeader(@NonNull String line, @Nullable IDevice device) {
        Matcher matcher = HEADER.matcher(line);

        if (!matcher.matches()) {
            return null;
        }

        LogCatTimestamp dateTime = LogCatTimestamp.fromString(matcher.group(1));
        int processId = parseProcessId(matcher.group(2));
        int threadId = parseThreadId(matcher.group(3));
        LogLevel priority = parsePriority(matcher.group(4));
        String tag = matcher.group(5);

        mPrevHeader =
                new LogCatHeader(
                        priority,
                        processId,
                        threadId,
                        getPackageName(device, processId),
                        tag,
                        dateTime);

        return mPrevHeader;
    }

    static int parseProcessId(@NonNull String string) {
        try {
            return Integer.parseInt(string);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    static int parseThreadId(@NonNull String string) {
        try {
            // Some versions of logcat return hexadecimal thread IDs. Propagate them as decimal.
            return Integer.decode(string);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    /**
     * Parses the <a href="https://developer.android.com/studio/command-line/logcat.html">priority
     * part of a logcat message header:</a> the "I" in
     *
     * <pre>[          1517949446.554  2848: 2848 I/MainActivity ]</pre>
     *
     * @return the log level corresponding to the priority. If the argument is not one of the
     *     expected letters returns LogLevel.WARN.
     */
    @NonNull
    static LogLevel parsePriority(@NonNull String string) {
        LogLevel priority = LogLevel.getByLetterString(string);

        if (priority == null) {
            if (!string.equals("F")) {
                return LogLevel.WARN;
            }

            return LogLevel.ASSERT;
        }

        return priority;
    }

    @NonNull
    static String getPackageName(@Nullable IDevice device, int processId) {
        if (device == null || processId == -1) {
            return "?";
        }

        String name = device.getClientName(processId);

        if (name == null || name.isEmpty()) {
            return "?";
        }

        return name;
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
