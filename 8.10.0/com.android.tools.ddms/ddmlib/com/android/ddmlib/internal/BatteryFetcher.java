/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.ddmlib.internal;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.MultiLineReceiver;
import com.google.common.util.concurrent.SettableFuture;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fetches battery level from device. */
class BatteryFetcher {
    private static final String LOG_TAG = "BatteryFetcher";
    /**
     * On Pixel 3 and Pixel 3 XL devices, the battery level reported in the UI is read from this
     * "maximum fuel gauge" ("maxfg") file. This is a specific design of these devices.
     */
    private static final Set<String> Pixel3_Pixel3XL =
            new HashSet<>(Arrays.asList("Pixel 3", "Pixel 3 XL"));

    private static final String MAXFG_PATH = "/sys/class/power_supply/maxfg/capacity";
    private static final String NORMAL_PATH = "/sys/class/power_supply/*/capacity";
    private static final String PROP_PRODUCT_MODEL = "ro.product.model";

    /** The amount of time to wait between unsuccessful battery fetch attempts. */
    private static final long BATTERY_TIMEOUT_MS = 2 * 1000; // 2 seconds

    /**
     * Output receiver for "cat /sys/class/power_supply/.../capacity" command line.
     */
    static final class SysFsBatteryLevelReceiver extends MultiLineReceiver {
        private static final Pattern BATTERY_LEVEL = Pattern.compile("^(\\d+)[.\\s]*");
        private Integer mBatteryLevel;

        /**
         * Get the parsed battery level.
         * @return battery level or <code>null</code> if it cannot be determined
         */
        @Nullable
        public Integer getBatteryLevel() {
            return mBatteryLevel;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void processNewLines(@NonNull String[] lines) {
            for (String line : lines) {
                Matcher batteryMatch = BATTERY_LEVEL.matcher(line);
                if (batteryMatch.matches()) {
                    if (mBatteryLevel == null) {
                        mBatteryLevel = Integer.parseInt(batteryMatch.group(1));
                    } else {
                        // multiple matches, check if they are different
                        Integer tmpLevel = Integer.parseInt(batteryMatch.group(1));
                        if (!mBatteryLevel.equals(tmpLevel)) {
                            Log.w(LOG_TAG, String.format(
                                    "Multiple lines matched with different value; " +
                                    "Original: %s, Current: %s (keeping original)",
                                    mBatteryLevel.toString(), tmpLevel.toString()));
                        }
                    }
                }
            }
        }
    }

    /**
     * Output receiver for "dumpsys battery" command line.
     */
    private static final class BatteryReceiver extends MultiLineReceiver {
        private static final Pattern BATTERY_LEVEL = Pattern.compile("\\s*level: (\\d+)");
        private static final Pattern SCALE = Pattern.compile("\\s*scale: (\\d+)");

        private Integer mBatteryLevel;
        private Integer mBatteryScale;

        /**
         * Returns the parsed percent battery level, or null if not available.
         */
        public @Nullable Integer getBatteryLevel() {
            if (mBatteryLevel != null && mBatteryScale != null) {
                return (mBatteryLevel * 100) / mBatteryScale;
            }
            return null;
        }

        @Override
        public void processNewLines(@NonNull String[] lines) {
            for (String line : lines) {
                Matcher batteryMatch = BATTERY_LEVEL.matcher(line);
                if (batteryMatch.matches()) {
                    try {
                        mBatteryLevel = Integer.parseInt(batteryMatch.group(1));
                    } catch (NumberFormatException e) {
                        Log.w(LOG_TAG, String.format("Failed to parse %s as an integer",
                                batteryMatch.group(1)));
                    }
                }
                Matcher scaleMatch = SCALE.matcher(line);
                if (scaleMatch.matches()) {
                    try {
                        mBatteryScale = Integer.parseInt(scaleMatch.group(1));
                    } catch (NumberFormatException e) {
                        Log.w(LOG_TAG, String.format("Failed to parse %s as an integer",
                                batteryMatch.group(1)));
                    }
                }
            }
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }

    private Integer mBatteryLevel;
    private final IDevice mDevice;
    private long mLastSuccessTime;
    private SettableFuture<Integer> mPendingRequest;

    public BatteryFetcher(IDevice device) {
        mDevice = device;
    }

    /**
     * Make a possibly asynchronous request for the device's battery level
     *
     * @param freshness the desired recentness of battery level
     * @param timeUnit the {@link TimeUnit} of freshness
     * @return a {@link Future} that can be used to retrieve the battery level
     */
    public synchronized Future<Integer> getBattery(long freshness, TimeUnit timeUnit) {
        SettableFuture<Integer> result;
        if (mBatteryLevel == null || isFetchRequired(freshness, timeUnit)) {
            if (mPendingRequest == null) {
                // no request underway - start a new one
                mPendingRequest = SettableFuture.create();
                initiateBatteryQuery();
            } else {
                // fall through - return the already created future from the request already
                // underway
            }
            result = mPendingRequest;
        } else {
            // cache is populated within desired freshness
            result = SettableFuture.create();
            result.set(mBatteryLevel);
        }
        return result;
    }

    private boolean isFetchRequired(long freshness, TimeUnit timeUnit) {
        long freshnessMs = timeUnit.toMillis(freshness);
        return (System.currentTimeMillis() - mLastSuccessTime) > freshnessMs;
    }

    private void initiateBatteryQuery() {
        String threadName = String.format("query-battery-%s", mDevice.getSerialNumber());
        Thread fetchThread =
                new Thread(threadName) {
                    @Override
                    public void run() {
                        Throwable exception;
                        try {
                            // first try to get it from sysfs
                            SysFsBatteryLevelReceiver sysBattReceiver =
                                    new SysFsBatteryLevelReceiver();
                            String batteryLevelFile = NORMAL_PATH;
                            String mProduct = mDevice.getProperty(PROP_PRODUCT_MODEL);
                            if (Pixel3_Pixel3XL.contains(mProduct)) {
                                batteryLevelFile = MAXFG_PATH;
                            }
                            mDevice.executeShellCommand(
                                    "cat " + batteryLevelFile,
                                    sysBattReceiver,
                                    BATTERY_TIMEOUT_MS,
                                    TimeUnit.MILLISECONDS);
                            if (!setBatteryLevel(sysBattReceiver.getBatteryLevel())) {
                                // failed! try dumpsys
                                BatteryReceiver receiver = new BatteryReceiver();
                                mDevice.executeShellCommand(
                                        "dumpsys battery",
                                        receiver,
                                        BATTERY_TIMEOUT_MS,
                                        TimeUnit.MILLISECONDS);
                                if (setBatteryLevel(receiver.getBatteryLevel())) {
                                    return;
                                }
                            } else {
                                return;
                            }
                            exception =
                                    new IOException(
                                            "Unrecognized response to battery level queries");
                        } catch (Throwable e) {
                            exception = e;
                        }
                        handleBatteryLevelFailure(exception);
                    }
                };
        fetchThread.setDaemon(true);
        fetchThread.start();
    }

    private synchronized boolean setBatteryLevel(Integer batteryLevel) {
        if (batteryLevel == null) {
            return false;
        }
        mLastSuccessTime = System.currentTimeMillis();
        mBatteryLevel = batteryLevel;
        if (mPendingRequest != null) {
            mPendingRequest.set(mBatteryLevel);
        }
        mPendingRequest = null;
        return true;
    }

    private synchronized void handleBatteryLevelFailure(Throwable e) {
        Log.w(LOG_TAG, String.format(
                "%s getting battery level for device %s: %s",
                e.getClass().getSimpleName(), mDevice.getSerialNumber(), e.getMessage()));
        if (mPendingRequest != null) {
            if (!mPendingRequest.setException(e)) {
                // should never happen
                Log.e(LOG_TAG, "Future.setException failed");
                mPendingRequest.set(null);
            }
        }
        mPendingRequest = null;
    }
}
