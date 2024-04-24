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

package com.android.build.gradle.internal.ide.dependencies;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.ide.DependencyFailureHandler;
import com.android.builder.errors.IssueReporter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;

/** For creating dependency graph based on {@link ResolvedArtifactResult}. */
class ArtifactDependencyGraph implements DependencyGraphBuilder {

    private final DependencyFailureHandler dependencyFailureHandler =
            new DependencyFailureHandler();

    @Override
    public void createDependencies(
            @NonNull DependencyModelBuilder<?> modelBuilder,
            @NonNull ArtifactCollectionsInputs artifactCollectionsInputs,
            boolean withFullDependency,
            @NonNull IssueReporter issueReporter) {
        try {
            // get the compile artifact first.
            Set<ResolvedArtifact> compileArtifacts =
                    artifactCollectionsInputs.getAllArtifacts(
                            COMPILE_CLASSPATH, dependencyFailureHandler);

            // runtimeLintJar and compileLintJar are lists of the dependencies' lint jars.
            // We'll match the component identifier of each artifact to find the lint.jar
            // that is coming via AARs.
            Set<ResolvedArtifactResult> runtimeLintJars =
                    artifactCollectionsInputs.getRuntimeLintJars().getArtifacts();
            Set<ResolvedArtifactResult> compileLintJars =
                    artifactCollectionsInputs.getCompileLintJars().getArtifacts();

            Map<ComponentIdentifier, File> mutableLintJarMap = new HashMap<>();
            for (ResolvedArtifactResult artifact : compileLintJars) {
                mutableLintJarMap.put(
                        artifact.getId().getComponentIdentifier(), artifact.getFile());
            }
            for (ResolvedArtifactResult artifact : runtimeLintJars) {
                mutableLintJarMap.put(
                        artifact.getId().getComponentIdentifier(), artifact.getFile());
            }
            Map<ComponentIdentifier, File> lintJarMap = ImmutableMap.copyOf(mutableLintJarMap);

            if (withFullDependency && modelBuilder.getNeedFullRuntimeClasspath()) {
                // in this mode, we build the full list of runtime artifact in the model
                Set<ResolvedArtifact> runtimeArtifacts =
                        artifactCollectionsInputs.getAllArtifacts(
                                RUNTIME_CLASSPATH, dependencyFailureHandler);

                Set<ComponentIdentifier> runtimeIds =
                        runtimeArtifacts.stream()
                                .map(ResolvedArtifact::getComponentIdentifier)
                                .collect(Collectors.toSet());

                for (ResolvedArtifact artifact : compileArtifacts) {
                    modelBuilder.addArtifact(
                            artifact,
                            !runtimeIds.contains(artifact.getComponentIdentifier()),
                            lintJarMap,
                            DependencyModelBuilder.ClasspathType.COMPILE);
                }

                for (ResolvedArtifact artifact : runtimeArtifacts) {
                    modelBuilder.addArtifact(
                            artifact,
                            false,
                            lintJarMap,
                            DependencyModelBuilder.ClasspathType.RUNTIME);
                }
            } else {
                // In this simpler model, we only build the compile classpath, but we want the
                // provided information for libraries

                // so we get the runtime artifact IDs in a faster way than getting the full list
                // of ResolvedArtifact
                Level1RuntimeArtifactCollections level1RuntimeArtifactCollections =
                        artifactCollectionsInputs.getLevel1RuntimeArtifactCollections();
                ImmutableSet<ComponentIdentifier> runtimeIds =
                        getRuntimeComponentIdentifiers(
                                level1RuntimeArtifactCollections.getRuntimeArtifacts());

                for (ResolvedArtifact artifact : compileArtifacts) {
                    modelBuilder.addArtifact(
                            artifact,
                            !runtimeIds.contains(artifact.getComponentIdentifier()),
                            lintJarMap,
                            DependencyModelBuilder.ClasspathType.COMPILE);
                }

                if (modelBuilder.getNeedRuntimeOnlyClasspath()) {
                    modelBuilder.setRuntimeOnlyClasspath(
                            getRuntimeOnlyClasspath(
                                    level1RuntimeArtifactCollections,
                                    compileArtifacts,
                                    runtimeIds));
                }
            }
        } finally {
            dependencyFailureHandler.registerIssues(issueReporter);
        }
    }

    private static ImmutableSet<ComponentIdentifier> getRuntimeComponentIdentifiers(
            @NonNull ArtifactCollection runtimeArtifactCollection) {
        // get the runtime artifact. We only care about the ComponentIdentifier so we don't
        // need to call getAllArtifacts() which computes a lot more many things.
        // Instead just get all the jars to get all the dependencies.
        // ImmutableSet also preserves order.
        ImmutableSet.Builder<ComponentIdentifier> runtimeIdentifiersBuilder =
                ImmutableSet.builder();
        for (ResolvedArtifactResult result : runtimeArtifactCollection.getArtifacts()) {
            runtimeIdentifiersBuilder.add(result.getId().getComponentIdentifier());
        }
        return runtimeIdentifiersBuilder.build();
    }

    private static ImmutableList<File> getRuntimeOnlyClasspath(
            @NonNull Level1RuntimeArtifactCollections runtimeArtifactCollections,
            @NonNull Set<ResolvedArtifact> artifacts,
            @NonNull ImmutableSet<ComponentIdentifier> runtimeIdentifiers) {
        // get runtime-only jars by filtering out compile dependencies from runtime artifacts.
        Set<ComponentIdentifier> compileIdentifiers =
                artifacts.stream()
                        .map(ResolvedArtifact::getComponentIdentifier)
                        .collect(Collectors.toSet());

        // only include external dependencies as projects are not needed IDE-side
        ImmutableMultimap<ComponentIdentifier, ResolvedArtifactResult> externalRuntime =
                asMultiMap(runtimeArtifactCollections.getRuntimeExternalJars());

        ImmutableList.Builder<File> runtimeOnlyClasspathBuilder = ImmutableList.builder();
        for (ComponentIdentifier runtimeIdentifier : runtimeIdentifiers) {
            if (compileIdentifiers.contains(runtimeIdentifier)) {
                continue;
            }
            for (ResolvedArtifactResult resolvedArtifactResult :
                    externalRuntime.get(runtimeIdentifier)) {
                runtimeOnlyClasspathBuilder.add(resolvedArtifactResult.getFile());
            }
        }
        return runtimeOnlyClasspathBuilder.build();
    }

    /**
     * This is a multi map to handle when there are multiple jars with the same component id.
     *
     * <p>FIXME this does not properly handle test fixtures because ComponentIdentifier is not
     * unique for lib+fixtures
     *
     * <p>e.g. see `AppWithClassifierDepTest`
     */
    private static ImmutableMultimap<ComponentIdentifier, ResolvedArtifactResult> asMultiMap(
            @NonNull ArtifactCollection collection) {
        ImmutableMultimap.Builder<ComponentIdentifier, ResolvedArtifactResult> builder =
                ImmutableMultimap.builder();

        for (ResolvedArtifactResult artifact : collection.getArtifacts()) {
            builder.put(artifact.getId().getComponentIdentifier(), artifact);
        }

        return builder.build();
    }

    ArtifactDependencyGraph() {
    }
}
