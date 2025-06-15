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

    // To introduce install over abbExec smoothly, we protect it with this flag. This can safely be
    // deleted once we are confident it did not break anything.
    private static boolean abbExecAllowed = true;

    @NonNull protected final IDevice mDevice;
    @NonNull private final String mOptions;
    @NonNull private final String mPrefix;

    // We need two services. The most recent devices uses the same ABB_EXEC but to remain backward
    // compatible we must mimic how install used to take place with SHELL for create, commit and
    // EXEC for write. Once we fix unify FakeAdbServer pm handling, we can move to having only
    // once service for all four types of queries.
    @NonNull private final AdbHelper.AdbService mService;
    @NonNull private final AdbHelper.AdbService mServiceWrite;

    protected SplitApkInstallerBase(@NonNull IDevice device, @NonNull String options) {
        this.mDevice = device;
        this.mOptions = options;

        // Multiple install strategies are available, offering increasing speed, depending on the
        // device capabilities.
        //
        // API <24, we must use "pm" (which connects to "package" binder service) over EXEC adb
        // service. This is the slowest option.
        // API >= 24, we use "cmd" (specifying "package" binder service ) over EXEC adb
        // service.
        // If available (likely API >=30 ), we use "package" binder service over ABB_EXEC
        // adb service. This is the fastest option to date.
        //
        // Note that two completely different services are involved :
        //    - ADB services (see services.txt for more details).
        //    - Android Binder services.

        if (mDevice.supportsFeature(IDevice.Feature.ABB_EXEC) && abbExecAllowed) {
            this.mPrefix = "package";
            this.mService = AdbHelper.AdbService.ABB_EXEC;
            this.mServiceWrite = AdbHelper.AdbService.ABB_EXEC;
        } else if (supportsCmd(device)) {
            this.mPrefix = "cmd package";
            this.mService = AdbHelper.AdbService.SHELL;
            this.mServiceWrite = AdbHelper.AdbService.EXEC;
        } else {
            this.mPrefix = "pm";
            this.mService = AdbHelper.AdbService.SHELL;
            this.mServiceWrite = AdbHelper.AdbService.EXEC;
        }

        Log.i(
                LOG_TAG,
                String.format(
                        "Install-Write Strategy '%s' over '%s'", mPrefix, mServiceWrite.name()));
    }

    private static boolean supportsCmd(IDevice device) {
        return device.getVersion()
                .isGreaterOrEqualThan(AndroidVersion.BINDER_CMD_AVAILABLE.getApiLevel());
    }

    static void setAbbExecAllowed(boolean allowed) {
        abbExecAllowed = allowed;
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
        mDevice.executeRemoteCommand(
          mService,
                cmd,
                receiver,
                0L,
                timeout,
                unit,
                null);
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

    // Because installation will invoke cmd or pm on the shell, we need to sanitize the upload name
    // we will use to not break the shell parser. Characters such as ' ', '(', ')', '+' and so on
    // will problematic.
    //
    // We must also sanitiaze in a way that does not break splits (differing only by digits) and
    // baseline profile (the .apk name must match the .dm name).
    static String sanitizeApkFilename(@NonNull String filename) {
        return UNSAFE_SHELL_SPLIT_NAME_CHARS.replaceFrom(filename, '_');
    }

    private static final CharMatcher UNSAFE_SHELL_SPLIT_NAME_CHARS =
            CharMatcher.inRange('a', 'z')
                    .or(CharMatcher.inRange('A', 'Z'))
                    .or(CharMatcher.inRange('0', '9'))
                    .or(CharMatcher.anyOf("_-."))
                    .negate();

    protected void installCommit(@NonNull String sessionId, long timeout, @NonNull TimeUnit unit)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
                    IOException, InstallException {
        String command = mPrefix + " install-commit " + sessionId;
        InstallReceiver receiver = new InstallReceiver();
        mDevice.executeRemoteCommand(
          mService,
                command,
                receiver,
                0L,
                timeout,
                unit,
                null);
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
        mDevice.executeRemoteCommand(
          mService,
                command,
                receiver,
                0L,
                timeout,
                unit,
                null);
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
    protected AdbHelper.AdbService getService() {
        return mService;
    }

    @NonNull
    protected AdbHelper.AdbService getServiceWrite() {
        return mServiceWrite;
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
