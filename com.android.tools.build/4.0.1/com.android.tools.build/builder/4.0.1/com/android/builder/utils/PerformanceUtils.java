/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.builder.utils;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.SdkUtils;
import com.google.common.annotations.VisibleForTesting;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;

/**
 * Helper methods when analyzing the environment in which the plugin in run. This might refer to
 * number of available core, heap size etc.
 */
public class PerformanceUtils {

    /** Approximate amount of heap space necessary for non-dexing steps of the build process. */
    @VisibleForTesting public static final long NON_DEX_HEAP_SIZE = 512 * 1024 * 1024; // 0.5 GiB

    /**
     * Returns the heap size that was specified by the -Xmx value from the user, or an approximated
     * value if the -Xmx value was not set or was set improperly. Value is in bytes.
     */
    public static long getUserDefinedHeapSize() {
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.startsWith("-Xmx")) {
                Long heapSize = parseSizeToBytes(arg.substring("-Xmx".length()));
                if (heapSize != null) {
                    return heapSize;
                }
                break;
            }
        }

        // If the -Xmx value was not set or was set improperly, get an approximation of the
        // heap size
        long heapSize = 0;
        for (MemoryPoolMXBean mpBean : ManagementFactory.getMemoryPoolMXBeans()) {
            if (mpBean.getType() == MemoryType.HEAP) {
                heapSize += mpBean.getUsage().getMax();
            }
        }
        return heapSize;
    }

    /**
     * Returns a number, {@link Long}, that is non-null when the size can be parsed successfully.
     * Otherwise, {@code null} is returned.
     */
    @VisibleForTesting
    @Nullable
    public static Long parseSizeToBytes(@NonNull String sizeParameter) {
        long multiplier = 1;
        if (SdkUtils.endsWithIgnoreCase(sizeParameter, "k")) {
            multiplier = 1024;
        } else if (SdkUtils.endsWithIgnoreCase(sizeParameter, "m")) {
            multiplier = 1024 * 1024;
        } else if (SdkUtils.endsWithIgnoreCase(sizeParameter, "g")) {
            multiplier = 1024 * 1024 * 1024;
        }

        if (multiplier != 1) {
            sizeParameter = sizeParameter.substring(0, sizeParameter.length() - 1);
        }

        try {
            return multiplier * Long.parseLong(sizeParameter);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static int getNumThreadsForDexArchives() {
        int numCores = Runtime.getRuntime().availableProcessors();
        long heapSize = getUserDefinedHeapSize();

        long available = heapSize - NON_DEX_HEAP_SIZE;
        // assumption: on average we need 200MB for a single thread, on 1.5GB we would run 5 threads
        long threadsBasedOnMemory = Math.max(1, available / (200 * 1024 * 1024));
        return (int) Math.min(numCores, threadsBasedOnMemory);
    }
}
