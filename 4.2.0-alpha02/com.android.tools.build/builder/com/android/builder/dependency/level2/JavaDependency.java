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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.dependency.HashCodeUtils;
import com.android.builder.dependency.MavenCoordinatesImpl;
import com.android.builder.model.MavenCoordinates;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.List;

/**
 * A Java dependency
 */
@Immutable
public final class JavaDependency extends Dependency {

    private static final String LOCAL_JAR_GROUPID = "__local_jars__";

    private final boolean isLocal;
    private final int hashcode;

    public JavaDependency(
            @NonNull File artifactFile,
            @NonNull MavenCoordinates coordinates,
            @NonNull String name,
            @Nullable String projectPath) {
        this(artifactFile, coordinates, name, projectPath, false);
    }

    public JavaDependency(@NonNull File artifactFile) {
        this(artifactFile, getCoordForLocalJar(artifactFile), null, null, true);
    }

    private JavaDependency(
            @NonNull File artifactFile,
            @NonNull MavenCoordinates coordinates,
            @Nullable String name,
            @Nullable String projectPath,
            boolean isLocal) {
        super(artifactFile, coordinates, name != null ? name : coordinates.toString(), projectPath);
        this.isLocal = isLocal;
        hashcode = computeHashCode();
    }

    @NonNull @Override
    public File getClasspathFile() {
        return getArtifactFile();
    }

    @Nullable @Override
    public List<File> getAdditionalClasspath() {
        return ImmutableList.of();
    }

    /**
     * Returns if the dependency is a local jar, i.e. a jar that's stored in the source rather
     * than accessed through a maven repo or a sub-module.
     */
    @Override
    public boolean isLocal() {
        return isLocal;
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
        JavaDependency that = (JavaDependency) o;
        return isLocal == that.isLocal;
    }

    @Override
    public int hashCode() {
        return hashcode;
    }

    private int computeHashCode() {
        return HashCodeUtils.hashCode(super.hashCode(), isLocal);
    }

    @NonNull
    public static MavenCoordinatesImpl getCoordForLocalJar(@NonNull File jarFile) {
        return new MavenCoordinatesImpl(LOCAL_JAR_GROUPID, jarFile.getPath(), "unspecified");
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("artifactFile", getArtifactFile())
                .add("coordinates", getCoordinates())
                .add("projectPath", getProjectPath())
                .add("isLocal", isLocal)
                .toString();
    }
}
