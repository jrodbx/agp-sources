/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.utils;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/** Static methods useful for tracing. */
public class TraceUtils {
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT);

    /**
     * Returns the current stack of the caller.
     *
     * @return the stack as a string
     */
    @NonNull
    public static String getCurrentStack() {
        return getCurrentStack(1);
    }

    /**
     * Returns the current stack of the caller. Optionally, removes few frames at the top of the
     * stack.
     *
     * @param numberOfTopFramesToRemove the number of top stack frames to remove
     * @return the stack as a string
     */
    @NonNull
    public static String getCurrentStack(int numberOfTopFramesToRemove) {
        String fullStack = getStackTrace(new Throwable() {
            @Override
            public String toString() {
                return "";
            }
        });
        // Remove our own frame and numberOfTopFramesToRemove frames requested by the caller.
        int start = 0;
        if (numberOfTopFramesToRemove < 0) {
            numberOfTopFramesToRemove = 0;
        }
        // The first character of the stack is always '\n'.
        for (int i = 0; i < numberOfTopFramesToRemove + 2; i++) {
            int pos = fullStack.indexOf('\n', start);
            if (pos < 0) {
                break;
            }
            start = pos + 1;
        }
        return fullStack.substring(start);
    }

    /**
     * Returns a stack trace of the given throwable as a string.
     *
     * @param t the throwable to get the stack trace from
     * @return the string containing the stack trace
     */
    @NonNull
    public static String getStackTrace(@NonNull Throwable t) {
        StringWriter stringWriter = new StringWriter();
        try (PrintWriter writer = new PrintWriter(stringWriter)) {
            t.printStackTrace(writer);
            return stringWriter.toString();
        }
    }

    /** Returns stack traces of all threads as a single string. */
    @NonNull
    public static String getStacksOfAllThreads() {
        StringBuilder buf = new StringBuilder();
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            if (buf.length() != 0) {
                buf.append('\n');
            }
            Thread thread = entry.getKey();
            buf.append(thread.toString());
            buf.append('\n');
            StackTraceElement[] stackTrace = entry.getValue();
            for (StackTraceElement frame : stackTrace) {
                buf.append("  at ");
                buf.append(frame.toString());
                buf.append('\n');
            }
        }
        return buf.toString();
    }

    /**
     * Returns a string consisting of the object's class name without the package part, '@'
     * separator, and the hexadecimal identity hash code, e.g. AndroidResGroupNode@5A1D1719.
     */
    @NonNull
    public static String getSimpleId(@Nullable Object obj) {
        return obj == null
                ? "null"
                : String.format(
                        "%s@%08X", obj.getClass().getSimpleName(), System.identityHashCode(obj));
    }

    /**
     * Returns a string containing comma-separated simple IDs of the elements of the given
     * iterable. Each simple ID is the object's class name without the package part, '@'
     * separator, and the hexadecimal identity hash code, e.g. AndroidResGroupNode@5A1D1719.
     */
    @NonNull
    public static String getSimpleIds(@NonNull Iterable<?> iterable) {
        StringBuilder result = new StringBuilder();
        for (Object element : iterable) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(getSimpleId(element));
        }
        return result.toString();
    }

    /** Returns the current time as a yyyy-MM-dd HH:mm:ss.SSS string. */
    @NonNull
    public static String currentTime() {
        return DATE_FORMAT.format(new Date());
    }

    private TraceUtils() {}
}
