/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.build.gradle.internal;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.Description;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Header;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Identifier;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Info;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.component.ComponentCreationConfig;
import com.android.build.gradle.internal.ide.dependencies.ArtifactUtils;
import com.android.build.gradle.internal.ide.dependencies.BuildIdentifierMethods;
import com.android.build.gradle.internal.ide.dependencies.MavenCoordinatesCacheBuildService;
import com.android.build.gradle.internal.ide.dependencies.ResolvedArtifact;
import com.android.build.gradle.internal.ide.dependencies.ResolvedArtifact.DependencyType;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Set;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.tasks.diagnostics.internal.ProjectDetails;
import org.gradle.api.tasks.diagnostics.internal.TextReportRenderer;
import org.gradle.internal.graph.GraphRenderer;

/** android version of the TextReportRenderer that outputs Android Library dependencies. */
public class AndroidDependenciesRenderer extends TextReportRenderer {

    private final MavenCoordinatesCacheBuildService mavenCoordinatesCacheBuildService;

    private boolean hasConfigs;
    private boolean hasCyclicDependencies;
    private GraphRenderer renderer;

    public AndroidDependenciesRenderer(
            MavenCoordinatesCacheBuildService mavenCoordinatesCacheBuildService) {
        this.mavenCoordinatesCacheBuildService = mavenCoordinatesCacheBuildService;
    }

    @Override
    public void startProject(ProjectDetails project) {
        super.startProject(project);
        hasConfigs = false;
        hasCyclicDependencies = false;
    }

    @Override
    public void completeProject(ProjectDetails project) {
        if (!hasConfigs) {
            getTextOutput().withStyle(Info).println("No dependencies");
        }
        super.completeProject(project);
    }

    public void startComponent(@NonNull ComponentCreationConfig component) {
        if (hasConfigs) {
            getTextOutput().println();
        }
        hasConfigs = true;
        renderer = new GraphRenderer(getTextOutput());
        renderer.visit(
                styledTextOutput -> getTextOutput().withStyle(Header).text(component.getName()),
                true);
    }

    public void render(@NonNull ComponentCreationConfig component) {
        Set<ResolvedArtifact> compileArtifacts =
                ArtifactUtils.getArtifactsForComponent(
                        component, AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH);

        getTextOutput()
                .withStyle(Identifier)
                .text(component.getVariantDependencies().getCompileClasspath().getName());
        getTextOutput().withStyle(Description).text(" - Dependencies for compilation");
        getTextOutput().println();
        renderer.startChildren();
        render(ImmutableList.copyOf(compileArtifacts));
        renderer.completeChildren();

        Set<ResolvedArtifact> runtimeArtifacts =
                ArtifactUtils.getArtifactsForComponent(
                        component, AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH);

        getTextOutput().println();
        getTextOutput()
                .withStyle(Identifier)
                .text(component.getVariantDependencies().getRuntimeClasspath().getName());
        getTextOutput().withStyle(Description).text(" - Dependencies for runtime/packaging");
        getTextOutput().println();
        renderer.startChildren();
        render(ImmutableList.copyOf(runtimeArtifacts));
        renderer.completeChildren();
    }

    @Override
    public void complete() {
        if (hasCyclicDependencies) {
            getTextOutput()
                    .withStyle(Info)
                    .println("\n(*) - dependencies omitted (listed previously)");
        }

        super.complete();
    }

    private void render(@NonNull List<ResolvedArtifact> artifacts) {
        for (int i = 0, count = artifacts.size(); i < count; i++) {
            ResolvedArtifact artifact = artifacts.get(i);

            renderer.visit(
                    styledTextOutput -> {
                        ComponentIdentifier id = artifact.getComponentIdentifier();

                        String text;

                        if (id instanceof ProjectComponentIdentifier) {
                            String projectId =
                                    BuildIdentifierMethods.getIdString(
                                            (ProjectComponentIdentifier) id);
                            if (artifact.isWrappedModule()) {
                                if (artifact.getArtifactFile() == null) {
                                    text = String.format("%s", projectId);
                                } else {
                                    String file = artifact.getArtifactFile().getAbsolutePath();
                                    text = String.format("%s (file: %s)", projectId, file);
                                }
                            } else {
                                if (artifact.getDependencyType() == DependencyType.ANDROID) {
                                    String variant = artifact.getVariantName();
                                    text = String.format("%s (variant: %s)", projectId, variant);
                                } else {
                                    text = projectId;
                                }
                            }

                        } else if (id instanceof ModuleComponentIdentifier) {
                            text = artifact.computeModelAddress(mavenCoordinatesCacheBuildService);

                        } else {
                            // local files?
                            if (artifact.getArtifactFile() == null) {
                                text = id.getDisplayName();
                            } else {
                                text = artifact.getArtifactFile().getAbsolutePath();
                            }
                        }

                        getTextOutput().text(text);
                    },
                    i == count - 1);
        }
    }
}
