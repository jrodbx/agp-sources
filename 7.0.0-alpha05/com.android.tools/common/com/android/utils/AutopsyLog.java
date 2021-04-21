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
package com.android.utils;

import com.android.annotations.NonNull;
import com.android.annotations.concurrency.GuardedBy;
import com.google.common.collect.ImmutableList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Fast in-memory log utilizing a cyclic buffer of a limited size. Intended to be used when
 * alternative logging methods are too slow, for example, in the following scenario:
 *
 * <ul>
 *   <li>There is an expected behavior in the code, possibly caused by a race condition.
 *   <li>You add logging to the code to investigate the problem.
 *   <li>The problem is no longer reproducible with logging enabled.
 * </ul>
 *
 * <p>You may try to replace the regular logging with:
 *
 * <pre>
 *     AutopsyLog.initialize(100);
 *     ...
 *     AutopsyLog.log(something);
 *     ...
 *     AutopsyLog.log(somethingElse);
 *     ...
 *     if (unexpectedBehaviorDetected) {
 *         AutopsyLog.print();
 *     }
 * </pre>
 */
public class AutopsyLog {
    private static final AutopsyLog INSTANCE = new AutopsyLog();

    @GuardedBy("this")
    private Deque<Object> records;
    @GuardedBy("this")
    private int sizeLimit;

    /**
     * Initializes the log. At most last {@code sizeLimit} records will be preserved in the log.
     * The prior contents of the log up to {@code sizeLimit} records are preserved.
     *
     * @param sizeLimit the maximum number of records to store
     */
    public static void initialize(int sizeLimit) {
        INSTANCE.setSizeLimit(sizeLimit);
    }

    /**
     * Writes a record to the log if it was initialized.
     *
     * @param record an arbitrary object to log
     */
    public static void log(@NonNull Object record) {
        INSTANCE.doLog(record);
    }

    /** Returns the contents of the log and clears the log. */
    @NonNull
    public static List<Object> getAndClear() {
        return INSTANCE.doGetAndClear();
    }

    /** Prints the contents of the log to stdout and clears the log. */
    public static void print() {
        for (Object record : getAndClear()) {
            System.out.println(record);
        }
    }

    private AutopsyLog() {}

    private synchronized void setSizeLimit(int sizeLimit) {
        if (sizeLimit > 0) {
            if (records == null) {
                records = new ArrayDeque<>(sizeLimit);
            } else {
                while (records.size() > sizeLimit) {
                    records.removeFirst();
                }
                Deque<Object> newRecords = new ArrayDeque<>(sizeLimit);
                newRecords.addAll(records);
                records = newRecords;
            }
        } else {
            records = null;
        }
        this.sizeLimit = sizeLimit;
    }

    private synchronized void doLog(@NonNull Object record) {
        if (records != null) {
            if (records.size() >= sizeLimit) {
                records.removeFirst();
            }
            records.add(record);
        }
    }

    @NonNull
    private synchronized List<Object> doGetAndClear() {
        if (records == null) {
            return ImmutableList.of();
        }
        List<Object> contents = ImmutableList.copyOf(records);
        records.clear();
        return contents;
    }
}
