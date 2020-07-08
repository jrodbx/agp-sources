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
import com.android.repository.api.Installer;
import com.android.repository.api.License;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.util.InstallerUtil;
import com.android.sdklib.repository.installer.SdkInstallerUtil;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

class InstallAction extends SdkPackagesAction {
    private static final String ACTION_ARG = "--install";

    InstallAction(@NonNull SdkManagerCliSettings settings) {
        super(settings);
    }

    public static void register(
            @NonNull Map<String, Function<SdkManagerCliSettings, SdkAction>> argToFactory) {
        argToFactory.put(ACTION_ARG, InstallAction::new);
    }

    @Override
    public void execute(@NonNull ProgressIndicator progress)
            throws SdkManagerCli.CommandFailedException {
        progress.setText("Loading package information...");
        getRepoManager()
                .loadSynchronously(0, progress.createSubProgress(0.1), getDownloader(), mSettings);

        List<RemotePackage> remotes = new ArrayList<>();
        for (String path : getPaths(getRepoManager())) {
            RemotePackage p = getRepoManager().getPackages().getRemotePackages().get(path);
            if (p == null) {
                progress.logWarning("Failed to find package " + path);
                throw new SdkManagerCli.CommandFailedException();
            }
            remotes.add(p);
        }
        remotes =
                InstallerUtil.computeRequiredPackages(
                        remotes, getRepoManager().getPackages(), progress);
        if (remotes != null) {
            List<RemotePackage> acceptedRemotes = checkLicenses(remotes, progress);
            if (!acceptedRemotes.equals(remotes)) {
                getOutputStream()
                        .println(
                                "The following packages can not be installed since their "
                                        + "licenses or those of the packages they depend on were not accepted:");
                remotes.stream()
                        .filter(p -> !acceptedRemotes.contains(p))
                        .forEach(p -> getOutputStream().println("  " + p.getPath()));
                if (!acceptedRemotes.isEmpty()) {
                    getOutputStream().print("Continue installing the remaining packages? (y/N): ");
                    if (!SdkManagerCli.askYesNo(getInputReader())) {
                        throw new SdkManagerCli.CommandFailedException();
                    }
                }
                remotes = acceptedRemotes;
            }
            double progressMax = 0.1;
            double progressIncrement = 0.9 / (remotes.size());
            for (RemotePackage p : remotes) {
                progress.setText("Installing " + p.getDisplayName());
                Installer installer =
                        SdkInstallerUtil.findBestInstallerFactory(p, getSdkHandler())
                                .createInstaller(
                                        p,
                                        getRepoManager(),
                                        getDownloader(),
                                        getSdkHandler().getFileOp());
                progressMax += progressIncrement;
                if (!applyPackageOperation(installer, progress.createSubProgress(progressMax))) {
                    // there was an error, abort.
                    throw new SdkManagerCli.CommandFailedException();
                }
                progress.setFraction(progressMax);
            }
            progress.setFraction(1);
        } else {
            progress.logWarning("Unable to compute a complete list of dependencies.");
            throw new SdkManagerCli.CommandFailedException();
        }
    }

    /**
     * Checks whether the licenses for the given packages are accepted. If they are not, request
     * that the user accept them.
     *
     * @return A list of packages that have had their licenses accepted. If some licenses are not
     *     accepted, both the package with the unaccepted license and any packages that depend on it
     *     are excluded from this list.
     */
    @NonNull
    private List<RemotePackage> checkLicenses(
            @NonNull List<RemotePackage> remotes, @NonNull ProgressIndicator progress) {
        Multimap<License, RemotePackage> unacceptedLicenses = HashMultimap.create();
        remotes.forEach(
                remote -> {
                    License l = remote.getLicense();
                    if (l != null
                            && !l.checkAccepted(
                                    getSdkHandler().getLocation(), getSdkHandler().getFileOp())) {
                        unacceptedLicenses.put(l, remote);
                    }
                });
        for (License l : new TreeSet<>(unacceptedLicenses.keySet())) {
            if (SdkManagerCli.askForLicense(l, getOutputStream(), getInputReader())) {
                unacceptedLicenses.removeAll(l);
                l.setAccepted(getRepoManager().getLocalPath(), getSdkHandler().getFileOp());
            }
        }
        if (!unacceptedLicenses.isEmpty()) {
            List<RemotePackage> acceptedPackages = new ArrayList<>(remotes);
            Set<RemotePackage> problemPackages = new HashSet<>(unacceptedLicenses.values());
            getOutputStream()
                    .println("Skipping following packages as the license is not accepted:");
            problemPackages.forEach(problem -> getOutputStream().println(problem.getDisplayName()));
            acceptedPackages.removeAll(problemPackages);
            Iterator<RemotePackage> acceptedIter = acceptedPackages.iterator();

            while (acceptedIter.hasNext()) {
                RemotePackage accepted = acceptedIter.next();
                List<RemotePackage> required =
                        InstallerUtil.computeRequiredPackages(
                                Collections.singletonList(accepted),
                                getRepoManager().getPackages(),
                                progress);
                if (!Collections.disjoint(required, problemPackages)) {
                    acceptedIter.remove();
                    problemPackages.add(accepted);
                }
            }
            remotes = acceptedPackages;
        }
        return remotes;
    }
}
