/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.repository.impl.installer;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.io.CancellableFileIo;
import com.android.repository.api.Downloader;
import com.android.repository.api.Installer;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.util.InstallerUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Framework for an installer that creates a temporary directory, writes package.xml when it's
 * done, and can resume if partly done.
 */
public abstract class AbstractInstaller extends AbstractPackageOperation
  implements Installer {

    /**
     * The package we're going to install.
     */
    private final RemotePackage mPackage;

    /**
     * The {@link Downloader} we'll use to download the archive.
     */
    private final Downloader mDownloader;

    private Path mInstallLocation = null;

    public AbstractInstaller(
            @NonNull RemotePackage p,
            @NonNull RepoManager manager,
            @NonNull Downloader downloader) {
        super(manager);
        mPackage = p;
        mDownloader = downloader;
        registerStateChangeListener(
                (op, progress) -> {
                    if (getInstallStatus() == InstallStatus.COMPLETE) {
                        try {
                            InstallerUtil.writePackageXml(
                                    getPackage(),
                                    getLocation(progress),
                                    getRepoManager(),
                                    progress);
                        } catch (IOException e) {
                            progress.logWarning("Failed to update package.xml", e);
                            throw new StatusChangeListenerException(e);
                        }
                    }
                });
    }

    @Override
    @NonNull
    public RemotePackage getPackage() {
        return mPackage;
    }

    @Override
    @NonNull
    public final Path getLocation(@NonNull ProgressIndicator progress) {
        if (mInstallLocation == null) {
            mInstallLocation = computeInstallLocation(progress);
        }
        return mInstallLocation;
    }

    @NonNull
    private Path computeInstallLocation(@NonNull ProgressIndicator progress) {
        LocalPackage existing =
                getRepoManager().getPackages().getLocalPackages().get(mPackage.getPath());
        if (existing != null) {
            // We're updating an existing package, overwrite it.
            return existing.getLocation();
        }
        // Get a new directory, without overwriting anything.
        return getNonConflictingPath(progress);
    }

    @NonNull
    private Path getNonConflictingPath(@NonNull ProgressIndicator progress) {
        Path dir = mPackage.getInstallDir(getRepoManager(), progress);
        if (!CancellableFileIo.exists(dir)) {
            return dir;
        }
        if (CancellableFileIo.isDirectory(dir)) {
            try (Stream<Path> fileStream = Files.list(dir)) {
                List<Path> files = fileStream.limit(2).collect(Collectors.toList());
                if (files.isEmpty()) {
                    return dir;
                }
                if (files.size() == 1
                        && files.get(0)
                                .getFileName()
                                .toString()
                                .equals(InstallerUtil.INSTALLER_DIR_FN)) {
                    return dir;
                }
            } catch (IOException ignore) {
                // fall through to the logic below
            }
        }
        // We're going to need a new path. Check to see if some other package is installed in
        // our expected place,
        // so we can print a better log message.
        LocalPackage conflicting = findConflictingPackage(dir, progress);
        String warning =
                "Package \""
                        + mPackage
                        + "\" ("
                        + mPackage.getPath()
                        + ") "
                        + "should be installed in \n\""
                        + dir
                        + "\" but \n";
        if (conflicting != null) {
            warning +=
                    "\""
                            + conflicting.getDisplayName()
                            + "\" ("
                            + conflicting.getPath()
                            + ") "
                            + "is already installed ";
            if (conflicting.getLocation().equals(dir)) {
                warning += "there.";
            } else {
                warning += "in \n\"" + conflicting.getLocation() + "\".";
            }
        } else {
            warning += "it already exists.";
        }
        Path parent = dir.getParent();
        String leaf = dir.getFileName().toString();
        for (int n = 2; ; n++) {
            dir = parent.resolve(leaf + "-" + n);
            if (!CancellableFileIo.exists(dir)) {
                break;
            }
        }
        warning += "\nInstalling in \"" + dir + "\" instead.";
        progress.logWarning(warning);
        return dir;
    }

    @Nullable
    private LocalPackage findConflictingPackage(
            @NonNull Path dir, @NonNull ProgressIndicator progress) {
        for (LocalPackage existing : getRepoManager().getPackages().getLocalPackages().values()) {
            String existingLocation = existing.getLocation().normalize().toString();
            String newLocation = dir.toString();
            if (existingLocation.startsWith(newLocation)
                    || newLocation.startsWith(existingLocation)) {
                return existing;
            }
        }
        return null;
    }

    @NonNull
    protected Downloader getDownloader() {
        return mDownloader;
    }

    @Override
    @NonNull
    public String getName() {
        return String.format("Install %1$s (revision: %2$s)", mPackage.getDisplayName(), mPackage.getVersion().toString());
    }
}
