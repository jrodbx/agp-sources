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
package com.android.ddmlib;

import com.android.annotations.NonNull;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RemoteSplitApkInstaller extends SplitApkInstallerBase {
    private static final String LOG_TAG = "RemoteSplitApkInstaller";

    @NonNull private final List<String> mRemoteApkPaths;

    private RemoteSplitApkInstaller(
            @NonNull IDevice device, @NonNull List<String> remoteApks, @NonNull String options) {
        super(device, options);
        this.mRemoteApkPaths = remoteApks;
    }

    /**
     * Installs an Android application made of several APK files sitting locally on the device
     *
     * @param timeout installation timeout
     * @param timeoutUnit {@link TimeUnit} corresponding to the timeout parameter
     * @throws InstallException if the installation fails.
     */
    public void install(long timeout, @NonNull TimeUnit unit) throws InstallException {
        try {
            // create a installation session.
            String sessionId = createMultiInstallSession(getOptions(), timeout, unit);

            // install-write each APK with the same sessionId.
            boolean allWriteSucceeded = true;
            for (String apkPath : mRemoteApkPaths) {
                Log.d(
                        LOG_TAG,
                        String.format("Add apk %s to install session %s", apkPath, sessionId));
                allWriteSucceeded = writeRemoteApk(sessionId, apkPath, timeout, unit);
                if (!allWriteSucceeded) {
                    Log.e(
                            LOG_TAG,
                            String.format(
                                    "Failed to write install session %s with %s",
                                    sessionId, apkPath));
                    break;
                }
            }

            // if all files were upload successfully, commit otherwise abandon the installation.
            if (!allWriteSucceeded) {
                installAbandon(sessionId, timeout, unit);
                throw new InstallException("Failed to install-write all apks");
            } else {
                installCommit(sessionId, timeout, unit);
                Log.d(LOG_TAG, "Successfully install apks: " + mRemoteApkPaths.toString());
            }
        } catch (InstallException e) {
            throw e;
        } catch (Exception e) {
            throw new InstallException(e);
        }
    }

    protected boolean writeRemoteApk(
            @NonNull String sessionId,
            @NonNull String filePath,
            long timeout,
            @NonNull TimeUnit unit) {
        String baseName =
                filePath.lastIndexOf('/') != -1
                        ? filePath.substring(filePath.lastIndexOf('/'), filePath.length())
                        : filePath;

        baseName = UNSAFE_PM_INSTALL_SESSION_SPLIT_NAME_CHARS.replaceFrom(baseName, '_');

        String command =
                String.format(
                        getPrefix() + " install-write %s %s %s", sessionId, baseName, filePath);

        Log.d(LOG_TAG, String.format("Executing : %s", command));
        try {
            InstallReceiver receiver = new InstallReceiver();
            getDevice().executeShellCommand(command, receiver, timeout, unit);
            if (receiver.isSuccessfullyCompleted()) {
                Log.d(
                        LOG_TAG,
                        String.format(
                                "Successfully add %s to install session %s", filePath, sessionId));
            } else {
                if (receiver.getErrorMessage() != null) {
                    Log.e(
                            LOG_TAG,
                            String.format(
                                    "Error install-write %s to session %s by command %s: %s",
                                    filePath, sessionId, command, receiver.getErrorMessage()));
                } else {
                    Log.e(
                            LOG_TAG,
                            String.format(
                                    "Failed to install-write session %s with %s by command %s",
                                    sessionId, filePath, command));
                }
            }
            return receiver.isSuccessfullyCompleted();
        } catch (Exception e) {
            Log.e(LOG_TAG, String.format("%s failed with error %s", command, e));
            return false;
        }
    }

    private static void validateArguments(@NonNull IDevice device, @NonNull List<String> apks) {
        validateApiLevel(device);

        if (apks.isEmpty()) {
            throw new IllegalArgumentException(
                    "List of APKs is empty: the main APK must be specified.");
        }
    }

    /**
     * Returns a {@link SplitApkInstaller} for the given list of APKs that are already uploaded to
     * the given device.
     *
     * @param device the device to install APK, must include at least the main APK.
     * @param applicationId the application id that to install new APKs with.
     * @param apks list of remote APKs.
     * @param reInstall whether to enable reinstall option.
     * @param pmOptions list of install options.
     */
    public static RemoteSplitApkInstaller create(
            @NonNull IDevice device,
            @NonNull List<String> remoteApks,
            boolean reInstall,
            @NonNull List<String> installOptions) {
        validateArguments(device, remoteApks);
        return new RemoteSplitApkInstaller(
                device, remoteApks, getOptions(reInstall, installOptions));
    }

    /**
     * Returns a {@link SplitApkInstaller} to install given list of APKs that are already uploaded
     * to the given device to an existing application on the device.
     *
     * @param device the device to install APK.
     * @param applicationId the application id of the existing application that to install new APKs
     *     with.
     * @param apks list of remote APKs.
     * @param reInstall whether to enable reinstall option.
     * @param pmOptions list of install options.
     */
    public static RemoteSplitApkInstaller create(
            @NonNull IDevice device,
            @NonNull String applicationId,
            @NonNull List<String> remoteApks,
            boolean reInstall,
            @NonNull List<String> installOptions) {
        validateArguments(device, remoteApks);
        return new RemoteSplitApkInstaller(
                device, remoteApks, getOptions(reInstall, true, applicationId, installOptions));
    }
}
