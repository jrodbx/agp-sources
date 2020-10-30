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
import com.android.repository.api.Downloader;
import com.android.repository.api.Installer;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.io.FileOp;
import com.android.repository.util.InstallerUtil;
import java.io.File;
import java.io.IOException;

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

    private File mInstallLocation = null;

    public AbstractInstaller(@NonNull RemotePackage p, @NonNull RepoManager manager,
      @NonNull Downloader downloader, @NonNull FileOp fop) {
        super(manager, fop);
        mPackage = p;
        mDownloader = downloader;
        registerStateChangeListener((op, progress) -> {
            if (getInstallStatus() == InstallStatus.COMPLETE) {
                try {
                    InstallerUtil
                            .writePackageXml(getPackage(), getLocation(progress), getRepoManager(),
                                    mFop, progress);
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
    public final File getLocation(@NonNull ProgressIndicator progress) {
        if (mInstallLocation == null) {
            mInstallLocation = computeInstallLocation(progress);
        }
        return mInstallLocation;
    }

    @NonNull
    private File computeInstallLocation(@NonNull ProgressIndicator progress) {
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
    private File getNonConflictingPath(@NonNull ProgressIndicator progress) {
        File dir = mPackage.getInstallDir(getRepoManager(), progress);
        if (!mFop.exists(dir)) {
            return dir;
        }
        if (mFop.isDirectory(dir)) {
            File[] files = mFop.listFiles(dir);
            if (files.length == 0) {
                return dir;
            }
            if (files.length == 1 && files[0].getName().equals(InstallerUtil.INSTALLER_DIR_FN)) {
                return dir;
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
        File parent = dir.getParentFile();
        String leaf = dir.getName();
        for (int n = 2; ; n++) {
            dir = new File(parent, leaf + "-" + n);
            if (!mFop.exists(dir)) {
                break;
            }
        }
        warning += "\nInstalling in \"" + dir + "\" instead.";
        progress.logWarning(warning);
        return dir;
    }

    @Nullable
    private LocalPackage findConflictingPackage(
            @NonNull File dir, @NonNull ProgressIndicator progress) {
        for (LocalPackage existing : getRepoManager().getPackages().getLocalPackages().values()) {
            try {
                String existingLocation = existing.getLocation().getCanonicalPath();
                String newLocation = dir.getCanonicalPath();
                if (existingLocation.startsWith(newLocation)
                        || newLocation.startsWith(existingLocation)) {
                    return existing;
                }
            } catch (IOException e) {
                progress.logWarning("Error while trying to check install path", e);
                // We couldn't verify that the path is ok, so assume it's not.
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
