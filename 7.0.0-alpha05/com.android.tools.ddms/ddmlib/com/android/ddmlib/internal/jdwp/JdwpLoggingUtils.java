/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.ddmlib.internal.jdwp;

import com.android.ddmlib.Log;
import com.android.ddmlib.internal.jdwp.chunkhandler.JdwpPacket;

public class JdwpLoggingUtils {
    public static void log(String owner, String action, JdwpPacket packet) {
        if (Log.isAtLeast(Log.LogLevel.VERBOSE)) {
            Log.v("JdwpProxy-Packet", String.format("%s %s (%d)", owner, action, packet.getId()));
            packet.log(action);
        }
    }

    public static void log(String owner, String action, byte[] buffer, int length) {
        if (Log.isAtLeast(Log.LogLevel.VERBOSE)) {
            Log.v(
                    "JdwpProxy-Buffer",
                    String.format(
                            "%s %s (%d) %s",
                            owner, action, length, formatBytesToString(buffer, length)));
        }
    }

    private static String formatBytesToString(byte[] buffer, int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            if (Character.isLetterOrDigit((char) buffer[i])) {
                builder.append((char) buffer[i]);
            } else {
                builder.append(Integer.toHexString(buffer[i]));
            }
        }
        return builder.toString();
    }
}
