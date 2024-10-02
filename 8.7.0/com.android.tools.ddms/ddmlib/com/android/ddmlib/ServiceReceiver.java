/*
 * Copyright (C) 2023 The Android Open Source Project
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServiceReceiver extends MultiLineReceiver {

    private static final String LOG_TAG = "ServiceReceiver";

    private static final Pattern serviceLinePattern =
            Pattern.compile("[\\d+]\\s+(\\S+):\\s+\\[(.*?)]");

    private final LinkedHashMap<String, ServiceInfo> runningServices = new LinkedHashMap<>();

    @NonNull
    public Map<String, ServiceInfo> getRunningServices() {
        return runningServices;
    }

    @Override
    public void processNewLines(@NonNull String[] lines) {
        // Skip line 'Found X services:' and start at offset 1
        for (int n = 1; n <= lines.length - 1; n++) {
            String line = lines[n];
            // Line example: ", 1       SurfaceFlinger: [android.ui.ISurfaceComposer]\n"
            Matcher matcher = serviceLinePattern.matcher(line);
            if (!matcher.find()) {
                throw new RuntimeException(
                        "Unable to parse service information from: " + line);
            }

            String serviceName = matcher.group(1);
            String servicePackage = matcher.group(2);
            ServiceInfo serviceInfo = new ServiceInfo(serviceName, servicePackage);
            runningServices.put(serviceName, serviceInfo);
        }
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    public void clear() {
        runningServices.clear();
    }
}
