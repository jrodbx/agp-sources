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

package com.android.sdklib.repository.meta;

import com.android.annotations.NonNull;
import com.android.sdklib.repository.IdDisplay;

import java.io.File;

/**
 * Parent class for {@code ObjectFactories} created by xjc from sdk-common-XX.xsd, for
 * creating sdk-specific types shared by multiple concrete schemas.
 */
@SuppressWarnings("MethodMayBeStatic")
public abstract class SdkCommonFactory {

    /**
     * Create a new {@link IdDisplay}.
     */
    @NonNull
    public abstract IdDisplay createIdDisplayType();

    /**
     * Create a new {@link Library};
     */
    @NonNull
    public abstract Library createLibraryType();

    /**
     * Convenience to create and initialize a {@link Library}.
     */
    public Library createLibraryType(
            @NonNull String libraryName,
            @NonNull String jarPath,
            @NonNull String description,
            @NonNull File packagePath,
            boolean requireManifestEntry) {
        Library result = createLibraryType();
        result.setName(libraryName);
        result.setLocalJarPath(jarPath);
        result.setDescription(description);
        result.setManifestEntryRequired(requireManifestEntry);
        result.setPackagePath(packagePath);
        return result;
    }

}
