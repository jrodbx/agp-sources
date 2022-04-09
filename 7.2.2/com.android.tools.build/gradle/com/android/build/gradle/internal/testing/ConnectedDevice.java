/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.testing;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.testing.api.DeviceConfig;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.android.ide.common.util.DeviceUtils;
import com.android.sdklib.AndroidVersion;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Local device connected to with ddmlib. This is a wrapper around {@link IDevice}.
 */
public class ConnectedDevice extends DeviceConnector {

    private final IDevice iDevice;
    private final ILogger mLogger;
    private final long mTimeout;
    private final TimeUnit  mTimeUnit;
    private String mName;
    private String mNameSuffix; // used when multiple device have the same name


    public ConnectedDevice(@NonNull IDevice iDevice, @NonNull ILogger logger,
            long timeout, @NonNull TimeUnit timeUnit) {
        this.iDevice = iDevice;
        mLogger = logger;
        mTimeout = timeout;
        mTimeUnit = timeUnit;
    }

    @NonNull
    @Override
    public String getName() {
        if (mName != null) {
            return mName;
        }
        String version = getNullableProperty(IDevice.PROP_BUILD_VERSION);
        boolean emulator = iDevice.isEmulator();

        String name;
        if (emulator) {
            name = iDevice.getAvdName() != null ?
                    iDevice.getAvdName() + "(AVD)" :
                    iDevice.getSerialNumber();
        } else {
            String model = getNullableProperty(IDevice.PROP_DEVICE_MODEL);
            name = model != null ? model : iDevice.getSerialNumber();
        }

        mName = version != null ? name + " - " + version : name;
        if (mNameSuffix != null) {
            mName = mName + "-" + mNameSuffix;
        }
        return mName;
    }

    void setNameSuffix(String suffix) {
        mNameSuffix = suffix;
        mName = null;
    }

    public String getNameSuffix() {
        return mNameSuffix;
    }

    @Override
    public void connect(int timeout, ILogger logger) throws TimeoutException {
        // nothing to do here
    }

    @Override
    public void disconnect(int timeout, ILogger logger) throws TimeoutException {
        // nothing to do here
    }

    @Override
    public void installPackage(@NonNull File apkFile,
            @NonNull Collection<String> options,
            int timeout,
            ILogger logger) throws DeviceException {
        try {
            // Prepend -t to install apk marked as testOnly.
            List<String> installOptions = Lists.newArrayListWithCapacity(1 + options.size());
            installOptions.add("-t");
            installOptions.addAll(options);
            iDevice.installPackage(
                    apkFile.getAbsolutePath(),
                    true /*reinstall*/,
                    installOptions.toArray(new String[0]));
        } catch (Exception e) {
            logger.error(e, "Unable to install " + apkFile.getAbsolutePath());
            throw new DeviceException(e);
        }
    }

    @Override
    public void installPackages(@NonNull List<File> splitApkFiles,
            @NonNull Collection<String> options,
            int timeoutInMs,
            ILogger logger)
            throws DeviceException {

        try {
            // Prepend -t to install apk marked as testOnly.
            List<String> installOptions = Lists.newArrayListWithCapacity(1 + options.size());
            installOptions.add("-t");
            installOptions.addAll(options);
            iDevice.installPackages(
                    splitApkFiles,
                    true /*reinstall*/,
                    installOptions,
                    timeoutInMs,
                    TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            List<String> apkFileNames =
                    Lists.transform(
                            splitApkFiles,
                            input -> input != null ? input.getAbsolutePath() : null);
            logger.error(e, "Unable to install " + Joiner.on(',').join(apkFileNames));
            throw new DeviceException(e);
        }
    }

    @Override
    public void uninstallPackage(@NonNull String packageName, int timeout, ILogger logger) throws DeviceException {
        try {
            iDevice.uninstallPackage(packageName);
        } catch (Exception e) {
            logger.error(e, "Unable to uninstall " + packageName);
            throw new DeviceException(e);
        }
    }

    @Override
    public void executeShellCommand(String command, IShellOutputReceiver receiver,
                                    long maxTimeToOutputResponse, TimeUnit maxTimeUnits)
                                    throws TimeoutException, AdbCommandRejectedException,
                                    ShellCommandUnresponsiveException, IOException {
        iDevice.executeShellCommand(command, receiver, maxTimeToOutputResponse, maxTimeUnits);
    }

    @Override
    public void executeShellCommand(
            String command,
            IShellOutputReceiver receiver,
            long maxTimeout,
            long maxTimeToOutputResponse,
            TimeUnit maxTimeUnits)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
                    IOException {
        iDevice.executeShellCommand(
                command, receiver, maxTimeout, maxTimeToOutputResponse, maxTimeUnits);
    }

    @NonNull
    @Override
    public ListenableFuture<String> getSystemProperty(@NonNull String name) {
        return iDevice.getSystemProperty(name);
    }

    @Override
    public void pullFile(String remote, String local) throws IOException {
        try {
            iDevice.pullFile(remote, local);

        } catch (TimeoutException e) {
            throw new IOException(String.format("Failed to pull %s from device", remote), e);
        } catch (AdbCommandRejectedException e) {
            throw new IOException(String.format("Failed to pull %s from device", remote), e);
        } catch (SyncException e) {
            throw new IOException(String.format("Failed to pull %s from device", remote), e);
        }
    }

    @NonNull
    @Override
    public String getSerialNumber() {
        return iDevice.getSerialNumber();
    }

    @Override
    public int getApiLevel() {
        String sdkVersion = getNullableProperty(IDevice.PROP_BUILD_API_LEVEL);
        if (sdkVersion != null) {
            try {
                return Integer.valueOf(sdkVersion);
            } catch (NumberFormatException ignored) { }
        }

        // can't get it, return 0.
        return 0;
    }

    @Nullable
    @Override
    public String getApiCodeName() {
        String codeName = getNullableProperty(IDevice.PROP_BUILD_CODENAME);
        if (codeName != null) {
            // if this is a release platform return null.
            if ("REL".equals(codeName)) {
                return null;
            }

            // else return the codename
            return codeName;
        }

        // can't get it, return 0.
        return null;
    }

    @Nullable
    @Override
    public IDevice.DeviceState getState() {
        return iDevice.getState();
    }

    @NonNull
    @Override
    public List<String> getAbis() {
         /* Try abiList (implemented in L onwards) otherwise fall back to abi and abi2. */
        String abiList = getNullableProperty(IDevice.PROP_DEVICE_CPU_ABI_LIST);
        if(abiList != null) {
            return Lists.newArrayList(Splitter.on(',').split(abiList));
        } else {
            List<String> abis = Lists.newArrayListWithExpectedSize(2);
            String abi = getNullableProperty(IDevice.PROP_DEVICE_CPU_ABI);
            if (abi != null) {
                abis.add(abi);
            }

            abi = getNullableProperty(IDevice.PROP_DEVICE_CPU_ABI2);
            if (abi != null) {
                abis.add(abi);
            }

            return abis;
        }
    }

    @Override
    public int getDensity() {
        String densityValue = getNullableProperty(IDevice.PROP_DEVICE_DENSITY);
        if (densityValue == null) {
            densityValue = getNullableProperty(IDevice.PROP_DEVICE_EMULATOR_DENSITY);
        }
        if (densityValue == null) {
            mLogger.verbose("Unable to get density for device %1$s", getName());
            return -1;
        }
        try {
            return Integer.parseInt(densityValue);
        } catch (NumberFormatException e) {
            mLogger.lifecycle(
                    "Unable to get density for device %1$s. "
                            + "Density value %2$s could not be parsed.",
                    getName(), densityValue);
            return -1;
        }
    }

    @Override
    public int getHeight() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public int getWidth() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public String getLanguage() {
        return getNullableProperty(IDevice.PROP_DEVICE_LANGUAGE);
    }

    @Nullable
    @Override
    public Set<String> getLanguageSplits()
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
                    IOException {
        AndroidVersion version = iDevice.getVersion();
        if (version.getApiLevel() < 21) {
            return null;
        }

        return DeviceUtils.getLanguages(iDevice, Duration.ofMillis(mTimeUnit.toMillis(mTimeout)));
    }

    @Override
    public String getRegion() {
        return getNullableProperty(IDevice.PROP_DEVICE_REGION);
    }

    @Override
    @NonNull
    public String getProperty(@NonNull String propertyName) {
        //TODO: Is this method needed in ConnectedDevice?
        //      If it is, what should its failure semantic be?
        return Preconditions.checkNotNull(getNullableProperty(propertyName));
    }

    @Nullable
    @Override
    public String getNullableProperty(@NonNull String propertyName) {
        try {
            Future<String> property = iDevice.getSystemProperty(propertyName);
            if (mTimeout > 0) {
                return property.get(mTimeout, mTimeUnit);
            } else {
                return property.get();
            }
        } catch (InterruptedException e) {
            return null;
        } catch (ExecutionException e) {
            return null;
        } catch (java.util.concurrent.TimeoutException e) {
            return null;
        }
    }

    @NonNull
    @Override
    public DeviceConfig getDeviceConfig() throws DeviceException {
        final List<String> output = new ArrayList<String>();
        final MultiLineReceiver receiver =
                new MultiLineReceiver() {
                    @Override
                    public void processNewLines(@NonNull String[] lines) {
                        output.addAll(Arrays.asList(lines));
                    }

                    @Override
                    public boolean isCancelled() {
                        return false;
                    }
                };
        try {
            executeShellCommand("am get-config", receiver, mTimeout, mTimeUnit);
            return DeviceConfig.Builder.parse(output);
        } catch (Exception e) {
            throw new DeviceException(e);
        }
    }

    /**
     * Returns the corresponding {@link IDevice}. Used in packages common to Studio and the Plugin.
     */
    @NonNull
    public IDevice getIDevice() {
        return iDevice;
    }
}
