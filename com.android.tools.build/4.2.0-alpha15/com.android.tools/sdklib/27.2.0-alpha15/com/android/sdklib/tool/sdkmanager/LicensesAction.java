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
package com.android.sdklib.tool.sdkmanager;

import com.android.annotations.NonNull;
import com.android.repository.api.License;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.google.common.collect.ImmutableList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

class LicensesAction extends SdkAction {
    private static final String LICENSES_ARG = "--licenses";

    private LicensesAction(@NonNull SdkManagerCliSettings settings) {
        super(settings);
    }

    public static void register(
            @NonNull Map<String, Function<SdkManagerCliSettings, SdkAction>> argToFactory) {
        argToFactory.put(LICENSES_ARG, LicensesAction::new);
    }

    @Override
    public void execute(@NonNull ProgressIndicator progress)
            throws SdkManagerCli.CommandFailedException {
        getRepoManager().loadSynchronously(0, progress, getDownloader(), mSettings);

        Set<License> licenses =
                getRepoManager()
                        .getPackages()
                        .getRemotePackages()
                        .values()
                        .stream()
                        .map(RemotePackage::getLicense)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(TreeSet::new));

        // Find licences that are not accepted yet.
        ImmutableList.Builder<License> licensesNotYetAcceptedBuilder = ImmutableList.builder();
        for (License license : licenses) {
            boolean accepted =
                    license.checkAccepted(
                            getSdkHandler().getLocation(), getSdkHandler().getFileOp());

            if (!accepted) {
                licensesNotYetAcceptedBuilder.add(license);
            }

            if (mSettings.isVerbose()) {
                SdkManagerCli.printLicense(license, getOutputStream());
                getOutputStream().format(accepted ? "Accepted%n%n" : "Not yet accepted%n%n");
            }
        }
        ImmutableList<License> licensesNotYetAccepted = licensesNotYetAcceptedBuilder.build();

        if (licensesNotYetAccepted.isEmpty()) {
            getOutputStream().println("All SDK package licenses accepted.");
            return;
        }

        getOutputStream()
                .format(
                        "%1$d of %2$d SDK package license%3$s not accepted.%n"
                                + "Review license%3$s that ha%4$s not been accepted (y/N)? ",
                        licensesNotYetAccepted.size(),
                        licenses.size(),
                        licensesNotYetAccepted.size() == 1 ? "" : "s",
                        licensesNotYetAccepted.size() == 1 ? "s" : "ve");
        if (!SdkManagerCli.askYesNo(getInputReader())) {
            return;
        }

        int newlyAcceptedCount = 0;
        for (int i = 0; i < licensesNotYetAccepted.size(); i++) {
            getOutputStream().format("%n%1$d/%2$d: ", i + 1, licensesNotYetAccepted.size());
            License license = licensesNotYetAccepted.get(i);
            if (SdkManagerCli.askForLicense(license, getOutputStream(), getInputReader())) {
                license.setAccepted(getRepoManager().getLocalPath(), getSdkHandler().getFileOp());
                newlyAcceptedCount++;
            }
        }

        if (newlyAcceptedCount == licensesNotYetAccepted.size()) {
            getOutputStream().println("All SDK package licenses accepted");
        } else {
            int notAccepted = licensesNotYetAccepted.size() - newlyAcceptedCount;
            getOutputStream()
                    .format(
                            "%1$d license%2$s not accepted%n",
                            notAccepted, notAccepted == 1 ? "" : "s");
        }
    }
}
