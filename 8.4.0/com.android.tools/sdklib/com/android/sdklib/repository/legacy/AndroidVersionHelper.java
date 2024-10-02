/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.sdklib.repository.legacy;

import com.android.annotations.NonNull;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.PkgProps;
import java.util.Properties;

/** Utility to create an AndroidVersion from properties files. */
public class AndroidVersionHelper {

    /**
     * Creates an {@link AndroidVersion} from {@link Properties}. The properties must contain
     * android version information, or an exception will be thrown.
     *
     * @throws AndroidVersion.AndroidVersionException if no Android version information have been
     *     found
     */
    @NonNull
    public static AndroidVersion create(@NonNull Properties properties)
            throws AndroidVersion.AndroidVersionException {
        String apiLevelProperty = properties.getProperty(PkgProps.VERSION_API_LEVEL);
        if (apiLevelProperty == null) {
            throw new AndroidVersion.AndroidVersionException(
                    PkgProps.VERSION_API_LEVEL + " not found!", null);
        }
        int apiLevel;
        try {
            apiLevel = Integer.parseInt(apiLevelProperty);
        } catch (NumberFormatException e) {
            throw new AndroidVersion.AndroidVersionException(
                    PkgProps.VERSION_API_LEVEL + " does not contain a parsable integer!", null);
        }
        String codeNameProperty = properties.getProperty(PkgProps.VERSION_CODENAME, null);
        String extensionLevelProperty = properties.getProperty(PkgProps.VERSION_EXTENSION_LEVEL);
        Integer extensionLevel = null;
        if (extensionLevelProperty != null) {
            try {
                extensionLevel = Integer.parseInt(extensionLevelProperty);
            } catch (NumberFormatException ignored) {
                // If there is not a parsable extension level, keep the null value.
            }
        }

        String isBaseExtensionProperty = properties.getProperty(PkgProps.VERSION_IS_BASE_EXTENSION);
        boolean isBaseExtension =
                isBaseExtensionProperty == null || parseBoolean(isBaseExtensionProperty);

        return new AndroidVersion(apiLevel, codeNameProperty, extensionLevel, isBaseExtension);
    }

    private static boolean parseBoolean(@NonNull String booleanStringValue) {
        return !"false".equals(booleanStringValue);
    }
}
