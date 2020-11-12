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
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * Factory class for {@link com.android.builder.desugaring.DesugaringGraph}. This is the only way to
 * create {@link DesugaringGraph} as we would like to cache them between runs.
 */
public final class DesugaringGraphs {

    @NonNull
    static Cache<String, DesugaringGraph> graphs = CacheBuilder.newBuilder().maximumSize(4).build();

    private DesugaringGraphs() {}

    /**
     * Get a {@link com.android.builder.desugaring.DesugaringGraph} associated with this key. Key
     * should be unique for the project and variant e.g. :app:debug. If the graph does not exist, it
     * is created from the supplied data.
     */
    @NonNull
    public static DesugaringGraph forVariant(
            @NonNull String projectVariant,
            @NonNull Supplier<Collection<DesugaringData>> ifFull,
            @NonNull Supplier<Collection<DesugaringData>> ifIncremental) {
        DesugaringGraph graph = graphs.getIfPresent(projectVariant);
        if (graph != null) {
            graph.update(ifIncremental.get());
        } else {
            graph = new DesugaringGraph(ifFull.get());
            graphs.put(projectVariant, graph);
        }
        return graph;
    }

    /**
     * Create a {@link com.android.builder.desugaring.DesugaringGraph} associated with this key. Key
     * should be unique for the project and variant e.g. :app:debug. The graph is created fully from
     * the supplied data.
     */
    @NonNull
    public static DesugaringGraph forVariant(
            @NonNull String projectVariant,
            @NonNull Collection<DesugaringData> fullDesugaringData) {
        DesugaringGraph graph = new DesugaringGraph(fullDesugaringData);
        graphs.put(projectVariant, graph);
        return graph;
    }

    /**
     * Update a {@link com.android.builder.desugaring.DesugaringGraph} associated with this key if a
     * cached version exists. Key should be unique for the project and variant e.g. :app:debug. Does
     * nothing if no cached version exists for the variant.
     */
    @NonNull
    public static DesugaringGraph updateVariant(
            @NonNull String projectVariant,
            @NonNull Supplier<Collection<DesugaringData>> incrementalDesugaringData) {
        DesugaringGraph graph = graphs.getIfPresent(projectVariant);
        if (graph != null) {
            graph.update(incrementalDesugaringData.get());
        }
        return graph;
    }

    /** Removes the desugaring graph for the specified project variant. */
    public static void invalidate(@NonNull String projectVariant) {
        graphs.invalidate(projectVariant);
    }
}
