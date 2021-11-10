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
package com.android.ddmlib.logcat

import com.android.ddmlib.IDevice
import com.android.ddmlib.Log.LogLevel
import com.android.ddmlib.Log.LogLevel.ASSERT
import com.android.ddmlib.Log.LogLevel.WARN
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.regex.Pattern

private val EPOCH = Pattern.compile("(?<epoch>(?<epochSec>\\d+)\\.(?<epochMilli>\\d\\d\\d))")

private val DATE = Pattern.compile("(?<month>\\d\\d)-(?<day>\\d\\d)")

private val TIME =
    Pattern.compile("(?<hour>\\d\\d):(?<min>\\d\\d):(?<sec>\\d\\d)\\.(?<milli>\\d\\d\\d)")

private val PID = Pattern.compile("(?<pid>\\d+)")

private val TID = Pattern.compile("(?<tid>\\w+)")

private val PRIORITY = Pattern.compile("(?<priority>[VDIWEAF])")

private val TAG = Pattern.compile("(?<tag>.*?)")

private const val UNKNOWN_APP_NAME = "?"

/**
 * Pattern for "logcat -v long" ([ MM-DD HH:MM:SS.mmm PID:TID LEVEL/TAG ]) or "logcat -v long,epoch"
 * header ([ SSSSSSSSSS.mmm PID:TID LEVEL/TAG ]). Example:
 *
 * `[ 08-18 16:39:11.760  2977: 2988 D/PhoneInterfaceManager ]`
 *
 * `[ 1619728495.554  2977: 2988 D/PhoneInterfaceManager ]`
 */
private val HEADER = Pattern.compile(
    "^\\[ +(($DATE +$TIME)|($EPOCH)) +$PID: *$TID +$PRIORITY/$TAG +]$"
)

class LogCatHeaderParser(
    private val defaultYear: Int = ZonedDateTime.now().year,
    private val defaultZoneId: ZoneId = ZoneId.systemDefault(),
) {

    /**
     * Parse a header line into a [LogCatHeader] object, or `null` if the input line
     * doesn't match the expected format.
     *
     * @param line   raw text that should be the header line from `logcat -v long` or
     * `logcat -v long,epoch`.
     * @param device device from which these log messages have been received
     * @return a [LogCatHeader] which represents the passed in text or null if text is not a
     * header.
     */
    fun parseHeader(line: String, device: IDevice?): LogCatHeader? {
        val m = HEADER.matcher(line)
        if (!m.matches()) {
            return null
        }
        val epoch: String? = m.group("epoch")
        val timestamp: Instant
        if (epoch != null) {
            timestamp = Instant.ofEpochSecond(
                parseEpochSeconds(m.group("epochSec")),
                MILLISECONDS.toNanos(m.group("epochMilli").toLong())
            )
        } else {
            timestamp = Instant.from(
                ZonedDateTime.of(
                    defaultYear,
                    m.group("month").toInt(),
                    m.group("day").toInt(),
                    m.group("hour").toInt(),
                    m.group("min").toInt(),
                    m.group("sec").toInt(),
                    MILLISECONDS.toNanos(m.group("milli").toLong()).toInt(),
                    defaultZoneId
                )
            )
        }
        val pid = parsePid(m.group("pid"))
        return LogCatHeader(
            parsePriority(m.group("priority")),
            pid,
            parseThreadId(m.group("tid")),
            getPackageName(device, pid),
            m.group("tag"),
            timestamp
        )
    }

    /**
     * Parses the [priority part of a logcat message header:](https://developer.android.com/studio/command-line/logcat.html)
     * , the "I" in
     *
     * `[          1517949446.554  2848: 2848 I/MainActivity ]`
     *
     * @return the log level corresponding to the priority. If the argument is not one of the
     *     expected letters returns LogLevel.WARN.
     */
    private fun parsePriority(string: String): LogLevel {
        val priority = LogLevel.getByLetterString(string)
        if (priority != null) {
            return priority
        }
        if (string != "F") {
            return WARN
        }
        return ASSERT
    }

    // Some versions of logcat return hexadecimal thread IDs. Propagate them as decimal.
    private fun parseThreadId(string: String): Int {
        return try {
            Integer.decode(string)
        } catch (exception: NumberFormatException) {
            -1
        }
    }

    // Pid has a pattern `\\d+` and might throw if there are too many digits
    private fun parsePid(string: String): Int {
        return try {
            string.toInt()
        } catch (exception: NumberFormatException) {
            -1
        }
    }

    // Epoch seconds has a pattern of `\\d+` and might throw if there are too many digits
    private fun parseEpochSeconds(string: String): Long {
        return try {
            string.toLong()
        } catch (exception: NumberFormatException) {
            0
        }
    }

    private fun getPackageName(device: IDevice?, pid: Int): String {
        val clientName = device?.getClientName(pid)
        return if (clientName.isNullOrEmpty()) {
            UNKNOWN_APP_NAME
        } else {
            clientName
        }
    }
}
