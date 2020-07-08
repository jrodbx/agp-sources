/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.annotations.VisibleForTesting;
import com.android.repository.api.DelegatingProgressIndicator;
import com.android.repository.api.Installer;
import com.android.repository.api.PackageOperation;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoManager;
import com.android.repository.api.Uninstaller;
import com.android.repository.io.FileOp;
import com.android.repository.util.InstallerUtil;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Frameworks for concrete {@link Installer}s and {@link Uninstaller}s that manages creation of temp
 * directories, writing package metadata and install status, and resuming in-progress installs.
 */
public abstract class AbstractPackageOperation implements PackageOperation {

    /**
     * Key used in the properties file for the temporary path.
     */
    private static final String PATH_KEY = "path";

    /**
     * The concrete type of the installer. TODO: do we actually need this?
     */
    private static final String CLASSNAME_KEY = "class";

    /**
     * The filename prefix used to store SDK manager metadata. Directories starting with this prefix should not be scanned for packages.
     */
    public static final String METADATA_FILENAME_PREFIX = ".";

    /**
     * Name of the marker file that's written into the temporary directory when the prepare phase
     * has completed successfully.
     */
    private static final String PREPARE_COMPLETE_FN = METADATA_FILENAME_PREFIX + "prepareComplete";

    /**
     * Name of the directory created in the final install location containing data to get the
     * install restarted if it stops.
     */
    private static final String INSTALL_DATA_FN = METADATA_FILENAME_PREFIX + "installData";

    /**
     * Name of the directory used as the base for temporary files and located within the repo root.
     * We intentionally do not use system temp for downloads due to potentially large download size
     * that wouldn't always fit into a system-managed temp directory, but should fit into the SDK
     * directory (since this is where the uncompressed package will be installed anyway).
     */
    public static final String REPO_TEMP_DIR_FN = METADATA_FILENAME_PREFIX + "temp";

    /**
     * Name of the directory used as the base for download intermediates which some package
     * operations may use to customize their downloader settings.
     */
    public static final String DOWNLOAD_INTERMEDIATES_DIR_FN =
            METADATA_FILENAME_PREFIX + "downloadIntermediates";

    /**
     * Prefix used when creating temporary directories.
     */
    static final String TEMP_DIR_PREFIX = "PackageOperation";

    /**
     * Maximal number of temporary directories for package operations.
     */
    static final int MAX_PACKAGE_OPERATION_TEMP_DIRS = 100;

    /**
     * Status of the installer.
     */
    private InstallStatus mInstallStatus = InstallStatus.NOT_STARTED;

    /**
     * Properties written to the final install folder, used to restart the installer if needed.
     */
    private Properties mInstallProperties;

    private PackageOperation mFallbackOperation;

    private final Object mStateChangeLock = new Object();

    private enum StartTaskStatus {STARTED, ALREADY_DONE, FAILED}

    /**
     * Listeners that will be notified when the status changes.
     */
    private List<StatusChangeListener> mListeners = Lists.newArrayList();

    private final RepoManager mRepoManager;

    protected final FileOp mFop;

    private DelegatingProgressIndicator mPrepareProgress;
    private DelegatingProgressIndicator mCompleteProgress;

    protected AbstractPackageOperation(@NonNull RepoManager repoManager, @NonNull FileOp fop) {
        mRepoManager = repoManager;
        mFop = fop;
    }

    /**
     * Subclasses should override this to prepare a package for (un)installation, including
     * downloading, unzipping, etc. as needed. No modification to the actual SDK should happen
     * during this time.
     *
     * @param installTempPath The dir that should be used for any intermediate processing.
     * @param progress        For logging and progress display
     */
    protected abstract boolean doPrepare(@NonNull File installTempPath,
            @NonNull ProgressIndicator progress);

    /**
     * Subclasses should implement this to do any install/uninstall completion actions required.
     *
     * @param installTemp The temporary dir in which we prepared the (un)install. May be {@code
     *                    null} if for example the installer removed the installer properties file,
     *                    but should not be normally.
     * @param progress    For logging and progress indication.
     * @return {@code true} if the operation succeeded, {@code false} otherwise.
     * @see #complete(ProgressIndicator)
     */
    protected abstract boolean doComplete(@Nullable File installTemp,
            @NonNull ProgressIndicator progress);

    /**
     * Finds the prepared files using the installer metadata, and calls {@link
     * #doComplete(File, ProgressIndicator)}.
     *
     * @param progress A {@link ProgressIndicator}, to show install progress and facilitate
     *                 logging.
     * @return {@code true} if the install was successful, {@code false} otherwise.
     */
    @Override
    public final boolean complete(@NonNull ProgressIndicator progress) {
        synchronized (mProgressLock) {
            mCompleteProgress = addProgress(progress, mCompleteProgress);
        }
        StartTaskStatus startResult = startTask(InstallStatus.RUNNING, mCompleteProgress);
        if (startResult != StartTaskStatus.STARTED) {
            return startResult == StartTaskStatus.ALREADY_DONE;
        }
        if (mInstallProperties == null) {
            try {
                mInstallProperties =
                        readInstallProperties(mFop.toPath(getLocation(mCompleteProgress)));
            } catch (IOException e) {
                // We won't have a temp path, but try to continue anyway
            }
        }
        boolean result = false;
        String installTempPath = null;
        if (mInstallProperties != null) {
            installTempPath = mInstallProperties.getProperty(PATH_KEY);
        }
        File installTemp = installTempPath == null ? null : new File(installTempPath);
        try {
            // Re-validate the install path, in case something was changed since prepare.
            if (!InstallerUtil.checkValidPath(
                    getLocation(mCompleteProgress), getRepoManager(), mCompleteProgress)) {
                return false;
            }

            result = doComplete(installTemp, mCompleteProgress);
            mCompleteProgress.logInfo(String.format("\"%1$s\" complete.", getName()));
        } finally {
            if (!result && mCompleteProgress.isCanceled()) {
                cleanup(mCompleteProgress);
            }
            result &=
                    updateStatus(
                            result ? InstallStatus.COMPLETE : InstallStatus.FAILED,
                            mCompleteProgress);
            if (result && installTemp != null) {
                mFop.deleteFileOrFolder(installTemp);
            }
            getRepoManager().installEnded(getPackage());
            getRepoManager().markLocalCacheInvalid();
        }

        mCompleteProgress.setFraction(1);
        mCompleteProgress.setIndeterminate(false);
        mCompleteProgress.logInfo(
                String.format("\"%1$s\" %2$s.", getName(), result ? "finished" : "failed"));
        return result;
    }

    @NonNull
    private StartTaskStatus startTask(
            @NonNull InstallStatus inProgress, @NonNull ProgressIndicator progress) {
        boolean alreadyStarted = false;
        CompletableFuture<Void> f = new CompletableFuture<>();
        synchronized (mStateChangeLock) {
            if (mInstallStatus == InstallStatus.FAILED) {
                return StartTaskStatus.FAILED;
            } else if (mInstallStatus.compareTo(inProgress) > 0) {
                return StartTaskStatus.ALREADY_DONE;
            } else if (mInstallStatus == inProgress) {
                registerStateChangeListener((op, p) -> {
                    // Complete only if we've moved on. Since the listeners are retrieved outside
                    // this synchronized block, it's possible for the update to be to the current
                    // inProgress state rather than away from it.
                    if (op.getInstallStatus().compareTo(inProgress) > 0) {
                        f.complete(null);
                    }
                });
                alreadyStarted = true;
            } else {
                // Don't use updateStatus here, since we don't want the listeners to fire in the
                // synchronized block.
                mInstallStatus = inProgress;
            }
        }
        boolean success;
        if (alreadyStarted) {
            // Method isn't expected to return while task is in process. Wait for existing one.
            try {
                f.get();
                success = getInstallStatus() != InstallStatus.FAILED;
            } catch (InterruptedException | ExecutionException e) {
                // Shouldn't happen, but if it does consider us to be failed.
                success = false;
            }
        } else {
            // Now fire the listeners for actually starting
            success = updateStatus(inProgress, progress);
        }
        if (!success) {
            progress.setFraction(1);
            progress.setIndeterminate(false);
            progress.logInfo(String.format("\"%1$s\" failed.", getName()));
            return StartTaskStatus.FAILED;
        }
        return alreadyStarted ? StartTaskStatus.ALREADY_DONE : StartTaskStatus.STARTED;
    }

    /**
     * Looks in {@code installPath} for an install properties file and returns the contents if
     * found.
     */
    @Nullable
    private static Properties readInstallProperties(@NonNull Path installPath) throws IOException {
        Path metaDir = installPath.resolve(InstallerUtil.INSTALLER_DIR_FN);
        Path dataFile = metaDir.resolve(INSTALL_DATA_FN);

        if (Files.exists(dataFile)) {
            Properties installProperties = new Properties();
            try (InputStream inStream = Files.newInputStream(dataFile)) {
                installProperties.load(inStream);
                return installProperties;
            }
        }
        return null;
    }

    protected void cleanup(@NonNull ProgressIndicator progress) {
        mFop.deleteFileOrFolder(new File(getLocation(progress), InstallerUtil.INSTALLER_DIR_FN));
    }

    /**
     * Writes information used to restore the operation state if needed, then calls {@link
     * #doPrepare(File, ProgressIndicator)}
     *
     * @param progress A {@link ProgressIndicator}, to show progress and facilitate logging.
     * @return {@code true} if the operation succeeded, {@code false} otherwise.
     */
    @Override
    public final boolean prepare(@NonNull ProgressIndicator progress) {
        synchronized (mProgressLock) {
            mPrepareProgress = addProgress(progress, mPrepareProgress);
        }
        StartTaskStatus startResult = startTask(InstallStatus.PREPARING, mPrepareProgress);
        if (startResult != StartTaskStatus.STARTED) {
            return startResult == StartTaskStatus.ALREADY_DONE;
        }

        mPrepareProgress.logInfo(String.format("Preparing \"%1$s\".", getName()));
        try {
            File dest = getLocation(mPrepareProgress);

            mInstallProperties = readOrCreateInstallProperties(dest, mPrepareProgress);
        } catch (IOException e) {
            mPrepareProgress.logWarning("Failed to read or create install properties file.");
            return false;
        }
        getRepoManager().installBeginning(getPackage(), this);
        boolean result = false;
        try {
            if (!InstallerUtil.checkValidPath(
                    getLocation(mPrepareProgress), getRepoManager(), mPrepareProgress)) {
                return false;
            }

            File installTempPath = writeInstallerMetadata(mPrepareProgress);
            if (installTempPath == null) {
                mPrepareProgress.logInfo(String.format("\"%1$s\" failed.", getName()));
                return false;
            }
            File prepareCompleteMarker = new File(installTempPath, PREPARE_COMPLETE_FN);
            if (!mFop.exists(prepareCompleteMarker)) {
                if (doPrepare(installTempPath, mPrepareProgress)) {
                    mFop.createNewFile(prepareCompleteMarker);
                    result = updateStatus(InstallStatus.PREPARED, mPrepareProgress);
                }
            } else {
                mPrepareProgress.logInfo("Found existing prepared package.");
                result = true;
            }
        } catch (IOException e) {
            result = false;
        } finally {
            if (!result) {
                getRepoManager().installEnded(getPackage());
                updateStatus(InstallStatus.FAILED, mPrepareProgress);
                // If there was a failure don't clean up the files, so we can continue if requested
                if (mPrepareProgress.isCanceled()) {
                    cleanup(mPrepareProgress);
                }
            }
        }
        mPrepareProgress.logInfo(
                String.format("\"%1$s\" %2$s.", getName(), result ? "ready" : "failed"));
        return result;
    }

    /**
     * Looks in {@code affectedPath} for an install properties file and returns the contents if
     * found. If not found, creates and populates it.
     *
     * @param affectedPath The path on which this operation acts (either to write to or delete from)
     * @return The read or created properties.
     */
    @NonNull
    private Properties readOrCreateInstallProperties(
            @NonNull File affectedPath, @NonNull ProgressIndicator progress) throws IOException {
        Properties installProperties = readInstallProperties(mFop.toPath(affectedPath));
        if (installProperties != null && installProperties.containsKey(PATH_KEY)) {
            return installProperties;
        }
        installProperties = new Properties();

        File metaDir = new File(affectedPath, InstallerUtil.INSTALLER_DIR_FN);
        if (!mFop.exists(metaDir)) {
            mFop.mkdirs(metaDir);
        }
        File dataFile = new File(metaDir, INSTALL_DATA_FN);
        File installTempPath = getNewPackageOperationTempDir(getRepoManager(), TEMP_DIR_PREFIX, mFop);
        if (installTempPath == null) {
            deleteOrphanedTempDirs(progress);
            installTempPath = getNewPackageOperationTempDir(getRepoManager(), TEMP_DIR_PREFIX, mFop);
            if (installTempPath == null) {
                throw new IOException("Failed to create temp path");
            }
        }
        installProperties.put(PATH_KEY, installTempPath.getPath());
        installProperties.put(CLASSNAME_KEY, getClass().getName());
        mFop.createNewFile(dataFile);
        try (OutputStream out = mFop.newFileOutputStream(dataFile)) {
            installProperties.store(out, null);
        }
        return installProperties;
    }

    private void deleteOrphanedTempDirs(@NonNull ProgressIndicator progress) {
        Path root = mFop.toPath(mRepoManager.getLocalPath());
        Path suffixPath = mFop.toPath(new File(InstallerUtil.INSTALLER_DIR_FN, INSTALL_DATA_FN));
        try (Stream<Path> paths = Files.walk(root)) {
            Set<File> tempDirs =
                    paths.filter(path -> path.endsWith(suffixPath))
                            .map(this::getPathPropertiesOrNull)
                            .filter(Objects::nonNull)
                            .map(props -> props.getProperty(PATH_KEY))
                            .map(File::new)
                            .collect(Collectors.toSet());
            retainPackageOperationTempDirs(tempDirs, TEMP_DIR_PREFIX, mFop);
        } catch (IOException e) {
            progress.logWarning("Error while searching for in-use temporary directories.", e);
        }
    }

    @VisibleForTesting
    static File getNewPackageOperationTempDir(@NonNull RepoManager repoManager, @NonNull String base, @NonNull FileOp fileOp) {
        for (int i = 1; i < MAX_PACKAGE_OPERATION_TEMP_DIRS; i++) {
            File folder = getPackageOperationTempDir(repoManager, base, i);
            if (!fileOp.exists(folder)) {
                fileOp.mkdirs(folder);
                return folder;
            }
        }
        return null;
    }

    @VisibleForTesting
    static File getPackageOperationTempDir(@NonNull RepoManager repoManager, @NonNull String base, int index) {
        File rootTempDir = new File(repoManager.getLocalPath(), REPO_TEMP_DIR_FN);
        return new File(rootTempDir, String.format("%1$s%2$02d", base, index));
    }

    private void retainPackageOperationTempDirs(Set<File> retain, String base, FileOp mFop) {
        for (int i = 1; i < MAX_PACKAGE_OPERATION_TEMP_DIRS; i++) {
            File dir = getPackageOperationTempDir(getRepoManager(), base, i);
            if (mFop.exists(dir) && !retain.contains(dir)) {
                mFop.deleteFileOrFolder(dir);
            }
        }
    }

    @Nullable
    private Properties getPathPropertiesOrNull(@NonNull Path path) {
        try {
            return readInstallProperties(path.getParent().getParent());
        } catch (IOException e) {
            return null;
        }
    }

    @Nullable
    private File writeInstallerMetadata(@NonNull ProgressIndicator progress) throws IOException {
        File installPath = getLocation(progress);
        Properties installProperties = readOrCreateInstallProperties(installPath, progress);
        File installTempPath = new File((String) installProperties.get(PATH_KEY));
        if (!mFop.exists(installPath) && !mFop.mkdirs(installPath) ||
                !mFop.isDirectory(installPath)) {
            progress.logWarning("Failed to create output directory: " + installPath);
            return null;
        }
        mFop.deleteOnExit(installTempPath);
        return installTempPath;
    }

    @NonNull
    @Override
    public RepoManager getRepoManager() {
        return mRepoManager;
    }

    /**
     * Registers a listener that will be called when the {@link InstallStatus} of
     * this installer changes.
     */
    @Override
    public final void registerStateChangeListener(@NonNull StatusChangeListener listener) {
        synchronized (mStateChangeLock) {
            mListeners.add(listener);
        }
    }

    /**
     * Gets the current {@link InstallStatus} of this installer.
     */
    @Override
    @NonNull
    public final InstallStatus getInstallStatus() {
        return mInstallStatus;
    }

    /**
     * Sets our status to {@code status} and notifies our listeners. If any listener throws an
     * exception we will stop processing listeners and update our status to {@code
     * InstallStatus.FAILED} (calling the listeners again with that status update).
     */
    protected final boolean updateStatus(
            @NonNull InstallStatus status, @NonNull ProgressIndicator progress) {
        List<StatusChangeListener> listeners;
        synchronized (mStateChangeLock) {
            mInstallStatus = status;
            listeners = new ArrayList<>(mListeners);
        }
        try {
            for (StatusChangeListener listener : listeners) {
                try {
                    listener.statusChanged(this, progress);
                } catch (Exception e) {
                    if (status != InstallStatus.FAILED) {
                        throw e;
                    }
                    // else ignore and continue with the other listeners
                }
            }
        } catch (Exception e) {
            progress.logError("Failed to update status to " + status, e);
            updateStatus(InstallStatus.FAILED, progress);
            return false;
        }
        return true;
    }


    @Override
    @Nullable
    public PackageOperation getFallbackOperation() {
        return mFallbackOperation;
    }

    @Override
    public void setFallbackOperation(@Nullable PackageOperation mFallbackOperation) {
        this.mFallbackOperation = mFallbackOperation;
    }

    private final Object mProgressLock = new Object();

    @NonNull
    private DelegatingProgressIndicator addProgress(
            @NonNull ProgressIndicator progress, @Nullable DelegatingProgressIndicator existing) {
        if (existing == null) {
            existing = new DelegatingProgressIndicator(progress);
        } else {
            existing.addDelegate(progress);
        }
        return existing;
    }
}

