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
import com.android.annotations.concurrency.Slow;
import com.android.repository.api.Dependency;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.UpdatablePackage;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.meta.RevisionType;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

class ListAction extends SdkAction {
    private static final String LIST_ARG = "--list";
    private static final String LIST_INSTALLED_ARG = "--list_installed";

    private boolean installedOnly = false;

    private ListAction(@NonNull SdkManagerCliSettings settings) {
        super(settings);
    }

    @Slow
    @Override
    public void execute(@NonNull ProgressIndicator progress) {
        progress.setText("Loading package information...");
        getRepoManager()
                .loadSynchronously(0, progress, installedOnly ? null : getDownloader(), mSettings);

        RepositoryPackages packages = getRepoManager().getPackages();

        Collection<LocalPackage> locals = new TreeSet<>();
        Collection<LocalPackage> localObsoletes = new TreeSet<>();
        for (LocalPackage local : packages.getLocalPackages().values()) {
            if (local.obsolete()) {
                localObsoletes.add(local);
            } else {
                locals.add(local);
            }
        }

        Collection<RemotePackage> remotes = new TreeSet<>();
        Collection<RemotePackage> remoteObsoletes = new TreeSet<>();
        for (RemotePackage remote : packages.getRemotePackages().values()) {
            if (remote.obsolete()) {
                remoteObsoletes.add(remote);
            } else {
                remotes.add(remote);
            }
        }

        Set<UpdatablePackage> updates = new TreeSet<>(packages.getUpdatedPkgs());

        if (mSettings.isVerbose()) {
            printListVerbose(locals, localObsoletes, remotes, remoteObsoletes, updates);
        } else {
            printList(locals, localObsoletes, remotes, remoteObsoletes, updates);
        }
    }

    public static void register(
            @NonNull Map<String, Function<SdkManagerCliSettings, SdkAction>> argToFactory) {
        argToFactory.put(LIST_ARG, ListAction::new);
        argToFactory.put(
                LIST_INSTALLED_ARG,
                settings -> {
                    ListAction action = new ListAction(settings);
                    action.installedOnly = true;
                    return action;
                });
    }

    private void printListVerbose(
            @NonNull Collection<LocalPackage> locals,
            @NonNull Collection<LocalPackage> localObsoletes,
            @NonNull Collection<RemotePackage> remotes,
            @NonNull Collection<RemotePackage> remoteObsoletes,
            @NonNull Set<UpdatablePackage> updates) {

        if (!locals.isEmpty()) {
            getOutputStream().println("Installed packages:");
            getOutputStream().println("--------------------------------------");

            verboseListLocal(locals);
        }

        if (mSettings.includeObsolete() && !localObsoletes.isEmpty()) {
            getOutputStream().println("Installed Obsolete Packages:");
            getOutputStream().println("--------------------------------------");
            verboseListLocal(locals);
        }

        if (!remotes.isEmpty()) {
            getOutputStream().println("Available Packages:");
            getOutputStream().println("--------------------------------------");
            verboseListRemote(remotes);
        }

        if (mSettings.includeObsolete() && !remoteObsoletes.isEmpty()) {
            getOutputStream().println();
            getOutputStream().println("Available Obsolete Packages:");
            getOutputStream().println("--------------------------------------");
            verboseListRemote(remoteObsoletes);
        }

        if (!updates.isEmpty()) {
            getOutputStream().println("Available Updates:");
            getOutputStream().println("--------------------------------------");
            for (UpdatablePackage update : updates) {
                getOutputStream().println(update.getPath());
                getOutputStream()
                        .println("    Installed Version: " + update.getLocal().getVersion());
                getOutputStream()
                        .println("    Available Version: " + update.getRemote().getVersion());
                if (update.getRemote().obsolete()) {
                    getOutputStream().println("    (Obsolete)");
                }
            }
        }
    }

    private void verboseListLocal(@NonNull Collection<LocalPackage> locals) {
        for (LocalPackage local : locals) {
            getOutputStream().println(local.getPath());
            getOutputStream().println("    Description:        " + local.getDisplayName());
            getOutputStream().println("    Version:            " + local.getVersion());
            getOutputStream().println("    Installed Location: " + local.getLocation());
            getOutputStream().println();
        }
    }

    private void verboseListRemote(@NonNull Collection<RemotePackage> remotes) {
        for (RemotePackage remote : remotes) {
            getOutputStream().println(remote.getPath());
            getOutputStream().println("    Description:        " + remote.getDisplayName());
            getOutputStream().println("    Version:            " + remote.getVersion());
            if (!remote.getAllDependencies().isEmpty()) {
                getOutputStream().println("    Dependencies:");
                for (Dependency dependency : remote.getAllDependencies()) {
                    RevisionType minRevision = dependency.getMinRevision();
                    getOutputStream().print("        " + dependency.getPath());
                    if (minRevision != null) {
                        getOutputStream().println(" Revision " + minRevision.toRevision());
                    } else {
                        getOutputStream().println();
                    }
                }
            }
            getOutputStream().println();
        }
    }

    private void printList(
            @NonNull Collection<LocalPackage> locals,
            @NonNull Collection<LocalPackage> localObsoletes,
            @NonNull Collection<RemotePackage> remotes,
            @NonNull Collection<RemotePackage> remoteObsoletes,
            @NonNull Set<UpdatablePackage> updates) {
        TableFormatter<LocalPackage> localTable = new TableFormatter<>();
        localTable.addColumn("Path", RepoPackage::getPath, 9999, 0);
        localTable.addColumn("Version", p -> p.getVersion().toString(), 100, 0);
        localTable.addColumn("Description", RepoPackage::getDisplayName, 100, 0);
        localTable.addColumn(
                "Location",
                p -> {
                    Path path = getRepoManager().getLocalPath();
                    return Objects.requireNonNull(path).relativize(p.getLocation()).toString();
                },
                9999,
                0);

        if (!locals.isEmpty()) {
            getOutputStream().println("Installed packages:");
            localTable.print(locals, getOutputStream());
        }

        if (mSettings.includeObsolete() && !localObsoletes.isEmpty()) {
            getOutputStream().println();
            getOutputStream().println("Installed Obsolete Packages:");
            localTable.print(localObsoletes, getOutputStream());
        }

        TableFormatter<RemotePackage> remoteTable = new TableFormatter<>();
        remoteTable.addColumn("Path", RepoPackage::getPath, 9999, 0);
        remoteTable.addColumn("Version", p -> p.getVersion().toString(), 100, 0);
        remoteTable.addColumn("Description", RepoPackage::getDisplayName, 100, 0);

        if (!remotes.isEmpty()) {
            getOutputStream().println();
            getOutputStream().println("Available Packages:");
            remoteTable.print(remotes, getOutputStream());
        }
        if (mSettings.includeObsolete() && !remoteObsoletes.isEmpty()) {
            getOutputStream().println();
            getOutputStream().println("Available Obsolete Packages:");
            remoteTable.print(remoteObsoletes, getOutputStream());
        }

        if (!updates.isEmpty()) {
            getOutputStream().println();
            getOutputStream().println("Available Updates:");
            TableFormatter<UpdatablePackage> updateTable = new TableFormatter<>();
            updateTable.addColumn("ID", UpdatablePackage::getPath, 9999, 0);
            updateTable.addColumn("Installed", p -> p.getLocal().getVersion().toString(), 20, 0);
            updateTable.addColumn("Available", p -> p.getRemote().getVersion().toString(), 20, 0);
            if (!mSettings.includeObsolete()) {
                updates.removeIf(updatable -> updatable.getRemote().obsolete());
            }
            updateTable.print(updates, getOutputStream());
        }
    }
}
