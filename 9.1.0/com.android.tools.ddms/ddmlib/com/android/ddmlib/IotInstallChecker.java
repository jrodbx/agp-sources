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
package com.android.ddmlib;

import com.android.annotations.NonNull;
import com.google.common.annotations.VisibleForTesting;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IotInstallChecker {
    private static final String TAG = IotInstallChecker.class.getSimpleName();
    private static final String DUMP_PACKAGES_CMD = "dumpsys package -f";

    public Set<String> getInstalledIotLauncherApps(@NonNull final IDevice device) {
        return getInstalledIotLauncherApps(device, 1, TimeUnit.MINUTES);
    }

    public Set<String> getInstalledIotLauncherApps(
            @NonNull final IDevice device, long timeout, @NonNull TimeUnit unit) {
        if (!device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)) {
            return Collections.emptySet();
        }
        LauncherPackagesReceiver launcherPackagesReceiver = new LauncherPackagesReceiver();
        SystemPackagesReceiver systemPackagesReceiver = new SystemPackagesReceiver();
        IShellOutputReceiver combinedReceiver =
                new CombinedReceiver(launcherPackagesReceiver, systemPackagesReceiver);
        try {
            device.executeShellCommand(DUMP_PACKAGES_CMD, combinedReceiver, timeout, unit);
        } catch (Exception e) {
            Log.e(TAG, e);
        }
        Set<String> thirdPartyLauncherPackages =
                new HashSet<>(launcherPackagesReceiver.getMatchingPackages());
        Set<String> systemPackages = systemPackagesReceiver.getMatchingPackages();
        thirdPartyLauncherPackages.removeAll(systemPackages);
        return thirdPartyLauncherPackages;
    }

    @VisibleForTesting
    static class CombinedReceiver extends MultiLineReceiver {
        private MultiLineReceiver[] receivers;

        public CombinedReceiver(MultiLineReceiver... receivers) {
            this.receivers = receivers;
        }

        @Override
        public void processNewLines(@NonNull String[] lines) {
            for (MultiLineReceiver receiver : receivers) {
                receiver.processNewLines(lines);
            }
        }

        @Override
        public boolean isCancelled() {
            for (MultiLineReceiver receiver : receivers) {
                if (!receiver.isCancelled()) {
                    return false;
                }
            }
            return true;
        }
    }

    @VisibleForTesting
    static class SystemPackagesReceiver extends PackageCollectorReceiver {
        private static final String PackagesPart = "Packages";
        //  Package [com.example.anhtnguyen.ereader] (8cd1023):
        private static final Pattern PackagesPackageRegex =
                Pattern.compile("^Package \\[([\\w\\.]+)\\] \\(\\w+\\):$");
        private static final Pattern FlagsRegex = Pattern.compile("^flags=\\[ ([\\w\\s_]+) \\]$");
        private static final String SYSTEM_FLAG = "SYSTEM";

        SystemPackagesReceiver() {
            super(PackagesPart, PackagesPackageRegex);
        }

        @Override
        boolean packageQualifies(String line) {
            // Checks if the package has the SYSTEM flag.
            Matcher matcher = FlagsRegex.matcher(line);
            if (matcher.matches()) {
                String[] flags = matcher.group(1).split(" ");
                for (String flag : flags) {
                    if (flag.equals(SYSTEM_FLAG)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    @VisibleForTesting
    static class LauncherPackagesReceiver extends PackageCollectorReceiver {
        private static final String FiltersPart = "android.intent.action.MAIN";
        // 2a41f7f com.android.provision/.DefaultActivity filter 9341c43
        private static final Pattern FiltersPackageRegex =
                Pattern.compile("^\\w+ ([\\w\\.]+)/\\.\\w+ filter \\w+$");
        private static final String IotLauncher = "android.intent.category.IOT_LAUNCHER";

        LauncherPackagesReceiver() {
            super(FiltersPart, FiltersPackageRegex);
        }

        @Override
        boolean packageQualifies(String line) {
            return line.contains(IotLauncher);
        }
    }

    @VisibleForTesting
    private abstract static class PackageCollectorReceiver extends MultiLineReceiver {
        // android.intent.action.MAIN:
        private static final Pattern ParagraphRegex = Pattern.compile("^([\\w\\.]+):$");
        private final Set<String> matchingPackages = new HashSet<>();
        private String currentPackage;
        private boolean mainPart = false;
        private boolean isCancelled = false;
        private String paragraphName;
        private Pattern packageRegex;

        private PackageCollectorReceiver(String paragraphName, Pattern packageRegex) {
            this.paragraphName = paragraphName;
            this.packageRegex = packageRegex;
        }

        @Override
        public void processNewLines(@NonNull String[] lines) {
            for (String l : lines) {
                processNewLine(l);
            }
        }

        private void processNewLine(String line) {
            boolean stateChanged = updateCurrentPart(line);
            if (stateChanged) {
                return;
            }

            // If we are in the main section, look for qualifying packages.
            if (mainPart) {
                stateChanged = updateCurrentPackage(line);
                if (stateChanged) {
                    return;
                }

                // We are currently in the section describing a certain package.
                if (!matchingPackages.contains(currentPackage) && packageQualifies(line)) {
                    matchingPackages.add(currentPackage);
                }
            }
        }

        /**
         * Updates which paragraph is being processed.
         *
         * @param line
         * @return true if it is entering or exiting the main part.
         */
        private boolean updateCurrentPart(String line) {
            Matcher matcher = ParagraphRegex.matcher(line);
            if (matcher.matches()) {
                if (matcher.group(1).equals(paragraphName)) {
                    mainPart = true;
                    return true;
                }
            }
            return false;
        }

        /**
         * Updates which package is being processed.
         *
         * @param line
         * @return true if the package has changed.
         */
        private boolean updateCurrentPackage(String line) {
            Matcher matcher = packageRegex.matcher(line);
            if (matcher.matches()) {
                currentPackage = matcher.group(1);
                return true;
            }
            return false;
        }

        abstract boolean packageQualifies(String line);

        @Override
        public boolean isCancelled() {
            return isCancelled;
        }

        public Set<String> getMatchingPackages() {
            return matchingPackages;
        }
    }

}
