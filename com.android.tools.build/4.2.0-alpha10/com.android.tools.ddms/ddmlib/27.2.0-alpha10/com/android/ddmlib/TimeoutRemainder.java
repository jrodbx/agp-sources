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
package com.android.ddmlib;

import java.util.concurrent.TimeUnit;

/**
 * Utility class to keep track of how much time is left given an initial timeout. This is useful
 * when a method receives a timeout parameter and needs to perform multiple operations within that
 * given timeout.
 *
 * <p>Note: The implementation keeps track of time using {@link System#nanoTime()} units, meaning
 * this class is not suitable for timeouts longer than ~290 years.
 */
public class TimeoutRemainder {
    private final SystemNanoTimeProvider nanoTimeProvider;
    private final long timeout;
    private final TimeUnit unit;
    /**
     * Value of system nano time when the timer is created, meaning overflowing occurs every ~290+
     * years.
     */
    private final long startNanos;

    public TimeoutRemainder(long timeout, TimeUnit unit) {
        this(DefaultSystemNanoTime.getInstance(), timeout, unit);
    }

    public TimeoutRemainder(SystemNanoTimeProvider nanoTimeProvider, long timeout, TimeUnit unit) {
        this.nanoTimeProvider = nanoTimeProvider;
        this.timeout = timeout;
        this.unit = unit;
        this.startNanos = elapsedNanos(0);
    }

    public long getRemainingNanos() {
        // This needs to handle Long.MAX_VALUE for timeout, i.e we cannot
        // overflow. This is ok as elapsedNanos() will never be negative.
        return this.unit.toNanos(timeout) - elapsedNanos(startNanos);
    }

    public long getRemainingUnits() {
        return getRemainingUnits(this.unit);
    }

    public long getRemainingUnits(TimeUnit unit) {
        // Using TimeUnit convert() ensures overflows are taken care of
        return unit.convert(getRemainingNanos(), TimeUnit.NANOSECONDS);
    }

    private long elapsedNanos(long startNanos) {
        return this.nanoTimeProvider.nanoTime() - startNanos;
    }

    public interface SystemNanoTimeProvider {
        long nanoTime();
    }

    public static class DefaultSystemNanoTime implements SystemNanoTimeProvider {
        public static DefaultSystemNanoTime sInstance = new DefaultSystemNanoTime();

        public static DefaultSystemNanoTime getInstance() {
            return sInstance;
        }

        @Override
        public long nanoTime() {
            return System.nanoTime();
        }
    }
}
