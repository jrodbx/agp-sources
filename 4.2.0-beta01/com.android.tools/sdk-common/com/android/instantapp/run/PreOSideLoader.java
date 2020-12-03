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

import static com.android.instantapp.utils.DeviceUtils.getOsBuildType;

import com.android.annotations.NonNull;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.android.instantapp.utils.LogcatService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Side loader for Instant Apps in Pre O devices. It receives a zip file (the bundle) and uploads it
 * to the device, making the necessary configurations in DevMan.
 */
class PreOSideLoader implements InstantAppSideLoader.Installer {
    @NonNull private static final String TMP_REMOTE_DIR = "/data/local/tmp/aia/";
    @NonNull private final File myZipFile;
    @NonNull private final RunListener myListener;

    // Timeout for shell commands in milliseconds.
    private long myShellTimeout = 500;

    // Timeout for waiting logcat messages in seconds.
    private long myLogcatTimeout = 5;

    PreOSideLoader(@NonNull File zip, @NonNull RunListener listener) {
        myZipFile = zip;
        myListener = listener;
    }

    /** Duplicates the current shell timeout to retry side loading. */
    void increaseShellTimeout() {
        myShellTimeout *= 2;
    }

    /** Duplicates the current logcat timeout to retry side loading. */
    void increaseLogcatTimeout() {
        myLogcatTimeout *= 2;
    }

    @Override
    public void install(@NonNull IDevice device) throws InstantAppRunException {
        myListener.logMessage("Checking logged in Google account.", null);
        checkLoggedInGoogleAccount(device);

        String osBuildType = getOsBuildType(device);

        // TODO(b/34235489): When OnePlatform issue is resolved we could remove this.
        if (osBuildType != null && osBuildType.compareTo("test-keys") == 0) {
            // Force sync the domain filter. Clear the error state. We need to do it here because
            // the disableDomainFilterFallback only works when Devman is present. So in case domain
            // filter was synced to a bad state at provisioning, we need to get it to the right state here.
            executeShellCommand(
                    device,
                    "am startservice -a com.google.android.gms.instantapps.ACTION_UPDATE_DOMAIN_FILTER");
        }

        // If our ADB is running as root somehow when we run this, and later becomes unrooted, the
        // next run won't be able to write to this temp directory and will fail. Thus, we ensure
        // that the directory we create is  owned by the "shell" (unrooted) user. Note that this
        // doesn't matter for replacing the APK itself, because unlink permissions are scoped to the
        // parent directory in Linux.
        if (osBuildType != null && osBuildType.compareTo("release-keys") != 0) {
            executeShellCommand(device, "su shell mkdir -p " + TMP_REMOTE_DIR);
        }

        // Upload the Instant App
        String remotePath = TMP_REMOTE_DIR + myZipFile.getName();
        try {
            myListener.logMessage(
                    "Pushing zip file \""
                            + myZipFile.getAbsolutePath()
                            + "\" to device in location \""
                            + remotePath
                            + "\"",
                    null);
            device.pushFile(myZipFile.getAbsolutePath(), remotePath);
        } catch (IOException | AdbCommandRejectedException | SyncException | TimeoutException e) {
            throw new InstantAppRunException(
                    InstantAppRunException.ErrorType.ADB_FAILURE,
                    "Couldn't push file \""
                            + myZipFile.getAbsolutePath()
                            + "\" to device in location \""
                            + remotePath
                            + "\".");
        }

        try {
            readIapk(device, remotePath, UUID.randomUUID());
        } finally {
            executeShellCommand(device, "rm -f " + remotePath);
        }

        executeShellCommand(device, "am force-stop com.google.android.instantapps.supervisor");
    }

    /**
     * Try to read the uploaded zip file, read error or success messages that DevMan writes in the
     * logcat.
     */
    @VisibleForTesting
    void readIapk(@NonNull IDevice device, @NonNull String remotePath, @NonNull UUID installToken)
            throws InstantAppRunException {
        String installTokenIdentifier = "token=" + installToken;
        myListener.printMessage("Side loading instant app.");
        myListener.logMessage(
                "Side loading instant app. Install token: " + installTokenIdentifier, null);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean succeeded = new AtomicBoolean(false);
        AtomicReference<String> error = new AtomicReference<>(null);

        LogcatService.Listener listener =
                line -> {
                    if (line.getTag().compareTo("IapkLoadService") == 0) {
                        if (line.getLogLevel() == Log.LogLevel.ERROR) {
                            String message = line.getMessage();
                            if (message.contains(installTokenIdentifier)) {
                                error.set(
                                        message.replace(installTokenIdentifier, "")
                                                + " - Please see the Logcat for more information.");
                                latch.countDown();
                            }
                        } else if (line.getLogLevel() == Log.LogLevel.INFO) {
                            String message = line.getMessage();
                            if (message.contains(installTokenIdentifier)) {
                                if (message.contains("LOAD_SUCCESS")) {
                                    succeeded.set(true);
                                    latch.countDown();
                                }
                            }
                        }
                    }
                };

        LogcatService logcatService = new LogcatService(device);

        logcatService.startListening(listener);

        executeShellCommand(
                device,
                "am startservice -a \"com.google.android.instantapps.devman.iapk.LOAD\" "
                        + "--es \"com.google.android.instantapps.devman.iapk.IAPK_PATH\" \""
                        + remotePath
                        + "\" "
                        + "--es \"com.google.android.instantapps.devman.iapk.INSTALL_TOKEN\" \""
                        + installToken
                        + "\" "
                        + "--ez \"com.google.android.instantapps.devman.iapk.FORCE\" \"false\" "
                        + "-n com.google.android.instantapps.devman/.iapk.IapkLoadService");

        try {
            if (latch.await(myLogcatTimeout, TimeUnit.SECONDS)) {
                if (!succeeded.get()) {
                    throw new InstantAppRunException(
                            InstantAppRunException.ErrorType.READ_IAPK_FAILED, error.get());
                }
            } else {
                throw new InstantAppRunException(
                        InstantAppRunException.ErrorType.READ_IAPK_TIMEOUT);
            }
        } catch (InterruptedException e) {
            throw new InstantAppRunException(InstantAppRunException.ErrorType.READ_IAPK_FAILED, e);
        } finally {
            logcatService.stopListening();
        }
    }

    /**
     * Checks if device is logged in a Google account.
     *
     * @param device to be checked.
     * @throws InstantAppRunException if has problem to run adb shell or if device is not logged in.
     */
    private void checkLoggedInGoogleAccount(@NonNull IDevice device) throws InstantAppRunException {
        String output = executeShellCommand(device, "dumpsys account");

        Iterable<String> lines = Splitter.on("\n").split(output);
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("Account {")) {
                if (line.contains("type=com.google")) {
                    return;
                }
            }
        }
        throw new InstantAppRunException(InstantAppRunException.ErrorType.NO_GOOGLE_ACCOUNT);
    }

    @NonNull
    private String executeShellCommand(@NonNull IDevice device, @NonNull String command)
            throws InstantAppRunException {
        return InstantAppSideLoader.executeShellCommand(device, command, false, myShellTimeout);
    }
}
