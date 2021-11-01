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

package com.android.sdklib.repository.targets;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.OptionalLibrary;
import com.android.sdklib.repository.meta.Library;
import com.google.common.base.Objects;
import java.io.File;

/**
 * Internal implementation of OptionalLibrary
 *
 * @deprecated in favor of {@link Library}
 */
@Deprecated
public class OptionalLibraryImpl implements OptionalLibrary {

    @NonNull
    private final String mLibraryName;
    @NonNull
    private final File mJarFile;
    @NonNull
    private final String mDescription;
    private final boolean mRequireManifestEntry;

    public OptionalLibraryImpl(
            @NonNull String libraryName,
            @NonNull File jarFile,
            @NonNull String description,
            boolean requireManifestEntry) {
        mLibraryName = libraryName;
        mJarFile = jarFile;
        mDescription = description;
        mRequireManifestEntry = requireManifestEntry;
    }

    @Override
    @NonNull
    public String getName() {
        return mLibraryName;
    }

    @Override
    @NonNull
    public File getJar() {
        return mJarFile;
    }

    @Override
    @NonNull
    public String getDescription() {
        return mDescription;
    }

    @Override
    public boolean isManifestEntryRequired() {
        return mRequireManifestEntry;
    }

    @Nullable
    @Override
    public String getLocalJarPath() {
        return getJar().getName();
    }

    public boolean equals(Object o) {
        if (!(o instanceof OptionalLibrary)) {
            return false;
        }
        OptionalLibrary lib = (OptionalLibrary) o;
        return Objects.equal(lib.getLocalJarPath(), getLocalJarPath()) && lib.getName()
                .equals(getName());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getLocalJarPath(), getName());
    }

    @Override
    public String toString() {
        return String.format("OptionalLibrary[name=\"%1$s\" description=\"%2$s\" jar=\"%3$s\"]",
                getName(), getDescription(), getLocalJarPath());
    }
}
