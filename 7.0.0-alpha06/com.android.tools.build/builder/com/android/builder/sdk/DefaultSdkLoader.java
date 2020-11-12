/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.builder.sdk;

import static com.android.SdkConstants.FD_EXTRAS;
import static com.android.SdkConstants.FD_M2_REPOSITORY;
import static com.android.SdkConstants.FD_PLATFORM_TOOLS;
import static com.android.SdkConstants.FD_SUPPORT;
import static com.android.SdkConstants.FD_TOOLS;
import static com.android.SdkConstants.FN_ADB;
import static com.android.SdkConstants.FN_ANNOTATIONS_JAR;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.prefs.AndroidLocationsProvider;
import com.android.repository.Revision;
import com.android.repository.api.Downloader;
import com.android.repository.api.Installer;
import com.android.repository.api.License;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.SettingsController;
import com.android.repository.api.UpdatablePackage;
import com.android.repository.util.InstallerUtil;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.LoggerProgressIndicatorWrapper;
import com.android.sdklib.repository.installer.SdkInstallerUtil;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Singleton-based implementation of SdkLoader for a standard SDK
 */
public class DefaultSdkLoader implements SdkLoader {
    private enum InstallResultType {
        SUCCESS,
        LICENSE_FAIL,
        INSTALL_FAIL,
    }

    private static DefaultSdkLoader sLoader;

    @NonNull private final AndroidLocationsProvider androidLocationsProvider;

    @NonNull
    private final File mSdkLocation;
    private AndroidSdkHandler mSdkHandler;
    private SdkInfo mSdkInfo;
    private final ImmutableList<File> mRepositories;

    public static synchronized SdkLoader getLoader(
            @NonNull AndroidLocationsProvider androidLocationsProvider, @NonNull File sdkLocation) {
        if (sLoader == null) {
            sLoader = new DefaultSdkLoader(androidLocationsProvider, sdkLocation);
        } else if (!FileUtils.isSameFile(sdkLocation, sLoader.mSdkLocation)) {
            throw new IllegalStateException(
                    String.format(
                            "%1$s already created using %2$s; cannot also use %3$s. If this is a Gradle "
                                    + "composite build, it could be that you have set \"%4$s\" to multiple values "
                                    + "across builds. Make sure that \"%4$s\" is the same in all .properties files of "
                                    + "the composite build",
                            DefaultSdkLoader.class.getSimpleName(),
                            sLoader.mSdkLocation,
                            sdkLocation,
                            ProjectProperties.PROPERTY_SDK));
        }

        return sLoader;
    }

    public static synchronized void unload() {
        sLoader = null;
    }

    @Override
    @NonNull
    public TargetInfo getTargetInfo(
            @NonNull String targetHash,
            @NonNull Revision buildToolRevision,
            @NonNull ILogger logger,
            @NonNull SdkLibData sdkLibData)
            throws LicenceNotAcceptedException, InstallFailedException {
        init(logger);

        // One progress is used for the auto-download feature,
        // the other is used for parsing the repository XMLs and other operations.
        ProgressIndicator progress = new LoggerProgressIndicatorWrapper(logger);
        ProgressIndicator stdOutputProgress = getNewDownloadProgress();
        IAndroidTarget target = mSdkHandler.getAndroidTargetManager(progress)
                .getTargetFromHashString(targetHash, progress);

        BuildToolInfo buildToolInfo =
                mSdkHandler.getBuildToolInfo(buildToolRevision, progress);

        if (sdkLibData.useSdkDownload()) {
            SettingsController settings = sdkLibData.getSettings();
            Downloader downloader = sdkLibData.getDownloader();
            Preconditions.checkNotNull(settings);
            Preconditions.checkNotNull(downloader);

            // Check if Build Tools is preview that the user is requesting the latest revision.
            if (buildToolInfo != null && !buildToolInfo.getRevision().equals(buildToolRevision)) {
                stdOutputProgress.logWarning("Build Tools revision " +
                        buildToolRevision +
                        " requested, but the latest available preview is " +
                        buildToolInfo.getRevision()+ ", which will be used to build.");
            }

            boolean isBuildToolInfoValid = buildToolInfo != null && buildToolInfo.isValid(logger);

            if (target == null || !isBuildToolInfoValid) {
                Map<RemotePackage, InstallResultType> installResults = new HashMap<>();
                RepoManager repoManager = mSdkHandler.getSdkManager(progress);
                checkNeedsCacheReset(repoManager, sdkLibData);
                repoManager.loadSynchronously(
                        RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, progress, downloader, settings);

                if (!isBuildToolInfoValid) {
                    installResults.putAll(
                            installBuildTools(
                                    buildToolRevision, repoManager, downloader, stdOutputProgress));
                }

                if (target == null) {
                    installResults.putAll(
                            installTarget(targetHash, repoManager, downloader, stdOutputProgress));
                }

                checkResults(installResults);

                repoManager.loadSynchronously(0, progress, null, null);

                buildToolInfo = mSdkHandler.getBuildToolInfo(buildToolRevision, progress);
                target = mSdkHandler.getAndroidTargetManager(progress)
                        .getTargetFromHashString(targetHash, progress);
            }
        }
        if (target == null) {
            throw new IllegalStateException(
                    "Failed to find target with hash string '"
                            + targetHash
                            + "' in: "
                            + mSdkLocation);
        }

        if (buildToolInfo == null) {
            throw new IllegalStateException(
                    "Failed to find Build Tools revision " + buildToolRevision.toString());
        } else if (!buildToolInfo.isValid(logger)) {
            throw new IllegalStateException(
                    "Installed Build Tools revision "
                            + buildToolRevision.toString()
                            + " is corrupted. Remove and install again using the SDK Manager.");
        }

        return new TargetInfo(target, buildToolInfo);
    }

    /**
     * Checks whether the {@link RepoManager} needs to have its local and remote packages cache
     * reset and invalidates them if they do.
     */
    private static void checkNeedsCacheReset(RepoManager repoManager, SdkLibData sdkLibData) {
        if (sdkLibData.needsCacheReset()) {
            repoManager.markInvalid();
            sdkLibData.setNeedsCacheReset(false);
        }
    }

    /**
     * Installs a compile target and its dependencies.
     *
     * @param targetHash hash of the target that needs to be installed.
     * @param repoManager used for interacting with repository packages.
     * @param downloader used to download packages.
     * @param progress a logger for messages.
     * @return a {@code Map<RemotePackages, InstallResultType>} of the compile target and its
     *         dependencies and their installation results.
     */
    @NonNull
    private Map<RemotePackage, InstallResultType> installTarget(
            @NonNull String targetHash,
            @NonNull RepoManager repoManager,
            @NonNull Downloader downloader,
            @NonNull ProgressIndicator progress) {
        Map<RemotePackage, InstallResultType> installResults = new HashMap<>();
        AndroidVersion targetVersion = AndroidTargetHash.getVersionFromHash(targetHash);

        if (targetVersion == null) {
            // For preview targets, like 'O', we can't do anything.
            return Collections.emptyMap();
        }

        String platformPath = DetailsTypes.getPlatformPath(targetVersion);

        UpdatablePackage platformPkg = repoManager.getPackages().getConsolidatedPkgs()
                .get(platformPath);

        // Malformed target hash
        if (platformPkg == null) {
            throw new IllegalStateException(
                    "Failed to find Platform SDK with path: "
                            + platformPath);
        }

        // Install platform sdk if it's not there.
        if (!platformPkg.hasLocal() && platformPkg.getRemote() != null) {
            installResults.putAll(
                    installRemotePackages(
                            ImmutableList.of(platformPkg.getRemote()),
                            repoManager,
                            downloader,
                            progress));
        }

        // Addon case
        if (!AndroidTargetHash.isPlatform(targetHash)) {
            RemotePackage addonPackage = null;
            for (RemotePackage p : repoManager.getPackages().getRemotePackages()
                    .values()) {
                if (p.getTypeDetails() instanceof DetailsTypes.AddonDetailsType) {
                    DetailsTypes.AddonDetailsType addonDetails
                            = (DetailsTypes.AddonDetailsType) p.getTypeDetails();
                    String addonHash = AndroidTargetHash.getAddonHashString(
                            addonDetails.getVendor().getDisplay(),
                            addonDetails.getTag().getDisplay(),
                            addonDetails.getAndroidVersion());
                    if (targetHash.equals(addonHash)) {
                        addonPackage = p;
                        break;
                    }
                }
            }

            // Malformed target hash
            if (addonPackage == null) {
                throw new IllegalStateException(
                        "Failed to find target with hash string " + targetHash);
            }

            installResults.putAll(
                    installRemotePackages(
                            ImmutableList.of(addonPackage), repoManager, downloader, progress));
        }

        return installResults;
    }

    /**
     * Installs a Build Tools revision.
     *
     * @param buildToolRevision the {@code Revision} of the build tools that need installation.
     * @param repoManager used for interacting with repository packages.
     * @param downloader used to download packages.
     * @param progress a logger for messages.
     * @return a {@code Map<RemotePackage, InstallResultType>} between the Build Tools packages and
     * its dependencies and their installation results.
     */
    private Map<RemotePackage, InstallResultType> installBuildTools(
            @NonNull Revision buildToolRevision,
            @NonNull RepoManager repoManager,
            @NonNull Downloader downloader,
            @NonNull ProgressIndicator progress) {
        String buildToolsPath = DetailsTypes.getBuildToolsPath(buildToolRevision);
        RemotePackage buildToolsPackage = repoManager
                .getPackages()
                .getRemotePackages()
                .get(buildToolsPath);

        if (buildToolsPackage == null) {
            throw new IllegalStateException("Failed to find Build Tools revision "
                    + buildToolRevision.toString());
        }

        if (!buildToolsPackage.getVersion().equals(buildToolRevision)) {
            progress.logWarning(
                    "Build Tools revision " +
                    buildToolRevision +
                    " requested, but the latest available preview is " +
                    buildToolsPackage.getVersion() + ", which will be installed.");
        }

        return installRemotePackages(
                ImmutableList.of(buildToolsPackage), repoManager, downloader, progress);
    }

    /**
     * Installs a list of {@code RemotePackage} and their dependent packages. Collects the install
     * results for each packages it tries to install.
     *
     * @param requestPackages the packages we want to install.
     * @param repoManager used for interacting with repository packages.
     * @param downloader used to download packages.
     * @param progress a progress logger for messages.
     * @return a {@code Map} of all the packages we tried to install and the install result.
     */
    private Map<RemotePackage, InstallResultType> installRemotePackages(
            @NonNull List<RemotePackage> requestPackages,
            @NonNull RepoManager repoManager,
            @NonNull Downloader downloader,
            @NonNull ProgressIndicator progress) {

        List<RemotePackage> remotePackages =
                InstallerUtil.computeRequiredPackages(
                        requestPackages, repoManager.getPackages(), progress);

        if (remotePackages == null) {
            return Maps.toMap(requestPackages, p -> InstallResultType.INSTALL_FAIL);
        }

        Map<RemotePackage, InstallResultType> installResults = new HashMap<>();
        for (RemotePackage p : remotePackages) {
            Path localPath = repoManager.getLocalPath();
            progress.logVerbose(
                    "Checking the license for package "
                            + p.getDisplayName()
                            + " in "
                            + localPath
                            + File.separator
                            + License.LICENSE_DIR);
            if (p.getLicense() != null
                    && !p.getLicense().checkAccepted(repoManager.getLocalPath())) {
                progress.logWarning("License for package " + p.getDisplayName() + " not accepted.");
                installResults.put(p, InstallResultType.LICENSE_FAIL);
            } else {
                progress.logVerbose("License for package " + p.getDisplayName() + " accepted.");
                Installer installer =
                        SdkInstallerUtil.findBestInstallerFactory(p, mSdkHandler)
                                .createInstaller(p, repoManager, downloader);
                if (installer.prepare(progress) && installer.complete(progress)) {
                    installResults.put(p, InstallResultType.SUCCESS);
                } else {
                    installResults.put(p, InstallResultType.INSTALL_FAIL);
                }
            }
        }
        return installResults;
    }

    @Override
    @NonNull
    public SdkInfo getSdkInfo(@NonNull ILogger logger) {
        init(logger);
        return mSdkInfo;
    }

    @Override
    @NonNull
    public ImmutableList<File> getRepositories() {
        return mRepositories;
    }

    private DefaultSdkLoader(
            @NonNull AndroidLocationsProvider androidLocationsProvider, @NonNull File sdkLocation) {
        this.androidLocationsProvider = androidLocationsProvider;
        mSdkLocation = sdkLocation;
        mRepositories = computeRepositories();
    }

    private synchronized void init(@NonNull ILogger logger) {
        if (mSdkHandler == null) {
            mSdkHandler =
                    AndroidSdkHandler.getInstance(androidLocationsProvider, mSdkLocation.toPath());
            ProgressIndicator progress = new LoggerProgressIndicatorWrapper(logger);
            mSdkHandler.getSdkManager(progress).reloadLocalIfNeeded(progress);

            File toolsFolder = new File(mSdkLocation, FD_TOOLS);
            File supportToolsFolder = new File(toolsFolder, FD_SUPPORT);
            File platformTools = new File(mSdkLocation, FD_PLATFORM_TOOLS);

            mSdkInfo = new SdkInfo(
                    new File(supportToolsFolder, FN_ANNOTATIONS_JAR),
                    new File(platformTools, FN_ADB));
        }
    }

    @NonNull
    private ImmutableList<File> computeRepositories() {
        return ImmutableList.of(
                new File(
                        mSdkLocation,
                        FD_EXTRAS + File.separator + "android" + File.separator + FD_M2_REPOSITORY),
                new File(
                        mSdkLocation,
                        FD_EXTRAS + File.separator + "google" + File.separator + FD_M2_REPOSITORY),
                new File(mSdkLocation, FD_EXTRAS + File.separator + FD_M2_REPOSITORY));
    }

    @Override
    @Nullable
    public File installSdkTool(@NonNull SdkLibData sdkLibData, @NonNull String packageId)
            throws LicenceNotAcceptedException, InstallFailedException {
        ProgressIndicator progress =
                new LoggerProgressIndicatorWrapper(new StdLogger(StdLogger.Level.WARNING));
        RepoManager repoManager = mSdkHandler.getSdkManager(progress);
        repoManager.loadSynchronously(0, progress, null, null);
        LocalPackage localSdkToolPackage =
                mSdkHandler.getLatestLocalPackageForPrefix(packageId, null, true, progress);
        if (localSdkToolPackage == null) {
            if (!sdkLibData.useSdkDownload()) {
                // If we are offline and we haven't found a local package for the SDK Tool
                // return null.
                return null;
            }
            checkNeedsCacheReset(repoManager, sdkLibData);
            repoManager.loadSynchronously(
                    RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
                    progress,
                    sdkLibData.getDownloader(),
                    sdkLibData.getSettings());

            RemotePackage sdkToolPackage =
                    mSdkHandler.getLatestRemotePackageForPrefix(packageId, null, true, progress);
            if (sdkToolPackage == null) {
                // If we haven't found the SDK Tool package remotely or locally return null.
                return null;
            } else {
                Map<RemotePackage, InstallResultType> installResults =
                        installRemotePackages(
                                ImmutableList.of(sdkToolPackage),
                                repoManager,
                                sdkLibData.getDownloader(),
                                getNewDownloadProgress());

                checkResults(installResults);
                repoManager.loadSynchronously(0, progress, null, null);
                localSdkToolPackage =
                        mSdkHandler.getLatestLocalPackageForPrefix(packageId, null, true, progress);
            }
        }
        // getLatestLocalPackageForPrefix above should have set it to non-null by now, but let's be
        // safe.
        return localSdkToolPackage != null
                ? mSdkHandler.getFileOp().toFile(localSdkToolPackage.getLocation())
                : null;
    }

    @Override
    @Nullable
    public File getLocalEmulator(@NonNull ILogger logger) {
        init(logger);
        ProgressIndicator progress =
                new LoggerProgressIndicatorWrapper(new StdLogger(StdLogger.Level.WARNING));
        RepoManager repoManager = mSdkHandler.getSdkManager(progress);
        repoManager.loadSynchronously(0, progress, null, null);
        LocalPackage localEmulatorPackage =
                mSdkHandler.getLatestLocalPackageForPrefix(
                        SdkConstants.FD_EMULATOR, null, false, progress);
        if (localEmulatorPackage == null) {
            // We want the developer to download/update the emulator manually. As this could have
            // unintended side-effects such as invalidating pre-existing avd snapshots.
            return null;
        }
        return mSdkHandler.getFileOp().toFile(localEmulatorPackage.getLocation());
    }

    /**
     * Checks if any of the installation attempts failed and prints out the appropriate error
     * message.
     *
     * @throws RuntimeException if some packages could not be installed.
     */
    private void checkResults(Map<RemotePackage, InstallResultType> installResults)
            throws LicenceNotAcceptedException, InstallFailedException {
        Function<InstallResultType, List<RemotePackage>> find =
                resultType ->
                        installResults
                                .entrySet()
                                .stream()
                                .filter(p -> p.getValue() == resultType)
                                .map(Map.Entry::getKey)
                                .collect(Collectors.toList());

        List<RemotePackage> unlicensedPackages = find.apply(InstallResultType.LICENSE_FAIL);
        if (!unlicensedPackages.isEmpty()) {
            throw new LicenceNotAcceptedException(mSdkLocation.toPath(), unlicensedPackages);
        }

        List<RemotePackage> failedPackages = find.apply(InstallResultType.INSTALL_FAIL);
        if (!failedPackages.isEmpty()) {
            throw new InstallFailedException(mSdkLocation.toPath(), failedPackages);
        }
    }

    private static ProgressIndicator getNewDownloadProgress() {
        return new LoggerProgressIndicatorWrapper(new StdLogger(StdLogger.Level.VERBOSE));
    }
}
