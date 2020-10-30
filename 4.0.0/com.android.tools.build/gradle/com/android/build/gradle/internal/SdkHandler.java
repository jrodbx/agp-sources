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

package com.android.build.gradle.internal;

import com.android.SdkConstants;
import com.android.Version;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.builder.core.ToolsRevisionUtils;
import com.android.builder.errors.IssueReporter;
import com.android.builder.errors.IssueReporter.Type;
import com.android.builder.sdk.DefaultSdkLoader;
import com.android.builder.sdk.InstallFailedException;
import com.android.builder.sdk.LicenceNotAcceptedException;
import com.android.builder.sdk.PlatformLoader;
import com.android.builder.sdk.SdkInfo;
import com.android.builder.sdk.SdkLibData;
import com.android.builder.sdk.SdkLoader;
import com.android.builder.sdk.TargetInfo;
import com.android.repository.Revision;
import com.android.repository.api.ConsoleProgressIndicator;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoPackage;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.utils.ILogger;
import com.android.utils.Pair;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Handles the all things SDK for the Gradle plugin. There is one instance per project, around
 * a singleton {@link com.android.builder.sdk.SdkLoader}.
 */
public class SdkHandler {
    @NonNull
    private final ILogger logger;

    @NonNull private final IssueReporter issueReporter;

    @NonNull private SdkLocationSourceSet sdkLocationSourceSet;
    @NonNull private SdkLibData sdkLibData = SdkLibData.dontDownload();
    private SdkLoader sdkLoader;

    public SdkHandler(
            @NonNull SdkLocationSourceSet sdkLocationSourceSet,
            @NonNull ILogger logger,
            @NonNull IssueReporter issueReporter) {
        this.sdkLocationSourceSet = sdkLocationSourceSet;
        this.logger = logger;
        this.issueReporter = issueReporter;
    }

    /**
     * Initialize the SDK target, build tools and library requests.
     *
     * @return true on success, false on failure after reporting errors via the issue reporter,
     *     which throws exceptions when not in sync mode.
     */
    @Nullable
    public Pair<SdkInfo, TargetInfo> initTarget(
            @NonNull String targetHash, @NonNull Revision buildToolRevision) {
        Preconditions.checkNotNull(targetHash, "android.compileSdkVersion is missing!");
        Preconditions.checkNotNull(buildToolRevision, "android.buildToolsVersion is missing!");

        SdkLoader sdkLoader = getSdkLoader();
        if (sdkLoader == null) {
            // SdkLoader couldn't be constructed, probably because we're missing the sdk dir configuration.
            // If so, getSdkLoader() already reported to the issueReporter.
            return null;
        }

        if (buildToolRevision.compareTo(ToolsRevisionUtils.MIN_BUILD_TOOLS_REV) < 0) {
            issueReporter.reportWarning(
                    Type.BUILD_TOOLS_TOO_LOW,
                    String.format(
                            "The specified Android SDK Build Tools version (%1$s) is "
                                    + "ignored, as it is below the minimum supported "
                                    + "version (%2$s) for Android Gradle Plugin %3$s.\n"
                                    + "Android SDK Build Tools %4$s will be used.\n"
                                    + "To suppress this warning, "
                                    + "remove \"buildToolsVersion '%1$s'\" "
                                    + "from your build.gradle file, as each "
                                    + "version of the Android Gradle Plugin now has a "
                                    + "default version of the build tools.",
                            buildToolRevision,
                            ToolsRevisionUtils.MIN_BUILD_TOOLS_REV,
                            Version.ANDROID_GRADLE_PLUGIN_VERSION,
                            ToolsRevisionUtils.DEFAULT_BUILD_TOOLS_REVISION),
                    ToolsRevisionUtils.DEFAULT_BUILD_TOOLS_REVISION.toString());
            buildToolRevision = ToolsRevisionUtils.DEFAULT_BUILD_TOOLS_REVISION;
        }

        Stopwatch stopwatch = Stopwatch.createStarted();
        SdkInfo sdkInfo = sdkLoader.getSdkInfo(logger);

        TargetInfo targetInfo;
        try {
            targetInfo = sdkLoader.getTargetInfo(targetHash, buildToolRevision, logger, sdkLibData);
        } catch (LicenceNotAcceptedException e) {
            issueReporter.reportError(
                    IssueReporter.Type.MISSING_SDK_PACKAGE,
                    e.getMessage(),
                    e.getAffectedPackages()
                            .stream()
                            .map(RepoPackage::getPath)
                            .collect(Collectors.joining(" ")));
            return null;
        } catch (InstallFailedException e) {
            issueReporter.reportError(
                    IssueReporter.Type.MISSING_SDK_PACKAGE,
                    e.getMessage(),
                    e.getAffectedPackages()
                            .stream()
                            .map(RepoPackage::getPath)
                            .collect(Collectors.joining(" ")));
            return null;
        } catch (IllegalStateException e) {
            issueReporter.reportError(IssueReporter.Type.MISSING_SDK_PACKAGE, e);
            return null;
        }

        logger.verbose("SDK initialized in %1$d ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
        return Pair.of(sdkInfo, targetInfo);
    }

    /**
     * Try and make sure the platform tools are present.
     *
     * <p>Reports an evaluation warning if the platform tools package is not present and could not
     * be automatically downloaded and installed.
     *
     * @return true if some download operation was attempted.
     */
    public boolean ensurePlatformToolsIsInstalledWarnOnFailure() {
        // Check if platform-tools are installed. We check here because realistically, all projects
        // should have platform-tools in order to build.
        ProgressIndicator progress = new ConsoleProgressIndicator();
        AndroidSdkHandler sdk =
                AndroidSdkHandler.getInstance(
                        SdkLocator.getSdkLocation(sdkLocationSourceSet, issueReporter)
                                .getDirectory());
        LocalPackage platformToolsPackage =
                sdk.getLatestLocalPackageForPrefix(
                        SdkConstants.FD_PLATFORM_TOOLS, null, true, progress);
        if (platformToolsPackage == null && sdkLoader != null) {
            if (sdkLibData.useSdkDownload()) {
                try {
                    sdkLoader.installSdkTool(sdkLibData, SdkConstants.FD_PLATFORM_TOOLS);
                    return true;
                } catch (LicenceNotAcceptedException e) {
                    issueReporter.reportWarning(
                            IssueReporter.Type.MISSING_SDK_PACKAGE,
                            SdkConstants.FD_PLATFORM_TOOLS
                                    + " package is not installed. Please accept the installation licence to continue",
                            SdkConstants.FD_PLATFORM_TOOLS);
                } catch (InstallFailedException e) {
                    issueReporter.reportWarning(
                            IssueReporter.Type.MISSING_SDK_PACKAGE,
                            SdkConstants.FD_PLATFORM_TOOLS
                                    + " package is not installed, and automatic installation failed.",
                            SdkConstants.FD_PLATFORM_TOOLS);
                }
            } else {
                issueReporter.reportWarning(
                        Type.MISSING_SDK_PACKAGE,
                        SdkConstants.FD_PLATFORM_TOOLS + " package is not installed.",
                        SdkConstants.FD_PLATFORM_TOOLS);
            }
        }
        return false;
    }

    @Nullable
    private synchronized SdkLoader getSdkLoader() {
        if (sdkLoader == null) {
            SdkLocation sdkLocation =
                    SdkLocator.getSdkLocation(sdkLocationSourceSet, issueReporter);

            switch (sdkLocation.getType()) {
                case TEST: // Fallthrough
                case REGULAR:
                    sdkLoader = DefaultSdkLoader.getLoader(sdkLocation.getDirectory());
                    break;
                case PLATFORM:
                    sdkLoader = PlatformLoader.getLoader(sdkLocation.getDirectory());
                    break;
                case MISSING:
                    // This error should have been reported earlier when SdkLocation was created
            }
        }

        return sdkLoader;
    }

    public synchronized void unload() {
        if (sdkLoader != null) {
            SdkLocation sdkLocation =
                    SdkLocator.getSdkLocation(sdkLocationSourceSet, issueReporter);

            switch (sdkLocation.getType()) {
                case TEST: // Intended falloff
                case REGULAR:
                    DefaultSdkLoader.unload();
                    break;
                case PLATFORM:
                    PlatformLoader.unload();
                    break;
                case MISSING:
                    // Do nothing
            }
            sdkLoader = null;
        }
    }

    public void setSdkLibData(@NonNull SdkLibData sdkLibData) {
        this.sdkLibData = sdkLibData;
    }

    /** Installs the NDK. */
    public void installNdk(@NonNull NdkHandler ndkHandler) {
        if (!sdkLibData.useSdkDownload()) {
            return;
        }
        SdkLoader loader = getSdkLoader();
        if (loader == null) {
            // If the loader is null it means we couldn't set-up one based on a local SDK.
            // So we can't even try to installPackage something. This set up error will be reported
            // during SdkHandler set-up.
            return;
        }

        loader.getSdkInfo(logger); // We need to make sure the loader was initialized.
        ndkHandler.installFromSdk(loader, sdkLibData);
    }

    /** Installs CMake. */
    public void installCMake(String version) {
        if (!sdkLibData.useSdkDownload()) {
            return;
        }
        try {
            SdkLoader loader = getSdkLoader();
            if (loader == null) {
                // If the loader is null it means we couldn't set-up one based on a local SDK.
                // So we can't even try to installPackage something. This set up error will be reported
                // during SdkHandler set-up.
                return;
            }
            loader.getSdkInfo(logger); // We need to make sure the loader was initialized.
            loader.installSdkTool(sdkLibData, SdkConstants.FD_CMAKE + ";" + version);
        } catch (LicenceNotAcceptedException | InstallFailedException e) {
            throw new RuntimeException(e);
        }
    }
}
