/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.sdklib.internal.avd;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.io.CancellableFileIo;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.devices.Device;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.targets.SystemImage;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/** An immutable structure describing an Android Virtual Device. */
public final class AvdInfo {

    /**
     * Status for an {@link AvdInfo}. Indicates whether or not this AVD is valid.
     */
    public enum AvdStatus {
        /** No error */
        OK,
        /** Missing 'path' property in the ini file */
        ERROR_PATH,
        /** Missing config.ini file in the AVD data folder */
        ERROR_CONFIG,
        /** Unable to parse config.ini */
        ERROR_PROPERTIES,
        /** System Image folder in config.ini doesn't exist */
        ERROR_IMAGE_DIR,
        /** The {@link Device} this AVD is based on has changed from its original configuration*/
        ERROR_DEVICE_CHANGED,
        /** The {@link Device} this AVD is based on is no longer available */
        ERROR_DEVICE_MISSING,
        /** the {@link SystemImage} this AVD is based on is no longer available */
        ERROR_IMAGE_MISSING,
        /** The AVD's .ini file is corrupted */
        ERROR_CORRUPTED_INI
    }

    @NonNull private final String mName;
    @NonNull private final Path mIniFile;
    @NonNull private final Path mFolderPath;
    @NonNull private final ImmutableMap<String, String> mProperties;
    @NonNull private final AvdStatus mStatus;
    @Nullable private final ISystemImage mSystemImage;
    private final boolean mHasPlayStore;

    /**
     * Creates a new valid AVD info. Values are immutable.
     *
     * <p>Such an AVD is available and can be used. The error string is set to null.
     *
     * @param name The name of the AVD (for display or reference)
     * @param iniFile The path to the config.ini file
     * @param folderPath The path to the data directory
     * @param systemImage The system image.
     * @param properties The configuration properties. If null, an empty map will be created.
     */
    public AvdInfo(
            @NonNull String name,
            @NonNull Path iniFile,
            @NonNull Path folderPath,
            @NonNull ISystemImage systemImage,
            @Nullable Map<String, String> properties) {
        this(name, iniFile, folderPath, systemImage, properties, AvdStatus.OK);
    }

    /**
     * Creates a new <em>invalid</em> AVD info. Values are immutable.
     *
     * <p>Such an AVD is not complete and cannot be used. The error string must be non-null.
     *
     * @param name The name of the AVD (for display or reference)
     * @param iniFile The path to the config.ini file
     * @param folderPath The path to the data directory
     * @param systemImage The system image. Can be null if the image wasn't found.
     * @param properties The configuration properties. If null, an empty map will be created.
     * @param status The {@link AvdStatus} of this AVD. Cannot be null.
     */
    public AvdInfo(
            @NonNull String name,
            @NonNull Path iniFile,
            @NonNull Path folderPath,
            @Nullable ISystemImage systemImage,
            @Nullable Map<String, String> properties,
            @NonNull AvdStatus status) {
        mName = name;
        mIniFile = iniFile;
        mFolderPath = folderPath;
        mSystemImage = systemImage;
        mProperties = properties == null ? ImmutableMap.of() : ImmutableMap.copyOf(properties);
        mStatus = status;
        String psString = mProperties.get(AvdManager.AVD_INI_PLAYSTORE_ENABLED);
        mHasPlayStore = "true".equalsIgnoreCase(psString) || "yes".equalsIgnoreCase(psString);
    }

    /** Returns a stable ID for the AVD that doesn't change even if the device is renamed */
    @NonNull
    public String getId() {
        return mFolderPath.toString();
    }

    /** Returns the name of the AVD. Do not use this as a device ID; use getId instead. */
    @NonNull
    public String getName() {
        return mName;
    }

    /** Returns the name of the AVD for use in UI. */
    @NonNull
    public String getDisplayName() {
        String name = getProperties().get(AvdManager.AVD_INI_DISPLAY_NAME);
        return name == null ? mName.replace('_', ' ') : name;
    }

    /** Returns the path of the AVD data directory. */
    @NonNull
    public Path getDataFolderPath() {
        return mFolderPath;
    }

    /** Returns the tag id/display of the AVD. */
    @NonNull
    public IdDisplay getTag() {
        String id = getProperties().get(AvdManager.AVD_INI_TAG_ID);
        if (id == null) {
            return SystemImage.DEFAULT_TAG;
        }
        String display = getProperties().get(AvdManager.AVD_INI_TAG_DISPLAY);
        return IdDisplay.create(id, display == null ? id : display);
    }

    /** Returns the processor type of the AVD. */
    @NonNull
    public String getAbiType() {
        return getProperties().get(AvdManager.AVD_INI_ABI_TYPE);
    }

    /** Returns true if this AVD supports Google Play Store */
    public boolean hasPlayStore() {
        return mHasPlayStore;
    }

    @NonNull
    public AndroidVersion getAndroidVersion() {
        Map<String, String> properties = getProperties();

        String apiStr = properties.get(AvdManager.AVD_INI_ANDROID_API);
        String codename = properties.get(AvdManager.AVD_INI_ANDROID_CODENAME);
        int api = 1;
        if (!Strings.isNullOrEmpty(apiStr)) {
            try {
                api = Integer.parseInt(apiStr);
            }
            catch (NumberFormatException e) {
                // continue with the default
            }
        }

        String extStr = properties.get(AvdManager.AVD_INI_ANDROID_EXTENSION);
        int extension = 1;
        if (!Strings.isNullOrEmpty(extStr)) {
            try {
                extension = Integer.parseInt(extStr);
            } catch (NumberFormatException e) {
                // continue with the default
            }
        }

        String isBaseStr = properties.get(AvdManager.AVD_INI_ANDROID_IS_BASE_EXTENSION);
        boolean isBase = true;
        if (!Strings.isNullOrEmpty(isBaseStr)) {
            isBase = Boolean.parseBoolean(isBaseStr);
        }

        return new AndroidVersion(api, codename, extension, isBase);
    }

    @NonNull
    public String getCpuArch() {
        String cpuArch = mProperties.get(AvdManager.AVD_INI_CPU_ARCH);
        if (cpuArch != null) {
            return cpuArch;
        }

        // legacy
        return SdkConstants.CPU_ARCH_ARM;
    }

    @NonNull
    public String getDeviceManufacturer() {
        String deviceManufacturer = mProperties.get(AvdManager.AVD_INI_DEVICE_MANUFACTURER);
        if (deviceManufacturer != null && !deviceManufacturer.isEmpty()) {
            return deviceManufacturer;
        }

        return "";                                                              // $NON-NLS-1$
    }

    @NonNull
    public String getDeviceName() {
        String deviceName = mProperties.get(AvdManager.AVD_INI_DEVICE_NAME);
        if (deviceName != null && !deviceName.isEmpty()) {
            return deviceName;
        }

        return "";                                                              // $NON-NLS-1$
    }

    /**
     * Gets the system image for this AVD. Can be null if the system image is not found.
     */
    @Nullable
    public ISystemImage getSystemImage() {
        return mSystemImage;
    }

    /** Returns the {@link AvdStatus} of the receiver. */
    @NonNull
    public AvdStatus getStatus() {
        return mStatus;
    }

    /**
     * Helper method that returns the default AVD folder that would be used for a given AVD name
     * <em>if and only if</em> the AVD was created with the default choice.
     *
     * <p>Callers must NOT use this to "guess" the actual folder from an actual AVD since the
     * purpose of the AVD .ini file is to be able to change this folder. Callers should however use
     * this to create a new {@link AvdInfo} to setup its data folder to the default.
     *
     * <p>The default is {@code getBaseAvdFolder()/avdname.avd/}.
     *
     * <p>For an actual existing AVD, callers must use {@link #getDataFolderPath()} instead.
     *
     * @param manager The AVD Manager, used to get the AVD storage path.
     * @param avdName The name of the desired AVD.
     * @param unique Whether to return the default or a unique variation of the default.
     */
    @NonNull
    public static Path getDefaultAvdFolder(
            @NonNull AvdManager manager, @NonNull String avdName, boolean unique) {
        Path base = manager.getBaseAvdFolder();
        Path result = base.resolve(avdName + AvdManager.AVD_FOLDER_EXTENSION);
        if (unique) {
            int suffix = 0;
            while (CancellableFileIo.exists(result)) {
                result =
                        base.resolve(
                                String.format(
                                        "%s_%d%s",
                                        avdName, (++suffix), AvdManager.AVD_FOLDER_EXTENSION));
            }
        }
        return result;
    }

    /**
     * Helper method that returns the .ini {@link File} for a given AVD name.
     *
     * <p>The default is {@code getBaseAvdFolder()/avdname.ini}.
     *
     * @param manager The AVD Manager, used to get the AVD storage path.
     * @param avdName The name of the desired AVD.
     */
    @NonNull
    public static Path getDefaultIniFile(@NonNull AvdManager manager, @NonNull String avdName) {
        Path avdRoot = manager.getBaseAvdFolder();
        return avdRoot.resolve(avdName + AvdManager.INI_EXTENSION);
    }

    /** Returns the .ini {@link File} for this AVD. */
    @NonNull
    public Path getIniFile() {
        return mIniFile;
    }

    /** Helper method that returns the Config file for a given AVD name. */
    @NonNull
    public static Path getConfigFile(@NonNull Path path) {
        return path.resolve(AvdManager.CONFIG_INI);
    }

    /** Returns the Config file for this AVD. */
    @NonNull
    public Path getConfigFile() {
        return getConfigFile(mFolderPath);
    }

    /**
     * Returns the value of the property with the given name, or null if the AVD doesn't have such
     * property.
     */
    @Nullable
    public String getProperty(@NonNull String propertyName) {
        return mProperties.get(propertyName);
    }

    /**
     * Returns an ImmutableMap of the AVD's configuration properties; i.e. the properties stored in
     * the <code>config.ini</code> file. Keys are defined in the <code>AVD_INI*</code> fields of
     * {@link AvdManager}.
     */
    @NonNull
    public Map<String, String> getProperties() {
        return mProperties;
    }

    /**
     * Returns the error message for the AVD or <code>null</code> if {@link #getStatus()}
     * returns {@link AvdStatus#OK}
     */
    @Nullable
    public String getErrorMessage() {
        switch (mStatus) {
            case ERROR_PATH:
                return String.format("Missing AVD 'path' property in %1$s", getIniFile());
            case ERROR_CONFIG:
                return String.format("Missing config.ini file in %1$s", mFolderPath);
            case ERROR_PROPERTIES:
                return String.format("Failed to parse properties from %1$s",
                        getConfigFile());
            case ERROR_IMAGE_DIR:
            case ERROR_IMAGE_MISSING:
                return String.format(
                        "Missing system image for %s%s %s.",
                        SystemImage.DEFAULT_TAG.equals(getTag()) ? "" : (getTag().getDisplay() + " "),
                        getAbiType(),
                        mName);
            case ERROR_DEVICE_CHANGED:
                return String.format("%1$s %2$s configuration has changed since AVD creation",
                        mProperties.get(AvdManager.AVD_INI_DEVICE_MANUFACTURER),
                        mProperties.get(AvdManager.AVD_INI_DEVICE_NAME));
            case ERROR_DEVICE_MISSING:
                return String.format("%1$s %2$s no longer exists as a device",
                        mProperties.get(AvdManager.AVD_INI_DEVICE_MANUFACTURER),
                        mProperties.get(AvdManager.AVD_INI_DEVICE_NAME));
            case ERROR_CORRUPTED_INI:
                return String.format("Corrupted AVD ini file: %1$s", getIniFile());
            case OK:
                return null;
        }

        return null;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AvdInfo avdInfo = (AvdInfo) o;
        return mHasPlayStore == avdInfo.mHasPlayStore
                && mName.equals(avdInfo.mName)
                && mIniFile.equals(avdInfo.mIniFile)
                && mFolderPath.equals(avdInfo.mFolderPath)
                && mProperties.equals(avdInfo.mProperties)
                && mStatus == avdInfo.mStatus
                && Objects.equals(mSystemImage, avdInfo.mSystemImage);
    }

    @Override
    public int hashCode() {
        int hashCode = mName.hashCode();

        hashCode = 31 * hashCode + mIniFile.hashCode();
        hashCode = 31 * hashCode + mFolderPath.hashCode();
        hashCode = 31 * hashCode + mProperties.hashCode();
        hashCode = 31 * hashCode + mStatus.hashCode();
        hashCode = 31 * hashCode + Objects.hashCode(mSystemImage);
        hashCode = 31 * hashCode + Objects.hashCode(mHasPlayStore);

        return hashCode;
    }

    @NonNull
    public String toDebugString() {
        String separator = System.lineSeparator();

        return "mName = "
                + mName
                + separator
                + "mIniFile = "
                + mIniFile
                + separator
                + "mFolderPath = "
                + mFolderPath
                + separator
                + "mProperties = "
                + mProperties
                + separator
                + "mStatus = "
                + mStatus
                + separator
                + "mSystemImage = "
                + mSystemImage
                + separator
                + "mHasPlayStore = "
                + mHasPlayStore
                + separator;
    }
}
