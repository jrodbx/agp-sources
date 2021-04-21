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
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.Archive;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.repository.util.InstallerUtil;
import com.google.common.base.Strings;
import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * A simple {@link Installer} that just unzips the {@code complete} version of an {@link
 * Archive} into its destination directory.
 *
 * Probably instances should be created by {@link BasicInstallerFactory}
 */
class BasicInstaller extends AbstractInstaller {
    static final String FN_UNZIP_DIR = "unzip";
    private File myUnzipDir;

    BasicInstaller(@NonNull RemotePackage p, @NonNull RepoManager mgr,
      @NonNull Downloader downloader, @NonNull FileOp fop) {
        super(p, mgr, downloader, fop);
    }

    /**
     * Downloads and unzips the complete archive for {@code p} into {@code installTempPath}.
     *
     * @see #prepare(ProgressIndicator)
     */
    @Override
    protected boolean doPrepare(@NonNull File installTempPath,
      @NonNull ProgressIndicator progress) {
        URL url = InstallerUtil.resolveCompleteArchiveUrl(getPackage(), progress);
        if (url == null) {
            progress.logWarning("No compatible archive found!");
            return false;
        }
        Archive archive = getPackage().getArchive();
        assert archive != null;
        try {
            String path = url.getPath();
            File downloadLocation =
                    new File(installTempPath, path.substring(path.lastIndexOf('/') + 1));
            String checksum = archive.getComplete().getChecksum();
            getDownloader()
                    .downloadFullyWithCaching(
                            url, downloadLocation, checksum, progress.createSubProgress(0.5));
            if (progress.isCanceled()) {
                progress.setFraction(1);
                return false;
            }
            progress.setFraction(0.5);
            if (!mFop.exists(downloadLocation)) {
                progress.logWarning("Failed to download package!");
                return false;
            }
            myUnzipDir = new File(installTempPath, FN_UNZIP_DIR);
            mFop.mkdirs(myUnzipDir);
            InstallerUtil.unzip(
                    downloadLocation,
                    myUnzipDir,
                    mFop,
                    archive.getComplete().getSize(),
                    progress.createSubProgress(1));
            progress.setFraction(1);
            if (progress.isCanceled()) {
                return false;
            }
            mFop.delete(downloadLocation);

            return true;
        } catch (IOException e) {
            String message = e.getMessage();
            progress.logWarning(
                    String.format(
                            "An error occurred while preparing SDK package %1$s%2$s",
                            getPackage().getDisplayName(),
                            (Strings.isNullOrEmpty(message) ? "." : ": " + message + ".")),
                    e);
        }
        return false;
    }

    @Override
    protected void cleanup(@NonNull ProgressIndicator progress) {
        super.cleanup(progress);
        mFop.deleteFileOrFolder(getLocation(progress));
        if (myUnzipDir != null) {
            mFop.deleteFileOrFolder(myUnzipDir);
        }
    }

    /**
     * Just moves the prepared files into place.
     *
     * @see #complete(ProgressIndicator)
     */
    @Override
    protected boolean doComplete(@Nullable File installTempPath,
            @NonNull ProgressIndicator progress) {
        if (installTempPath == null) {
            return false;
        }
        try {
            if (progress.isCanceled()) {
                return false;
            }
            // Archives must contain a single top-level directory.
            File unzipDir = new File(installTempPath, FN_UNZIP_DIR);
            File[] topDirContents = mFop.listFiles(unzipDir);
            File packageRoot;
            if (topDirContents.length != 1) {
                // TODO: we should be consistent and only support packages with a single top-level
                // directory, but right now haxm doesn't have one. Put this check back when it's
                // fixed.
                // throw new IOException("Archive didn't have single top level directory");
                packageRoot = unzipDir;
            } else {
                packageRoot = topDirContents[0];
            }

            progress
              .logInfo(String.format("Installing %1$s in %2$s", getPackage().getDisplayName(),
                getLocation(progress)));

            // Move the final unzipped archive into place.
            FileOpUtils.safeRecursiveOverwrite(packageRoot, getLocation(progress), mFop, progress);

            return true;
        } catch (IOException e) {
            String message = e.getMessage();
            progress.logWarning("An error occurred during installation" +
              (message.isEmpty() ? "." : ": " + message + "."), e);
        } finally {
            progress.setFraction(1);
        }

        return false;
    }
}
