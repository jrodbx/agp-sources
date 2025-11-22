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
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Data class for timestamp information which gets reported by logcat.
 */
public final class LogCatTimestamp {

    private LogCatTimestamp() {}

    private static final Pattern sTimePattern = Pattern.compile(
            "^(\\d\\d)-(\\d\\d)\\s(\\d\\d):(\\d\\d):(\\d\\d)\\.(\\d+)$");

    public static Instant parse(@NonNull String timeString) {
        return parse(timeString, ZonedDateTime.now().getYear(), ZoneId.systemDefault());
    }

    /**
     * Parse a logcat line timestamp from an old version of logcat where the timestamp doesn't
     * support epoch or year/time zone.
     *
     * <p>Since we return an {@link Instant}, we fill in the missing year and time zone information
     * with values provided by the caller.
     *
     * @param timeString A time string in the format "MM-DD HH:mm:ss.sss"
     * @param year a year to use with parsed data.
     * @param zoneId a time zone to use with parsed data.
     */
    static Instant parse(@NonNull String timeString, int year, ZoneId zoneId) {
        Matcher matcher = sTimePattern.matcher(timeString);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid timestamp. Expected MM-DD HH:MM:SS:mmm");
        }

        int month = Integer.parseInt(matcher.group(1));
        int day = Integer.parseInt(matcher.group(2));
        int hour = Integer.parseInt(matcher.group(3));
        int minute = Integer.parseInt(matcher.group(4));
        int second = Integer.parseInt(matcher.group(5));
        int millisecond = Integer.parseInt(matcher.group(6));

        // ms is 3 digits max. e.g. convert "123456" into "123" (and rounding error is fine)
        while (millisecond >= 1000) {
            millisecond /= 10;
        }

        return Instant.from(
                ZonedDateTime.of(
                        year,
                        month,
                        day,
                        hour,
                        minute,
                        second,
                        (int) TimeUnit.MILLISECONDS.toNanos(millisecond),
                        zoneId));
    }
}
