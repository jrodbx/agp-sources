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
import com.android.builder.model.MavenCoordinates;
import com.google.common.base.Preconditions;
import java.io.File;
import java.util.List;
import java.util.Objects;

/**
 * Base attributes for a dependency element.
 *
 * All implementations must enforce immutability.
 */
@Immutable
public abstract class Dependency {

    @Nullable private final File artifactFile;
    @NonNull
    private final MavenCoordinates coordinates;
    @NonNull
    private final String name;
    /**
     * if the dependency is a sub-project, then the project path
     */
    @Nullable
    private final String projectPath;


    public Dependency(
            @Nullable File artifactFile,
            @NonNull MavenCoordinates coordinates,
            @NonNull String name,
            @Nullable String projectPath) {
        this.artifactFile = artifactFile;
        this.coordinates = coordinates;
        this.name = name;
        this.projectPath = projectPath;
    }

    /**
     * Returns a unique address that matches {@link DependencyNode#getAddress()}.
     */
    @NonNull
    public Object getAddress() {
        return projectPath != null ? projectPath : coordinates;
    }

    /**
     * Returns the artifact location.
     */
    @NonNull
    public File getArtifactFile() {
        Preconditions.checkNotNull(artifactFile, "artifactFile should not be null.");
        return artifactFile;
    }

    @NonNull
    public abstract File getClasspathFile();

    @Nullable
    public abstract List<File> getAdditionalClasspath();

    /**
     * Returns the maven coordinates.
     */
    @NonNull
    public MavenCoordinates getCoordinates() {
        return coordinates;
    }

    /**
     * Returns a user friendly name.
     */
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * Returns an optional project path if the dependency is a sub-module.
     */
    @Nullable
    public String getProjectPath() {
        return projectPath;
    }

    public boolean isLocal() {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Dependency that = (Dependency) o;
        return Objects.equals(artifactFile, that.artifactFile) &&
                Objects.equals(coordinates, that.coordinates) &&
                Objects.equals(name, that.name) &&
                Objects.equals(projectPath, that.projectPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(artifactFile, coordinates, name, projectPath);
    }
}
