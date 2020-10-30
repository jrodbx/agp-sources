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

import com.android.annotations.NonNull;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.GraphItem;
import com.google.common.collect.ImmutableList;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Creates a deep copy of a {@link DependencyGraphs}. */
public final class IdeDependencyGraphs implements DependencyGraphs, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 2L;

    @NonNull private final List<GraphItem> myCompileDependencies;
    @NonNull private final List<GraphItem> myPackageDependencies;
    @NonNull private final List<String> myProvidedLibraries;
    @NonNull private final List<String> mySkippedLibraries;
    private final int myHashCode;

    // Used for serialization by the IDE.
    @SuppressWarnings("unused")
    IdeDependencyGraphs() {
        myCompileDependencies = Collections.emptyList();
        myPackageDependencies = Collections.emptyList();
        myProvidedLibraries = Collections.emptyList();
        mySkippedLibraries = Collections.emptyList();

        myHashCode = 0;
    }

    public IdeDependencyGraphs(@NonNull DependencyGraphs graphs, @NonNull ModelCache modelCache) {
        myCompileDependencies =
                IdeModel.copy(
                        graphs.getCompileDependencies(),
                        modelCache,
                        item -> new IdeGraphItem(item, modelCache));
        myPackageDependencies =
                IdeModel.copy(
                        graphs.getPackageDependencies(),
                        modelCache,
                        item -> new IdeGraphItem(item, modelCache));
        myProvidedLibraries = ImmutableList.copyOf(graphs.getProvidedLibraries());
        mySkippedLibraries = ImmutableList.copyOf(graphs.getSkippedLibraries());

        myHashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public List<GraphItem> getCompileDependencies() {
        return myCompileDependencies;
    }

    @Override
    @NonNull
    public List<GraphItem> getPackageDependencies() {
        return myPackageDependencies;
    }

    @Override
    @NonNull
    public List<String> getProvidedLibraries() {
        return myProvidedLibraries;
    }

    @Override
    @NonNull
    public List<String> getSkippedLibraries() {
        return mySkippedLibraries;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeDependencyGraphs)) {
            return false;
        }
        IdeDependencyGraphs graphs = (IdeDependencyGraphs) o;
        return Objects.equals(myCompileDependencies, graphs.myCompileDependencies)
                && Objects.equals(myPackageDependencies, graphs.myPackageDependencies)
                && Objects.equals(myProvidedLibraries, graphs.myProvidedLibraries)
                && Objects.equals(mySkippedLibraries, graphs.mySkippedLibraries);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(
                myCompileDependencies,
                myPackageDependencies,
                myProvidedLibraries,
                mySkippedLibraries);
    }

    @Override
    public String toString() {
        return "IdeDependencyGraphs{"
                + "myCompileDependencies="
                + myCompileDependencies
                + ", myPackageDependencies="
                + myPackageDependencies
                + ", myProvidedLibraries="
                + myProvidedLibraries
                + ", mySkippedLibraries="
                + mySkippedLibraries
                + '}';
    }
}
