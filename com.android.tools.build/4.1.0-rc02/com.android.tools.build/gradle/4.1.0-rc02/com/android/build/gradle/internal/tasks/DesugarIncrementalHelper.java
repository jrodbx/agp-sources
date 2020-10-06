/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks;

import static com.android.builder.desugaring.DesugaringClassAnalyzer.analyze;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.builder.desugaring.DesugaringClassAnalyzer;
import com.android.builder.desugaring.DesugaringData;
import com.android.builder.desugaring.DesugaringGraph;
import com.android.builder.desugaring.DesugaringGraphs;
import com.android.ide.common.internal.WaitableExecutor;

import com.google.common.base.Stopwatch;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * This helper analyzes the task inputs, updates the {@link DesugaringGraph} it owns, and its
 * main goal is to provide paths that should also be considered out of date, in addition to the
 * changed files. See {@link #getAdditionalPaths()} for details.
 */
public class DesugarIncrementalHelper {

    @NonNull
    private static final LoggerWrapper logger =
            LoggerWrapper.getLogger(DesugarIncrementalHelper.class);

    @NonNull private final String projectVariant;
    @NonNull private final Iterable<Path> allInputs;
    @NonNull private final WaitableExecutor executor;

    @NonNull private final Supplier<Set<Path>> changedPaths;

    @NonNull private final Supplier<DesugaringGraph> desugaringGraph;
    private final boolean isIncremental;

    public DesugarIncrementalHelper(
            @NonNull String projectVariant,
            boolean isIncremental,
            @NonNull Iterable<File> allInputs,
            @NonNull Supplier<Set<Path>> changedPaths,
            @NonNull WaitableExecutor executor) {
        this.projectVariant = projectVariant;
        this.isIncremental = isIncremental;
        this.allInputs = Iterables.transform(allInputs, File::toPath);
        this.executor = executor;
        this.changedPaths = Suppliers.memoize(changedPaths::get);
        DesugaringGraph graph;
        if (!isIncremental) {
            DesugaringGraphs.invalidate(projectVariant);
            graph = null;
        } else {
            graph =
                    DesugaringGraphs.updateVariant(
                            projectVariant, () -> getIncrementalData(changedPaths, executor));
        }
        desugaringGraph =
                graph != null ? () -> graph : Suppliers.memoize(this::makeDesugaringGraph);
    }

    /**
     * Get the list of paths that should be re-desugared, and update the dependency graph.
     *
     * <p>For full builds, graph will be invalidated. No additional paths to process are returned,
     * as all inputs are considered out-of-date, and will be re-processed.
     *
     * <p>In incremental builds, graph will be initialized (if not already), or updated
     * incrementally. Once it has been populated, set of changed files is analyzed, and all
     * impacted, non-changed, paths will be returned as a result.
     */
    @NonNull
    public Set<Path> getAdditionalPaths() {
        if (!isIncremental) {
            return ImmutableSet.of();
        }

        Stopwatch stopwatch = Stopwatch.createStarted();
        logger.verbose("Desugaring dependencies incrementally.");

        Set<Path> additionalPaths = new HashSet<>();
        for (Path changed : changedPaths.get()) {
            for (Path path : desugaringGraph.get().getDependentPaths(changed)) {
                if (!changedPaths.get().contains(path)) {
                    additionalPaths.add(path);
                }
            }
        }

        logger.verbose(
                "Time to calculate desugaring dependencies: %d",
                stopwatch.elapsed(TimeUnit.MILLISECONDS));
        logger.verbose("Additional paths to desugar: %s", additionalPaths.toString());
        return additionalPaths;
    }

    @NonNull
    private DesugaringGraph makeDesugaringGraph() {
        if (!isIncremental) {
            // Rebuild totally the graph whatever the cache status
            return DesugaringGraphs.forVariant(
                    projectVariant, getInitalGraphData(allInputs, executor));
        }
        return DesugaringGraphs.forVariant(
                projectVariant,
                () -> getInitalGraphData(allInputs, executor),
                () -> getIncrementalData(changedPaths, executor));
    }

    @NonNull
    private static Collection<DesugaringData> getInitalGraphData(
            @NonNull Iterable<Path> allInputs, @NonNull WaitableExecutor executor) {
        Set<DesugaringData> data = Sets.newConcurrentHashSet();

        for (Path input : allInputs) {
            executor.execute(
                    () -> {
                        try {
                            if (Files.exists(input)) {
                                data.addAll(analyze(input));
                            }
                            return null;
                        } catch (Throwable t) {
                            logger.error(t, "error processing %s", input);
                            throw t;
                        }
                    });
        }

        try {
            executor.waitForTasksWithQuickFail(true);
        } catch (InterruptedException e) {
            throw new RuntimeException("Unable to get desugaring graph", e);
        }

        return data;
    }

    @NonNull
    private static Set<DesugaringData> getIncrementalData(
            @NonNull Supplier<Set<Path>> changedPaths, @NonNull WaitableExecutor executor) {
        Set<DesugaringData> data = Sets.newConcurrentHashSet();
        for (Path input : changedPaths.get()) {
            if (Files.notExists(input)) {
                data.add(DesugaringClassAnalyzer.forRemoved(input));
            } else {
                executor.execute(
                        () -> {
                            try {
                                data.addAll(analyze(input));
                                return null;
                            } catch (Throwable t) {
                                logger.error(t, "error processing %s", input);
                                throw t;
                            }
                        });
            }
        }

        try {
            executor.waitForTasksWithQuickFail(true);
        } catch (InterruptedException e) {
            throw new RuntimeException("Unable to get desugaring graph", e);
        }
        return data;
    }

    public Set<Path> getDependenciesPaths(Path path) {
        return desugaringGraph.get().getDependenciesPaths(path);
    }
}
