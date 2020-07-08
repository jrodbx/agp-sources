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
import com.android.repository.api.Downloader;
import com.android.repository.api.Installer;
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
        return mPackage.getInstallDir(getRepoManager(), progress);
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
