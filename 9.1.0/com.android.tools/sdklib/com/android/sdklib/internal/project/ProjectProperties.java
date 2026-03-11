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
import com.android.io.IAbstractFile;
import com.android.io.StreamException;
import com.android.utils.ILogger;
import com.android.utils.SdkUtils;
import com.google.common.io.Closeables;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Static utilities for working with properties files. */
public class ProjectProperties {
    protected static final Pattern PATTERN_PROP = Pattern.compile(
    "^([a-zA-Z0-9._-]+)\\s*=\\s*(.*)\\s*$");

    /** The property name for the project target */
    public static final String PROPERTY_TARGET = "target";

    public static final String PROPERTY_SDK = "sdk.dir";
    public static final String PROPERTY_NDK = "ndk.dir";
    public static final String PROPERTY_NDK_SYMLINKDIR = "ndk.symlinkdir";
    public static final String PROPERTY_CMAKE = "cmake.dir";

    public static final String PROPERTY_SPLIT_BY_DENSITY = "split.density";

    public static final String PROPERTY_PACKAGE = "package";
    public static final String PROPERTY_VERSIONCODE = "versionCode";
    public static final String PROPERTY_PROJECTS = "projects";

    /**
     * Parses a property file (using UTF-8 encoding) and returns a map of the content.
     *
     * <p>If the file is not present, null is returned with no error messages sent to the log.
     *
     * <p>IMPORTANT: This method is now unfortunately used in multiple places to parse random
     * property files. This is NOT a safe practice since there is no corresponding method to write
     * property files. Code that writes INI or properties without at least using {@link
     * SdkUtils#escapePropertyValue(String)} (String)} will certainly not load back correct data.
     * <br>
     * Unless there's a strong legacy need to support existing files, new callers should probably
     * just use Java's {@link Properties} which has well defined semantics. It's also a mistake to
     * write/read property files using this code and expect it to work with Java's {@link
     * Properties} or external tools (e.g. ant) since there can be differences in escaping and in
     * character encoding.
     *
     * @param propFile the property file to parse
     * @param log the ILogger object receiving warning/error from the parsing.
     * @return the map of (key,value) pairs, or null if the parsing failed.
     */
    public static Map<String, String> parsePropertyFile(
            @NonNull IAbstractFile propFile, @Nullable ILogger log) {
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
     *
     * <p>Always closes the given input stream on exit.
     *
     * <p>IMPORTANT: This method is now unfortunately used in multiple places to parse random
     * property files. This is NOT a safe practice since there is no corresponding method to write
     * property files. Code that writes INI or properties without at least using {@link
     * SdkUtils#escapePropertyValue(String)} (String)} will certainly not load back correct data.
     * <br>
     * Unless there's a strong legacy need to support existing files, new callers should probably
     * just use Java's {@link Properties} which has well defined semantics. It's also a mistake to
     * write/read property files using this code and expect it to work with Java's {@link
     * Properties} or external tools (e.g. ant) since there can be differences in escaping and in
     * character encoding.
     *
     * @param propStream the input stream of the property file to parse.
     * @param propPath the file path, for display purposed in case of error.
     * @param log the ILogger object receiving warning/error from the parsing.
     * @return the map of (key,value) pairs, or null if the parsing failed.
     */
    public static Map<String, String> parsePropertyStream(
            @NonNull InputStream propStream, @NonNull String propPath, @Nullable ILogger log) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(
                        new InputStreamReader(propStream, SdkConstants.INI_CHARSET));

            String line;
            Map<String, String> map = new HashMap<>();
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

    private static String unescape(String value) {
        return value.replaceAll("\\\\\\\\", "\\\\").replace("\\:", ":");
    }
}
