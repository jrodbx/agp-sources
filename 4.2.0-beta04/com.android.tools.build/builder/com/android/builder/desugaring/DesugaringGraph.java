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

package com.android.builder.desugaring;

import com.android.annotations.NonNull;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class that keeps track of desugaring dependencies. Main goal of this class is to provide a set of
 * paths that should be reprocessed, in addition to the set of class files whose content has
 * changed.
 *
 * <p>It is built by combining {@link DesugaringData}, local desugaring dependencies, for all paths
 * that are relevant. Once all data is added, for a path, a dependent set of paths can be calculated
 * by traversing the graph in the following way:
 *
 * <ul>
 *   <li>For the input path, get all types it contains. For .class file this will be a single type,
 *       for .jar it will be multiple.
 *   <li>For every type T, from the previous step, find the set of dependent types. These are the
 *       types whose desugaring output depends on T.
 *   <li>Once the set of dependent types is known, paths that define them are found, and that is the
 *       resulting set of paths.
 * </ul>
 */
public class DesugaringGraph {
    @NonNull
    public static final DesugaringGraph EMPTY =
            new DesugaringGraph(Collections.emptyList()) {
                @Override
                public void update(@NonNull Collection<DesugaringData> data) {
                    throw new AssertionError();
                }

                @NonNull
                @Override
                public Set<Path> getDependentPaths(@NonNull Path path) {
                    return ImmutableSet.of();
                }
            };

    @NonNull private final TypeDependencies typeDependencies;
    @NonNull private final TypePaths typePaths;

    DesugaringGraph(@NonNull Collection<DesugaringData> data) {
        typeDependencies = new TypeDependencies();
        typePaths = new TypePaths();

        for (DesugaringData d : data) {
            typeDependencies.add(d.getInternalName(), d.getDependencies());
            typePaths.add(d.getPath(), d.getInternalName());
        }
    }

    /** Initializes or updates the graph with the new data. */
    public void update(@NonNull Collection<DesugaringData> data) {
        removeItems(data);
        insertLiveItems(data);
    }

    /** Returns a set of paths the given path is depending on. */
    @NonNull
    public Set<Path> getDependenciesPaths(@NonNull Path path) {
        Set<String> types = typePaths.getTypes(path);

        Set<String> impactedTypes = Sets.newHashSet();
        for (String type : types) {
            impactedTypes.addAll(typeDependencies.getAllDependencies(type));
        }

        Set<Path> impactedPaths = Sets.newHashSetWithExpectedSize(impactedTypes.size());
        for (String impactedType : impactedTypes) {
            impactedPaths.addAll(typePaths.getPaths(impactedType));
        }
        impactedPaths.remove(path);
        return impactedPaths;
    }

    /**
     * Returns a set of paths that should be additionally processed, based on the changed input
     * path.
     */
    @NonNull
    public Set<Path> getDependentPaths(@NonNull Path path) {
        Set<String> types = typePaths.getTypes(path);

        Set<String> impactedTypes = Sets.newHashSet();
        for (String type : types) {
            impactedTypes.addAll(typeDependencies.getAllDependents(type));
        }

        Set<Path> impactedPaths = Sets.newHashSetWithExpectedSize(impactedTypes.size());
        for (String impactedType : impactedTypes) {
            impactedPaths.addAll(typePaths.getPaths(impactedType));
        }
        impactedPaths.remove(path);
        return impactedPaths;
    }

    @VisibleForTesting
    @NonNull
    Set<String> getDependents(@NonNull String type) {
        return typeDependencies.getDependents(type);
    }

    @VisibleForTesting
    @NonNull
    Set<String> getDependencies(@NonNull String type) {
        return typeDependencies.getDependencies(type);
    }

    @VisibleForTesting
    @NonNull
    Set<String> getAllDependentTypes(@NonNull String type) {
        return typeDependencies.getAllDependents(type);
    }

    private void removeItems(@NonNull Collection<DesugaringData> data) {
        Set<Path> modifiedPaths =
                data.stream().map(DesugaringData::getPath).collect(Collectors.toSet());

        for (DesugaringData d : data) {
            Set<String> typesInPath = typePaths.remove(d.getPath(), modifiedPaths);
            if (typesInPath == null) {
                continue;
            }

            for (String removedType : typesInPath) {
                typeDependencies.remove(removedType);
            }
        }
    }

    private void insertLiveItems(@NonNull Collection<DesugaringData> data) {
        for (DesugaringData d : data) {
            if (!d.isLive()) {
                continue;
            }

            typePaths.add(d.getPath(), d.getInternalName());
            typeDependencies.add(d.getInternalName(), d.getDependencies());
        }
    }
}
