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

package com.android.sdklib.internal.project;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.io.FolderWrapper;
import com.android.io.IAbstractFile;
import com.android.io.IAbstractFolder;
import com.android.io.StreamException;
import com.android.utils.ILogger;
import com.android.utils.SdkUtils;
import com.google.common.io.Closeables;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class representing project properties for both ADT and Ant-based build.
 * <p>The class is associated to a {@link PropertyType} that indicate which of the project
 * property file is represented.
 * <p>To load an existing file, use {@link #load(IAbstractFolder, PropertyType)}.
 * <p>The class is meant to be always in sync (or at least not newer) than the file it represents.
 * Once created, it can only be updated through {@link #reload()}
 *
 * <p>The make modification or make new file, use a {@link ProjectPropertiesWorkingCopy} instance,
 * either through {@link #create(IAbstractFolder, PropertyType)} or through
 * {@link #makeWorkingCopy()}.
 *
 */
public class ProjectProperties implements IPropertySource {
    protected static final Pattern PATTERN_PROP = Pattern.compile(
    "^([a-zA-Z0-9._-]+)\\s*=\\s*(.*)\\s*$");

    /** The property name for the project target */
    public static final String PROPERTY_TARGET = "target";

    public static final String PROPERTY_PROGUARD_CONFIG = "proguard.config";

    public static final String PROPERTY_SDK = "sdk.dir";
    public static final String PROPERTY_NDK = "ndk.dir";
    public static final String PROPERTY_NDK_SYMLINKDIR = "ndk.symlinkdir";
    public static final String PROPERTY_CMAKE = "cmake.dir";

    // LEGACY - Kept so that we can actually remove it from local.properties.
    private static final String PROPERTY_SDK_LEGACY = "sdk-location";

    public static final String PROPERTY_SPLIT_BY_DENSITY = "split.density";

    public static final String PROPERTY_PACKAGE = "package";
    public static final String PROPERTY_VERSIONCODE = "versionCode";
    public static final String PROPERTY_PROJECTS = "projects";

    public enum PropertyType {
        LOCAL(SdkConstants.FN_LOCAL_PROPERTIES, LOCAL_HEADER, new String[] {
                PROPERTY_SDK
            },
            new String[] { PROPERTY_SDK_LEGACY }),
        @Deprecated
        LEGACY_DEFAULT("default.properties", null, null, null),
        @Deprecated
        LEGACY_BUILD("build.properties", null, null, null);


        private final String mFilename;
        private final String mHeader;
        private final Set<String> mKnownProps;
        private final Set<String> mRemovedProps;

        PropertyType(String filename, String header, String[] validProps, String[] removedProps) {
            mFilename = filename;
            mHeader = header;
            HashSet<String> s = new HashSet<String>();
            if (validProps != null) {
                s.addAll(Arrays.asList(validProps));
            }
            mKnownProps = Collections.unmodifiableSet(s);

            s = new HashSet<String>();
            if (removedProps != null) {
                s.addAll(Arrays.asList(removedProps));
            }
            mRemovedProps = Collections.unmodifiableSet(s);

        }

        public String getFilename() {
            return mFilename;
        }

        public String getHeader() {
            return mHeader;
        }

        /**
         * Returns whether a given property is known for the property type.
         */
        public boolean isKnownProperty(String name) {
            for (String propRegex : mKnownProps) {
                if (propRegex.equals(name) || Pattern.matches(propRegex, name)) {
                    return true;
                }
            }

            return false;
        }

        /**
         * Returns whether a given property should be removed for the property type.
         */
        public boolean isRemovedProperty(String name) {
            for (String propRegex : mRemovedProps) {
                if (propRegex.equals(name) || Pattern.matches(propRegex, name)) {
                    return true;
                }
            }

            return false;
        }
    }

    private static final String LOCAL_HEADER =
//           1-------10--------20--------30--------40--------50--------60--------70--------80
            "# This file is automatically generated by Android Tools.\n" +
            "# Do not modify this file -- YOUR CHANGES WILL BE ERASED!\n" +
            "#\n" +
            "# This file must *NOT* be checked into Version Control Systems,\n" +
            "# as it contains information specific to your local configuration.\n" +
            "\n";

    protected final IAbstractFolder mProjectFolder;
    protected final Map<String, String> mProperties;
    protected final PropertyType mType;

    /**
     * Loads a project properties file and return a {@link ProjectProperties} object
     * containing the properties.
     *
     * @param projectFolderOsPath the project folder.
     * @param type One the possible {@link PropertyType}s.
     */
    public static ProjectProperties load(String projectFolderOsPath, PropertyType type) {
        IAbstractFolder wrapper = new FolderWrapper(projectFolderOsPath);
        return load(wrapper, type);
    }

    /**
     * Loads a project properties file and return a {@link ProjectProperties} object
     * containing the properties.
     *
     * @param projectFolder the project folder.
     * @param type One the possible {@link PropertyType}s.
     */
    public static ProjectProperties load(IAbstractFolder projectFolder, PropertyType type) {
        if (projectFolder.exists()) {
            IAbstractFile propFile = projectFolder.getFile(type.mFilename);
            if (propFile.exists()) {
                Map<String, String> map = parsePropertyFile(propFile, null /* log */);
                if (map != null) {
                    return new ProjectProperties(projectFolder, map, type);
                }
            }
        }
        return null;
    }


    /**
     * Creates a new project properties object, with no properties.
     * <p>The file is not created until {@link ProjectPropertiesWorkingCopy#save()} is called.
     * @param projectFolderOsPath the project folder.
     * @param type the type of property file to create
     *
     * @see #createEmpty(String, PropertyType)
     */
    public static ProjectPropertiesWorkingCopy create(@NonNull String projectFolderOsPath,
            @NonNull PropertyType type) {
        // create and return a ProjectProperties with an empty map.
        IAbstractFolder folder = new FolderWrapper(projectFolderOsPath);
        return create(folder, type);
    }

    /**
     * Creates a new project properties object, with no properties.
     * <p>The file is not created until {@link ProjectPropertiesWorkingCopy#save()} is called.
     * @param projectFolder the project folder.
     * @param type the type of property file to create
     *
     * @see #createEmpty(IAbstractFolder, PropertyType)
     */
    public static ProjectPropertiesWorkingCopy create(@NonNull IAbstractFolder projectFolder,
            @NonNull PropertyType type) {
        // create and return a ProjectProperties with an empty map.
        return new ProjectPropertiesWorkingCopy(projectFolder, new HashMap<String, String>(), type);
    }

    /**
     * Creates a new project properties object, with no properties.
     * <p>Nothing can be added to it, unless a {@link ProjectPropertiesWorkingCopy} is created
     * first with {@link #makeWorkingCopy()}.
     * @param projectFolderOsPath the project folder.
     * @param type the type of property file to create
     *
     * @see #create(String, PropertyType)
     */
    public static ProjectProperties createEmpty(@NonNull String projectFolderOsPath,
            @NonNull PropertyType type) {
        // create and return a ProjectProperties with an empty map.
        IAbstractFolder folder = new FolderWrapper(projectFolderOsPath);
        return createEmpty(folder, type);
    }

    /**
     * Creates a new project properties object, with no properties.
     * <p>Nothing can be added to it, unless a {@link ProjectPropertiesWorkingCopy} is created
     * first with {@link #makeWorkingCopy()}.
     * @param projectFolder the project folder.
     * @param type the type of property file to create
     *
     * @see #create(IAbstractFolder, PropertyType)
     */
    public static ProjectProperties createEmpty(@NonNull IAbstractFolder projectFolder,
            @NonNull PropertyType type) {
        // create and return a ProjectProperties with an empty map.
        return new ProjectProperties(projectFolder, new HashMap<String, String>(), type);
    }

    /**
     * Returns the location of this property file.
     */
    public IAbstractFile getFile() {
        return mProjectFolder.getFile(mType.mFilename);
    }

    /**
     * Creates and returns a copy of the current properties as a
     * {@link ProjectPropertiesWorkingCopy} that can be modified and saved.
     * @return a new instance of {@link ProjectPropertiesWorkingCopy}
     */
    public ProjectPropertiesWorkingCopy makeWorkingCopy() {
        return makeWorkingCopy(mType);
    }

    /**
     * Creates and returns a copy of the current properties as a
     * {@link ProjectPropertiesWorkingCopy} that can be modified and saved. This also allows
     * converting to a new type, by specifying a different {@link PropertyType}.
     *
     * @param type the {@link PropertyType} of the prop file to save.
     *
     * @return a new instance of {@link ProjectPropertiesWorkingCopy}
     */
    public ProjectPropertiesWorkingCopy makeWorkingCopy(PropertyType type) {
        // copy the current properties in a new map
        Map<String, String> propList = new HashMap<String, String>(mProperties);

        return new ProjectPropertiesWorkingCopy(mProjectFolder, propList, type);
    }

    /**
     * Returns the type of the property file.
     *
     * @see PropertyType
     */
    public PropertyType getType() {
        return mType;
    }

    /**
     * Returns the value of a property.
     * @param name the name of the property.
     * @return the property value or null if the property is not set.
     */
    @Override
    public synchronized String getProperty(String name) {
        return mProperties.get(name);
    }

    /**
     * Returns a set of the property keys. Unlike {@link Map#keySet()} this is not a view of the
     * map keys. Modifying the returned {@link Set} will not impact the underlying {@link Map}.
     */
    public synchronized Set<String> keySet() {
        return new HashSet<String>(mProperties.keySet());
    }

    /**
     * Reloads the properties from the underlying file.
     */
    public synchronized void reload() {
        if (mProjectFolder.exists()) {
            IAbstractFile propFile = mProjectFolder.getFile(mType.mFilename);
            if (propFile.exists()) {
                Map<String, String> map = parsePropertyFile(propFile, null /* log */);
                if (map != null) {
                    mProperties.clear();
                    mProperties.putAll(map);
                }
            }
        }
    }

    /**
     * Parses a property file (using UTF-8 encoding) and returns a map of the content.
     * <p>
     * If the file is not present, null is returned with no error messages sent to the log.
     * <p>
     * IMPORTANT: This method is now unfortunately used in multiple places to parse random
     * property files. This is NOT a safe practice since there is no corresponding method
     * to write property files unless you use {@link ProjectPropertiesWorkingCopy#save()}.
     * Code that writes INI or properties without at least using
     * {@link SdkUtils#escapePropertyValue(String)} (String)} will
     * certainly not load back correct data. <br>
     * Unless there's a strong legacy need to support existing files, new callers should
     * probably just use Java's {@link Properties} which has well defined semantics.
     * It's also a mistake to write/read property files using this code and expect it to
     * work with Java's {@link Properties} or external tools (e.g. ant) since there can be
     * differences in escaping and in character encoding.
     *
     * @param propFile the property file to parse
     * @param log the ILogger object receiving warning/error from the parsing.
     * @return the map of (key,value) pairs, or null if the parsing failed.
     */
    public static Map<String, String> parsePropertyFile(
            @NonNull IAbstractFile propFile,
            @Nullable ILogger log) {
        InputStream is = null;
        try {
            is = propFile.getContents();
            return parsePropertyStream(is,
                                       propFile.getOsLocation(),
                                       log);
        } catch (StreamException e) {
            if (log != null) {
                log.warning("Error parsing '%1$s': %2$s.",
                        propFile.getOsLocation(),
                        e.getMessage());
            }
        } finally {
            try {
                Closeables.close(is, true /* swallowIOException */);
            } catch (IOException e) {
                // cannot happen
            }
        }


        return null;
    }

    /**
     * Parses a property file (using UTF-8 encoding) and returns a map of the content.
     * <p>
     * Always closes the given input stream on exit.
     * <p>
     * IMPORTANT: This method is now unfortunately used in multiple places to parse random
     * property files. This is NOT a safe practice since there is no corresponding method
     * to write property files unless you use {@link ProjectPropertiesWorkingCopy#save()}.
     * Code that writes INI or properties without at least using
     * {@link SdkUtils#escapePropertyValue(String)} (String)} will
     * certainly not load back correct data. <br>
     * Unless there's a strong legacy need to support existing files, new callers should
     * probably just use Java's {@link Properties} which has well defined semantics.
     * It's also a mistake to write/read property files using this code and expect it to
     * work with Java's {@link Properties} or external tools (e.g. ant) since there can be
     * differences in escaping and in character encoding.
     *
     * @param propStream the input stream of the property file to parse.
     * @param propPath the file path, for display purposed in case of error.
     * @param log the ILogger object receiving warning/error from the parsing.
     * @return the map of (key,value) pairs, or null if the parsing failed.
     */
    public static Map<String, String> parsePropertyStream(
            @NonNull InputStream propStream,
            @NonNull String propPath,
            @Nullable ILogger log) {
        BufferedReader reader = null;
        try {
            //noinspection IOResourceOpenedButNotSafelyClosed
            reader = new BufferedReader(
                        new InputStreamReader(propStream, SdkConstants.INI_CHARSET));

            String line = null;
            Map<String, String> map = new HashMap<String, String>();
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && line.charAt(0) != '#') {

                    Matcher m = PATTERN_PROP.matcher(line);
                    if (m.matches()) {
                        map.put(m.group(1), unescape(m.group(2)));
                    } else {
                        if (log != null) {
                            log.warning("Error parsing '%1$s': \"%2$s\" is not a valid syntax",
                                    propPath,
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
        } catch (IOException e) {
            if (log != null) {
                log.warning("Error parsing '%1$s': %2$s.",
                        propPath,
                        e.getMessage());
            }
        } finally {
            try {
                Closeables.close(reader, true /* swallowIOException */);
            } catch (IOException e) {
                // cannot happen
            }
            try {
                Closeables.close(propStream, true /* swallowIOException */);
            } catch (IOException e) {
                // cannot happen
            }
        }

        return null;
    }

    /**
     * Private constructor.
     * <p>
     * Use {@link #load(String, PropertyType)} or {@link #create(String, PropertyType)}
     * to instantiate.
     */
    protected ProjectProperties(
            @NonNull IAbstractFolder projectFolder,
            @NonNull Map<String, String> map,
            @NonNull PropertyType type) {
        mProjectFolder = projectFolder;
        mProperties = map;
        mType = type;
    }

    private static String unescape(String value) {
        return value.replaceAll("\\\\\\\\", "\\\\").replace("\\:", ":");
    }

    @Override
    public void debugPrint() {
        System.out.println("DEBUG PROJECTPROPERTIES: " + mProjectFolder);
        System.out.println("type: " + mType);
        for (Entry<String, String> entry : mProperties.entrySet()) {
            System.out.println(entry.getKey() + " -> " + entry.getValue());
        }
        System.out.println("<<< DEBUG PROJECTPROPERTIES");

    }

}
