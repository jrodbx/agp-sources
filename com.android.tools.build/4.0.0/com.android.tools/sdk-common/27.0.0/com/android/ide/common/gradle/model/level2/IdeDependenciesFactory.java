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

import static com.android.builder.model.level2.Library.*;
import static com.android.ide.common.gradle.model.IdeLibraries.computeAddress;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.BaseArtifact;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.GlobalLibraryMap;
import com.android.builder.model.level2.GraphItem;
import com.android.builder.model.level2.Library;
import com.android.ide.common.gradle.model.ModelCache;
import com.android.ide.common.repository.GradleVersion;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/** Creates {@link IdeDependencies} from {@link BaseArtifact}. */
public class IdeDependenciesFactory {
    // Map from unique artifact address to level2 library instance. The library instances are supposed to be shared by all artifacts.
    // When creating IdeLevel2Dependencies, check if current library is available in this map,
    // if it's available, don't create new one, simple add reference to it.
    // If it's not available, create new instance and save to this map, so it can be reused the next time when the same library is added.
    @NonNull private final Map<String, Library> myLibrariesById = new HashMap<>();

    @NonNull private final IdeLibraryFactory myLibraryFactory = new IdeLibraryFactory();
    @NonNull private final BuildFolderPaths myBuildFolderPaths = new BuildFolderPaths();

    /**
     * Stores the "build" folder path for the given module.
     *
     * @param buildId build identifier of project.
     * @param moduleGradlePath module's gradle path.
     * @param buildFolder path to the module's build directory.
     */
    public void findAndAddBuildFolderPath(
            @NonNull String buildId, @NonNull String moduleGradlePath, @NonNull File buildFolder) {
        myBuildFolderPaths.addBuildFolderMapping(buildId, moduleGradlePath, buildFolder);
    }

    /**
     * Set the build identifier of root project.
     *
     * @param rootBuildId build identifier of root project.
     */
    public void setRootBuildId(@NonNull String rootBuildId) {
        myBuildFolderPaths.setRootBuildId(rootBuildId);
    }

    /**
     * Create {@link IdeDependencies} from {@link BaseArtifact}.
     *
     * @param artifact Instance of {@link BaseArtifact} returned from Android plugin.
     * @param modelVersion Version of Android plugin.
     * @return New instance of {@link IdeDependencies}.
     */
    public IdeDependencies create(
            @NonNull BaseArtifact artifact, @Nullable GradleVersion modelVersion) {
        // Create a fresh model cache for this class, since current instance is based on dependencyGraphs or dependencies, which
        // ha1ve been copied in the constructor of IdeBaseArtifact.
        ModelCache modelCache = new ModelCache();
        if (modelVersion != null
                && modelVersion.getMajor() >= 3
                && !artifact.getDependencyGraphs().getCompileDependencies().isEmpty()) {
            return createFromDependencyGraphs(artifact.getDependencyGraphs());
        }
        return createFromDependencies(artifact.getDependencies());
    }

    /** Call this method on level 4 DependencyGraphs. */
    @VisibleForTesting
    @NonNull
    public IdeDependencies createFromDependencyGraphs(@NonNull DependencyGraphs graphs) {
        return createInstance(
                graphs.getCompileDependencies()
                        .stream()
                        .map(GraphItem::getArtifactAddress)
                        .collect(Collectors.toList()),
                Collections.emptyList());
    }

    /** Call this method on level 1 Dependencies model. */
    @NonNull
    private IdeDependencies createFromDependencies(@NonNull Dependencies dependencies) {
        Set<String> visited = new LinkedHashSet<>();
        populateAndroidLibraries(dependencies.getLibraries(), visited);
        populateJavaLibraries(dependencies.getJavaLibraries(), visited);
        populateModuleDependencies(dependencies, visited);
        Collection<File> jars;
        try {
            jars = dependencies.getRuntimeOnlyClasses();
        } catch (UnsupportedOperationException e) {
            // Gradle older than 3.4.
            jars = Collections.emptyList();
        }
        return createInstance(visited, jars);
    }

    private void populateModuleDependencies(
            @NonNull Dependencies dependencies, @NonNull Set<String> visited) {
        try {
            for (Dependencies.ProjectIdentifier identifier : dependencies.getJavaModules()) {
                createModuleLibrary(
                        visited,
                        identifier.getProjectPath(),
                        computeAddress(identifier),
                        identifier.getBuildId());
            }
        } catch (UnsupportedOperationException ignored) {
            // Dependencies::getJavaModules is available for AGP 3.1+. Use Dependencies::getProjects for the old plugins.
            //noinspection deprecation
            for (String projectPath : dependencies.getProjects()) {
                createModuleLibrary(visited, projectPath, projectPath, null);
            }
        }
    }

    private void createModuleLibrary(
            @NonNull Set<String> visited,
            @NonNull String projectPath,
            @NonNull String artifactAddress,
            @Nullable String buildId) {
        if (!visited.contains(artifactAddress)) {
            visited.add(artifactAddress);
            myLibrariesById.computeIfAbsent(
                    artifactAddress,
                    id -> IdeLibraryFactory.create(projectPath, artifactAddress, buildId));
        }
    }

    private void populateAndroidLibraries(
            @NonNull Collection<? extends AndroidLibrary> androidLibraries,
            @NonNull Set<String> visited) {
        for (AndroidLibrary androidLibrary : androidLibraries) {
            String address = computeAddress(androidLibrary);
            if (!visited.contains(address)) {
                visited.add(address);
                myLibrariesById.computeIfAbsent(
                        address, id -> myLibraryFactory.create(androidLibrary, myBuildFolderPaths));
                populateAndroidLibraries(androidLibrary.getLibraryDependencies(), visited);
                populateJavaLibraries(getJavaDependencies(androidLibrary), visited);
            }
        }
    }

    @NonNull
    private static Collection<? extends JavaLibrary> getJavaDependencies(
            AndroidLibrary androidLibrary) {
        try {
            return androidLibrary.getJavaDependencies();
        } catch (UnsupportedOperationException e) {
            return Collections.emptyList();
        }
    }

    private void populateJavaLibraries(
            @NonNull Collection<? extends JavaLibrary> javaLibraries,
            @NonNull Set<String> visited) {
        for (JavaLibrary javaLibrary : javaLibraries) {
            String address = computeAddress(javaLibrary);
            if (!visited.contains(address)) {
                visited.add(address);
                myLibrariesById.computeIfAbsent(address, k -> myLibraryFactory.create(javaLibrary));
                populateJavaLibraries(javaLibrary.getDependencies(), visited);
            }
        }
    }

    @NonNull
    private IdeDependencies createInstance(
            @NonNull Collection<String> artifactAddresses,
            @NonNull Collection<File> runtimeOnlyJars) {
        ImmutableList.Builder<Library> androidLibraries = ImmutableList.builder();
        ImmutableList.Builder<Library> javaLibraries = ImmutableList.builder();
        ImmutableList.Builder<Library> moduleDependencies = ImmutableList.builder();

        for (String address : artifactAddresses) {
            Library library = myLibrariesById.get(address);
            assert library != null;
            switch (library.getType()) {
                case LIBRARY_ANDROID:
                    androidLibraries.add(library);
                    break;
                case LIBRARY_JAVA:
                    javaLibraries.add(library);
                    break;
                case LIBRARY_MODULE:
                    moduleDependencies.add(library);
                    break;
                default:
                    throw new UnsupportedOperationException(
                            "Unknown library type " + library.getType());
            }
        }
        return new IdeDependenciesImpl(
                androidLibraries.build(),
                javaLibraries.build(),
                moduleDependencies.build(),
                ImmutableList.copyOf(runtimeOnlyJars));
    }

    /**
     * Populate global library map from the list of {@link GlobalLibraryMap} by merging and making
     * deep copies of each entry.
     *
     * @param globalLibraryMaps List of GlobalLibraryMap model returned from Android Plugin.
     */
    public void setUpGlobalLibraryMap(@NonNull List<GlobalLibraryMap> globalLibraryMaps) {
        for (GlobalLibraryMap globalLibraryMap : globalLibraryMaps) {
            for (Library library : globalLibraryMap.getLibraries().values()) {
                myLibrariesById.computeIfAbsent(
                        library.getArtifactAddress(), k -> myLibraryFactory.create(library));
            }
        }
    }
}
