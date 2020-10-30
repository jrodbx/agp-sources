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
package com.android.sdklib.repository.meta;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.OptionalLibrary;
import com.google.common.base.Objects;
import java.io.File;
import javax.xml.bind.annotation.XmlTransient;

/** Information about a {@link OptionalLibrary} provided by a package. */
@XmlTransient
public abstract class Library implements OptionalLibrary {

    /**
     * Reference to the path of the containing package.
     */
    @XmlTransient
    private File mPackagePath;

    /**
     * Sets the path of the containing package. Must be called before calling {@link #getJar()}.
     */
    public void setPackagePath(@NonNull File packagePath) {
        mPackagePath = packagePath;
    }

    /**
     * Absolute path to the library jar file. Will be {@code null} when a legacy remote package
     * is installed.
     */
    @Override
    @Nullable
    public File getJar() {
        assert mPackagePath != null;
        String localPath = getLocalJarPath();
        if (localPath == null) {
            return null;
        }
        localPath = localPath.replace('/', File.separatorChar);
        return new File(mPackagePath, SdkConstants.OS_ADDON_LIBS_FOLDER + localPath);
    }

    /**
     * The name of the library.
     */
    @Override
    @NonNull
    public abstract String getName();

    /**
     * User-friendly description of the library.
     */
    @Override
    @NonNull
    public abstract String getDescription();

    /**
     * Whether a manifest entry is required for this library.
     */
    @Override
    public abstract boolean isManifestEntryRequired();

    public abstract void setLocalJarPath(String path);

    public abstract void setDescription(String description);

    public abstract void setName(String name);

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

    public abstract void setManifestEntryRequired(Boolean b);
}
