/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.sdklib;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.repository.PkgProps;

import java.util.Properties;

public class AndroidVersionHelper {

    /**
     * Creates an {@link AndroidVersion} from {@link Properties}, with default values if the {@link
     * Properties} object doesn't contain the expected values. <p>The {@link Properties} is
     * expected to have been filled with the deleted
     * <code>saveProperties(AndroidVersion, Properties)</code>}.
     */
    @NonNull
    public static AndroidVersion create(@Nullable Properties properties, int defaultApiLevel,
            @Nullable String defaultCodeName) {
        if (properties == null) {
            return new AndroidVersion(defaultApiLevel, defaultCodeName);
        } else {
            int api = Integer.parseInt(properties.getProperty(PkgProps.VERSION_API_LEVEL,
                    Integer.toString(defaultApiLevel)));
            String codeName = properties.getProperty(PkgProps.VERSION_CODENAME, defaultCodeName);
            return new AndroidVersion(api, codeName);
        }
    }

    /**
     * Creates an {@link AndroidVersion} from {@link Properties}. The properties must contain
     * android version information, or an exception will be thrown.
     *
     * @throws AndroidVersion.AndroidVersionException if no Android version information have been
     *                                                found
     */
    @NonNull
    public static AndroidVersion create(@NonNull Properties properties)
            throws AndroidVersion.AndroidVersionException {
        Exception error = null;

        String apiLevel = properties.getProperty(PkgProps.VERSION_API_LEVEL, null/*defaultValue*/);
        if (apiLevel != null) {
            try {
                int api = Integer.parseInt(apiLevel);
                String codeName = properties.getProperty(PkgProps.VERSION_CODENAME,
                        null/*defaultValue*/);
                return new AndroidVersion(api, codeName);
            } catch (NumberFormatException e) {
                error = e;
            }
        }

        // reaching here means the Properties object did not contain the apiLevel which is required.
        throw new AndroidVersion.AndroidVersionException(PkgProps.VERSION_API_LEVEL + " not found!",
                error);
    }
}
