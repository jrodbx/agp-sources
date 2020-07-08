/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.ide.dependencies.MavenCoordinatesUtils;
import com.android.build.gradle.internal.ide.level2.GraphItemImpl;
import com.android.build.gradle.internal.ide.level2.JavaLibraryImpl;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.GraphItem;
import com.android.builder.model.level2.Library;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.gradle.api.artifacts.Configuration;

/**
 * Implementation of {@link DependencyGraphs} over a Gradle
 * Configuration object. This is used to lazily query the list of files from the config object.
 *
 * This is only used when registering extra Java Artifacts.
 */
public class ConfigurationDependencyGraphs implements DependencyGraphs {

    @NonNull
    private final Configuration configuration;

    @NonNull
    private List<GraphItem> graphItems;
    private List<Library> libraries;


    public ConfigurationDependencyGraphs(@NonNull Configuration configuration) {

        this.configuration = configuration;
    }

    @NonNull
    public List<Library> getLibraries() {
        init();
        return libraries;
    }

    @NonNull
    @Override
    public List<GraphItem> getCompileDependencies() {
        init();
        return graphItems;
    }

    @NonNull
    @Override
    public List<GraphItem> getPackageDependencies() {
        init();
        return graphItems;
    }

    @NonNull
    @Override
    public List<String> getProvidedLibraries() {
        return Collections.emptyList();
    }

    @NonNull
    @Override
    public List<String> getSkippedLibraries() {
        return Collections.emptyList();
    }

    private void init() {
        //noinspection ConstantConditions,VariableNotUsedInsideIf
        if (graphItems != null) {
            return;
        }

        Set<File> files = configuration.getFiles();
        if (files.isEmpty()) {
            graphItems = Collections.emptyList();
            libraries = Collections.emptyList();
            return;
        }

        graphItems = Lists.newArrayListWithCapacity(files.size());
        libraries = Lists.newArrayListWithCapacity(files.size());

        for (File file : files) {
            Library javaLib =
                    new JavaLibraryImpl(
                            MavenCoordinatesUtils.getMavenCoordForLocalFile(file)
                                    .toString()
                                    .intern(),
                            file);
            libraries.add(javaLib);
            graphItems.add(new GraphItemImpl(javaLib.getArtifactAddress(), ImmutableList.of()));
        }
    }
}
