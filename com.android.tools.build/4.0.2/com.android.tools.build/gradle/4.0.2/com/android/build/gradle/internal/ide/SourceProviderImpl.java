/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.internal.ide;

import com.android.annotations.NonNull;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.model.SourceProvider;
import com.google.common.base.MoreObjects;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;

/**
 * Implementation of SourceProvider that is serializable. Objects used in the DSL cannot be
 * serialized.
 */
@Immutable
final class SourceProviderImpl implements SourceProvider, Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    private final String name;
    @NonNull
    private final File manifestFile;
    @NonNull
    private final Collection<File> javaDirs;
    @NonNull
    private final Collection<File> resourcesDirs;
    @NonNull
    private final Collection<File> aidlDirs;
    @NonNull
    private final Collection<File> rsDirs;
    @NonNull
    private final Collection<File> cDirs;
    @NonNull
    private final Collection<File> cppDirs;
    @NonNull
    private final Collection<File> resDirs;
    @NonNull
    private final Collection<File> assetsDirs;
    @NonNull
    private final Collection<File> libsDirs;
    @NonNull
    private final Collection<File> shaderDirs;

    public SourceProviderImpl(@NonNull SourceProvider sourceProvider) {
        this.name = sourceProvider.getName();
        this.manifestFile = sourceProvider.getManifestFile();
        this.javaDirs = sourceProvider.getJavaDirectories();
        this.resourcesDirs = sourceProvider.getResourcesDirectories();
        this.aidlDirs = sourceProvider.getAidlDirectories();
        this.rsDirs = sourceProvider.getRenderscriptDirectories();
        this.cDirs = sourceProvider.getCDirectories();
        this.cppDirs = sourceProvider.getCDirectories();
        this.resDirs = sourceProvider.getResDirectories();
        this.assetsDirs = sourceProvider.getAssetsDirectories();
        this.libsDirs = sourceProvider.getJniLibsDirectories();
        this.shaderDirs = sourceProvider.getShadersDirectories();
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @NonNull
    @Override
    public File getManifestFile() {
        return manifestFile;
    }

    @NonNull
    @Override
    public Collection<File> getJavaDirectories() {
        return javaDirs;
    }

    @NonNull
    @Override
    public Collection<File> getResourcesDirectories() {
        return resourcesDirs;
    }

    @NonNull
    @Override
    public Collection<File> getAidlDirectories() {
        return aidlDirs;
    }

    @NonNull
    @Override
    public Collection<File> getRenderscriptDirectories() {
        return rsDirs;
    }

    @NonNull
    @Override
    public Collection<File> getCDirectories() {
        return cDirs;
    }

    @NonNull
    @Override
    public Collection<File> getCppDirectories() {
        return cppDirs;
    }

    @NonNull
    @Override
    public Collection<File> getResDirectories() {
        return resDirs;
    }

    @NonNull
    @Override
    public Collection<File> getAssetsDirectories() {
        return assetsDirs;
    }

    @NonNull
    @Override
    public Collection<File> getJniLibsDirectories() {
        return libsDirs;
    }

    @NonNull
    @Override
    public Collection<File> getShadersDirectories() {
        return shaderDirs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SourceProviderImpl that = (SourceProviderImpl) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(manifestFile, that.manifestFile) &&
                Objects.equals(javaDirs, that.javaDirs) &&
                Objects.equals(resourcesDirs, that.resourcesDirs) &&
                Objects.equals(aidlDirs, that.aidlDirs) &&
                Objects.equals(rsDirs, that.rsDirs) &&
                Objects.equals(cDirs, that.cDirs) &&
                Objects.equals(cppDirs, that.cppDirs) &&
                Objects.equals(resDirs, that.resDirs) &&
                Objects.equals(assetsDirs, that.assetsDirs) &&
                Objects.equals(libsDirs, that.libsDirs) &&
                Objects.equals(shaderDirs, that.shaderDirs);
    }

    @Override
    public int hashCode() {
        return Objects
                .hash(name, manifestFile, javaDirs, resourcesDirs, aidlDirs, rsDirs, cDirs, cppDirs,
                        resDirs, assetsDirs, libsDirs, shaderDirs);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", name)
                .add("manifestFile", manifestFile)
                .add("javaDirs", javaDirs)
                .add("resourcesDirs", resourcesDirs)
                .add("aidlDirs", aidlDirs)
                .add("rsDirs", rsDirs)
                .add("cDirs", cDirs)
                .add("cppDirs", cppDirs)
                .add("resDirs", resDirs)
                .add("assetsDirs", assetsDirs)
                .add("libsDirs", libsDirs)
                .add("shaderDirs", shaderDirs)
                .toString();
    }
}
