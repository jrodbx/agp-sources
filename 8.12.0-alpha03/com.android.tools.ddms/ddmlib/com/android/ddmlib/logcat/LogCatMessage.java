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

import java.util.Objects;

public class LogCatMessage {

    @NonNull
    private final LogCatHeader header;

    @NonNull
    private final String message;

    public LogCatMessage(@NonNull LogCatHeader header, @NonNull String message) {
        this.header = header;
        this.message = message;
    }

    @NonNull
    public LogCatHeader getHeader() {
        return header;
    }

    @NonNull
    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return String.format("%s: %s", header, message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(header, message);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof LogCatMessage)) {
            return false;
        }
        LogCatMessage other = (LogCatMessage)obj;
        return Objects.equals(header, other.header) &&
               Objects.equals(message, other.message);
    }
}
