/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.sdklib.devices;

import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_RAM_SIZE;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Slow;
import com.android.io.CancellableFileIo;
import com.android.prefs.AndroidLocationsProvider;
import com.android.repository.api.RepoManager;
import com.android.resources.KeyboardState;
import com.android.resources.Navigation;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.internal.avd.HardwareProperties;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.LoggerProgressIndicatorWrapper;
import com.android.sdklib.repository.PkgProps;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.utils.ILogger;
import com.google.common.base.Charsets;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.Closeables;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import org.xml.sax.SAXException;

/**
 * Manager class for interacting with {@link Device}s within the SDK
 */
public class DeviceManager {

    private static final String  DEVICE_PROFILES_PROP = "DeviceProfiles";
    private static final Pattern PATH_PROPERTY_PATTERN =
        Pattern.compile('^' + PkgProps.EXTRA_PATH + '=' + DEVICE_PROFILES_PROP + '$');
    @Nullable private final Path mAndroidFolder;
    private final ILogger mLog;
    private Table<String, String, Device> mVendorDevices;
    private Table<String, String, Device> mSysImgDevices;
    private Table<String, String, Device> mUserDevices;
    private Table<String, String, Device> mDefaultDevices;
    private final Object mLock = new Object();
    private final List<DevicesChangedListener> sListeners = new ArrayList<>();
    private final Path mOsSdkPath;
    private final AndroidSdkHandler mSdkHandler;

    public enum DeviceFilter {
        /** getDevices() flag to list default devices from the bundled devices.xml definitions. */
        DEFAULT,
        /** getDevices() flag to list user devices saved in the .android home folder. */
        USER,
        /** getDevices() flag to list vendor devices -- the bundled nexus.xml devices
         *  as well as all those coming from extra packages. */
        VENDOR,
        /** getDevices() flag to list devices from system-images/platform-N/tag/abi/devices.xml */
        SYSTEM_IMAGES,
    }

    /** getDevices() flag to list all devices. */
    public static final EnumSet<DeviceFilter> ALL_DEVICES  = EnumSet.allOf(DeviceFilter.class);

    public enum DeviceStatus {
        /**
         * The device exists unchanged from the given configuration
         */
        EXISTS,
        /**
         * A device exists with the given name and manufacturer, but has a different configuration
         */
        CHANGED,
        /**
         * There is no device with the given name and manufacturer
         */
        MISSING
    }

    /**
     * Creates a new instance of {@link DeviceManager}, using the user's android folder.
     *
     * @see #createInstance(AndroidSdkHandler, ILogger)
     */
    public static DeviceManager createInstance(
            @NonNull AndroidLocationsProvider androidLocationsProvider,
            @Nullable Path sdkLocation,
            @NonNull ILogger log) {
        return createInstance(
                AndroidSdkHandler.getInstance(androidLocationsProvider, sdkLocation), log);
    }

    public static DeviceManager createInstance(
            @NonNull AndroidSdkHandler sdkHandler,
            @NonNull ILogger log) {
        // TODO consider using a cache and reusing the same instance of the device manager
        // for the same manager/log combo.
        return new DeviceManager(sdkHandler, log);
    }

    /**
     * Creates a new instance of DeviceManager.
     *
     * @param sdkHandler The AndroidSdkHandler to use.
     * @param log SDK logger instance. Should be non-null.
     */
    private DeviceManager(
            @NonNull AndroidSdkHandler sdkHandler,
            @NonNull ILogger log) {
        mSdkHandler = sdkHandler;
        mOsSdkPath = sdkHandler.getLocation() == null ? null : sdkHandler.getLocation();
        mAndroidFolder =
                sdkHandler.getAndroidFolder() == null
                        ? Paths.get("")
                        : sdkHandler.getAndroidFolder();
        mLog = log;
    }

    /**
     * Interface implemented by objects which want to know when changes occur to the {@link Device}
     * lists.
     */
    public interface DevicesChangedListener {
        /**
         * Called after one of the {@link Device} lists has been updated.
         */
        void onDevicesChanged();
    }

    /**
     * Register a listener to be notified when the device lists are modified.
     *
     * @param listener The listener to add. Ignored if already registered.
     */
    public void registerListener(@NonNull DevicesChangedListener listener) {
        synchronized (sListeners) {
            if (!sListeners.contains(listener)) {
                sListeners.add(listener);
            }
        }
    }

    /**
     * Removes a listener from the notification list such that it will no longer receive
     * notifications when modifications to the {@link Device} list occur.
     *
     * @param listener The listener to remove.
     */
    public boolean unregisterListener(@NonNull DevicesChangedListener listener) {
        synchronized (sListeners) {
            return sListeners.remove(listener);
        }
    }

    @NonNull
    public DeviceStatus getDeviceStatus(@NonNull String name, @NonNull String manufacturer) {
        Device d = getDevice(name, manufacturer);
        if (d == null) {
            return DeviceStatus.MISSING;
        }

        return DeviceStatus.EXISTS;
    }

    @Nullable
    public Device getDevice(@NonNull String id, @NonNull String manufacturer) {
        initDevicesLists();
        Device d = mUserDevices.get(id, manufacturer);
        if (d != null) {
            return d;
        }
        d = mSysImgDevices.get(id, manufacturer);
        if (d != null) {
            return d;
        }
        d = mDefaultDevices.get(id, manufacturer);
        if (d != null) {
            return d;
        }
        d = mVendorDevices.get(id, manufacturer);
        return d;
    }

    /**
     * Returns the known {@link Device} list.
     *
     * @param deviceFilter One of the {@link DeviceFilter} constants.
     * @return A copy of the list of {@link Device}s. Can be empty but not null.
     */
    @NonNull
    public Collection<Device> getDevices(@NonNull DeviceFilter deviceFilter) {
        return getDevices(EnumSet.of(deviceFilter));
    }

    /**
     * Returns the known {@link Device} list.
     *
     * @param deviceFilter A combination of the {@link DeviceFilter} constants
     *                     or the constant {@link DeviceManager#ALL_DEVICES}.
     * @return A copy of the list of {@link Device}s. Can be empty but not null.
     */
    @NonNull
    public Collection<Device> getDevices(@NonNull EnumSet<DeviceFilter> deviceFilter) {
        initDevicesLists();
        Table<String, String, Device> devices = HashBasedTable.create();
        if (mUserDevices != null && (deviceFilter.contains(DeviceFilter.USER))) {
            devices.putAll(mUserDevices);
        }
        if (mDefaultDevices != null && (deviceFilter.contains(DeviceFilter.DEFAULT))) {
            devices.putAll(mDefaultDevices);
        }
        if (mVendorDevices != null && (deviceFilter.contains(DeviceFilter.VENDOR))) {
            devices.putAll(mVendorDevices);
        }
        if (mSysImgDevices != null && (deviceFilter.contains(DeviceFilter.SYSTEM_IMAGES))) {
            devices.putAll(mSysImgDevices);
        }
        return Collections.unmodifiableCollection(devices.values());
    }

    private void initDevicesLists() {
        boolean changed = initDefaultDevices();
        changed |= initVendorDevices();
        changed |= initSysImgDevices();
        changed |= initUserDevices();
        if (changed) {
            notifyListeners();
        }
    }

    /**
     * Initializes the {@link Device}s packaged with the SDK.
     * @return True if the list has changed.
     */
    private boolean initDefaultDevices() {
        synchronized (mLock) {
            if (mDefaultDevices != null) {
                return false;
            }
            InputStream stream = DeviceManager.class
                    .getResourceAsStream(SdkConstants.FN_DEVICES_XML);
            try {
                assert stream != null : SdkConstants.FN_DEVICES_XML + " not bundled in sdklib.";
                mDefaultDevices = DeviceParser.parse(stream);
                return true;
            } catch (IllegalStateException e) {
                // The device builders can throw IllegalStateExceptions if
                // build gets called before everything is properly setup
                mLog.error(e, null);
                mDefaultDevices = HashBasedTable.create();
            } catch (Exception e) {
                mLog.error(e, "Error reading default devices");
                mDefaultDevices = HashBasedTable.create();
            } finally {
                Closeables.closeQuietly(stream);
            }
        }
        return false;
    }

    /**
     * Vendor-provided device files to parse. See {@link #initVendorDevices()}. Each entry
     * corresponds to an ".xml" file located in this same package.
     */
    private static final String[] DEVICE_FILES = {"nexus", "wear", "tv", "automotive"};

    /**
     * Initializes all vendor-provided {@link Device}s: the bundled nexus.xml devices
     * as well as all those coming from extra packages.
     * @return True if the list has changed.
     */
    private boolean initVendorDevices() {
        synchronized (mLock) {
            if (mVendorDevices != null) {
                return false;
            }

            mVendorDevices = HashBasedTable.create();

            for (String deviceFile : DEVICE_FILES) {
                InputStream stream = DeviceManager.class.getResourceAsStream(deviceFile + ".xml");
                try {
                    mVendorDevices.putAll(DeviceParser.parse(stream));
                } catch (Exception e) {
                    mLog.error(e, "Could not load " + deviceFile + " devices");
                } finally {
                    Closeables.closeQuietly(stream);
                }
            }

            if (mOsSdkPath != null) {
                // Load devices from vendor extras
                Path extrasFolder = mOsSdkPath.resolve(SdkConstants.FD_EXTRAS);
                List<Path> deviceDirs = getExtraDirs(extrasFolder);
                for (Path deviceDir : deviceDirs) {
                    Path deviceXml = deviceDir.resolve(SdkConstants.FN_DEVICES_XML);
                    if (Files.isRegularFile(deviceXml)) {
                        mVendorDevices.putAll(loadDevices(deviceXml));
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Initializes all system-image provided {@link Device}s.
     *
     * @return true if the list has changed.
     */
    @Slow
    private boolean initSysImgDevices() {
        synchronized (mLock) {
            if (mSysImgDevices != null) {
                return false;
            }
            mSysImgDevices = HashBasedTable.create();

            if (mOsSdkPath == null) {
                return false;
            }
            // Load device definitions from the system image directories.
            // Load in increasing order of Android version. This way, if there is a conflict,
            // we'll retain the definitions from the higher API level. The file in the higher
            // API directory is probably newer and more accurate.
            LoggerProgressIndicatorWrapper progress = new LoggerProgressIndicatorWrapper(mLog);

            RepoManager mgr = mSdkHandler.getSdkManager(progress);
            mgr.loadSynchronously(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, progress, null, null);
            mgr.getPackages().getLocalPackages().values().stream()
                    .filter(pkg -> pkg.getTypeDetails() instanceof DetailsTypes.SysImgDetailsType)
                    .sorted(
                            Comparator.comparing(
                                    pkg ->
                                            ((DetailsTypes.SysImgDetailsType) pkg.getTypeDetails())
                                                    .getAndroidVersion()))
                    .forEach(
                            pkg -> {
                                Path deviceXml =
                                        pkg.getLocation().resolve(SdkConstants.FN_DEVICES_XML);
                                if (CancellableFileIo.isRegularFile(deviceXml)) {
                                    mSysImgDevices.putAll(loadDevices(deviceXml));
                                }
                            });
            return true;
        }
    }

    /**
     * Initializes all user-created {@link Device}s
     * @return True if the list has changed.
     */
    private boolean initUserDevices() {
        synchronized (mLock) {
            if (mUserDevices != null) {
                return false;
            }
            // User devices should be saved out to
            // $HOME/.android/devices.xml
            mUserDevices = HashBasedTable.create();
            Path userDevicesFile = null;
            try {
                try {
                    userDevicesFile = mAndroidFolder.resolve(SdkConstants.FN_DEVICES_XML);
                    if (userDevicesFile != null && Files.exists(userDevicesFile)) {
                        mUserDevices.putAll(DeviceParser.parse(userDevicesFile));
                        return true;
                    }
                } catch (SAXException e) {
                    // Probably an old config file which we don't want to overwrite.
                    if (userDevicesFile != null) {
                        Path parent = userDevicesFile.toAbsolutePath().getParent();
                        String base = userDevicesFile.getFileName().toString() + ".old";
                        Path renamedConfig = parent.resolve(base);
                        int i = 0;
                        while (CancellableFileIo.exists(renamedConfig)) {
                            renamedConfig = parent.resolve(base + '.' + (i++));
                        }
                        mLog.error(
                                e,
                                "Error parsing %1$s, backing up to %2$s",
                                userDevicesFile.toAbsolutePath(),
                                renamedConfig.toAbsolutePath());
                        Files.move(userDevicesFile, renamedConfig);
                    }
                }
            } catch (ParserConfigurationException | IOException e) {
                mLog.error(
                        e,
                        "Error parsing %1$s",
                        userDevicesFile == null ? "(null)" : userDevicesFile.toAbsolutePath());
            }
        }
        return false;
    }

    public void addUserDevice(@NonNull Device d) {
        boolean changed = false;
        synchronized (mLock) {
            if (mUserDevices == null) {
                initUserDevices();
                assert mUserDevices != null;
            }
            if (mUserDevices != null) {
                mUserDevices.put(d.getId(), d.getManufacturer(), d);
            }
            changed = true;
        }
        if (changed) {
            notifyListeners();
        }
    }

    public void removeUserDevice(@NonNull Device d) {
        synchronized (mLock) {
            if (mUserDevices == null) {
                initUserDevices();
                assert mUserDevices != null;
            }
            if (mUserDevices != null) {
                if (mUserDevices.contains(d.getId(), d.getManufacturer())) {
                    mUserDevices.remove(d.getId(), d.getManufacturer());
                    notifyListeners();
                }
            }
        }
    }

    public void replaceUserDevice(@NonNull Device d) {
        synchronized (mLock) {
            if (mUserDevices == null) {
                initUserDevices();
            }
            removeUserDevice(d);
            addUserDevice(d);
        }
    }

    /**
     * Saves out the user devices to {@link SdkConstants#FN_DEVICES_XML} in the Android folder.
     */
    public void saveUserDevices() {
        if (mUserDevices == null) {
            return;
        }
        if (mAndroidFolder == null) {
            return;
        }

        Path userDevicesFile = mAndroidFolder.resolve(SdkConstants.FN_DEVICES_XML);

        if (mUserDevices.isEmpty()) {
            try {
                Files.deleteIfExists(userDevicesFile);
            } catch (IOException ignore) {
                // nothing
            }
            return;
        }

        synchronized (mLock) {
            if (!mUserDevices.isEmpty()) {
                try {
                    DeviceWriter.writeToXml(
                            Files.newOutputStream(userDevicesFile), mUserDevices.values());
                } catch (FileNotFoundException e) {
                    mLog.warning("Couldn't open file: %1$s", e.getMessage());
                } catch (ParserConfigurationException
                        | IOException
                        | TransformerException
                        | TransformerFactoryConfigurationError e) {
                    mLog.warning("Error writing file: %1$s", e.getMessage());
                }
            }
        }
    }

    /**
     * Returns hardware properties (defined in hardware.ini) as a {@link Map}.
     *
     * @param s The {@link State} from which to derive the hardware properties.
     * @return A {@link Map} of hardware properties.
     */
    @NonNull
    public static Map<String, String> getHardwareProperties(@NonNull State s) {
        Hardware hw = s.getHardware();
        Map<String, String> props = new HashMap<>();
        props.put(HardwareProperties.HW_MAINKEYS,
                getBooleanVal(hw.getButtonType().equals(ButtonType.HARD)));
        props.put(HardwareProperties.HW_TRACKBALL,
                getBooleanVal(hw.getNav().equals(Navigation.TRACKBALL)));
        props.put(HardwareProperties.HW_DPAD,
                getBooleanVal(hw.getNav().equals(Navigation.DPAD)));

        Set<Sensor> sensors = hw.getSensors();
        props.put(HardwareProperties.HW_GPS, getBooleanVal(sensors.contains(Sensor.GPS)));
        props.put(HardwareProperties.HW_BATTERY,
                getBooleanVal(hw.getChargeType().equals(PowerType.BATTERY)));
        props.put(HardwareProperties.HW_ACCELEROMETER,
                getBooleanVal(sensors.contains(Sensor.ACCELEROMETER)));
        props.put(HardwareProperties.HW_ORIENTATION_SENSOR,
                getBooleanVal(sensors.contains(Sensor.GYROSCOPE)));
        props.put(HardwareProperties.HW_AUDIO_INPUT, getBooleanVal(hw.hasMic()));
        props.put(HardwareProperties.HW_SDCARD, getBooleanVal(hw.hasSdCard()));
        props.put(HardwareProperties.HW_LCD_DENSITY,
                Integer.toString(hw.getScreen().getPixelDensity().getDpiValue()));
        props.put(HardwareProperties.HW_LCD_WIDTH,
                Integer.toString(hw.getScreen().getXDimension()));
        props.put(HardwareProperties.HW_LCD_HEIGHT,
                Integer.toString(hw.getScreen().getYDimension()));
        props.put(HardwareProperties.HW_PROXIMITY_SENSOR,
                getBooleanVal(sensors.contains(Sensor.PROXIMITY_SENSOR)));
        if (hw.getScreen().isFoldable()) {
            props.put(HardwareProperties.HW_KEYBOARD_LID, getBooleanVal(true));
            props.put(HardwareProperties.HW_LCD_FOLDED_X_OFFSET,
                      Integer.toString(hw.getScreen().getFoldedXOffset()));
            props.put(HardwareProperties.HW_LCD_FOLDED_Y_OFFSET,
                      Integer.toString(hw.getScreen().getFoldedYOffset()));
            props.put(HardwareProperties.HW_LCD_FOLDED_HEIGHT,
                      Integer.toString(hw.getScreen().getFoldedHeight()));
            props.put(HardwareProperties.HW_LCD_FOLDED_WIDTH,
                      Integer.toString(hw.getScreen().getFoldedWidth()));
            if (hw.getScreen().getFoldedWidth2() != 0 && hw.getScreen().getFoldedHeight2() != 0) {
                props.put(
                        HardwareProperties.HW_LCD_FOLDED_X_OFFSET_2,
                        Integer.toString(hw.getScreen().getFoldedXOffset2()));
                props.put(
                        HardwareProperties.HW_LCD_FOLDED_Y_OFFSET_2,
                        Integer.toString(hw.getScreen().getFoldedYOffset2()));
                props.put(
                        HardwareProperties.HW_LCD_FOLDED_WIDTH_2,
                        Integer.toString(hw.getScreen().getFoldedWidth2()));
                props.put(
                        HardwareProperties.HW_LCD_FOLDED_HEIGHT_2,
                        Integer.toString(hw.getScreen().getFoldedHeight2()));
            }
            if (hw.getScreen().getFoldedWidth3() != 0 && hw.getScreen().getFoldedHeight3() != 0) {
                props.put(
                        HardwareProperties.HW_LCD_FOLDED_X_OFFSET_3,
                        Integer.toString(hw.getScreen().getFoldedXOffset3()));
                props.put(
                        HardwareProperties.HW_LCD_FOLDED_Y_OFFSET_3,
                        Integer.toString(hw.getScreen().getFoldedYOffset3()));
                props.put(
                        HardwareProperties.HW_LCD_FOLDED_WIDTH_3,
                        Integer.toString(hw.getScreen().getFoldedWidth3()));
                props.put(
                        HardwareProperties.HW_LCD_FOLDED_HEIGHT_3,
                        Integer.toString(hw.getScreen().getFoldedHeight3()));
            }
        }
        return props;
    }

    /**
     * Returns the hardware properties defined in
     * {@link AvdManager#HARDWARE_INI} as a {@link Map}.
     *
     * This is intended to be dumped in the config.ini and already contains
     * the device name, manufacturer and device hash.
     *
     * @param d The {@link Device} from which to derive the hardware properties.
     * @return A {@link Map} of hardware properties.
     */
    @NonNull
    public static Map<String, String> getHardwareProperties(@NonNull Device d) {
        Map<String, String> props = getHardwareProperties(d.getDefaultState());
        for (State s : d.getAllStates()) {
            final Storage ramSize = s.getHardware().getRam();
            if (ramSize.getSize() > 0) {
                props.put(AVD_INI_RAM_SIZE, Long.toString(ramSize.getSizeAsUnit(Storage.Unit.MiB)));
            }
            if (s.getKeyState().equals(KeyboardState.HIDDEN)) {
                props.put("hw.keyboard.lid", getBooleanVal(true));
            }
        }

        HashFunction md5 = Hashing.md5();
        Hasher hasher = md5.newHasher();

        ArrayList<String> keys = new ArrayList<>(props.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            if (key != null) {
                hasher.putString(key, Charsets.UTF_8);
                String value = props.get(key);
                hasher.putString(value == null ? "null" : value, Charsets.UTF_8);
            }
        }
        // store the hash method for potential future compatibility
        String hash = "MD5:" + hasher.hash().toString();
        props.put(AvdManager.AVD_INI_DEVICE_HASH_V2, hash);
        props.remove(AvdManager.AVD_INI_DEVICE_HASH_V1);

        props.put(AvdManager.AVD_INI_DEVICE_NAME, d.getId());
        props.put(AvdManager.AVD_INI_DEVICE_MANUFACTURER, d.getManufacturer());
        return props;
    }

    /**
     * Checks whether the the hardware props have changed.
     * If the hash is the same, returns null for success.
     * If the hash is not the same or there's not enough information to indicate it's
     * the same (e.g. if in the future we change the digest method), simply return the
     * new hash, indicating it would be best to update it.
     *
     * @param d The device.
     * @param hashV2 The previous saved AvdManager.AVD_INI_DEVICE_HASH_V2 property.
     * @return Null if the same, otherwise returns the new and different hash.
     */
    @Nullable
    public static String hasHardwarePropHashChanged(@NonNull Device d, @NonNull String hashV2) {
        Map<String, String> props = getHardwareProperties(d);
        String newHash = props.get(AvdManager.AVD_INI_DEVICE_HASH_V2);

        // Implementation detail: don't just return the hash and let the caller decide whether
        // the hash is the same. That's because the hash contains the digest method so if in
        // the future we decide to change it, we could potentially recompute the hash here
        // using an older digest method here and still determine its validity, whereas the
        // caller cannot determine that.

        if (newHash != null && newHash.equals(hashV2)) {
            return null;
        }
        return newHash;
    }


    /**
     * Takes a boolean and returns the appropriate value for
     * {@link HardwareProperties}
     *
     * @param bool The boolean value to turn into the appropriate
     *            {@link HardwareProperties} value.
     * @return {@code HardwareProperties#BOOLEAN_YES} if true,
     *         {@code HardwareProperties#BOOLEAN_NO} otherwise.
     */
    private static String getBooleanVal(boolean bool) {
        if (bool) {
            return HardwareProperties.BOOLEAN_YES;
        }
        return HardwareProperties.BOOLEAN_NO;
    }

    @NonNull
    private Table<String, String, Device> loadDevices(@NonNull Path deviceXml) {
        try {
            return DeviceParser.parse(deviceXml);
        } catch (SAXException | ParserConfigurationException | AssertionError e) {
            mLog.error(e, "Error parsing %1$s", deviceXml.toAbsolutePath());
        } catch (IOException e) {
            mLog.error(e, "Error reading %1$s", deviceXml.toAbsolutePath());
        } catch (IllegalStateException e) {
            // The device builders can throw IllegalStateExceptions if
            // build gets called before everything is properly setup
            mLog.error(e, null);
        }
        return HashBasedTable.create();
    }

    private void notifyListeners() {
        synchronized (sListeners) {
            for (DevicesChangedListener listener : sListeners) {
                listener.onDevicesChanged();
            }
        }
    }

    /* Returns all of DeviceProfiles in the extras/ folder */
    @NonNull
    private List<Path> getExtraDirs(@NonNull Path extrasFolder) {
        List<Path> extraDirs = new ArrayList<>();
        // All OEM provided device profiles are in
        // $SDK/extras/$VENDOR/$ITEM/devices.xml
        if (CancellableFileIo.isDirectory(extrasFolder)) {
            try {
                Files.walkFileTree(
                        extrasFolder,
                        ImmutableSet.of(),
                        2,
                        new SimpleFileVisitor<Path>() {
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                                if (attrs.isDirectory() && isDevicesExtra(file)) {
                                    extraDirs.add(file);
                                }
                                return FileVisitResult.CONTINUE;
                            }
                        });
            } catch (IOException ignore) {
            }
        }
        return extraDirs;
    }

    /*
     * Returns whether a specific folder for a specific vendor is a
     * DeviceProfiles folder
     */
    private static boolean isDevicesExtra(@NonNull Path item) {
        Path properties = item.resolve(SdkConstants.FN_SOURCE_PROP);
        try (BufferedReader propertiesReader = Files.newBufferedReader(properties)) {
            String line;
            while ((line = propertiesReader.readLine()) != null) {
                Matcher m = PATH_PROPERTY_PATTERN.matcher(line);
                if (m.matches()) {
                    return true;
                }
            }
        } catch (IOException ignore) {
        }
        return false;
    }
}
