/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.sdklib.repository;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.io.CancellableFileIo;
import com.android.repository.Revision;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Misc utilities to help extracting elements and attributes out of a repository XML document.
 */
public class PackageParserUtils {

    /**
     * Utility method to parse the {@link PkgProps#PKG_REVISION} property as a revision
     * (major.minor.micro.preview).
     *
     * @param props   The properties to parse.
     * @param propKey The name of the property. Must not be null.
     * @return A {@link Revision} or null if there is no such property or it couldn't be parsed.
     */
    @Nullable
    public static Revision getRevisionProperty(
            @Nullable Properties props,
            @NonNull String propKey) {
        String revStr = getProperty(props, propKey, null);

        Revision rev = null;
        if (revStr != null) {
            try {
                rev = Revision.parseRevision(revStr);
            } catch (NumberFormatException ignore) {
            }
        }

        return rev;
    }

    /**
     * Utility method that returns a property from a {@link Properties} object. Returns the default
     * value if props is null or if the property is not defined.
     *
     * @param props        The {@link Properties} to search into. If null, the default value is
     *                     returned.
     * @param propKey      The name of the property. Must not be null.
     * @param defaultValue The default value to return if {@code props} is null or if the key is not
     *                     found. Can be null.
     * @return The string value of the given key in the properties, or null if the key isn't found
     * or if {@code props} is null.
     */
    @Nullable
    public static String getProperty(
            @Nullable Properties props,
            @NonNull String propKey,
            @Nullable String defaultValue) {
        if (props == null) {
            return defaultValue;
        }
        return props.getProperty(propKey, defaultValue);
    }

    /**
     * Parses the skin folder and builds the skin list.
     *
     * @param skinRootFolder The path to the skin root folder.
     */
    @NonNull
    public static List<Path> parseSkinFolder(@NonNull Path skinRootFolder) {
        if (CancellableFileIo.isDirectory(skinRootFolder)) {
            try {
                return CancellableFileIo.list(skinRootFolder)
                        .filter(CancellableFileIo::isDirectory)
                        .filter(
                                dir ->
                                        CancellableFileIo.isRegularFile(
                                                dir.resolve(SdkConstants.FN_SKIN_LAYOUT)))
                        .sorted()
                        .collect(Collectors.toList());
            } catch (IOException e) {
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }

}
