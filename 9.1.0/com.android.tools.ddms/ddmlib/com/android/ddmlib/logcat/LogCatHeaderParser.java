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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.android.annotations.Nullable;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogCatHeaderParser {

    private static final Pattern EPOCH = Pattern.compile(
            "(?<epoch>(?<epochSec>\\d+)\\.(?<epochMilli>\\d\\d\\d))");

    private static final Pattern DATE = Pattern.compile("(?<month>\\d\\d)-(?<day>\\d\\d)");

    private static final Pattern TIME =
            Pattern.compile("(?<hour>\\d\\d):(?<min>\\d\\d):(?<sec>\\d\\d)\\.(?<milli>\\d\\d\\d)");

    private static final Pattern PID = Pattern.compile("(?<pid>\\d+)");

    private static final Pattern TID = Pattern.compile("(?<tid>\\w+)");

    private static final Pattern PRIORITY = Pattern.compile("(?<priority>[VDIWEAF])");

    private static final Pattern TAG = Pattern.compile("(?<tag>.*?)");

    private static final String UNKNOWN_APP_NAME = "?";

    /**
     * Pattern for "logcat -v long" ([ MM-DD HH:MM:SS.mmm PID:TID LEVEL/TAG ]) or "logcat -v
     * long,epoch" header ([ SSSSSSSSSS.mmm PID:TID LEVEL/TAG ]). Example:
     *
     * `[ 08-18 16:39:11.760  2977: 2988 D/PhoneInterfaceManager ]`
     *
     * `[ 1619728495.554  2977: 2988 D/PhoneInterfaceManager ]`
     */
    private static final Pattern HEADER = Pattern.compile(
            String.format(
                    "^\\[ +((%s +%s)|(%s)) +%s: *%s +%s/%s +]$",
                    DATE, TIME, EPOCH, PID, TID, PRIORITY, TAG));

    private final int defaultYear;

    private final ZoneId defaultZoneId;

    public LogCatHeaderParser() {
        this(ZonedDateTime.now().getYear(), ZoneId.systemDefault());
    }

    public LogCatHeaderParser(int year, ZoneId id) {
        this.defaultYear = year;
        this.defaultZoneId = id;
    }

    /**
     * Parse a header line into a [LogCatHeader] object, or `null` if the input line doesn't match
     * the expected format.
     *
     * @param line   raw text that should be the header line from `logcat -v long` or `logcat -v
     *               long,epoch`.
     * @param device device from which these log messages have been received
     * @return a [LogCatHeader] which represents the passed in text or null if text is not a header.
     */
    @Nullable
    public LogCatHeader parseHeader(String line, @Nullable IDevice device) {
        return parseHeader(line, pid -> getPackageName(device, pid));
    }

    /**
     * Parse a header line into a [LogCatHeader] object, or `null` if the input line doesn't match
     * the expected format.
     *
     * @param line raw text that should be the header line from `logcat -v long` or `logcat -v
     *     long,epoch`.
     * @param pidToPackageName resolves a pid to a package name
     * @return a [LogCatHeader] which represents the passed in text or null if text is not a header.
     */
    @Nullable
    public LogCatHeader parseHeader(String line, PidToPackageName pidToPackageName) {
        Matcher m = HEADER.matcher(line);
        if (!m.matches()) {
            return null;
        }
        String epoch = m.group("epoch");
        Instant timestamp;
        if (epoch != null) {
            timestamp = Instant.ofEpochSecond(
                    parseEpochSeconds(m.group("epochSec")),
                    MILLISECONDS.toNanos(Long.parseLong(m.group("epochMilli")))
            );
        }
        else {
            timestamp = Instant.from(
                    ZonedDateTime.of(
                            defaultYear,
                            Integer.parseInt(m.group("month")),
                            Integer.parseInt(m.group("day")),
                            Integer.parseInt(m.group("hour")),
                            Integer.parseInt(m.group("min")),
                            Integer.parseInt(m.group("sec")),
                            (int)MILLISECONDS.toNanos(Long.parseLong(m.group("milli"))),
                            defaultZoneId
                    )
            );
        }
        int pid = parsePid(m.group("pid"));
        return new LogCatHeader(
                parsePriority(m.group("priority")),
                pid,
                parseThreadId(m.group("tid")),
                pidToPackageName.apply(pid),
                m.group("tag"),
                timestamp);
    }

    /**
     * Parses the [priority part of a logcat message header:](https://developer.android.com/studio/command-line/logcat.html)
     * , the "I" in
     *
     * `[          1517949446.554  2848: 2848 I/MainActivity ]`
     *
     * @return the log level corresponding to the priority. If the argument is not one of the
     * expected letters returns LogLevel.WARN.
     */
    private Log.LogLevel parsePriority(String string) {
        Log.LogLevel priority = Log.LogLevel.getByLetterString(string);
        if (priority != null) {
            return priority;
        }
        if (!string.equals("F")) {
            return Log.LogLevel.WARN;
        }
        return Log.LogLevel.ASSERT;
    }

    // Some versions of logcat return hexadecimal thread IDs. Propagate them as decimal.
    private int parseThreadId(String string) {
        try {
            return Integer.decode(string);
        }
        catch (NumberFormatException exception) {
            return -1;
        }
    }

    // Pid has a pattern `\\d+` and might throw if there are too many digits
    private int parsePid(String string) {
        try {
            return Integer.parseInt(string);
        }
        catch (NumberFormatException exception) {
            return -1;
        }
    }

    // Epoch seconds has a pattern of `\\d+` and might throw if there are too many digits
    private long parseEpochSeconds(String string) {
        try {
            return Long.parseLong(string);
        }
        catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String getPackageName(IDevice device, int pid) {
        if (device == null) {
            return UNKNOWN_APP_NAME;
        }

        String clientName = device.getClientName(pid);
        if (clientName == null || clientName.isEmpty()) {
            return UNKNOWN_APP_NAME;
        }
        return clientName;
    }

    @FunctionalInterface
    public interface PidToPackageName {

        String apply(int pid);
    }
}
