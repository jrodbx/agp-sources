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
package com.android.instantapp.run;

import static com.android.instantapp.utils.DeviceUtils.isPostO;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.InstallException;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Tries to install / side load instant apps to the device. */
public class InstantAppSideLoader {
    @NonNull private final String myPkgName;
    @NonNull private final Installer myInstaller;

    @NonNull private final RunListener myListener;

    private long myShellTimeout = 500;

    public InstantAppSideLoader(@NonNull String pkgName, @NonNull File bundle) {
        this(pkgName, bundle, new RunListener.NullListener());
    }

    public InstantAppSideLoader(@NonNull String pkgName, @NonNull List<File> apks) {
        this(pkgName, apks, new RunListener.NullListener());
    }

    /**
     * Must be used for PreO.
     *
     * @param pkgName package name of the app, will be used to uninstall non instant app version.
     * @param bundle the zip file with the apks.
     */
    public InstantAppSideLoader(
            @NonNull String pkgName, @NonNull File bundle, @NonNull RunListener listener) {
        myPkgName = pkgName;
        myInstaller = new PreOSideLoader(bundle, listener);
        myListener = listener;
    }

    /**
     * Must be used for PostO.
     *
     * @param pkgName package name of the app, will be used to uninstall non instant app version.
     * @param apks a list with the apks to be installed.
     */
    public InstantAppSideLoader(
            @NonNull String pkgName, @NonNull List<File> apks, @NonNull RunListener listener) {
        myPkgName = pkgName;
        myInstaller = new PostOInstaller(apks, listener);
        myListener = listener;
    }

    interface Installer {
        void install(@NonNull IDevice device) throws InstantAppRunException;
    }

    public void install(@NonNull IDevice device) throws InstantAppRunException {
        install(device, 1);
    }

    public void install(@NonNull IDevice device, int retries) throws InstantAppRunException {
        boolean success = false;
        while (!success) {
            try {
                tryToInstall(device);
                success = true;
            } catch (InstantAppRunException e) {
                if (retries > 0 && prepareRetry(e.getErrorType())) {
                    retries--;
                    myListener.logMessage("Retrying to side load instant app.", e);
                } else {
                    myListener.printMessage("Side loading instant app failed: " + e.getMessage());
                    myListener.logMessage("Side loading instant app failed.", e);
                    throw e;
                }
            }
        }
    }

    private void tryToInstall(@NonNull IDevice device) throws InstantAppRunException {
        myListener.setProgress(0);
        if (isPostO(device)) {
            myListener.logMessage("PostO device.", null);
            assert myInstaller instanceof PostOInstaller;
        } else {
            myListener.logMessage("PreO device.", null);
            assert myInstaller instanceof PreOSideLoader;
        }
        if (!myListener.isCancelled()) {
            uninstallAppIfInstalled(device);
        }
        myListener.setProgress(1.0 / 5);
        if (!myListener.isCancelled()) {
            myListener.printMessage("Side loading instant app.");
            myListener.logMessage("Side loading instant app.", null);
            myInstaller.install(device);
        }
        myListener.setProgress(1);
    }

    /**
     * Analyses if it's worth it to retry depending on the error and makes the necessary
     * modifications for retrying.
     *
     * @param errorType the type of error resulted from the last provision.
     * @return if it's worth it to retry.
     */
    private boolean prepareRetry(InstantAppRunException.ErrorType errorType) {
        switch (errorType) {
            case NO_GOOGLE_ACCOUNT:
            case CANCELLED:
                return false;
            case READ_IAPK_TIMEOUT:
                if (myInstaller instanceof PreOSideLoader) {
                    ((PreOSideLoader) myInstaller).increaseLogcatTimeout();
                }
                return true;
            case SHELL_TIMEOUT:
                myShellTimeout *= 2;
                if (myInstaller instanceof PreOSideLoader) {
                    ((PreOSideLoader) myInstaller).increaseShellTimeout();
                }
                return true;
            case READ_IAPK_FAILED:
            case ADB_FAILURE:
            case INSTALL_FAILED:
            case UNKNOWN:
                return true;
        }
        return false;
    }

    @VisibleForTesting
    void uninstallAppIfInstalled(@NonNull IDevice device) throws InstantAppRunException {
        try {
            if (!isEmpty(
                    executeShellCommand(device, "pm path " + myPkgName, false, myShellTimeout))) {
                myListener.logMessage("Non instant app was installed. Uninstalling it.", null);
                String result = device.uninstallPackage(myPkgName);
                if (result != null) {
                    throw new InstantAppRunException(
                            InstantAppRunException.ErrorType.UNKNOWN, result);
                }
            }
        } catch (InstallException e) {
            throw new InstantAppRunException(
                    InstantAppRunException.ErrorType.INSTALL_FAILED,
                    "Failed uninstalling existing package " + myPkgName,
                    e);
        }
    }

    private static boolean isEmpty(@Nullable String str) {
        return str == null || str.isEmpty();
    }

    @NonNull
    static String executeShellCommand(
            @NonNull IDevice device, @NonNull String command, boolean rootRequired, long timeout)
            throws InstantAppRunException {
        CountDownLatch latch = new CountDownLatch(1);
        CollectingOutputReceiver receiver = new CollectingOutputReceiver(latch);

        try {
            if (rootRequired) {
                device.root();
            }
            device.executeShellCommand(command, receiver);
            if (!latch.await(timeout, TimeUnit.MILLISECONDS)) {
                throw new InstantAppRunException(
                        InstantAppRunException.ErrorType.SHELL_TIMEOUT,
                        "Failed executing command \"" + command + "\".");
            }
        } catch (AdbCommandRejectedException
                | ShellCommandUnresponsiveException
                | IOException
                | TimeoutException
                | InterruptedException e) {
            throw new InstantAppRunException(
                    e instanceof InterruptedException
                            ? InstantAppRunException.ErrorType.UNKNOWN
                            : InstantAppRunException.ErrorType.ADB_FAILURE,
                    "Failed executing command \"" + command + "\".",
                    e);
        }

        return receiver.getOutput();
    }
}
