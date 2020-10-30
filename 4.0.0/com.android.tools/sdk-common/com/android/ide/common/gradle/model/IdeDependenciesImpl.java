/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.ide.common.gradle.model;

import static java.util.Objects.requireNonNull;

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Consumer;

/** Creates a deep copy of a {@link Dependencies}. */
public final class IdeDependenciesImpl implements IdeDependencies, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 4L;

    @NonNull private final Collection<AndroidLibrary> myLibraries;
    @NonNull private final Collection<JavaLibrary> myJavaLibraries;
    @NonNull private final Collection<String> myProjects;
    @NonNull private final Collection<ProjectIdentifier> myJavaModules;
    @NonNull private final Collection<File> myRuntimeOnlyClasses;
    private final int myHashCode;

    // Used for serialization by the IDE.
    IdeDependenciesImpl() {
        myLibraries = Collections.emptyList();
        myJavaLibraries = Collections.emptyList();
        myProjects = Collections.emptyList();
        myJavaModules = Collections.emptyList();
        myRuntimeOnlyClasses = Collections.emptyList();

        myHashCode = 0;
    }

    public IdeDependenciesImpl(@NonNull Dependencies dependencies, @NonNull ModelCache modelCache) {

        myLibraries =
                IdeModel.copy(
                        dependencies.getLibraries(),
                        modelCache,
                        library -> new IdeAndroidLibrary(library, modelCache));
        myJavaLibraries =
                IdeModel.copy(
                        dependencies.getJavaLibraries(),
                        modelCache,
                        library -> new IdeJavaLibrary(library, modelCache));

        myProjects = ImmutableList.copyOf(dependencies.getProjects());
        myJavaModules =
                IdeModel.copy(
                        dependencies::getJavaModules,
                        modelCache,
                        projectId -> new IdeProjectIdentifierImpl(projectId));
        myRuntimeOnlyClasses =
                ImmutableList.copyOf(
                        requireNonNull(
                                IdeModel.copyNewProperty(
                                        dependencies::getRuntimeOnlyClasses,
                                        Collections.emptyList())));

        myHashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public Collection<AndroidLibrary> getLibraries() {
        return myLibraries;
    }

    @Override
    @NonNull
    public Collection<JavaLibrary> getJavaLibraries() {
        return myJavaLibraries;
    }

    @Override
    @NonNull
    public Collection<String> getProjects() {
        return myProjects;
    }

    @NonNull
    @Override
    public Collection<ProjectIdentifier> getJavaModules() {
        return myJavaModules;
    }

    @NonNull
    @Override
    public Collection<File> getRuntimeOnlyClasses() {
        return myRuntimeOnlyClasses;
    }

    @Override
    public void forEachLibrary(@NonNull Consumer<IdeAndroidLibrary> action) {
        for (AndroidLibrary library : myLibraries) {
            action.accept((IdeAndroidLibrary) library);
        }
    }

    @Override
    public void forEachJavaLibrary(@NonNull Consumer<IdeJavaLibrary> action) {
        for (JavaLibrary library : myJavaLibraries) {
            action.accept((IdeJavaLibrary) library);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeDependenciesImpl)) {
            return false;
        }
        IdeDependenciesImpl that = (IdeDependenciesImpl) o;
        return Objects.equals(myLibraries, that.myLibraries)
                && Objects.equals(myJavaLibraries, that.myJavaLibraries)
                && Objects.equals(myProjects, that.myProjects)
                && Objects.equals(myJavaModules, that.myJavaModules)
                && Objects.equals(myRuntimeOnlyClasses, that.myRuntimeOnlyClasses);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(
                myLibraries, myJavaLibraries, myProjects, myJavaModules, myRuntimeOnlyClasses);
    }

    @Override
    public String toString() {
        return "IdeDependencies{"
                + "myLibraries="
                + myLibraries
                + ", myJavaLibraries="
                + myJavaLibraries
                + ", myProjects="
                + myProjects
                + ", myJavaModules="
                + myJavaModules
                + ", myRuntimeOnlyClasses="
                + myRuntimeOnlyClasses
                + '}';
    }
}
