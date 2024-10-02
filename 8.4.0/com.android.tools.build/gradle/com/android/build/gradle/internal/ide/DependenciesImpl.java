/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;


/** Implementation of {@link com.android.builder.model.Dependencies} interface */
@Immutable
public class DependenciesImpl implements Dependencies, Serializable {
    private static final long serialVersionUID = 2L;

    @NonNull
    public static final Dependencies EMPTY =
            new DependenciesImpl(
                    ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), ImmutableList.of());


    @Immutable
    public static class ProjectIdentifierImpl implements ProjectIdentifier, Serializable {
        private static final long serialVersionUID = 1L;

        @NonNull private final String buildId;
        @NonNull private final String projectPath;

        public ProjectIdentifierImpl(@NonNull String buildId, @NonNull String projectPath) {
            this.buildId = buildId;
            this.projectPath = projectPath;
        }

        @NonNull
        @Override
        public String getBuildId() {
            return buildId;
        }

        @NonNull
        @Override
        public String getProjectPath() {
            return projectPath;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ProjectIdentifierImpl that = (ProjectIdentifierImpl) o;
            return Objects.equals(buildId, that.buildId)
                    && Objects.equals(projectPath, that.projectPath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(buildId, projectPath);
        }
    }

    @NonNull private final List<AndroidLibrary> libraries;
    @NonNull private final List<JavaLibrary> javaLibraries;
    @NonNull private final Set<String> projects;
    @NonNull private final List<ProjectIdentifier> javaModules;
    @NonNull private final List<File> runtimeOnlyClasses;

    public DependenciesImpl(
            @NonNull List<AndroidLibrary> libraries,
            @NonNull List<JavaLibrary> javaLibraries,
            @NonNull List<ProjectIdentifier> javaModules,
            @NonNull List<File> runtimeOnlyClasses) {
        this.libraries = libraries;
        this.javaLibraries = javaLibraries;
        this.javaModules = javaModules;
        this.runtimeOnlyClasses = runtimeOnlyClasses;
        projects =
                javaModules
                        .stream()
                        .map(ProjectIdentifier::getProjectPath)
                        .collect(ImmutableSet.toImmutableSet());
    }

    @NonNull
    @Override
    public Collection<AndroidLibrary> getLibraries() {
        return libraries;
    }

    @NonNull
    @Override
    public Collection<JavaLibrary> getJavaLibraries() {
        return javaLibraries;
    }

    @NonNull
    @Override
    public Collection<String> getProjects() {
        return projects;
    }

    @NonNull
    @Override
    public List<ProjectIdentifier> getJavaModules() {
        return javaModules;
    }

    @NonNull
    @Override
    public Collection<File> getRuntimeOnlyClasses() {
        return runtimeOnlyClasses;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("libraries", libraries)
                .add("javaLibraries", javaLibraries)
                .add("javaModules", javaModules)
                .add("projects", projects)
                .add("runtimeOnlyClasses", runtimeOnlyClasses)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DependenciesImpl that = (DependenciesImpl) o;
        return Objects.equals(libraries, that.libraries)
                && Objects.equals(javaLibraries, that.javaLibraries)
                && Objects.equals(projects, that.projects)
                && Objects.equals(javaModules, that.javaModules)
                && Objects.equals(runtimeOnlyClasses, that.runtimeOnlyClasses);
    }

    @Override
    public int hashCode() {
        return Objects.hash(libraries, javaLibraries, projects, javaModules, runtimeOnlyClasses);
    }
}
