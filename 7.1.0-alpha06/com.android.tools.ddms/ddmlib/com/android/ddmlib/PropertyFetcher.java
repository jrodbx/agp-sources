/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fetches and caches 'getprop' values from device. */
public final class PropertyFetcher {
    /** the amount of time to wait between unsuccessful prop fetch attempts */
    private static final String GETPROP_COMMAND = "getprop"; //$NON-NLS-1$
    private static final Pattern GETPROP_PATTERN = Pattern.compile("^\\[([^]]+)\\]\\:\\s*\\[(.*)\\]$"); //$NON-NLS-1$
    /** Two patterns in case the property span several lines. */
    private static final Pattern GETPROP_START_LINE_PATTERN =
            Pattern.compile("^\\[([^]]+)\\]\\:\\s*\\[(.*)$"); //$NON-NLS-1$

    private static final Pattern GETPROP_END_LINE_PATTERN =
            Pattern.compile("(.*)\\]$"); //$NON-NLS-1$

    private static final int GETPROP_TIMEOUT_SEC = 2;
    private static final int EXPECTED_PROP_COUNT = 150;

    private enum CacheState {
        UNPOPULATED, FETCHING, POPULATED
    }

    /**
     * Shell output parser for a getprop command
     *
     * <p>We don't process String[] as they are read from the socket because they can be "cut"
     * anywhere depending on how the socket is read (e.g: We can receive a multiline property with
     * three values as String[2] and then String[1]). Instead, we buffer all the lines and parse
     * them once the full stream has be received.
     */
    @VisibleForTesting
    static class GetPropReceiver extends MultiLineReceiver {

        private final Map<String, String> mCollectedProperties =
                Maps.newHashMapWithExpectedSize(EXPECTED_PROP_COUNT);

        private String[] lines = new String[0];

        @Override
        public void processNewLines(@NonNull String[] newLines) {
            String[] tmp = new String[lines.length + newLines.length];
            System.arraycopy(lines, 0, tmp, 0, lines.length);
            System.arraycopy(newLines, 0, tmp, lines.length, newLines.length);
            lines = tmp;
        }

        @Override
        public void done() {
            // We receive an array of lines.
            // Some properties are single line, e.g.
            //   [foo.bar] = [blah]
            // Some properties span multiple lines, e.g.
            //   [foo.bar] = [line 1\n
            //   line 2\n
            //   line 3]
            String multiLineLabel = null;
            String multiLineValue = null;
            for (String line : lines) {
                // If the line is empty in a multi-line property, we keep it, as its part of the
                // property field.
                if (multiLineLabel == null && (line.isEmpty() || line.startsWith("#"))) {
                    continue;
                }

                Matcher m = GETPROP_PATTERN.matcher(line);
                if (m.matches()) {
                    String label = m.group(1);
                    String value = m.group(2);

                    if (!label.isEmpty()) {
                        mCollectedProperties.put(label, value);
                    }
                    multiLineLabel = null;
                    multiLineValue = null;
                    continue;
                }

                Matcher multiLinePattern = GETPROP_START_LINE_PATTERN.matcher(line);
                if (multiLinePattern.matches()) {
                    multiLineLabel = multiLinePattern.group(1);
                    if (multiLineLabel.isEmpty()) {
                        continue;
                    }
                    multiLineValue = multiLinePattern.group(2);
                    continue;
                }

                Matcher endPattern = GETPROP_END_LINE_PATTERN.matcher(line);
                if (endPattern.matches()) {
                    multiLineValue += "\n" + endPattern.group(1);
                    mCollectedProperties.put(multiLineLabel, multiLineValue);
                    multiLineLabel = null;
                    multiLineValue = null;
                    continue;
                }
                // If we are in-progress of a multi-line property.
                if (multiLineValue != null) {
                    multiLineValue += "\n" + line;
                }
            }
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        Map<String, String> getCollectedProperties() {
            return mCollectedProperties;
        }
    }

    private final Map<String, String> mProperties = Maps.newHashMapWithExpectedSize(
            EXPECTED_PROP_COUNT);
    private final IDevice mDevice;
    private CacheState mCacheState = CacheState.UNPOPULATED;
    private final Map<String, SettableFuture<String>> mPendingRequests =
            Maps.newHashMapWithExpectedSize(4);

    public PropertyFetcher(IDevice device) {
        mDevice = device;
    }

    /**
     * Returns the full list of cached properties.
     */
    public synchronized Map<String, String> getProperties() {
        return mProperties;
    }

    /**
     * Ideally we should not cache mutable system properties. But removing cache will result in more
     * blocking calls. Thus we keep the option to enable it here.
     */
    private static boolean sEnableCachingMutableProps = true;

    public static void enableCachingMutableProps(boolean enabled) {
        sEnableCachingMutableProps = enabled;
    }

    /**
     * Make a possibly asynchronous request for a system property value.
     *
     * @param name the property name to retrieve
     * @return a {@link Future} that can be used to retrieve the prop value
     */
    @NonNull
    public synchronized ListenableFuture<String> getProperty(@NonNull String name) {
        SettableFuture<String> result;
        if (mCacheState.equals(CacheState.FETCHING)) {
            result = addPendingRequest(name);
        } else if (mDevice.isOnline() && mCacheState.equals(CacheState.UNPOPULATED)
                || !isImmutableProperty(name)) {
            // cache is empty, or this is a volatile prop that requires a query
            result = addPendingRequest(name);
            mCacheState = CacheState.FETCHING;
            initiatePropertiesQuery();
        } else {
            result = SettableFuture.create();
            // cache is populated and this is a ro prop
            result.set(mProperties.get(name));
        }
        return result;
    }

    private SettableFuture<String> addPendingRequest(String name) {
        SettableFuture<String> future = mPendingRequests.get(name);
        if (future == null) {
            future = SettableFuture.create();
            mPendingRequests.put(name, future);
        }
        return future;
    }

    private void initiatePropertiesQuery() {
        String threadName = String.format("query-prop-%s", mDevice.getSerialNumber());
        Thread propThread = new Thread(threadName) {
            @Override
            public void run() {
                try {
                    GetPropReceiver propReceiver = new GetPropReceiver();
                    mDevice.executeShellCommand(GETPROP_COMMAND, propReceiver, GETPROP_TIMEOUT_SEC,
                            TimeUnit.SECONDS);
                    populateCache(propReceiver.getCollectedProperties());
                } catch (Throwable e) {
                    handleException(e);
                }
            }
        };
        propThread.setDaemon(true);
        propThread.start();
    }

    private synchronized void populateCache(@NonNull Map<String, String> props) {
        mCacheState = props.isEmpty() ? CacheState.UNPOPULATED : CacheState.POPULATED;
        if (!props.isEmpty()) {
            if (sEnableCachingMutableProps) {
                mProperties.putAll(props);
            } else {
                for (Map.Entry<String, String> entry : props.entrySet()) {
                    if (isImmutableProperty(entry.getKey())) {
                        mProperties.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
        for (Map.Entry<String, SettableFuture<String>> entry : mPendingRequests.entrySet()) {
            if (sEnableCachingMutableProps || isImmutableProperty(entry.getKey())) {
                entry.getValue().set(mProperties.get(entry.getKey()));
            } else {
                entry.getValue().set(props.get(entry.getKey()));
            }
        }
        mPendingRequests.clear();
    }

    private synchronized void handleException(Throwable e) {
        mCacheState = CacheState.UNPOPULATED;
        String msg =
                String.format(
                        "%s getting properties for device %s",
                        e.getClass().getSimpleName(), mDevice.getSerialNumber());
        Log.w("PropertyFetcher", new Throwable(msg, e));
        for (Map.Entry<String, SettableFuture<String>> entry : mPendingRequests.entrySet()) {
            entry.getValue().setException(e);
        }
        mPendingRequests.clear();
    }

    /**
     * Return true if cache is populated.
     *
     * @deprecated implementation detail
     */
    @Deprecated
    public synchronized boolean arePropertiesSet() {
        return CacheState.POPULATED.equals(mCacheState);
    }

    private static boolean isImmutableProperty(@NonNull String propName) {
        return propName.startsWith("ro.") || propName.equals(IDevice.PROP_DEVICE_EMULATOR_DENSITY);
    }
}
