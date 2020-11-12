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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Data class for timestamp information which gets reported by logcat.
 */
public final class LogCatTimestamp {

    public static final LogCatTimestamp ZERO = new LogCatTimestamp(1, 1, 0, 0, 0, 0);

    private final int mMonth;
    private final int mDay;
    private final int mHour;
    private final int mMinute;
    private final int mSecond;
    private final int mMilli;

    private static final Pattern sTimePattern = Pattern.compile(
            "^(\\d\\d)-(\\d\\d)\\s(\\d\\d):(\\d\\d):(\\d\\d)\\.(\\d+)$");

    public static LogCatTimestamp fromString(@NonNull String timeString) {
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

        return new LogCatTimestamp(month, day, hour, minute, second, millisecond);
    }

    /**
     * Construct an immutable timestamp object.
     */
    public LogCatTimestamp(int month, int day, int hour, int minute, int second, int milli) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException(
                    String.format("Month should be between 1-12: %d", month));
        }

        if (day < 1 || day > 31) {
            throw new IllegalArgumentException(
                    String.format("Day should be between 1-31: %d", day));
        }

        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException(
                    String.format("Hour should be between 0-23: %d", hour));
        }

        if (minute < 0 || minute > 59) {
            throw new IllegalArgumentException(
                    String.format("Minute should be between 0-59: %d", minute));
        }

        if (second < 0 || second > 59) {
            throw new IllegalArgumentException(
                    String.format("Second should be between 0-59 %d", second));
        }

        if (milli < 0 || milli > 999) {
            throw new IllegalArgumentException(
                    String.format("Millisecond should be between 0-999: %d", milli));
        }

        mMonth = month;
        mDay = day;
        mHour = hour;
        mMinute = minute;
        mSecond = second;
        mMilli = milli;
    }

    public boolean isBefore(@NonNull LogCatTimestamp other) {
        if (mMonth == 12 && other.mMonth == 1) {
            // Timestamps don't indicate year, so in practice, if you get two timestamps in short
            // succession:
            // 12-31 23:59:59.999
            // 01-01 00:00:01.111
            // we assume that the latter timestamp is newer than the previous
            // Unfortunately, if someone leaves their Android running for a whole year, this logic
            // would only take us so far, but that's unlikely to be an issue, at least compared to
            // someone leaving their Android device running overnight on the new year.
            return true;
        }
        else if (mMonth == 1 && other.mMonth == 12) {
            return false;
        }

        if (mMonth < other.mMonth) {
            return true;
        }
        else if (mMonth > other.mMonth) {
            return false;
        }

        if (mDay < other.mDay) {
            return true;
        }
        else if (mDay > other.mDay) {
            return false;
        }

        if (mHour < other.mHour) {
            return true;
        }
        else if (mHour > other.mHour) {
            return false;
        }

        if (mMinute < other.mMinute) {
            return true;
        }
        else if (mMinute > other.mMinute) {
            return false;
        }

        if (mSecond < other.mSecond) {
            return true;
        }
        else if (mSecond > other.mSecond) {
            return false;
        }

        if (mMilli < other.mMilli) {
            return true;
        }
        else if (mMilli > other.mMilli) {
            return false;
        }

        return false;

    }

    @Override
    public boolean equals(@Nullable Object object) {
        if (!(object instanceof LogCatTimestamp)) {
            return false;
        }

        LogCatTimestamp timestamp = (LogCatTimestamp) object;

        return mMonth == timestamp.mMonth
                && mDay == timestamp.mDay
                && mHour == timestamp.mHour
                && mMinute == timestamp.mMinute
                && mSecond == timestamp.mSecond
                && mMilli == timestamp.mMilli;
    }

    @Override
    public int hashCode() {
        int hashCode = 17;

        hashCode = 31 * hashCode + mMonth;
        hashCode = 31 * hashCode + mDay;
        hashCode = 31 * hashCode + mHour;
        hashCode = 31 * hashCode + mMinute;
        hashCode = 31 * hashCode + mSecond;
        hashCode = 31 * hashCode + mMilli;

        return hashCode;
    }

    @Override
    public String toString() {
        return String.format("%02d-%02d %02d:%02d:%02d.%03d", mMonth, mDay, mHour, mMinute, mSecond,
                mMilli);
    }
}
