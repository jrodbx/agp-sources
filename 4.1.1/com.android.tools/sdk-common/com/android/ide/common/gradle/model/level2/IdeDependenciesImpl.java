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
package com.android.ide.common.gradle.model.level2;

import com.android.annotations.NonNull;
import com.android.builder.model.level2.Library;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public class IdeDependenciesImpl implements IdeDependencies, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 3L;

    @NonNull private final Collection<Library> myAndroidLibraries;
    @NonNull private final Collection<Library> myJavaLibraries;
    @NonNull private final Collection<Library> myModuleDependencies;
    @NonNull private final Collection<File> myRuntimeOnlyClasses;
    private final int myHashCode;

    // Used for serialization by the IDE. DO NOT USE ELSEWHERE.
    @SuppressWarnings("unused")
    public IdeDependenciesImpl() {
        myAndroidLibraries = Collections.emptyList();
        myJavaLibraries = Collections.emptyList();
        myModuleDependencies = Collections.emptyList();
        myRuntimeOnlyClasses = Collections.emptyList();

        myHashCode = 0;
    }

    IdeDependenciesImpl(
            @NonNull ImmutableList<Library> androidLibraries,
            @NonNull ImmutableList<Library> javaLibraries,
            @NonNull ImmutableList<Library> moduleDependencies,
            @NonNull ImmutableList<File> runtimeOnlyClasses) {
        myAndroidLibraries = androidLibraries;
        myJavaLibraries = javaLibraries;
        myModuleDependencies = moduleDependencies;
        myRuntimeOnlyClasses = runtimeOnlyClasses;
        myHashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public Collection<Library> getAndroidLibraries() {
        return myAndroidLibraries;
    }

    @Override
    @NonNull
    public Collection<Library> getJavaLibraries() {
        return myJavaLibraries;
    }

    @Override
    @NonNull
    public Collection<Library> getModuleDependencies() {
        return myModuleDependencies;
    }

    @Override
    @NonNull
    public Collection<File> getRuntimeOnlyClasses() {
        return myRuntimeOnlyClasses;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeDependenciesImpl)) {
            return false;
        }
        IdeDependenciesImpl item = (IdeDependenciesImpl) o;
        return Objects.equals(myAndroidLibraries, item.myAndroidLibraries)
                && Objects.equals(myJavaLibraries, item.myJavaLibraries)
                && Objects.equals(myModuleDependencies, item.myModuleDependencies)
                && Objects.equals(myRuntimeOnlyClasses, item.myRuntimeOnlyClasses);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(
                myAndroidLibraries, myJavaLibraries, myModuleDependencies, myRuntimeOnlyClasses);
    }

    @Override
    public String toString() {
        return "IdeDependenciesImpl{"
                + "myAndroidLibraries="
                + myAndroidLibraries
                + ", myJavaLibraries="
                + myJavaLibraries
                + ", myModuleDependencies="
                + myModuleDependencies
                + ", myRuntimeOnlyClasses="
                + myRuntimeOnlyClasses
                + '}';
    }
}
