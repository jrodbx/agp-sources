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
import com.android.sdklib.AndroidVersion;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public abstract class SplitApkInstallerBase {
    private static final String LOG_TAG = "SplitApkInstallerBase";

    @NonNull private final IDevice mDevice;
    @NonNull private final String mOptions;
    @NonNull private final String mPrefix;

    protected SplitApkInstallerBase(@NonNull IDevice device, @NonNull String options) {
        this.mDevice = device;
        this.mOptions = options;
        this.mPrefix =
                mDevice.getVersion()
                                .isGreaterOrEqualThan(
                                        AndroidVersion.BINDER_CMD_AVAILABLE.getApiLevel())
                        ? "cmd package"
                        : "pm";
    }

    protected String createMultiInstallSession(
            @NonNull String options, long timeout, @NonNull TimeUnit unit)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
                    IOException, InstallException {

        InstallCreateReceiver receiver = new InstallCreateReceiver();
        String cmd = mPrefix + " install-create";
        if (!options.trim().isEmpty()) {
            cmd = cmd + " " + options;
        }
        mDevice.executeShellCommand(cmd, receiver, timeout, unit);
        String sessionId = receiver.getSessionId();
        if (sessionId == null) {
            String message = String.format("'%s'", cmd);
            if (receiver.getErrorMessage() != null) {
                message =
                        String.format("%s returns error '%s'", message, receiver.getErrorMessage());
            } else if (receiver.getSuccessMessage() != null) {
                message =
                        String.format(
                                "%s returns '%s' without session ID",
                                message, receiver.getSuccessMessage());
            } else {
                message = String.format("Failed to create install session with %s", message);
            }
            Log.e(LOG_TAG, message);
            throw new InstallException(message);
        }
        Log.i(
                LOG_TAG,
                String.format("Created install session %s with options %s", sessionId, options));
        return sessionId;
    }

    protected static final CharMatcher UNSAFE_PM_INSTALL_SESSION_SPLIT_NAME_CHARS =
            CharMatcher.inRange('a', 'z')
                    .or(CharMatcher.inRange('A', 'Z'))
                    .or(CharMatcher.anyOf("_-"))
                    .negate();

    protected void installCommit(@NonNull String sessionId, long timeout, @NonNull TimeUnit unit)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
                    IOException, InstallException {
        String command = mPrefix + " install-commit " + sessionId;
        InstallReceiver receiver = new InstallReceiver();
        mDevice.executeShellCommand(command, receiver, timeout, unit);
        if (!receiver.isSuccessfullyCompleted()) {
            String message =
                    String.format(
                            "Failed to commit install session %s with command %s.",
                            sessionId, command);
            if (receiver.getErrorMessage() != null) {
                message += String.format(" Error: %s", receiver.getErrorMessage());
            }
            Log.e(LOG_TAG, message);
            throw new InstallException(message, receiver.getErrorCode());
        }
    }

    protected void installAbandon(@NonNull String sessionId, long timeout, @NonNull TimeUnit unit)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
                    IOException, InstallException {
        String command = mPrefix + " install-abandon " + sessionId;
        InstallReceiver receiver = new InstallReceiver();
        mDevice.executeShellCommand(command, receiver, timeout, unit);
        if (!receiver.isSuccessfullyCompleted()) {
            Log.e(LOG_TAG, String.format("Failed to abandon install session %s", sessionId));
        }
    }

    @NonNull
    protected IDevice getDevice() {
        return mDevice;
    }

    @NonNull
    protected String getPrefix() {
        return mPrefix;
    }

    @NonNull
    protected String getOptions() {
        return mOptions;
    }

    @NonNull
    protected static String getOptions(boolean reInstall, @NonNull List<String> installOptions) {
        return getOptions(reInstall, false, null, installOptions);
    }

    @NonNull
    protected static String getOptions(
            boolean reInstall,
            boolean partialInstall,
            String applicationId,
            @NonNull List<String> installOptions) {
        StringBuilder sb = new StringBuilder();

        if (reInstall) {
            sb.append("-r");
        }

        if (partialInstall) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            if (applicationId == null) {
                throw new IllegalArgumentException(
                        "Cannot do a partial install without knowing the application id");
            }

            sb.append("-p ");
            sb.append(applicationId);
        }

        if (!installOptions.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(Joiner.on(" ").join(installOptions));
        }

        return sb.toString();
    }

    protected static void validateApiLevel(@NonNull IDevice device) {
        int apiWithSplitApk = AndroidVersion.ALLOW_SPLIT_APK_INSTALLATION.getApiLevel();
        if (!device.getVersion().isGreaterOrEqualThan(apiWithSplitApk)) {
            throw new IllegalArgumentException(
                    String.format(
                            "Device %s API level=%d. Cannot install split APKs with API level < %d",
                            device.getSerialNumber(),
                            device.getVersion().getApiLevel(),
                            apiWithSplitApk));
        }
    }
}
