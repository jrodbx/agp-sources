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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

import static java.util.stream.Collectors.joining;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.GuardedBy;
import com.android.annotations.concurrency.Slow;
import com.android.io.CancellableFileIo;
import com.android.io.IAbstractFile;
import com.android.io.StreamException;
import com.android.prefs.AndroidLocationsException;
import com.android.repository.api.ConsoleProgressIndicator;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.PathFileWrapper;
import com.android.sdklib.SystemImageTags;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.devices.DeviceManager.DeviceStatus;
import com.android.sdklib.internal.avd.AvdInfo.AvdStatus;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.LoggerProgressIndicatorWrapper;
import com.android.utils.FileUtils;
import com.android.utils.GrabProcessOutput;
import com.android.utils.GrabProcessOutput.IProcessOutput;
import com.android.utils.GrabProcessOutput.Wait;
import com.android.utils.ILogger;
import com.android.utils.PathUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Closeables;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.OptionalLong;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Android Virtual Device Manager to manage AVDs.
 */
public class AvdManager {

    /**
     * Exception thrown when something is wrong with a target path.
     */
    private static final class InvalidTargetPathException extends Exception {
        private static final long serialVersionUID = 1L;

        InvalidTargetPathException(String message) {
            super(message);
        }
    }

    private static final Pattern INI_LINE_PATTERN =
            Pattern.compile("^([a-zA-Z0-9._-]+)\\s*=\\s*(.*)\\s*$");

    public static final String AVD_FOLDER_EXTENSION = ".avd";

    /** Pattern to match pixel-sized skin "names", e.g. "320x480". */
    public static final Pattern NUMERIC_SKIN_SIZE = Pattern.compile("([0-9]{2,})x([0-9]{2,})");

    public static final String DATA_FOLDER = "data";
    public static final String USERDATA_IMG = "userdata.img";
    public static final String USERDATA_QEMU_IMG = "userdata-qemu.img";
    public static final String SNAPSHOTS_DIRECTORY = "snapshots";
    public static final String USER_SETTINGS_INI = "user-settings.ini"; // $NON-NLS-1$

    private static final String BOOT_PROP = "boot.prop";
    static final String CONFIG_INI = "config.ini";
    private static final String HARDWARE_QEMU_INI = "hardware-qemu.ini";
    private static final String SDCARD_IMG = "sdcard.img";

    static final String INI_EXTENSION = ".ini";
    private static final Pattern INI_NAME_PATTERN =
            Pattern.compile("(.+)\\" + INI_EXTENSION + "$", Pattern.CASE_INSENSITIVE);

    private static final Pattern IMAGE_NAME_PATTERN =
            Pattern.compile("(.+)\\.img$", Pattern.CASE_INSENSITIVE);

    public static final String HARDWARE_INI = "hardware.ini";

    private static class AvdMgrException extends Exception {}

    @NonNull private final AndroidSdkHandler mSdkHandler;

    @NonNull private final Path mBaseAvdFolder;

    @NonNull private final ILogger mLog;

    @NonNull private final DeviceManager mDeviceManager;

    @GuardedBy("mAllAvdList")
    private final ArrayList<AvdInfo> mAllAvdList = new ArrayList<>();

    @GuardedBy("mAllAvdList")
    private ImmutableList<AvdInfo> mValidAvdList;

    private AvdManager(
            @NonNull AndroidSdkHandler sdkHandler,
            @NonNull Path baseAvdFolder,
            @NonNull DeviceManager deviceManager,
            @NonNull ILogger log) {
        if (sdkHandler.getLocation() == null) {
            throw new IllegalArgumentException("Local SDK path not set!");
        }
        mSdkHandler = sdkHandler;
        mBaseAvdFolder = baseAvdFolder;
        mLog = log;
        mDeviceManager = deviceManager;
        try {
            buildAvdList(mAllAvdList);
        } catch (AndroidLocationsException e) {
            mLog.warning("Constructing AvdManager: %s", e.getMessage());
        }
    }

    @NonNull
    public static AvdManager createInstance(
            @NonNull AndroidSdkHandler sdkHandler,
            @NonNull Path baseAvdFolder,
            @NonNull DeviceManager deviceManager,
            @NonNull ILogger log) {
        return new AvdManager(sdkHandler, baseAvdFolder, deviceManager, log);
    }


    /** Returns the base folder where AVDs are created. */
    @NonNull
    public Path getBaseAvdFolder() {
        return mBaseAvdFolder;
    }

    /**
     * Returns all the existing AVDs.
     * @return a newly allocated array containing all the AVDs.
     */
    @NonNull
    public AvdInfo[] getAllAvds() {
        synchronized (mAllAvdList) {
            return mAllAvdList.toArray(new AvdInfo[0]);
        }
    }

    /** Returns all the valid AVDs. */
    @NonNull
    public ImmutableList<AvdInfo> getValidAvds() {
        synchronized (mAllAvdList) {
            if (mValidAvdList == null) {
                mValidAvdList =
                        mAllAvdList.stream()
                                .filter(avd -> avd.getStatus() == AvdStatus.OK)
                                .collect(toImmutableList());
            }
            return mValidAvdList;
        }
    }

    /**
     * Returns the {@link AvdInfo} matching the given <var>name</var>.
     *
     * <p>The search is case-insensitive on Windows.
     *
     * @param name the name of the AVD to return
     * @param validAvdOnly if <code>true</code>, only look through the list of valid AVDs.
     * @return the matching AvdInfo or <code>null</code> if none were found.
     */
    @Nullable
    public AvdInfo getAvd(@Nullable String name, boolean validAvdOnly) {

        boolean ignoreCase = SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS;

        if (validAvdOnly) {
            for (AvdInfo info : getValidAvds()) {
                String name2 = info.getName();
                if (name2.equals(name) || (ignoreCase && name2.equalsIgnoreCase(name))) {
                    return info;
                }
            }
        } else {
            synchronized (mAllAvdList) {
                for (AvdInfo info : mAllAvdList) {
                    String name2 = info.getName();
                    if (name2.equals(name) || (ignoreCase && name2.equalsIgnoreCase(name))) {
                        return info;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Returns the {@link AvdInfo} with the given <var>display name</var>.
     *
     * @return the matching AvdInfo or <code>null</code> if none were found.
     */
    @Nullable
    public AvdInfo findAvdWithDisplayName(@NonNull String displayName) {
        synchronized (mAllAvdList) {
            for (AvdInfo avd : mAllAvdList) {
                if (avd.getDisplayName().equals(displayName)) {
                    return avd;
                }
            }
        }
        return null;
    }

    /** Returns whether an emulator is currently running the AVD. */
    @Slow
    public boolean isAvdRunning(@NonNull AvdInfo info) {
        String pid;
        try {
            pid = getAvdPid(info);
        }
        catch (IOException e) {
            mLog.error(e, "IOException while getting PID");
            // To be safe return true
            return true;
        }
        if (pid != null) {
            String command;
            if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
                command = "cmd /c \"tasklist /FI \"PID eq " + pid + "\" | findstr " + pid
                        + "\"";
            } else {
                command = "kill -0 " + pid;
            }
            try {
                Process p = Runtime.getRuntime().exec(command);
                // If the process ends with non-0 it means the process doesn't exist
                return p.waitFor() == 0;
            } catch (IOException e) {
                mLog.warning(
                        "Got IOException while checking running processes:\n%s",
                        Arrays.toString(e.getStackTrace()));
                // To be safe return true
                return true;
            } catch (InterruptedException e) {
                mLog.warning(
                        "Got InterruptedException while checking running processes:\n%s",
                        Arrays.toString(e.getStackTrace()));
                // To be safe return true
                return true;
            }
        }

        return false;
    }

    // Log info about a running AVD.
    // This is intended to help identify why we occasionally get a false report
    // that an AVD instance is already executing.
    @Slow
    public void logRunningAvdInfo(@NonNull AvdInfo info) {
        String pid;
        try {
            pid = getAvdPid(info);
        }
        catch (IOException ex) {
            mLog.error(ex, "AVD not launched but got IOException while getting PID");
            return;
        }
        if (pid == null) {
            mLog.warning(
                    "AVD not launched but PID is null. Should not have indicated that the AVD is"
                            + " running.");
            return;
        }
        mLog.warning("AVD not launched because an instance appears to be running on PID " + pid);
        String command;
        int numTermChars;
        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
            command = "cmd /c \"tasklist /FI \"PID eq " + pid + "\" /FO csv /V /NH\"";
            numTermChars = 2; // <CR><LF>
        }
        else {
            command =
                    "ps -o pid= -o user= -o pcpu= -o tty= -o stat= -o time= -o etime= -o cmd= -p "
                            + pid;
            numTermChars = 1; // <LF>
        }
        try {
            Process proc = Runtime.getRuntime().exec(command);
            if (proc.waitFor() != 0) {
                mLog.warning("Could not get info for that AVD process");
            }
            else {
                InputStream procInfoStream = proc.getInputStream(); // proc's output is our input
                final int strMax = 256;
                byte[] procInfo = new byte[strMax];
                int nRead = procInfoStream.read(procInfo, 0, strMax);
                if (nRead <= numTermChars) {
                    mLog.warning("Info for that AVD process is null");
                }
                else {
                    mLog.warning(
                            "AVD process info: ["
                                    + new String(procInfo, 0, nRead - numTermChars)
                                    + "]");
                }
            }
        }
        catch (IOException | InterruptedException ex) {
            mLog.warning(
                    "Got exception when getting info on that AVD process:\n%s",
                    Arrays.toString(ex.getStackTrace()));
        }
    }

    @Slow
    public void stopAvd(@NonNull AvdInfo info) {
        try {
            String pid = getAvdPid(info);
            if (pid != null) {
                String command;
                if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
                    command = "cmd /c \"taskkill /PID " + pid + "\"";
                } else {
                    command = "kill " + pid;
                }
                try {
                    Process p = Runtime.getRuntime().exec(command);
                    // If the process ends with non-0 it means the process doesn't exist
                    p.waitFor();
                } catch (InterruptedException e) {
                }
            }
        }
        catch (IOException e) {
        }
    }

    @Slow
    @NonNull
    public OptionalLong getPid(@NonNull AvdInfo avd) {
        OptionalLong pid = getPid(avd, "hardware-qemu.ini.lock");

        if (pid.isPresent()) {
            return pid;
        }

        return getPid(avd, "userdata-qemu.img.lock");
    }

    @NonNull
    private OptionalLong getPid(@NonNull AvdInfo avd, @NonNull String element) {
        Path file = resolve(avd, element);

        try (Scanner scanner = new Scanner(file)) {
            // TODO(http://b/233670812)
            scanner.useDelimiter("\0");

            return OptionalLong.of(scanner.nextLong());
        } catch (NoSuchFileException exception) {
            mLog.info("%s not found for %s", file, avd.getName());
            return OptionalLong.empty();
        } catch (IOException | NoSuchElementException exception) {
            mLog.error(exception, "avd = %s, file = %s", avd.getName(), file);
            return OptionalLong.empty();
        }
    }

    @VisibleForTesting
    @NonNull
    Path resolve(@NonNull AvdInfo avd, @NonNull String element) {
        Path path = mBaseAvdFolder.resolve(avd.getDataFolderPath()).resolve(element);

        // path is a file on Linux and macOS. On Windows it's a directory. Return the path to the
        // pid file under it.
        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
            return path.resolve("pid");
        }

        return path;
    }

    /** @deprecated Use {@link #getPid(AvdInfo)} */
    @Deprecated
    private String getAvdPid(@NonNull AvdInfo info) throws IOException {
        Path dataFolderPath = mBaseAvdFolder.resolve(info.getDataFolderPath());
        // this is a file on Unix, and a directory on Windows.
        Path f = dataFolderPath.resolve("hardware-qemu.ini.lock"); // $NON-NLS-1$
        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
            f = f.resolve("pid");
        }
        // This is an alternative identifier for Unix and Windows when the above one is missing.
        Path alternative = dataFolderPath.resolve("userdata-qemu.img.lock");
        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
            alternative = alternative.resolve("pid");
        }
        try {
            return CancellableFileIo.readString(f).trim();
        } catch (NoSuchFileException ignore) {
        }
        try {
            return CancellableFileIo.readString(alternative).trim();
        } catch (NoSuchFileException ignore) {
        }

        return null;
    }

    /**
     * Reloads the AVD list.
     *
     * @throws AndroidLocationsException if there was an error finding the location of the AVD
     *     folder.
     */
    @Slow
    public void reloadAvds() throws AndroidLocationsException {
        mSdkHandler.clearSystemImageManagerCache();
        // Build the list in a temp list first, in case the method throws an exception.
        // It's better than deleting the whole list before reading the new one.
        ArrayList<AvdInfo> allList = new ArrayList<>();
        buildAvdList(allList);

        synchronized (mAllAvdList) {
            mAllAvdList.clear();
            mAllAvdList.addAll(allList);
            mValidAvdList = null;
        }
    }

    /**
     * Reloads a single AVD but does not update the list.
     *
     * @param avdInfo an existing AVD
     * @return an updated AVD
     */
    @Slow
    public AvdInfo reloadAvd(@NonNull AvdInfo avdInfo) {
        AvdInfo newInfo = parseAvdInfo(avdInfo.getIniFile());
        synchronized (mAllAvdList) {
            int index = mAllAvdList.indexOf(avdInfo);
            if (index >= 0) {
                // Update the existing list of AVDs, unless the original AVD is not found, in which
                // case someone else may already have updated the list.
                replaceAvd(avdInfo, newInfo);
            }
        }
        return newInfo;
    }

    /**
     * Initializes an AvdBuilder based on a Device. This is used to set defaults for a device that
     * is under construction for the first time.
     */
    @NonNull
    public AvdBuilder createAvdBuilder(@NonNull Device device) {
        String avdName =
                AvdNamesKt.uniquifyAvdName(this, AvdNames.cleanAvdName(device.getDisplayName()));
        Path avdFolder = AvdInfo.getDefaultAvdFolder(this, avdName, true);
        AvdBuilder builder =
                new AvdBuilder(mBaseAvdFolder.resolve(avdName + ".ini"), avdFolder, device);
        builder.initializeFromDevice();
        builder.setDisplayName(AvdNamesKt.uniquifyDisplayName(this, device.getDisplayName()));
        return builder;
    }

    /**
     * Creates an AVD from the given AvdBuilder.
     *
     * @return the resulting AvdInfo, or null if the creation failed.
     */
    @Slow
    @Nullable
    public AvdInfo createAvd(@NonNull AvdBuilder builder) {
        checkArgument(Files.notExists(builder.getAvdFolder()), "AVD already exists");
        return createOrEditAvd(builder);
    }

    /**
     * Edits an existing AVD (passed as AvdInfo) using the definition from the given AvdBuilder. If
     * the AVD name or data folder path have changed, move the existing AVD to the new location
     * before applying the edits.
     *
     * @return the resulting AvdInfo, or null if the editing failed.
     */
    @Slow
    @Nullable
    public AvdInfo editAvd(@NonNull AvdInfo avdInfo, @NonNull AvdBuilder builder) {
        if (!avdInfo.getName().equals(builder.getAvdName())
                || !avdInfo.getDataFolderPath().equals(builder.getAvdFolder())) {
            if (!moveAvd(avdInfo, builder.getAvdName(), builder.getAvdFolder())) {
                return null;
            }
        }
        return createOrEditAvd(builder);
    }

    /**
     * Creates a copy of an existing AVD (passed as AvdInfo) using the definition from the given
     * AvdBuilder. This copies the full contents of the old directory, including files generated by
     * the emulator such as snapshots and hardware-qemu.ini, then writes the new config from the
     * supplied builder on top of it.
     *
     * @return the resulting AvdInfo, or null if the editing failed.
     */
    @Slow
    @Nullable
    public AvdInfo duplicateAvd(@NonNull AvdInfo avdInfo, @NonNull AvdBuilder builder) {
        checkArgument(!avdInfo.getName().equals(builder.getAvdName()), "Old and new name are the same");
        checkArgument(
            !avdInfo.getDataFolderPath().equals(builder.getAvdFolder()),
            "Old and new path are the same");
        checkNotNull(builder.getSystemImage(), "systemImage is required");

        AvdInfo duplicatedAvd =
            duplicateAvd(
                avdInfo.getDataFolderPath(),
                builder.getAvdFolder(),
                builder.getAvdName(),
                builder.getSystemImage());
        if (duplicatedAvd == null) {
            return null;
        }

        return createOrEditAvd(builder);
    }

    @Nullable
    private AvdInfo createOrEditAvd(@NonNull AvdBuilder builder) {
        String avdName = checkNotNull(builder.getAvdName(), "avdName is required");
        if (!avdName.equals(AvdNames.cleanAvdName(avdName))) {
            throw new IllegalArgumentException(
                    "AVD name \"" + avdName + "\" contains invalid characters");
        }
        return createAvd(
                checkNotNull(builder.getAvdFolder(), "avdFolder is required"),
                avdName,
                checkNotNull(builder.getSystemImage(), "systemImage is required"),
                builder.getSkin(),
                builder.getSdCard(),
                builder.configProperties(),
                builder.getUserSettings(),
                builder.getDevice().getBootProps(),
                builder.getDevice().hasPlayStore(),
                false,
                true);
    }

    /**
     * Creates a new AVD. It is expected that there is no existing AVD with this name already.
     *
     * @param avdFolder the data folder for the AVD. It will be created as needed. Unless you want
     *     to locate it in a specific directory, the ideal default is {@link
     *     AvdInfo#getDefaultAvdFolder}.
     * @param avdName the name of the AVD
     * @param systemImage the system image of the AVD
     * @param skin the skin to use, if specified. Can be null.
     * @param sdcard the parameter value for the sdCard. Can be null. This is either a path to an
     *     existing sdcard image or a sdcard size (\d+, \d+K, \dM).
     * @param hardwareConfig the hardware setup for the AVD. Can be null to use defaults.
     * @param userSettings optional settings for the AVD. Can be null.
     * @param bootProps the optional boot properties for the AVD. Can be null.
     * @param removePrevious If true remove any previous files.
     * @param editExisting If true, edit an existing AVD, changing only the minimum required. This
     *     won't remove files unless required or unless {@code removePrevious} is set.
     * @return The new {@link AvdInfo} in case of success (which has just been added to the internal
     *     list) or null in case of failure.
     */
    @Nullable
    @Slow
    public AvdInfo createAvd(
            @NonNull Path avdFolder,
            @NonNull String avdName,
            @NonNull ISystemImage systemImage,
            @Nullable Skin skin,
            @Nullable SdCard sdcard,
            @Nullable Map<String, String> hardwareConfig,
            @Nullable Map<String, String> userSettings,
            @Nullable Map<String, String> bootProps,
            boolean deviceHasPlayStore,
            boolean removePrevious,
            boolean editExisting) {
        Path iniFile = null;
        boolean needCleanup = false;
        try {
            AvdInfo newAvdInfo = null;
            HashMap<String, String> configValues = new HashMap<>();
            if (!CancellableFileIo.exists(avdFolder)) {
                // create the AVD folder.
                Files.createDirectories(avdFolder);
                inhibitCopyOnWrite(avdFolder, mLog);
                // We're not editing an existing AVD.
                editExisting = false;
            }
            else if (removePrevious) {
                // AVD already exists and removePrevious is set, try to remove the
                // directory's content first (but not the directory itself).
                try {
                    deleteContentOf(avdFolder);
                    inhibitCopyOnWrite(avdFolder, mLog);
                }
                catch (SecurityException e) {
                    mLog.warning("Failed to delete %1$s: %2$s", avdFolder.toAbsolutePath(), e);
                }
            }
            else if (!editExisting) {
                // The AVD already exists, we want to keep it, and we're not
                // editing it. We must be making a copy. Duplicate the folder.
                String oldAvdFolderPath = avdFolder.toAbsolutePath().toString();
                Path destAvdFolder = avdFolder.getParent().resolve(avdName + AVD_FOLDER_EXTENSION);
                newAvdInfo = duplicateAvd(avdFolder, destAvdFolder, avdName, systemImage);
                if (newAvdInfo == null) {
                    return null;
                }
                avdFolder = mBaseAvdFolder.resolve(newAvdInfo.getDataFolderPath());
                configValues.putAll(newAvdInfo.getProperties());
                // If the hardware config includes an SD Card path in the old directory,
                // update the path to the new directory
                if (hardwareConfig != null) {
                    String oldSdCardPath = hardwareConfig.get(ConfigKey.SDCARD_PATH);
                    if (oldSdCardPath != null && oldSdCardPath.startsWith(oldAvdFolderPath)) {
                        // The hardware config points to the old directory. Substitute the new
                        // directory.
                        hardwareConfig.put(
                                ConfigKey.SDCARD_PATH,
                                oldSdCardPath.replace(
                                        oldAvdFolderPath,
                                        newAvdInfo.getDataFolderPath().toString()));
                    }
                }
            }

            if (!setImagePathProperties(systemImage, configValues)) {
                mLog.warning("Failed to set image path properties in the AVD folder.");
                throw new AvdMgrException();
            }

            // Tag and abi type
            IdDisplay tag = systemImage.getTag();
            configValues.put(ConfigKey.TAG_ID, tag.getId());
            configValues.put(ConfigKey.TAG_DISPLAY, tag.getDisplay());
            List<IdDisplay> tags = systemImage.getTags();
            configValues.put(
                    ConfigKey.TAG_IDS, tags.stream().map(IdDisplay::getId).collect(joining(",")));
            configValues.put(
                    ConfigKey.TAG_DISPLAYNAMES,
                    tags.stream().map(IdDisplay::getDisplay).collect(joining(",")));
            configValues.put(ConfigKey.ABI_TYPE, systemImage.getPrimaryAbiType());
            configValues.put(ConfigKey.PLAYSTORE_ENABLED, Boolean.toString(deviceHasPlayStore && systemImage.hasPlayStore()));
            configValues.put(
                    ConfigKey.ARC, Boolean.toString(SystemImageTags.CHROMEOS_TAG.equals(tag)));

            // Add the hardware config to the config file. We copy values from the following
            // sources, in order, with later sources overriding earlier ones:
            // 1. The hardware.ini file supplied by the system image, if present
            // 2. The hardware.ini file supplied by the skin, if present
            // 3. The hardwareConfig argument (i.e. user-supplied settings)
            // 4. The system image CPU architecture
            addSystemImageHardwareConfig(systemImage, configValues);
            if (skin != null) {
                addSkin(skin, configValues);
            }
            if (hardwareConfig != null) {
                configValues.putAll(hardwareConfig);
            }
            addCpuArch(systemImage, configValues, mLog);

            // We've done as much work as we can without writing to disk. Now start writing the
            // .ini files, creating the SD card (if necessary), copying userdata.img, etc. After
            // this point, we will delete the AVD if something goes wrong, since it will be in an
            // unknown state.

            iniFile =
                    createAvdIniFile(
                            avdName, avdFolder, removePrevious, systemImage.getAndroidVersion());

            needCleanup = true;

            createAvdUserdata(systemImage, avdFolder);

            if (sdcard != null) {
                configValues.putAll(sdcard.configEntries());
            }
            if (sdcard instanceof InternalSdCard) {
                createAvdSdCard((InternalSdCard) sdcard, editExisting, avdFolder);
            }

            // Finally write configValues to config.ini
            writeIniFile(avdFolder.resolve(CONFIG_INI), configValues, true);

            if (userSettings != null) {
                try {
                    writeIniFile(avdFolder.resolve(USER_SETTINGS_INI), userSettings, true);
                }
                catch (IOException e) {
                    mLog.warning("Could not write user settings file (at %1$s): %2$s",
                                 avdFolder.resolve(USER_SETTINGS_INI).toString(), e);
                }
            }

            if (bootProps != null && !bootProps.isEmpty()) {
                Path bootPropsFile = avdFolder.resolve(BOOT_PROP);
                writeIniFile(bootPropsFile, bootProps, false);
            }

            AvdInfo oldAvdInfo = getAvd(avdName, false /*validAvdOnly*/);

            if (newAvdInfo == null) {
                newAvdInfo =
                        createAvdInfoObject(
                                systemImage,
                                removePrevious,
                                editExisting,
                                iniFile,
                                avdFolder,
                                oldAvdInfo,
                                configValues,
                                userSettings);
            }

            if ((removePrevious || editExisting) &&
                    oldAvdInfo != null &&
                    !oldAvdInfo.getDataFolderPath().equals(newAvdInfo.getDataFolderPath())) {
                mLog.warning(
                        "Removing previous AVD directory at %s", oldAvdInfo.getDataFolderPath());
                // Remove the old data directory
                try {
                    PathUtils.deleteRecursivelyIfExists(
                            mBaseAvdFolder.resolve(oldAvdInfo.getDataFolderPath()));
                } catch (IOException exception) {
                    mLog.warning("Failed to delete %1$s: %2$s", oldAvdInfo.getDataFolderPath());
                }
            }

            needCleanup = false;
            return newAvdInfo;
        } catch (AvdMgrException e) {
            // Warning has already been logged
        } catch (SecurityException | AndroidLocationsException | IOException e) {
            mLog.warning("%1$s", e);
        } finally {
            if (needCleanup) {
                if (iniFile != null) {
                    try {
                        PathUtils.deleteRecursivelyIfExists(iniFile);
                    } catch (IOException ignore) {
                    }
                }

                try {
                    PathUtils.deleteRecursivelyIfExists(avdFolder);
                } catch (Exception e) {
                    mLog.warning("Failed to delete %1$s: %2$s", avdFolder.toAbsolutePath(), e);
                }
            }
        }
        return null;
    }

    /**
     * Duplicates an existing AVD. Update the 'config.ini' and 'hardware-qemu.ini' files to
     * reference the new name and path.
     *
     * @param avdFolder the data folder of the AVD to be duplicated
     * @param newAvdName name of the new copy
     * @param systemImage system image that the AVD uses
     */
    @Nullable
    private AvdInfo duplicateAvd(
            @NonNull Path avdFolder,
            @NonNull Path destAvdFolder,
            @NonNull String newAvdName,
            @NonNull ISystemImage systemImage) {
        try {
            inhibitCopyOnWrite(destAvdFolder, mLog);

            ProgressIndicator progInd = new ConsoleProgressIndicator();
            progInd.setText("Copying files");
            progInd.setIndeterminate(true);
            FileOpUtils.recursiveCopy(
                    avdFolder,
                    destAvdFolder,
                    false,
                    path -> !path.toString().endsWith(".lock"), // Do not copy *.lock files
                    progInd);

            // Modify the ID and display name in the new config.ini
            Path configIni = destAvdFolder.resolve(CONFIG_INI);
            Map<String, String> configVals = parseIniFile(new PathFileWrapper(configIni), mLog);
            Map<String, String> userSettingsVals =
                    AvdInfo.parseUserSettingsFile(destAvdFolder, mLog);
            configVals.put(ConfigKey.AVD_ID, newAvdName);
            configVals.put(ConfigKey.DISPLAY_NAME, newAvdName);
            writeIniFile(configIni, configVals, true);

            // Update the AVD name and paths in the new copies of config.ini and hardware-qemu.ini
            String origAvdName = avdFolder.getFileName().toString().replace(".avd", "");
            String origAvdFolder = avdFolder.toAbsolutePath().toString();
            String newAvdFolder = destAvdFolder.toAbsolutePath().toString();

            configVals =
                    updateNameAndIniPaths(
                            configIni, origAvdName, origAvdFolder, newAvdName, newAvdFolder);

            Path hwQemu = destAvdFolder.resolve(HARDWARE_QEMU_INI);
            updateNameAndIniPaths(hwQemu, origAvdName, origAvdFolder, newAvdName, newAvdFolder);

            // Create <AVD name>.ini
            Path metadataIniFile =
                    createAvdIniFile(
                            newAvdName, destAvdFolder, false, systemImage.getAndroidVersion());

            // Create an AVD object from these files
            return new AvdInfo(
                    metadataIniFile, destAvdFolder, systemImage, configVals, userSettingsVals);
        } catch (AndroidLocationsException | IOException e) {
            mLog.warning("Exception while duplicating an AVD: %1$s", e);
            return null;
        }
    }

    /**
     * Modifies an ini file to switch values from an old AVD name and path to a new AVD name and
     * path. Values that are {@code oldName} are switched to {@code newName} Values that start with
     * {@code oldPath} are modified to start with {@code newPath}
     *
     * @return the updated ini settings
     */
    @Nullable
    private Map<String, String> updateNameAndIniPaths(
            @NonNull Path iniFile,
            @NonNull String oldName,
            @NonNull String oldPath,
            @NonNull String newName,
            @NonNull String newPath)
            throws IOException {
        Map<String, String> iniVals = parseIniFile(new PathFileWrapper(iniFile), mLog);
        if (iniVals != null) {
            for (Map.Entry<String, String> iniEntry : iniVals.entrySet()) {
                String origIniValue = iniEntry.getValue();
                if (origIniValue.equals(oldName)) {
                    iniVals.put(iniEntry.getKey(), newName);
                }
                if (origIniValue.startsWith(oldPath)) {
                    String newIniValue = origIniValue.replace(oldPath, newPath);
                    iniVals.put(iniEntry.getKey(), newIniValue);
                }
            }
            writeIniFile(iniFile, iniVals, true);
        }
        return iniVals;
    }

    /**
     * Returns the path to the target images folder as a relative path to the SDK, if the folder
     * is not empty. If the image folder is empty or does not exist, <code>null</code> is returned.
     * @throws InvalidTargetPathException if the target image folder is not in the current SDK.
     */
    private String getImageRelativePath(@NonNull ISystemImage systemImage)
            throws InvalidTargetPathException {

        Path folder = systemImage.getLocation();
        String imageFullPath = folder.toAbsolutePath().toString();

        // make this path relative to the SDK location
        String sdkLocation = mSdkHandler.getLocation().toAbsolutePath().toString();
        if (!imageFullPath.startsWith(sdkLocation)) {
            // this really really should not happen.
            assert false;
            throw new InvalidTargetPathException("Target location is not inside the SDK.");
        }

        String[] list;
        try (Stream<Path> contents = CancellableFileIo.list(folder)) {
            list =
                    contents.map(path -> path.getFileName().toString())
                            .filter(path -> IMAGE_NAME_PATTERN.matcher(path).matches())
                            .toArray(String[]::new);
        } catch (IOException e) {
            return null;
        }
        if (list.length > 0) {
            // Remove the SDK root path, e.g. /sdk/dir1/dir2 -> /dir1/dir2
            imageFullPath = imageFullPath.substring(sdkLocation.length());
            // The path is relative, so it must not start with a file separator
            String separator = folder.getFileSystem().getSeparator();
            if (imageFullPath.startsWith(separator)) {
                imageFullPath = imageFullPath.substring(separator.length());
            }
            // For compatibility with previous versions, we denote folders
            // by ending the path with file separator
            if (!imageFullPath.endsWith(separator)) {
                imageFullPath += separator;
            }

            return imageFullPath;
        }

        return null;
    }

    /**
     * Creates the metadata ini file for an AVD.
     *
     * @param avdName the basename of the metadata ini file of the AVD.
     * @param avdFolder path for the data folder of the AVD.
     * @param removePrevious True if an existing ini file should be removed.
     * @throws AndroidLocationsException if there's a problem getting android root directory.
     * @throws IOException if {@link Files#delete(Path)} ()} fails.
     */
    private Path createAvdIniFile(
            @NonNull String avdName,
            @NonNull Path avdFolder,
            boolean removePrevious,
            @NonNull AndroidVersion version)
            throws AndroidLocationsException, IOException {
        Path iniFile = AvdInfo.getDefaultIniFile(this, avdName);

        if (removePrevious) {
            if (CancellableFileIo.isRegularFile(iniFile)) {
                Files.delete(iniFile);
            } else if (CancellableFileIo.isDirectory(iniFile)) {
                try {
                    PathUtils.deleteRecursivelyIfExists(iniFile);
                } catch (IOException ignore) {
                }
            }
        }

        String absPath = avdFolder.toAbsolutePath().toString();
        String relPath = null;
        Path androidFolder = mSdkHandler.getAndroidFolder();
        if (androidFolder == null) {
            throw new AndroidLocationsException(
                    "Can't locate Android SDK installation directory for the AVD .ini file.");
        }
        String androidPath = androidFolder.toAbsolutePath() + File.separator;
        if (absPath.startsWith(androidPath)) {
            // Compute the AVD path relative to the android path.
            relPath = absPath.substring(androidPath.length());
        }

        HashMap<String, String> values = new HashMap<>();
        if (relPath != null) {
            values.put(MetadataKey.REL_PATH, relPath);
        }
        values.put(MetadataKey.ABS_PATH, absPath);
        values.put(MetadataKey.TARGET, AndroidTargetHash.getPlatformHashString(version));
        writeIniFile(iniFile, values, true);

        return iniFile;
    }

    /**
     * Creates the metadata ini file for an AVD.
     *
     * @param info of the AVD.
     * @throws AndroidLocationsException if there's a problem getting android root directory.
     * @throws IOException if {@link Files#delete(Path)} fails.
     */
    private Path createAvdIniFile(@NonNull AvdInfo info)
            throws AndroidLocationsException, IOException {
        return createAvdIniFile(
                info.getName(),
                mBaseAvdFolder.resolve(info.getDataFolderPath()),
                false,
                info.getAndroidVersion());
    }

    /**
     * Actually deletes the files of an existing AVD.
     *
     * <p>This also remove it from the manager's list, The caller does not need to call {@link
     * #removeAvd(AvdInfo)} afterwards.
     *
     * <p>This method is designed to somehow work with an unavailable AVD, that is an AVD that could
     * not be loaded due to some error. That means this method still tries to remove the AVD ini
     * file or its folder if it can be found. An error will be output if any of these operations
     * fail.
     *
     * @param avdInfo the information on the AVD to delete
     * @return True if the AVD was deleted with no error.
     */
    @Slow
    public boolean deleteAvd(@NonNull AvdInfo avdInfo) {
        try {
            boolean error = false;

            Path f = avdInfo.getIniFile();
            try {
                Files.deleteIfExists(f);
            } catch (IOException exception) {
                mLog.warning("Failed to delete %1$s\n", f);
                error = true;
            }

            Path path = avdInfo.getDataFolderPath();
            f = mBaseAvdFolder.resolve(path);
            try {
                PathUtils.deleteRecursivelyIfExists(f);
            } catch (IOException exception) {
                mLog.warning("Failed to delete %1$s\n", f);
                StringWriter writer = new StringWriter();
                exception.printStackTrace(new PrintWriter(writer));
                mLog.warning(writer.toString());
                error = true;
            }

            removeAvd(avdInfo);

            if (error) {
                mLog.info(
                        "\nAVD '%1$s' deleted with errors. See errors above.\n", avdInfo.getName());
            } else {
                mLog.info("\nAVD '%1$s' deleted.\n", avdInfo.getName());
                return true;
            }

        } catch (SecurityException e) {
            mLog.warning("%1$s", e);
        }
        return false;
    }

    /**
     * Moves and/or renames an existing AVD and its files. This also change it in the manager's
     * list.
     *
     * <p>The caller should make sure the name or path given are valid, do not exist and are
     * actually different than current values.
     *
     * @param avdInfo the information on the AVD to move.
     * @param newAvdName the new name of the AVD if non null.
     * @param newAvdFolder the new data folder if non null.
     * @return True if the move succeeded or there was nothing to do. If false, this method will
     *     have had already output error in the log.
     */
    @Slow
    public boolean moveAvd(
            @NonNull AvdInfo avdInfo, @Nullable String newAvdName, @Nullable Path newAvdFolder) {
        try {
            if (newAvdFolder != null) {
                Path f = mBaseAvdFolder.resolve(avdInfo.getDataFolderPath());
                mLog.info("Moving '%1$s' to '%2$s'.\n", avdInfo.getDataFolderPath(), newAvdFolder);
                try {
                    Files.move(f, mBaseAvdFolder.resolve(newAvdFolder));
                } catch (IOException exception) {
                    mLog.error(
                            exception,
                            "Failed to move '%1$s' to '%2$s'.\n",
                            avdInfo.getDataFolderPath(),
                            newAvdFolder);
                    return false;
                }

                // update AVD info
                AvdInfo info =
                        new AvdInfo(
                                avdInfo.getIniFile(),
                                newAvdFolder,
                                avdInfo.getSystemImage(),
                                avdInfo.getProperties(),
                                avdInfo.getUserSettings());
                replaceAvd(avdInfo, info);

                // update the ini file
                createAvdIniFile(info);
            }

            if (newAvdName != null) {
                Path oldMetadataIniFile = avdInfo.getIniFile();
                Path newMetadataIniFile = AvdInfo.getDefaultIniFile(this, newAvdName);

                mLog.warning("Moving '%1$s' to '%2$s'.", oldMetadataIniFile, newMetadataIniFile);
                try {
                    Files.move(oldMetadataIniFile, newMetadataIniFile);
                } catch (IOException exception) {
                    mLog.warning(
                            "Failed to move '%1$s' to '%2$s'.",
                            oldMetadataIniFile,
                            newMetadataIniFile);
                    return false;
                }

                // update AVD info
                AvdInfo info =
                        new AvdInfo(
                                avdInfo.getIniFile(),
                                avdInfo.getDataFolderPath(),
                                avdInfo.getSystemImage(),
                                avdInfo.getProperties(),
                                avdInfo.getUserSettings());
                replaceAvd(avdInfo, info);
            }

            mLog.info("AVD '%1$s' moved.\n", avdInfo.getName());

        } catch (AndroidLocationsException | IOException e) {
            mLog.warning("$1%s", e);
            return false;
        }

        // nothing to do or succeeded
        return true;
    }

    /**
     * Recursively deletes a folder's content (but not the folder itself).
     *
     * @throws SecurityException like {@link File#delete()} does if file/folder is not writable.
     */
    private boolean deleteContentOf(Path folder) throws SecurityException {
        boolean success = true;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder)) {
            for (Path entry : entries) {
                try {
                    PathUtils.deleteRecursivelyIfExists(entry);
                } catch (IOException ignore) {
                    success = false;
                }
            }
        } catch (IOException exception) {
            return false;
        }
        return success;
    }

    /**
     * Returns a list of files that are potential AVD ini files.
     *
     * <p>This lists the $HOME/.android/avd/<name>.ini files. Such files are properties file than
     * then indicate where the AVD folder is located.
     *
     * <p>Note: the method is to be considered private. It is made protected so that unit tests can
     * easily override the AVD root.
     *
     * @return A new {@link Path} array or null. The array might be empty.
     * @throws AndroidLocationsException if there's a problem getting android root directory.
     */
    private Path[] buildAvdFilesList() throws AndroidLocationsException {
        // ensure folder validity.
        if (CancellableFileIo.isRegularFile(mBaseAvdFolder)) {
            throw new AndroidLocationsException(
                    String.format(
                            "%1$s is a regular file; expected a directory.",
                            mBaseAvdFolder.toAbsolutePath()));
        } else if (CancellableFileIo.notExists(mBaseAvdFolder)) {
            // folder is not there, we create it and return
            try {
                Files.createDirectories(mBaseAvdFolder);
            } catch (IOException e) {
                throw new AndroidLocationsException(
                        "Unable to create AVD home directory: " + mBaseAvdFolder.toAbsolutePath(),
                        e);
            }
            return null;
        }

        Path[] avds = new Path[0];
        try (Stream<Path> contents = CancellableFileIo.list(mBaseAvdFolder)) {
            avds =
                    contents.filter(
                                    path -> {
                                        if (INI_NAME_PATTERN
                                                .matcher(path.getFileName().toString())
                                                .matches()) {
                                            // check it's a file and not a folder
                                            return Files.isRegularFile(path);
                                        }

                                        return false;
                                    })
                            .toArray(Path[]::new);
        } catch (IOException ignore) {
        }
        return avds;
    }

    /**
     * Computes the internal list of available AVDs
     *
     * @param allList the list to contain all the AVDs
     * @throws AndroidLocationsException if there's a problem getting android root directory.
     */
    private void buildAvdList(ArrayList<AvdInfo> allList) throws AndroidLocationsException {
        Path[] avds = buildAvdFilesList();
        if (avds != null) {
            for (Path avd : avds) {
                AvdInfo info = parseAvdInfo(avd);
                if (info != null && !allList.contains(info)) {
                    allList.add(info);
                }
            }
        }
    }

    /**
     * Parses an AVD .ini file to create an {@link AvdInfo}.
     *
     * @param metadataIniFile The path to the AVD .ini file
     * @return A new {@link AvdInfo} with an {@link AvdStatus} indicating whether this AVD is valid
     *     or not.
     */
    @VisibleForTesting
    @Slow
    AvdInfo parseAvdInfo(@NonNull Path metadataIniFile) {
        Map<String, String> metadata = parseIniFile(new PathFileWrapper(metadataIniFile), mLog);

        Path avdFolder = null;
        if (metadata != null) {
            String path = metadata.get(MetadataKey.ABS_PATH);
            avdFolder = path == null ? null : metadataIniFile.resolve(path);
            if (avdFolder == null
                    || !(CancellableFileIo.isDirectory(mBaseAvdFolder.resolve(avdFolder)))) {
                // Try to fallback on the relative path, if present.
                String relPath = metadata.get(MetadataKey.REL_PATH);
                if (relPath != null) {
                    Path androidFolder = mSdkHandler.getAndroidFolder();
                    Path f =
                            androidFolder == null
                                    ? mSdkHandler.toCompatiblePath(relPath)
                                    : androidFolder.resolve(relPath);
                    if (CancellableFileIo.isDirectory(f)) {
                        avdFolder = f;
                    }
                }
            }
        }
        if (avdFolder == null
                || !(CancellableFileIo.isDirectory(mBaseAvdFolder.resolve(avdFolder)))) {
            // Corrupted .ini file
            return new AvdInfo(
                    metadataIniFile,
                    metadataIniFile,
                    null,
                    null,
                    null,
                    AvdStatus.ERROR_CORRUPTED_INI);
        }

        PathFileWrapper configIniFile;
        Map<String, String> properties = null;
        LoggerProgressIndicatorWrapper progress =
                new LoggerProgressIndicatorWrapper(mLog) {
                    @Override
                    public void logVerbose(@NonNull String s) {
                        // Skip verbose messages }
                    }
                };

        // load the AVD properties.
        configIniFile = new PathFileWrapper(mBaseAvdFolder.resolve(avdFolder).resolve(CONFIG_INI));

        if (!configIniFile.exists()) {
            mLog.warning("Missing file '%1$s'.", configIniFile.getOsLocation());
            configIniFile = null;
        } else {
            properties = parseIniFile(configIniFile, mLog);
        }

        // Check if the value of image.sysdir.1 is valid.
        String imageSysDir = null;
        ISystemImage sysImage = null;
        if (properties != null) {
            imageSysDir = properties.get(ConfigKey.IMAGES_1);
            if (imageSysDir != null) {
                Path sdkLocation = mSdkHandler.getLocation();
                Path imageDir =
                        sdkLocation == null
                                ? mBaseAvdFolder.resolve(imageSysDir)
                                : sdkLocation.resolve(imageSysDir);
                sysImage = mSdkHandler.getSystemImageManager(progress).getImageAt(imageDir);
            }
        }


        // Get the device status if this AVD is associated with a device
        DeviceStatus deviceStatus = null;
        boolean updateHashV2 = false;
        if (properties != null) {
            String deviceName = properties.get(ConfigKey.DEVICE_NAME);
            String deviceMfctr = properties.get(ConfigKey.DEVICE_MANUFACTURER);

            Device d;

            if (deviceName != null && deviceMfctr != null) {
                d = mDeviceManager.getDevice(deviceName, deviceMfctr);
                deviceStatus = d == null ? DeviceStatus.MISSING : DeviceStatus.EXISTS;

                if (d != null) {
                    updateHashV2 = true;
                    String hashV2 = properties.get(ConfigKey.DEVICE_HASH_V2);
                    if (hashV2 != null) {
                        String newHashV2 = DeviceManager.hasHardwarePropHashChanged(d, hashV2);
                        if (newHashV2 == null) {
                            updateHashV2 = false;
                        } else {
                            properties.put(ConfigKey.DEVICE_HASH_V2, newHashV2);
                        }
                    }

                    String hashV1 = properties.get(ConfigKey.DEVICE_HASH_V1);
                    if (hashV1 != null) {
                        // will recompute a hash v2 and save it below
                        properties.remove(ConfigKey.DEVICE_HASH_V1);
                    }
                }
            }
        }


        // TODO: What about missing sdcard, skins, etc?

        AvdStatus status;

        if (configIniFile == null) {
            status = AvdStatus.ERROR_CONFIG;
        } else if (properties == null || imageSysDir == null) {
            status = AvdStatus.ERROR_PROPERTIES;
        } else if (deviceStatus == DeviceStatus.MISSING) {
            status = AvdStatus.ERROR_DEVICE_MISSING;
        } else if (sysImage == null) {
            status = AvdStatus.ERROR_IMAGE_MISSING;
        } else {
            status = AvdStatus.OK;
        }

        if (properties == null) {
            properties = new HashMap<>();
        }

        if (!properties.containsKey(ConfigKey.ANDROID_API)
                && !properties.containsKey(ConfigKey.ANDROID_CODENAME)) {
            String targetHash = metadata.get(MetadataKey.TARGET);
            if (targetHash != null) {
                AndroidVersion version = AndroidTargetHash.getVersionFromHash(targetHash);
                if (version != null) {
                    properties.put(ConfigKey.ANDROID_API, Integer.toString(version.getApiLevel()));
                    if (version.getExtensionLevel() != null) {
                        properties.put(
                                ConfigKey.ANDROID_EXTENSION,
                                Integer.toString(version.getExtensionLevel()));
                        properties.put(
                                ConfigKey.ANDROID_IS_BASE_EXTENSION,
                                Boolean.toString(version.isBaseExtension()));
                    }
                    if (version.getCodename() != null) {
                        properties.put(ConfigKey.ANDROID_CODENAME, version.getCodename());
                    }
                    if (!version.isBaseExtension() && version.getExtensionLevel() != null) {
                        properties.put(
                                ConfigKey.ANDROID_EXTENSION_LEVEL,
                                Integer.toString(version.getExtensionLevel()));
                    }
                }
            }
        }
        // Set the "tag.ids" property if it is not present but the "tag.id" property is.
        if (!properties.containsKey(ConfigKey.TAG_IDS)) {
            String tagId = properties.get(ConfigKey.TAG_ID);
            if (tagId != null && !tagId.isEmpty()) {
                properties.put(ConfigKey.TAG_IDS, tagId);
            }
        }

        Map<String, String> userSettings = AvdInfo.parseUserSettingsFile(avdFolder, mLog);

        AvdInfo info =
                new AvdInfo(metadataIniFile, avdFolder, sysImage, properties, userSettings, status);

        if (updateHashV2) {
            try {
                return updateDeviceChanged(info);
            } catch (IOException ignore) {}
        }

        return info;
    }

    /**
     * Writes a .ini file from a set of properties, using UTF-8 encoding. The keys are sorted. The
     * file should be read back later by {@link #parseIniFile(IAbstractFile, ILogger)}.
     *
     * @param iniFile The file to generate.
     * @param values The properties to place in the ini file.
     * @param addEncoding When true, add a property {@link ConfigKey#ENCODING} indicating the
     *     encoding used to write the file.
     * @throws IOException if {@link FileWriter} fails to open, write or close the file.
     */
    private void writeIniFile(Path iniFile, Map<String, String> values, boolean addEncoding)
            throws IOException {

        Charset charset = Charsets.UTF_8;
        try (OutputStreamWriter writer =
                new OutputStreamWriter(Files.newOutputStream(iniFile), charset)) {
            if (addEncoding) {
                // Write down the charset we're using in case we want to use it later.
                values = new HashMap<>(values);
                values.put(ConfigKey.ENCODING, charset.name());
            }

            ArrayList<String> keys = new ArrayList<>(values.keySet());
            // Do not save these values (always recompute)
            keys.remove(ConfigKey.ANDROID_API);
            keys.remove(ConfigKey.ANDROID_EXTENSION);
            keys.remove(ConfigKey.ANDROID_IS_BASE_EXTENSION);
            keys.remove(ConfigKey.ANDROID_CODENAME);
            Collections.sort(keys);

            for (String key : keys) {
                String value = values.get(key);
                if (value != null) {
                    writer.write(String.format("%1$s=%2$s\n", key, value));
                }
            }
        }
    }

    /**
     * Parses a property file and returns a map of the content.
     *
     * <p>If the file is not present, null is returned with no error messages sent to the log.
     *
     * <p>Charset encoding will be either the system's default or the one specified by the {@link
     * ConfigKey#ENCODING} key if present.
     *
     * @param propFile the property file to parse
     * @param logger the ILogger object receiving warning/error from the parsing.
     * @return the map of (key,value) pairs, or null if the parsing failed.
     */
    @Slow
    public static Map<String, String> parseIniFile(
            @NonNull IAbstractFile propFile, @Nullable ILogger logger) {
        return parseIniFileImpl(propFile, logger, null);
    }

    /**
     * Implementation helper for the {@link #parseIniFile(IAbstractFile, ILogger)} method.
     * Don't call this one directly.
     *
     * @param propFile the property file to parse
     * @param log the ILogger object receiving warning/error from the parsing.
     * @param charset When a specific charset is specified, this will be used as-is.
     *   When null, the default charset will first be used and if the key
     *   {@link ConfigKey#ENCODING} is found the parsing will restart using that specific
     *   charset.
     * @return the map of (key,value) pairs, or null if the parsing failed.
     */
    private static Map<String, String> parseIniFileImpl(
            @NonNull IAbstractFile propFile,
            @Nullable ILogger log,
            @Nullable Charset charset) {

        BufferedReader reader = null;
        try {
            boolean canChangeCharset = false;
            if (charset == null) {
                canChangeCharset = true;
                charset = Charsets.ISO_8859_1;
            }
            reader = new BufferedReader(new InputStreamReader(propFile.getContents(), charset));

            String line;
            Map<String, String> map = new HashMap<>();
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && line.charAt(0) != '#') {

                    Matcher m = INI_LINE_PATTERN.matcher(line);
                    if (m.matches()) {
                        // Note: we do NOT escape values.
                        String key = m.group(1);
                        String value = m.group(2);

                        // If we find the charset encoding and it's not the same one and
                        // it's a valid one, re-read the file using that charset.
                        if (canChangeCharset
                                && ConfigKey.ENCODING.equals(key)
                                && !charset.name().equals(value)
                                && Charset.isSupported(value)) {
                            charset = Charset.forName(value);
                            return parseIniFileImpl(propFile, log, charset);
                        }

                        map.put(key, value);
                    } else {
                        if (log != null) {
                            log.warning("Error parsing '%1$s': \"%2$s\" is not a valid syntax",
                                    propFile.getOsLocation(),
                                    line);
                        }
                        return null;
                    }
                }
            }

            return map;
        } catch (FileNotFoundException e) {
            // this should not happen since we usually test the file existence before
            // calling the method.
            // Return null below.
        } catch (IOException | StreamException e) {
            if (log != null) {
                log.warning("Error parsing '%1$s': %2$s.",
                        propFile.getOsLocation(),
                        e.getMessage());
            }
        } finally {
            try {
                Closeables.close(reader, true /* swallowIOException */);
            } catch (IOException e) {
                // cannot happen.
            }
        }

        return null;
    }

    /**
     * Removes an {@link AvdInfo} from the internal list.
     *
     * @param avdInfo The {@link AvdInfo} to remove.
     * @return true if this {@link AvdInfo} was present and has been removed.
     */
    public boolean removeAvd(AvdInfo avdInfo) {
        synchronized (mAllAvdList) {
            if (mAllAvdList.remove(avdInfo)) {
                mValidAvdList = null;
                return true;
            }
        }

        return false;
    }

    @Slow
    public AvdInfo updateAvd(AvdInfo avd, Map<String, String> newProperties) throws IOException {
        // now write the config file
        Path configIniFile = mBaseAvdFolder.resolve(avd.getDataFolderPath()).resolve(CONFIG_INI);
        writeIniFile(configIniFile, newProperties, true);

        // finally create a new AvdInfo for this unbroken avd and add it to the list.
        // instead of creating the AvdInfo object directly we reparse it, to detect other possible
        // errors
        // FIXME: We may want to create this AvdInfo by reparsing the AVD instead. This could detect
        // other errors.
        AvdInfo newAvd =
                new AvdInfo(
                        avd.getIniFile(),
                        avd.getDataFolderPath(),
                        avd.getSystemImage(),
                        newProperties,
                        avd.getUserSettings());

        replaceAvd(avd, newAvd);

        return newAvd;
    }

    /**
     * Updates the device-specific part of an AVD ini.
     *
     * @param avd the AVD to update.
     * @return The new AVD on success.
     */
    @Slow
    @Nullable
    public AvdInfo updateDeviceChanged(@NonNull AvdInfo avd) throws IOException {
        // Overwrite the properties derived from the device and nothing else
        Map<String, String> properties = new HashMap<>(avd.getProperties());

        Device d = mDeviceManager.getDevice(avd);
        if (d == null) {
            mLog.warning("Base device information incomplete or missing.");
            return null;
        }

        // The device has a RAM size, but we don't want to use it.
        // Instead, we'll keep the AVD's existing RAM size setting.
        final Map<String, String> deviceHwProperties = DeviceManager.getHardwareProperties(d);
        deviceHwProperties.remove(ConfigKey.RAM_SIZE);
        properties.putAll(deviceHwProperties);
        try {
            return updateAvd(avd, properties);
        } catch (IOException e) {
            mLog.warning("%1$s", e);
            return null;
        }
    }

    /**
     * Sets the paths to the system images in a properties map.
     *
     * @param image the system image for this avd.
     * @param properties the properties in which to set the paths.
     * @return true if success, false if some path are missing.
     */
    private boolean setImagePathProperties(ISystemImage image, Map<String, String> properties) {
        properties.remove(ConfigKey.IMAGES_1);
        properties.remove(ConfigKey.IMAGES_2);

        try {
            String property = ConfigKey.IMAGES_1;

            // First the image folders of the target itself
            String imagePath = getImageRelativePath(image);
            if (imagePath != null) {
                properties.put(property, imagePath);
                return true;
            }
        } catch (InvalidTargetPathException e) {
            mLog.warning("%1$s", e);
        }

        return false;
    }

    /**
     * Replaces an old {@link AvdInfo} with a new one in the lists storing them.
     * @param oldAvd the {@link AvdInfo} to remove.
     * @param newAvd the {@link AvdInfo} to add.
     */
    private void replaceAvd(AvdInfo oldAvd, AvdInfo newAvd) {
        synchronized (mAllAvdList) {
            mAllAvdList.remove(oldAvd);
            mAllAvdList.add(newAvd);
            mValidAvdList = null;
        }
    }

    /**
     * For old system images, copies userdata.img from the system image to the AVD. Does nothing for
     * new system images which contain a "data" folder.
     */
    private void createAvdUserdata(@NonNull ISystemImage systemImage, @NonNull Path avdFolder)
            throws IOException, AvdMgrException {
        // Copy userdata.img from system-images to the *.avd directory
        Path imageFolder = systemImage.getLocation();
        Path userdataSrc = imageFolder.resolve(USERDATA_IMG);

        if (CancellableFileIo.notExists(userdataSrc)) {
            if (CancellableFileIo.isDirectory(imageFolder.resolve(DATA_FOLDER))) {
                // Because this image includes a data folder, a
                // userdata.img file is not needed. Don't signal
                // an error.
                // (The emulator will access the 'data' folder directly;
                //  we do not need to copy it over.)
                return;
            }
            mLog.warning(
                    "Unable to find a '%1$s' file for ABI %2$s to copy into the AVD folder.",
                    USERDATA_IMG, systemImage.getPrimaryAbiType());
            throw new AvdMgrException();
        }

        Path userdataDest = avdFolder.resolve(USERDATA_IMG);

        if (CancellableFileIo.notExists(userdataDest)) {
            FileUtils.copyFile(userdataSrc, userdataDest);

            if (CancellableFileIo.notExists(userdataDest)) {
                mLog.warning("Unable to create '%1$s' file in the AVD folder.", userdataDest);
                throw new AvdMgrException();
            }
        }
    }

    /**
     * Add the CPU architecture of the system image to the AVD configuration.
     *
     * @param systemImage the system image of the AVD
     * @param values settings for the AVD
     * @param log receives error messages
     */
    private void addCpuArch(
            @NonNull ISystemImage systemImage,
            @NonNull Map<String, String> values,
            @NonNull ILogger log)
            throws AvdMgrException {

        String abiType = systemImage.getPrimaryAbiType();
        Abi abi = Abi.getEnum(abiType);
        if (abi != null) {
            String arch = abi.getCpuArch();
            // Chrome OS image is a special case: the system image
            // is actually x86_64 while the android container inside
            // it is x86. We have to set it x86_64 to let it boot
            // under android emulator.
            if (arch.equals(SdkConstants.CPU_ARCH_INTEL_ATOM)
                    && SystemImageTags.CHROMEOS_TAG.equals(systemImage.getTag())) {
                arch = SdkConstants.CPU_ARCH_INTEL_ATOM64;
            }
            values.put(ConfigKey.CPU_ARCH, arch);

            String model = abi.getCpuModel();
            if (model != null) {
                values.put(ConfigKey.CPU_MODEL, model);
            }
        } else {
            log.warning("ABI %1$s is not supported by this version of the SDK Tools", abiType);
            throw new AvdMgrException();
        }
    }

  /** Adds parameters for the given skin to the AVD config. */
  private void addSkin(@NonNull Skin skin, @NonNull Map<String, String> values) throws AvdMgrException {
    String skinName = skin.getName();
    String skinPath;

    if (skin instanceof OnDiskSkin) {
        Path path = ((OnDiskSkin) skin).getPath();
        if (CancellableFileIo.notExists(path)) {
            mLog.warning("Skin '%1$s' does not exist at %2$s.", skinName, path);
            throw new AvdMgrException();
        }

        skinPath = path.toString();

        // If the skin contains a hardware.ini, add its contents to the AVD config.
        PathFileWrapper skinHardwareFile = new PathFileWrapper(path.resolve(HARDWARE_INI));
        if (skinHardwareFile.exists()) {
            Map<String, String> skinHardwareConfig =
                    ProjectProperties.parsePropertyFile(skinHardwareFile, mLog);

            if (skinHardwareConfig != null) {
                values.putAll(skinHardwareConfig);
            }
        }
    } else if (skin instanceof GenericSkin) {
        skinPath = skinName;
    } else {
        throw new IllegalArgumentException("Unknown skin type");
    }

    // Set skin.name for display purposes in the AVD manager and
    // set skin.path for use by the emulator.
    values.put(ConfigKey.SKIN_NAME, skinName);
    values.put(ConfigKey.SKIN_PATH, skinPath);
  }

    /**
     * Creates an SD card for the AVD. Any existing card will be replaced with a new one, unless the
     * card is already the right size and editExisting is set.
     *
     * @param sdcard the spec of the card to create
     * @param editExisting true if modifying an existing AVD
     * @param avdFolder where the AVDs live
     */
    private void createAvdSdCard(
            @NonNull InternalSdCard sdcard, boolean editExisting, @NonNull Path avdFolder)
            throws AvdMgrException {

        if (!mBaseAvdFolder.getFileSystem().equals(FileSystems.getDefault())) {
            // We don't have a real filesystem, so we won't be able to run the tool. Skip.
            return;
        }

        Path sdcardFile = avdFolder.resolve(SDCARD_IMG);
        try {
            if (CancellableFileIo.size(sdcardFile) == sdcard.getSize() && editExisting) {
                // There's already an sdcard file with the right size and we're
                // not overriding it... so don't remove it.
                mLog.info("SD Card already present with same size, was not changed.\n");
                return;
            }
        } catch (NoSuchFileException ignore) {
        } catch (IOException exception) {
            AvdMgrException wrapper = new AvdMgrException();
            wrapper.initCause(exception);
            throw wrapper;
        }

        String path = sdcardFile.toAbsolutePath().toString();

        // execute mksdcard with the proper parameters.
        LoggerProgressIndicatorWrapper progress =
                new LoggerProgressIndicatorWrapper(mLog) {
                    @Override
                    public void logVerbose(@NonNull String s) {
                        // Skip verbose messages
                    }
                };
        EmulatorPackage p = EmulatorPackages.getEmulatorPackage(mSdkHandler, progress);
        if (p == null) {
            mLog.warning("Emulator package is not installed");
            throw new AvdMgrException();
        }

        Path mkSdCard = p.getMkSdCardBinary();
        if (mkSdCard == null || !CancellableFileIo.isRegularFile(mkSdCard)) {
            mLog.warning(
                    String.format(
                            "Unable to find %1$s in the %2$s component",
                            SdkConstants.mkSdCardCmdName(), SdkConstants.FD_EMULATOR));
            throw new AvdMgrException();
        }

        if (!SdCards.createSdCard(
                mLog, mkSdCard.toAbsolutePath().toString(), sdcard.sizeSpec(), path)) {
            // mksdcard output has already been displayed, no need to
            // output anything else.
            mLog.warning("Failed to create sdcard in the AVD folder.");
            throw new AvdMgrException();
        }
    }

    /**
     * Read the system image's hardware.ini into the provided Map.
     *
     * @param systemImage the system image of the AVD
     * @param values mutable Map to add the values to
     */
    private void addSystemImageHardwareConfig(
            @NonNull ISystemImage systemImage, @NonNull Map<String, String> values) {
        PathFileWrapper sysImgHardwareFile =
                new PathFileWrapper(systemImage.getLocation().resolve(HARDWARE_INI));
        if (sysImgHardwareFile.exists()) {
            Map<String, String> imageHardwardConfig =
                    ProjectProperties.parsePropertyFile(sysImgHardwareFile, mLog);

            if (imageHardwardConfig != null) {
                values.putAll(imageHardwardConfig);
            }
        }
    }

    /**
     * Creates an AvdInfo object from the new AVD.
     *
     * @param systemImage the system image of the AVD
     * @param removePrevious true if the existing AVD should be deleted
     * @param editExisting true if modifying an existing AVD
     * @param metadataIniFile the .ini file of this AVD
     * @param avdFolder where the AVD resides
     * @param oldAvdInfo configuration of the old AVD
     * @param values a map of the AVD's info
     */
    @NonNull
    private AvdInfo createAvdInfoObject(
            @NonNull ISystemImage systemImage,
            boolean removePrevious,
            boolean editExisting,
            @NonNull Path metadataIniFile,
            @NonNull Path avdFolder,
            @Nullable AvdInfo oldAvdInfo,
            @Nullable Map<String, String> values,
            @Nullable Map<String, String> userSettings)
            throws AvdMgrException {

        // create the AvdInfo object, and add it to the list
        AvdInfo theAvdInfo =
                new AvdInfo(metadataIniFile, avdFolder, systemImage, values, userSettings);

        synchronized (mAllAvdList) {
            if (oldAvdInfo != null && (removePrevious || editExisting)) {
                mAllAvdList.remove(oldAvdInfo);
            }
            mAllAvdList.add(theAvdInfo);
            mValidAvdList = null;
        }
        return theAvdInfo;
    }

    /**
     * (Linux only) Sets the AVD folder to not be "Copy on Write"
     *
     * <p>CoW at the file level conflicts with QEMU's explicit CoW operations and can hurt Emulator
     * performance. NOTE: The "chatter +C" command does not impact existing files in the folder.
     * Thus this method should be called before the folder is populated. This method is "best
     * effort." Common failures are silently ignored. Other failures are logged and ignored.
     *
     * @param avdFolder where the AVD's files will be written
     * @param log the log object to receive action logs
     */
    private static void inhibitCopyOnWrite(@NonNull Path avdFolder, @NonNull ILogger log) {
        if (SdkConstants.CURRENT_PLATFORM != SdkConstants.PLATFORM_LINUX) {
            return;
        }
        try {
            String[] chattrCommand = new String[3];
            chattrCommand[0] = "chattr";
            chattrCommand[1] = "+C";
            chattrCommand[2] = avdFolder.toAbsolutePath().toString();
            Process chattrProcess = Runtime.getRuntime().exec(chattrCommand);

            final ArrayList<String> errorOutput = new ArrayList<>();

            GrabProcessOutput.grabProcessOutput(
              chattrProcess,
              Wait.WAIT_FOR_READERS,
              new IProcessOutput() {
                  @Override
                  public void out(@Nullable String line) { }

                  @Override
                  public void err(@Nullable String line) {
                      // Don't complain if this command is not supported. That just means
                      // that the file system is not 'btrfs', and it does not support Copy
                      // on Write. So we're happy.
                      if (line != null && !line.startsWith("chattr: Operation not supported")) {
                          errorOutput.add(line);
                      }
                  }
              });

            if (!errorOutput.isEmpty()) {
                log.warning("Failed 'chattr' for %1$s:", avdFolder.toAbsolutePath().toString());
                for (String error : errorOutput) {
                    log.warning(" -- %1$s", error);
                }
            }
        }
        catch (InterruptedException | IOException ee) {
            log.warning(
                    "Failed 'chattr' for %1$s: %2$s", avdFolder.toAbsolutePath().toString(), ee);
        }
    }
}
