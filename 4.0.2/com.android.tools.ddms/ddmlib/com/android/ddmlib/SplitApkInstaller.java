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
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SplitApkInstaller extends SplitApkInstallerBase {
    private static final String LOG_TAG = "SplitApkInstaller";

    @NonNull private final List<File> mApks;

    private SplitApkInstaller(@NonNull IDevice device, @NonNull List<File> apks,
            @NonNull String options) {
        super(device, options);
        this.mApks = apks;
    }

    /**
     * Installs an Android application made of several APK files by streaming from files on host
     *
     * @param timeout installation timeout
     * @param timeoutUnit {@link TimeUnit} corresponding to the timeout parameter
     * @return {@link InstallMetrics} metrics for time elapsed during this installation
     * @throws InstallException if the installation fails.
     */
    public InstallMetrics install(long timeout, @NonNull TimeUnit unit) throws InstallException {
        // Installing multiple APK's is perfomed as follows:
        //  # First we create a install session passing in the total size of all APKs
        //      $ [pm|cmd package] install-create -S <total_size>
        //      Success: [integer-session-id]   # error if session-id < 0
        //  # Then for each APK, we perform the following. A unique id per APK is generated
        //  # as <index>_<name>, the - at the end means that the APK is streamed via stdin
        //      $ [pm|cmd package] install-write -S <session-id> <per_apk_unique_id> -
        //  # Finally, we close the session
        //      $ [pm|cmd package] install-commit <session-id>  (or)
        //      $ [pm|cmd package] install-abandon <session-id>

        try {
            // create a installation session.
            long totalFileSize = 0L;
            for (File apkFile : mApks) {
                totalFileSize += apkFile.length();
            }
            String option = String.format("-S %d", totalFileSize);
            if (getOptions() != null) {
                option = getOptions() + " " + option;
            }
            String sessionId = createMultiInstallSession(option, timeout, unit);

            // now upload each APK in turn.
            int index = 0;
            boolean allUploadSucceeded = true;

            long uploadStartNs = System.nanoTime();
            while (allUploadSucceeded && index < mApks.size()) {
                allUploadSucceeded = uploadApk(sessionId, mApks.get(index), index++, timeout, unit);
            }

            // if all files were upload successfully, commit otherwise abandon the installation.
            long uploadFinishNs = System.nanoTime();
            if (!allUploadSucceeded) {
                installAbandon(sessionId, timeout, unit);
                throw new InstallException("Failed to install-write all apks");
            } else {
                installCommit(sessionId, timeout, unit);
                Log.d(LOG_TAG, "Successfully install apks: " + mApks.toString());
            }

            return new InstallMetrics(
                    uploadStartNs, uploadFinishNs, uploadFinishNs, System.nanoTime());
        } catch (InstallException e) {
            throw e;
        } catch (Exception e) {
            throw new InstallException(e);
        }
    }

    protected boolean uploadApk(
            @NonNull String sessionId,
            @NonNull File fileToUpload,
            int uniqueId,
            long timeout,
            @NonNull TimeUnit unit) {
        Log.i(
                LOG_TAG,
                String.format("Uploading APK %s to session %s", fileToUpload.getPath(), sessionId));
        if (!fileToUpload.exists()) {
            Log.e(LOG_TAG, String.format("File not found: %1$s", fileToUpload.getPath()));
            return false;
        }
        if (fileToUpload.isDirectory()) {
            Log.e(
                    LOG_TAG,
                    String.format(
                            "Directory upload not supported: %s", fileToUpload.getAbsolutePath()));
            return false;
        }
        String baseName =
                fileToUpload.getName().lastIndexOf('.') != -1
                        ? fileToUpload
                                .getName()
                                .substring(0, fileToUpload.getName().lastIndexOf('.'))
                        : fileToUpload.getName();

        baseName = UNSAFE_PM_INSTALL_SESSION_SPLIT_NAME_CHARS.replaceFrom(baseName, '_');

        String command =
                String.format(
                        getPrefix() + " install-write -S %d %s %d_%s -",
                        fileToUpload.length(),
                        sessionId,
                        uniqueId,
                        baseName);

        Log.d(LOG_TAG, String.format("Executing : %1$s", command));
        InputStream inputStream = null;
        try {
            inputStream = new BufferedInputStream(new FileInputStream(fileToUpload));
            InstallReceiver receiver = new InstallReceiver();
            AdbHelper.executeRemoteCommand(
                    AndroidDebugBridge.getSocketAddress(),
                    AdbHelper.AdbService.EXEC,
                    command,
                    getDevice(),
                    receiver,
                    timeout,
                    unit,
                    inputStream);
            if (receiver.isSuccessfullyCompleted()) {
                Log.d(LOG_TAG, String.format("Successfully uploaded %1$s", fileToUpload.getName()));
            } else {
                Log.e(
                        LOG_TAG,
                        String.format(
                                "Error while uploading %1$s : %2$s",
                                fileToUpload.getName(), receiver.getErrorMessage()));
            }
            return receiver.isSuccessfullyCompleted();
        } catch (Exception e) {
            Log.e(sessionId, e);
            return false;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    Log.e(sessionId, e);
                }
            }
        }
    }

    private static void validateArguments(@NonNull IDevice device, @NonNull List<File> apks) {
        validateApiLevel(device);

        if (apks.isEmpty()) {
            throw new IllegalArgumentException(
                    "List of APKs is empty: the main APK must be specified.");
        }

        for (File apk : apks) {
            if (!apk.isFile()) {
                throw new IllegalArgumentException("Invalid File: " + apk.getPath());
            }
        }
    }

    /**
     * Returns a {@link SplitApkInstaller} for the given list of APK files from host to the given
     * device.
     *
     * @param device the device to install APK, must include at least the main APK.
     * @param apks list of APK files.
     * @param reInstall whether to enable reinstall option.
     * @param options list of install options.
     */
    public static SplitApkInstaller create(
            @NonNull IDevice device,
            @NonNull List<File> apks,
            boolean reInstall,
            @NonNull List<String> installOptions) {
        validateArguments(device, apks);
        return new SplitApkInstaller(device, apks, getOptions(reInstall, installOptions));
    }

    /**
     * Returns a {@link SplitApkInstaller} to install given list of APK files from host to an
     * existing application on the given device.
     *
     * @param device the device to install APK.
     * @param applicationId the application id of the existing application that to install new APKs
     *     with.
     * @param apks list of APK files.
     * @param reInstall whether to enable reinstall option.
     * @param pmOptions list of install options.
     */
    public static SplitApkInstaller create(
            @NonNull IDevice device,
            @NonNull String applicationId,
            @NonNull List<File> apks,
            boolean reInstall,
            @NonNull List<String> installOptions) {
        validateArguments(device, apks);
        return new SplitApkInstaller(
                device, apks, getOptions(reInstall, true, applicationId, installOptions));
    }
}
