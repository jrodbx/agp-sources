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
package com.android.ide.common.repository;

import static java.io.File.separator;
import static java.io.File.separatorChar;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.gradle.Component;
import com.android.ide.common.gradle.Module;
import com.android.ide.common.gradle.Version;
import com.android.io.CancellableFileIo;
import com.android.repository.io.FileOpUtils;
import com.google.common.collect.ImmutableSortedSet;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Utilities for dealing with the standard m2repository directory layout.
 */
@SuppressWarnings("WeakerAccess")
public class MavenRepositories {
    private static final String MAVEN_METADATA_FILE_NAME = "maven-metadata.xml";

    private MavenRepositories() {}

    /**
     * Finds the best matching {@link Component}.
     *
     * @param groupId the artifact group id
     * @param artifactId the artifact id
     * @param repository the path to the m2repository directory
     * @param filter an optional version filter that has to be satisfied by the matched coordinate
     * @param allowPreview whether preview versions are allowed to match
     * @return the best (highest version) matching coordinate, or null if none were found
     */
    @Nullable
    public static Component getHighestInstalledVersion(
            @NonNull String groupId,
            @NonNull String artifactId,
            @NonNull Path repository,
            @Nullable Predicate<Version> filter,
            boolean allowPreview) {
        Path versionDir = getArtifactIdDirectory(repository, groupId, artifactId);
        Path[] versions = FileOpUtils.listFiles(versionDir);
        Component highest = null;
        for (Path dir : versions) {
            if (!CancellableFileIo.isDirectory(dir)) {
                continue;
            }
            Version version = Version.Companion.parse(dir.getFileName().toString());
            Component component = new Component(groupId, artifactId, version);
            if (allowPreview || !isPreview(component)) {
                if (filter == null || filter.test(version)) {
                    if (highest == null || version.compareTo(highest.getVersion()) > 0) {
                        highest = component;
                    }
                }
            }
        }

        return highest;
    }

    /**
     * Given a directory containing version numbers returns the highest version number matching the
     * given filter
     *
     * @param versionDir the directory containing version numbers
     * @param filter an optional filter which the matched version must satisfy
     * @param allowPreview whether preview versions are allowed to match
     * @return the best (highest version), or null if none were found
     */
    @Nullable
    public static Version getHighestVersion(
            @NonNull Path versionDir,
            @Nullable Predicate<Version> filter,
            boolean allowPreview) {
        Path[] versionDirs = FileOpUtils.listFiles(versionDir);
        Version maxVersion = null;
        for (Path dir : versionDirs) {
            if (!CancellableFileIo.isDirectory(dir)) {
                continue;
            }
            String name = dir.getFileName().toString();
            if (name.isEmpty() || !Character.isDigit(name.charAt(0))) {
                continue;
            }
            Version version = Version.Companion.parse(name);
            if ((allowPreview || !version.isPreview())
                    && (filter == null || filter.test(version))
                    && (maxVersion == null || version.compareTo(maxVersion) > 0)) {
                maxVersion = version;
            }
        }

        return maxVersion;
    }

    /**
     * Decides whether a given {@link Component} is considered preview.
     *
     * <p>This is mostly compatible with {@link Version#isPreview()}, but there is one edge
     * case that we need to handle, related to Play Services. (See https://b.android.com/75292)
     */
    public static boolean isPreview(Component component) {
        //noinspection SimplifiableIfStatement
        if (component.getVersion().isPreview()) {
            return true;
        }

        return "com.google.android.gms".equals(component.getGroup())
                && "play-services".equals(component.getName())
                && component.getVersion().equals(Version.Companion.parse("5.2.08"));
    }

    /**
     * Returns true if the group is an AndroidX group.
     */
    public static boolean isAndroidX(@NonNull String groupId) {
        return groupId.startsWith("androidx.");
    }

    public static Path getArtifactIdDirectory(
            @NonNull Path repository, @NonNull String groupId, @NonNull String artifactId) {
        return getModuleDirectory(repository, new Module(groupId, artifactId));
    }

    public static Path getModuleDirectory(@NonNull Path repository, @NonNull Module module) {
        return repository.resolve(
                module.getGroup().replace('.', separatorChar) + separator + module.getName());
    }

    public static Path getArtifactDirectory(
            @NonNull Path repository, @NonNull Component component) {
        Path artifactIdDirectory =
                getArtifactIdDirectory(repository, component.getGroup(), component.getName());

        return artifactIdDirectory.resolve(component.getVersion().toString());
    }

    public static Set<Version> getAllVersions(@NonNull Path repository, @NonNull Module module) {
        Path moduleDirectory = getModuleDirectory(repository, module);
        if (!CancellableFileIo.isDirectory(moduleDirectory)) {
            return Collections.emptySet();
        }
        Path[] versionDirs = FileOpUtils.listFiles(moduleDirectory);
        ImmutableSortedSet.Builder<Version> builder = ImmutableSortedSet.naturalOrder();
        for (Path dir : versionDirs) {
            if (!CancellableFileIo.isDirectory(dir)) {
                continue;
            }
            String name = dir.getFileName().toString();
            if (name.isEmpty()) {
                continue;
            }
            builder.add(Version.Companion.parse(name));
        }
        return builder.build();
    }
}
