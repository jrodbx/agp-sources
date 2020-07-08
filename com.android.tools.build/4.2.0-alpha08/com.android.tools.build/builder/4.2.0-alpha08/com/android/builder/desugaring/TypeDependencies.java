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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Keeps track of the type desugaring dependencies. This is required in order to determine a set of
 * types that might need to be reprocessed if some type changes.
 *
 * <p>For every type T, this class can calculate set of types whose desugaring depends on T, by
 * following transitive dependencies between types.
 *
 * <p>For details when a dependency between two types exists, see {@link DesugaringData}.
 */
final class TypeDependencies {
    /** Map from type to types it depends on in the desugaring process. */
    @NonNull private final Map<String, Set<String>> typeToDependencies = Maps.newHashMap();
    /**
     * Map from type to types that depend on it in the desugaring process. This field should be
     * accessed only using {@link #reverseMapping()}.
     */
    @NonNull private final Map<String, Set<String>> typeToDependents = Maps.newHashMap();

    boolean isReverseMappingValid = false;

    void add(@NonNull String dependent, @NonNull Set<String> dependencies) {
        Set<String> myDependencies = typeToDependencies.getOrDefault(dependent, new HashSet<>());
        myDependencies.addAll(dependencies);
        typeToDependencies.put(dependent, myDependencies);
        invalidateReverseMapping();
    }

    @NonNull
    Set<String> getDependencies(@NonNull String type) {
        return typeToDependencies.getOrDefault(type, ImmutableSet.of());
    }

    @NonNull
    Set<String> getDependents(@NonNull String type) {
        return reverseMapping().getOrDefault(type, ImmutableSet.of());
    }

    @NonNull
    Set<String> getAllDependents(@NonNull String type) {
        return collectNeighbours(type, this::getDependents);
    }

    @NonNull
    Set<String> getAllDependencies(@NonNull String type) {
        return collectNeighbours(type, this::getDependencies);
    }

    /**
     * Transitively collect neighbours of {@code start} node in a graph.
     *
     * @param start Start node.
     * @param getNeighbours A function giving the neighbours of a node.
     * @return all nodes having a direct or indirect neighbour relation from {@code start} node.
     *     This does not include {@code start} node.
     */
    private static Set<String> collectNeighbours(
            @NonNull String start, @NonNull Function<String, Set<String>> getNeighbours) {
        // BFS traversal: we start from start, traverse all of its neighbours, then neighbours of
        // its neighbours, and so on.
        Set<String> children = Sets.newHashSet();

        ArrayDeque<String> dequeue = new ArrayDeque<>();
        dequeue.add(start);

        while (!dequeue.isEmpty()) {
            String current = dequeue.removeFirst();

            Set<String> neighbours = getNeighbours.apply(current);
            for (String neighbour : neighbours) {
                if ((!neighbour.equals(start)) && children.add(neighbour)) {
                    dequeue.addLast(neighbour);
                }
            }
        }

        return children;

    }

    private void invalidateReverseMapping() {
        isReverseMappingValid = false;
    }

    @NonNull
    private Map<String, Set<String>> reverseMapping() {
        if (isReverseMappingValid) {
            return typeToDependents;
        }

        typeToDependents.clear();
        for (Map.Entry<String, Set<String>> typeToDeps : typeToDependencies.entrySet()) {
            for (String dependency : typeToDeps.getValue()) {
                Set<String> myDependents =
                        typeToDependents.getOrDefault(dependency, Sets.newHashSet());
                myDependents.add(typeToDeps.getKey());
                this.typeToDependents.put(dependency, myDependents);
            }
        }

        isReverseMappingValid = true;
        return typeToDependents;
    }

    void remove(@NonNull String removedType) {
        typeToDependencies.remove(removedType);
        invalidateReverseMapping();
    }
}
