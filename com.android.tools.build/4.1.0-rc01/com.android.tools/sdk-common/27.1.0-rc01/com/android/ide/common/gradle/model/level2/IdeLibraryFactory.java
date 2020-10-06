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

import static com.android.builder.model.level2.Library.LIBRARY_ANDROID;
import static com.android.builder.model.level2.Library.LIBRARY_JAVA;
import static com.android.builder.model.level2.Library.LIBRARY_MODULE;
import static com.android.ide.common.gradle.model.IdeLibraries.computeAddress;
import static com.android.ide.common.gradle.model.IdeLibraries.isLocalAarModule;
import static com.android.utils.FileUtils.join;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.level2.Library;
import com.android.utils.ImmutableCollectors;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Creates instance of {@link Library}. */
public class IdeLibraryFactory {
    /**
     * @param library Instance of level 2 library returned by android plugin.
     * @return Deep copy of {@link Library} based on library type.
     */
    @NonNull
    public Library create(@NonNull Library library) {
        if (library.getType() == LIBRARY_ANDROID) {
            File folder = library.getFolder();
            return new IdeAndroidLibrary(
                    library.getArtifactAddress(),
                    library.getFolder(),
                    getFullPath(folder, library.getManifest()),
                    getFullPath(folder, library.getJarFile()),
                    getFullPath(
                            folder,
                            checkNotNull(
                                    defaultValueIfNotPresent(
                                            library::getCompileJarFile, library.getJarFile()))),
                    getFullPath(folder, library.getResFolder()),
                    library.getResStaticLibrary(),
                    getFullPath(folder, library.getAssetsFolder()),
                    getLocalJars(library, folder),
                    getFullPath(folder, library.getJniFolder()),
                    getFullPath(folder, library.getAidlFolder()),
                    getFullPath(folder, library.getRenderscriptFolder()),
                    getFullPath(folder, library.getProguardRules()),
                    getFullPath(folder, library.getLintJar()),
                    getFullPath(folder, library.getExternalAnnotations()),
                    getFullPath(folder, library.getPublicResources()),
                    library.getArtifact(),
                    library.getSymbolFile());
        }
        if (library.getType() == LIBRARY_JAVA) {
            return new IdeJavaLibrary(library.getArtifactAddress(), library.getArtifact());
        }
        if (library.getType() == LIBRARY_MODULE) {
            return new IdeModuleLibrary(library, library.getArtifactAddress());
        }
        throw new UnsupportedOperationException("Unknown library type " + library.getType());
    }

    @NonNull
    private static ImmutableList<String> getLocalJars(
            @NonNull Library library, @NonNull File libraryFolderPath) {
        return library.getLocalJars()
                .stream()
                .map(jar -> getFullPath(libraryFolderPath, jar))
                .collect(ImmutableCollectors.toImmutableList());
    }

    /**
     * @param androidLibrary Instance of {@link AndroidLibrary} returned by android plugin.
     * @param moduleBuildDirs Instance of {@link BuildFolderPaths} that contains map from project
     *     path to build directory for all modules.
     * @return Instance of {@link Library} based on dependency type.
     */
    @NonNull
    Library create(
            @NonNull AndroidLibrary androidLibrary, @NonNull BuildFolderPaths moduleBuildDirs) {
        // If the dependency is a sub-module that wraps local aar, it should be considered as external dependency, i.e. type LIBRARY_ANDROID.
        // In AndroidLibrary, getProject() of such dependency returns non-null project name, but they should be converted to IdeLevel2AndroidLibrary.
        // Identify such case with the location of aar bundle.
        // If the aar bundle is inside of build directory of sub-module, then it's regular library module dependency, otherwise it's a wrapped aar module.
        if (androidLibrary.getProject() != null
                && !isLocalAarModule(androidLibrary, moduleBuildDirs)) {
            return new IdeModuleLibrary(androidLibrary, computeAddress(androidLibrary));
        } else {
            return new IdeAndroidLibrary(
                    computeAddress(androidLibrary),
                    androidLibrary.getFolder(),
                    androidLibrary.getManifest().getPath(),
                    androidLibrary.getJarFile().getPath(),
                    checkNotNull(
                                    defaultValueIfNotPresent(
                                            androidLibrary::getCompileJarFile,
                                            androidLibrary.getJarFile()))
                            .getPath(),
                    androidLibrary.getResFolder().getPath(),
                    defaultValueIfNotPresent(androidLibrary::getResStaticLibrary, null),
                    androidLibrary.getAssetsFolder().getPath(),
                    androidLibrary
                            .getLocalJars()
                            .stream()
                            .map(File::getPath)
                            .collect(Collectors.toList()),
                    androidLibrary.getJniFolder().getPath(),
                    androidLibrary.getAidlFolder().getPath(),
                    androidLibrary.getRenderscriptFolder().getPath(),
                    androidLibrary.getProguardRules().getPath(),
                    androidLibrary.getLintJar().getPath(),
                    androidLibrary.getExternalAnnotations().getPath(),
                    androidLibrary.getPublicResources().getPath(),
                    androidLibrary.getBundle(),
                    getSymbolFilePath(androidLibrary));
        }
    }

    @NonNull
    private static String getFullPath(@NonNull File libraryFolderPath, @NonNull String fileName) {
        return join(libraryFolderPath, fileName).getPath();
    }

    @NonNull
    private static String getSymbolFilePath(@NonNull AndroidLibrary androidLibrary) {
        try {
            return androidLibrary.getSymbolFile().getPath();
        } catch (UnsupportedOperationException e) {
            return new File(androidLibrary.getFolder(), SdkConstants.FN_RESOURCE_TEXT).getPath();
        }
    }

    @Nullable
    protected static <T> T defaultValueIfNotPresent(
            @NonNull Supplier<T> propertyInvoker, @Nullable T defaultValue) {
        try {
            return propertyInvoker.get();
        } catch (UnsupportedOperationException ignored) {
            return defaultValue;
        }
    }

    /**
     * @param javaLibrary Instance of {@link JavaLibrary} returned by android plugin.
     * @return Instance of {@link Library} based on dependency type.
     */
    @NonNull
    Library create(@NonNull JavaLibrary javaLibrary) {
        String project = getProject(javaLibrary);
        if (project != null) {
            // Java modules don't have variant.
            return new IdeModuleLibrary(javaLibrary, computeAddress(javaLibrary));
        } else {
            return new IdeJavaLibrary(computeAddress(javaLibrary), javaLibrary.getJarFile());
        }
    }

    @Nullable
    private static String getProject(@NonNull JavaLibrary javaLibrary) {
        try {
            return javaLibrary.getProject();
        } catch (UnsupportedOperationException e) {
            return null;
        }
    }

    /**
     * @param projectPath Name of module dependencies.
     * @return An instance of {@link Library} of type LIBRARY_MODULE.
     */
    @NonNull
    static Library create(
            @NonNull String projectPath,
            @NonNull String artifactAddress,
            @Nullable String buildId) {
        return new IdeModuleLibrary(projectPath, artifactAddress, buildId);
    }
}
