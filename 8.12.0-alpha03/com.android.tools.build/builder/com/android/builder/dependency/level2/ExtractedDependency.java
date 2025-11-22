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

package com.android.builder.dependency.level2;

import static com.android.SdkConstants.FD_ASSETS;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.dependency.HashCodeUtils;
import com.android.builder.model.MavenCoordinates;
import com.android.manifmerger.ManifestProvider;
import java.io.File;
import java.util.Objects;

/**
 * Based implementation for all dependency types that need to extract its archive before usage.
 */
public abstract class ExtractedDependency extends Dependency implements ManifestProvider {

    @NonNull
    private final File extractedFolder;

    // pre-computed derived values for performance, not part of the object identity.
    @NonNull
    private final File manifestFile;

    private final String variant;

    /**
     * Creates the mBundle dependency with an optional mName.
     *
     * @param artifactFile the dependency's artifact file.
     * @param coordinates the maven coordinates of the artifact
     * @param name the dependency's user friendly name
     * @param projectPath an optional project path.
     * @param extractedFolder the folder containing the unarchived library content.
     */
    public ExtractedDependency(
            @Nullable File artifactFile,
            @NonNull MavenCoordinates coordinates,
            @NonNull String name,
            @Nullable String projectPath,
            @NonNull File extractedFolder,
            @Nullable String variant) {
        super(artifactFile, coordinates, name, projectPath);
        this.extractedFolder = extractedFolder;
        this.variant = variant;
        this.manifestFile = new File(extractedFolder, FN_ANDROID_MANIFEST_XML);
    }

    /**
     * Returns a unique address that matches {@link DependencyNode#getAddress()}.
     */
    @Override
    @NonNull
    public Object getAddress() {
        if (variant != null) {
            return getProjectPath() + "::" + variant;
        }

        return super.getAddress();
    }

    @NonNull
    public File getExtractedFolder() {
        return extractedFolder;
    }

    /**
     * Returns an optional configuration name if the library is output by a module
     * that publishes more than one variant.
     */
    @Nullable
    public String getVariant() {
        return variant;
    }

    @Override @NonNull
    public File getManifest() {
        return manifestFile;
    }

    @NonNull
    public File getResFolder() {
        return new File(extractedFolder, FD_RES);
    }

    @NonNull
    public File getAssetsFolder() {
        return new File(extractedFolder, FD_ASSETS);
    }

    /**
     * Returns the location of the jar file to use for either packaging or compiling depending on
     * the bundle type.
     *
     * @return a File for the jar file. The file may not point to an existing file.
     */
    @NonNull
    public abstract File getJarFile();

    @NonNull @Override
    public File getClasspathFile() {
        return getJarFile();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        ExtractedDependency that = (ExtractedDependency) o;
        return Objects.equals(extractedFolder, that.extractedFolder)
                && Objects.equals(variant, that.variant);
    }

    @Override
    public int hashCode() {
        return HashCodeUtils.hashCode(super.hashCode(), extractedFolder, variant);
    }
}
