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
package com.android.instantapp.provision;

import static com.android.instantapp.provision.ProvisionRunner.executeShellCommand;

import com.android.annotations.NonNull;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.InstallException;
import com.android.instantapp.sdk.Metadata;
import com.google.common.annotations.VisibleForTesting;
import java.util.LinkedList;
import java.util.List;

/** Installer for the necessary APKs in the provision process. */
class ProvisionApksInstaller {
    @NonNull private final LinkedList<Metadata.ApkInfo> myApkInfos;

    ProvisionApksInstaller(@NonNull List<Metadata.ApkInfo> apkInfos) {
        myApkInfos = new LinkedList<>();

        // DevMan must be installed first
        for (Metadata.ApkInfo apkInfo : apkInfos) {
            if (apkInfo.getPkgName().compareTo("com.google.android.instantapps.devman") == 0) {
                myApkInfos.addFirst(apkInfo);
            } else {
                myApkInfos.addLast(apkInfo);
            }
        }
    }

    private static long getVersion(@NonNull IDevice device, @NonNull String pkgName)
            throws ProvisionException {
        String output = executeShellCommand(device, "dumpsys package " + pkgName, false, 500);
        return parseOutput(output);
    }

    private static long parseOutput(@NonNull String output) {
        int index = output.indexOf("versionCode");
        if (index == -1) {
            return 0;
        }

        int begIndex = output.indexOf('=', index) + 1;
        int endIndex = output.indexOf(' ', begIndex);
        endIndex = endIndex == -1 ? output.indexOf('\n', begIndex) : endIndex;

        if (endIndex == -1) {
            return 0;
        }

        String versionCode = output.substring(begIndex, endIndex);
        return Long.parseLong(versionCode);
    }

    void installAll(
            @NonNull IDevice device,
            @NonNull ProvisionRunner.ProvisionState provisionState,
            @NonNull ProvisionListener listener)
            throws ProvisionException {
        boolean firstGms = true;
        int currentInstalling = 0;
        for (Metadata.ApkInfo apkInfo : myApkInfos) {
            listener.setProgress(8.0 / 20 + currentInstalling * 11.0 / (20 * myApkInfos.size()));
            long installedVer = getVersion(device, apkInfo.getPkgName());
            long installingVer = apkInfo.getVersionCode();

            // Normalize SDK version number so we are comparing the actual version, before the
            // artificial version bump. See b/70919102.
            if (apkInfo.getPkgName().compareTo("com.google.android.instantapps.supervisor") == 0) {
                final long sdkVersionCodeRange = 100_000_000L;
                installingVer =
                        installingVer > sdkVersionCodeRange
                                ? installingVer - sdkVersionCodeRange
                                : installingVer;
                installedVer =
                        installedVer > sdkVersionCodeRange
                                ? installedVer - sdkVersionCodeRange
                                : installedVer;
            }

            if (currentInstalling > provisionState.lastInstalled && installedVer < installingVer) {
                try {
                    listener.printMessage("Installing package " + apkInfo.getPkgName());
                    listener.logMessage("Installing apk " + apkInfo.getApk(), null);
                    device.installPackage(apkInfo.getApk().getAbsolutePath(), true, "-d");
                    provisionState.lastInstalled = currentInstalling;
                } catch (InstallException e) {
                    // For the moment we have two different GmsCore apks. If at least one succeeds it's fine.
                    if (firstGms && apkInfo.getPkgName().compareTo("com.google.android.gms") == 0) {
                        firstGms = false;
                    } else {
                        throw new ProvisionException(
                                ProvisionException.ErrorType.INSTALL_FAILED,
                                "APK " + apkInfo.getApk() + " could not be installed.",
                                e);
                    }
                }
            }
            currentInstalling++;
            if (listener.isCancelled()) {
                throw new ProvisionException(ProvisionException.ErrorType.CANCELLED);
            }
        }
    }

    @VisibleForTesting
    // Only for test
    List<Metadata.ApkInfo> getApks() {
        return myApkInfos;
    }
}
