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

import static com.android.instantapp.utils.DeviceUtils.getOsBuildType;
import static com.android.instantapp.utils.DeviceUtils.isPostO;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.instantapp.sdk.InstantAppSdkException;
import com.android.instantapp.sdk.Metadata;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ProvisionRunner {
    @NonNull private final Metadata myMetadata;
    @NonNull private final Map<IDevice, ProvisionState> myProvisionCache;

    @NonNull private final ProvisionListener myListener;

    // Timeout for shell commands in milliseconds.
    private long shellTimeout = 500;

    public ProvisionRunner(@NonNull File instantAppSdk) throws ProvisionException {
        this(instantAppSdk, new ProvisionListener.NullListener());
    }

    public ProvisionRunner(@NonNull File instantAppSdk, @NonNull ProvisionListener listener)
            throws ProvisionException {
        if (!instantAppSdk.exists() || !instantAppSdk.isDirectory()) {
            throw new ProvisionException(
                    ProvisionException.ErrorType.INVALID_SDK,
                    "Path " + instantAppSdk.getAbsolutePath() + " is not valid.");
        }
        Metadata metadata;
        try {
            metadata = Metadata.getInstance(instantAppSdk);
        } catch (InstantAppSdkException e) {
            throw new ProvisionException(ProvisionException.ErrorType.INVALID_SDK, e);
        }
        myMetadata = metadata;
        myListener = listener;
        myProvisionCache = new HashMap<>();
    }

    public void runProvision(@NonNull IDevice device) throws ProvisionException {
        runProvision(device, 2);
    }

    /**
     * Run provision with either a cached or new (if has no cached) {@link ProvisionState} and
     * automatically retry {@code retries} times if fails. See {@link #runProvision(IDevice,
     * ProvisionState)}.
     */
    public void runProvision(@NonNull IDevice device, int retries) throws ProvisionException {
        boolean success = false;
        if (!myProvisionCache.containsKey(device)) {
            myProvisionCache.put(device, new ProvisionState());
        }
        while (!success) {
            try {
                runProvision(device, myProvisionCache.get(device));
                success = true;
            } catch (ProvisionException e) {
                if (retries > 0 && prepareRetry(e.getErrorType())) {
                    myListener.logMessage("Retrying to provision", e);
                    retries--;
                    try {
                        Thread.sleep(shellTimeout);
                    } catch (InterruptedException e1) {
                        // Ignore
                    }
                } else {
                    myListener.printMessage("Provision failed: " + e.getMessage());
                    myListener.logMessage("Provision failed", e);
                    throw e;
                }
            }
        }
    }

    /**
     * Analyses if it's worth it to retry depending on the error and makes the necessary
     * modifications for retrying.
     *
     * @param errorType the type of error resulted from the last provision.
     * @return if it's worth it to retry.
     */
    private boolean prepareRetry(ProvisionException.ErrorType errorType) {
        switch (errorType) {
            case ARCH_NOT_SUPPORTED:
            case DEVICE_NOT_SUPPORTED:
            case NO_GOOGLE_ACCOUNT:
            case INVALID_SDK:
            case UNINSTALL_FAILED:
            case CANCELLED:
                return false;
            case SHELL_TIMEOUT:
                shellTimeout *= 2;
                return true;
            case ADB_FAILURE:
            case INSTALL_FAILED:
            case UNKNOWN:
                return true;
        }
        return false;
    }

    /**
     * Provisions the device based on the last state.
     *
     * @param device to be provisioned.
     * @param provisionState can be either a brand new or represents the last provision run.
     */
    private void runProvision(@NonNull IDevice device, @NonNull ProvisionState provisionState)
            throws ProvisionException {
        myListener.setProgress(0);
        myListener.logMessage("Starting provision. Cached state: " + provisionState, null);
        myListener.printMessage("Starting provision");

        String buildType = getOsBuildType(device);
        int apiLevel = device.getVersion().getApiLevel();

        if (!myListener.isCancelled() && provisionState.lastSucceeded == ProvisionState.Step.NONE) {
            myListener.logMessage("Checking API level", null);
            if (isPostO(device)) {
                provisionState.lastSucceeded = ProvisionState.Step.FINISHED;
                myListener.logMessage("Device is post O", null);
                myListener.printMessage("Post O device, no provision needed.");
                myListener.setProgress(1);
                return;
            }
            provisionState.lastSucceeded = ProvisionState.Step.CHECK_POSTO;
        }
        myListener.setProgress(1.0 / 20);

        myListener.printMessage("Checking device");
        if (!myListener.isCancelled()
                && provisionState.lastSucceeded == ProvisionState.Step.CHECK_POSTO) {
            myListener.logMessage("Checking device architecture", null);
            provisionState.arch = getPreferredDeviceArchitecture(device);
            myListener.logMessage("Device architecture: " + provisionState.arch, null);
            provisionState.lastSucceeded = ProvisionState.Step.CHECK_ARCH;
        }
        myListener.setProgress(2.0 / 20);

        if (!myListener.isCancelled()
                && provisionState.lastSucceeded == ProvisionState.Step.CHECK_ARCH) {
            myListener.logMessage("Checking device information.", null);
            provisionState.deviceInfo = getDeviceInfo(device);
            myListener.logMessage("Device information: " + provisionState.deviceInfo, null);
            provisionState.lastSucceeded = ProvisionState.Step.CHECK_DEVICE;
        }
        myListener.setProgress(3.0 / 20);

        if (!myListener.isCancelled()
                && provisionState.lastSucceeded == ProvisionState.Step.CHECK_DEVICE) {
            myListener.logMessage("Checking device information.", null);
            checkLoggedInGoogleAccount(device);
            myListener.logMessage("Logged in Google account", null);
            provisionState.lastSucceeded = ProvisionState.Step.CHECK_ACCOUNT;
        }
        myListener.setProgress(4.0 / 20);

        if (!myListener.isCancelled()
                && provisionState.lastSucceeded == ProvisionState.Step.CHECK_ACCOUNT) {
            assert provisionState.deviceInfo != null;
            myListener.printMessage("Overriding GServices");
            myListener.logMessage("Overriding GServices", null);
            if (buildType != null && buildType.compareTo("release-keys") != 0) {
                overrideGServices(device, provisionState.deviceInfo, provisionState);
                myListener.logMessage("GServices overrides complete", null);
            } else {
                checkInGooglePlay(device);
                myListener.logMessage("Device is release-keys", null);
            }
            provisionState.lastSucceeded = ProvisionState.Step.GSERVICES;
        }
        myListener.setProgress(8.0 / 20);

        if (!myListener.isCancelled()
                && provisionState.lastSucceeded == ProvisionState.Step.GSERVICES) {
            assert provisionState.arch != null;
            myListener.printMessage("Installing apks");
            myListener.logMessage("Installing apks", null);
            installApks(device, provisionState.arch, apiLevel, provisionState);
            myListener.logMessage("Apks installed successfully", null);
            provisionState.lastSucceeded = ProvisionState.Step.INSTALL;
        }
        myListener.setProgress(19.0 / 20);

        if (!myListener.isCancelled()
                && provisionState.lastSucceeded == ProvisionState.Step.INSTALL) {
            myListener.logMessage("Setting flags", null);
            if (buildType != null && buildType.compareTo("release-keys") != 0) {
                setFlags(device);
            }
            provisionState.lastSucceeded = ProvisionState.Step.FINISHED;
        }

        if (myListener.isCancelled()) {
            throw new ProvisionException(ProvisionException.ErrorType.CANCELLED);
        }

        myListener.printMessage("Provision completed");
        myListener.logMessage("Provision completed", null);
        myListener.setProgress(1);
    }

    public void clearCache() {
        myProvisionCache.clear();
    }

    @NonNull
    private Metadata.Arch getPreferredDeviceArchitecture(@NonNull IDevice device)
            throws ProvisionException {
        List<String> architectures = device.getAbis();
        for (String arch : architectures) {
            if (myMetadata.isSupportedArch(arch)) {
                return Metadata.Arch.create(arch);
            }
        }
        throw new ProvisionException(
                ProvisionException.ErrorType.ARCH_NOT_SUPPORTED,
                "Detected architectures are: " + architectures);
    }

    @NonNull
    private Metadata.Device getDeviceInfo(@NonNull IDevice device) throws ProvisionException {
        String manufacturer = device.getProperty("ro.product.manufacturer");
        String androidDevice = device.getProperty("ro.product.device");
        int apiLevel = device.getVersion().getApiLevel();
        String product = device.getProperty("ro.product.name");
        String hardware = device.getProperty("ro.hardware");
        Metadata.Device deviceInfo =
                new Metadata.Device(
                        manufacturer,
                        androidDevice,
                        Collections.singleton(apiLevel),
                        product,
                        hardware);

        return deviceInfo;
    }

    /**
     * Checks if device is logged in a Google account.
     *
     * @param device to be checked.
     * @throws ProvisionException if has problem to run adb shell or if device is not logged in.
     */
    private void checkLoggedInGoogleAccount(@NonNull IDevice device) throws ProvisionException {
        String output = executeShellCommand(device, "dumpsys account", false);

        Iterable<String> lines = Splitter.on("\n").split(output);
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("Account {")) {
                if (line.contains("type=com.google")) {
                    return;
                }
            }
        }
        throw new ProvisionException(ProvisionException.ErrorType.NO_GOOGLE_ACCOUNT);
    }

    private void installApks(
            @NonNull IDevice device,
            @NonNull Metadata.Arch arch,
            int apiLevel,
            @NonNull ProvisionState provisionState)
            throws ProvisionException {
        ProvisionApksInstaller apksInstaller =
                new ProvisionApksInstaller(myMetadata.getApks(arch, apiLevel));
        apksInstaller.installAll(device, provisionState, myListener);
    }

    private void checkInGooglePlay(@NonNull IDevice device) throws ProvisionException {
        executeShellCommand(device, "am broadcast -a android.server.checkin.CHECKIN", false);
        executeShellCommand(device,
                "am broadcast -a com.google.android.finsky.action.CONTENT_FILTERS_CHANGED",
                false);
    }

    private void overrideGServices(
            @NonNull IDevice device,
            @NonNull Metadata.Device deviceInfo,
            @NonNull ProvisionState provisionState)
            throws ProvisionException {
        int currentGService = 0;
        for (Metadata.GServicesOverride gServicesOverride :
                myMetadata.getGServicesOverrides(deviceInfo)) {
            if (currentGService > provisionState.lastGService) {
                executeShellCommand(
                        device,
                        "CLASSPATH=/system/framework/am.jar su root app_process "
                                + "/system/bin com.android.commands.am.Am broadcast "
                                + "-a com.google.gservices.intent.action.GSERVICES_OVERRIDE -e "
                                + gServicesOverride.getKey()
                                + " "
                                + gServicesOverride.getValue(),
                        true);
                provisionState.lastGService = currentGService;
            }
            currentGService++;
        }
        executeShellCommand(device,
                "am broadcast -a com.google.android.finsky.action.CONTENT_FILTERS_CHANGED",
                false);
        executeShellCommand(device, "am force-stop com.google.android.gms", false);
    }

    private void setFlags(@NonNull IDevice device) throws ProvisionException {
        executeShellCommand(
                device,
                "pm grant com.google.android.instantapps.devman android.permission.READ_EXTERNAL_STORAGE",
                false);

        // Broadcast a Phenotype update
        executeShellCommand(
                device,
                "CLASSPATH=/system/framework/am.jar su root app_process "
                        + "/system/bin com.android.commands.am.Am broadcast "
                        + "-a com.google.android.gms.phenotype.UPDATE",
                true);

        // Make sure that if UrlHandler was disabled before it becomes enabled now
        executeShellCommand(
                device,
                "CLASSPATH=/system/framework/pm.jar su root app_process "
                        + "/system/bin com.android.commands.pm.Pm enable "
                        + "com.google.android.instantapps.supervisor/.UrlHandler",
                true);

        // Trigger a domain filter reload. Domain filters need to be populated in order to have an eligible account on the device, and this happens on a
        // loose 24-hour schedule. Developers need AIA on their device right away, and this will cause our GCore module to pull new domain filters.
        executeShellCommand(
                device,
                "am broadcast -a com.google.android.finsky.action.CONTENT_FILTERS_CHANGED",
                false);
    }

    @NonNull
    private String executeShellCommand(
            @NonNull IDevice device, @NonNull String command, boolean rootRequired)
            throws ProvisionException {
        return executeShellCommand(device, command, rootRequired, shellTimeout);
    }

    @NonNull
    static String executeShellCommand(
            @NonNull IDevice device, @NonNull String command, boolean rootRequired, long timeout)
            throws ProvisionException {
        CountDownLatch latch = new CountDownLatch(1);
        CollectingOutputReceiver receiver = new CollectingOutputReceiver(latch);


        try {
            if (rootRequired) {
                device.root();
            }
            device.executeShellCommand(command, receiver);
            if (!latch.await(timeout, TimeUnit.MILLISECONDS)) {
                throw new ProvisionException(
                        ProvisionException.ErrorType.SHELL_TIMEOUT,
                        "Failed executing command \"" + command + "\".");
            }
        } catch (AdbCommandRejectedException
                | ShellCommandUnresponsiveException
                | IOException
                | TimeoutException
                | InterruptedException e) {
            throw new ProvisionException(
                    e instanceof InterruptedException
                            ? ProvisionException.ErrorType.UNKNOWN
                            : ProvisionException.ErrorType.ADB_FAILURE,
                    "Failed executing command \"" + command + "\".",
                    e);
        }

        return receiver.getOutput();
    }

    /**
     * Keeps track of the progress of the provision process, so retries or reprovisions are faster.
     */
    static class ProvisionState {
        @NonNull Step lastSucceeded;
        @Nullable Metadata.Arch arch;
        @Nullable Metadata.Device deviceInfo;
        int lastInstalled;
        int lastGService;

        ProvisionState() {
            lastSucceeded = Step.NONE;
            arch = null;
            deviceInfo = null;
            lastInstalled = -1;
            lastGService = -1;
        }

        @Override
        public String toString() {
            return "{lastSucceeded: "
                    + lastSucceeded
                    + ", arch: "
                    + arch
                    + ", deviceInfo: "
                    + deviceInfo
                    + ", lastInstalled: "
                    + lastInstalled
                    + ", lastGService: "
                    + lastGService
                    + "}";
        }

        enum Step {
            NONE,
            CHECK_POSTO,
            CHECK_ARCH,
            CHECK_DEVICE,
            CHECK_ACCOUNT,
            GSERVICES,
            INSTALL,
            FINISHED
        }
    }

    @VisibleForTesting
    // Only for test.
    Metadata getMetadata() {
        return myMetadata;
    }

    @VisibleForTesting
    // Only for test.
    Map<IDevice, ProvisionState> getCache() {
        return myProvisionCache;
    }

    @VisibleForTesting
    // Only for test.
    ProvisionListener getListener() {
        return myListener;
    }
}
