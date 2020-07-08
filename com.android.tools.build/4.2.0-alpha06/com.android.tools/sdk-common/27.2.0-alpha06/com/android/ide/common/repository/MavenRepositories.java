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

import static com.android.ide.common.repository.GradleCoordinate.COMPARE_PLUS_HIGHER;
import static java.io.File.separator;
import static java.io.File.separatorChar;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.io.FileOp;
import java.io.File;
import java.util.function.Predicate;

/**
 * Utilities for dealing with the standard m2repository directory layout.
 */
@SuppressWarnings("WeakerAccess")
public class MavenRepositories {
    private static final String MAVEN_METADATA_FILE_NAME = "maven-metadata.xml";

    private MavenRepositories() {}

    /**
     * Finds the best matching {@link GradleCoordinate}.
     *
     * @param groupId      the artifact group id
     * @param artifactId   the artifact id
     * @param repository   the path to the m2repository directory
     * @param filter       an optional version filter that has to be satisfied by the matched coordinate
     * @param allowPreview whether preview versions are allowed to match
     * @return the best (highest version) matching coordinate, or null if none were found
     */
    @Nullable
    public static GradleCoordinate getHighestInstalledVersion(
            @NonNull String groupId,
            @NonNull String artifactId,
            @NonNull File repository,
            @Nullable Predicate<GradleVersion> filter,
            boolean allowPreview,
            @NonNull FileOp fileOp) {
        File versionDir = getArtifactIdDirectory(repository, groupId, artifactId);
        File[] versions = fileOp.listFiles(versionDir);
        GradleCoordinate maxVersion = null;
        for (File dir : versions) {
            if (!fileOp.isDirectory(dir)) {
                continue;
            }
            GradleCoordinate gc = GradleCoordinate.parseCoordinateString(
                    groupId + ":" + artifactId + ":" + dir.getName());

            if (gc != null && (allowPreview || !isPreview(gc))
                    && (maxVersion == null || COMPARE_PLUS_HIGHER.compare(gc, maxVersion) > 0)
                    && (applyVersionPredicate(gc.getRevision(), filter))) {
                maxVersion = gc;
            }
        }

        return maxVersion;
    }

    private static boolean applyVersionPredicate(@NonNull String revision,
            @Nullable Predicate<GradleVersion> predicate) {
        if (predicate == null) {
            return true;
        }
        GradleVersion version = GradleVersion.tryParse(revision);
        return version != null && predicate.test(version);
    }

    /**
     * Finds the best matching {@link GradleVersion}. Like
     * {@link #getHighestInstalledVersion(String, String, File, Predicate, boolean, FileOp)} but
     * operates on {@link GradleVersion} instead of {@link GradleCoordinate}.
     *
     * @param groupId      the artifact group id
     * @param artifactId   the artifact id
     * @param repository   the path to the m2repository directory
     * @param filter       an optional filter which the matched version must satisfy
     * @param allowPreview whether preview versions are allowed to match
     * @param fileOp       file operator to use for file access
     * @return the best (highest version) matching version, or null if none were found
     */
    @Nullable
    public static GradleVersion getHighestInstalledVersionNumber(
            @NonNull String groupId,
            @NonNull String artifactId,
            @NonNull File repository,
            @Nullable Predicate<GradleVersion> filter,
            boolean allowPreview,
            @NonNull FileOp fileOp) {
        File versionDir = getArtifactIdDirectory(repository, groupId, artifactId);
        return getHighestVersion(versionDir, filter, allowPreview, fileOp);
    }

    /**
     * Given a directory containing version numbers returns the highest version number
     * matching the given filter
     *
     * @param versionDir   the directory containing version numbers
     * @param filter       an optional filter which the matched version must satisfy
     * @param allowPreview whether preview versions are allowed to match
     * @param fileOp       file operator to use for file access
     * @return the best (highest version), or null if none were found
     */
    @Nullable
    public static GradleVersion getHighestVersion(
            @NonNull File versionDir,
            @Nullable Predicate<GradleVersion> filter,
            boolean allowPreview, @NonNull FileOp fileOp) {
        File[] versionDirs = fileOp.listFiles(versionDir);
        GradleVersion maxVersion = null;
        for (File dir : versionDirs) {
            if (!fileOp.isDirectory(dir)) {
                continue;
            }
            String name = dir.getName();
            if (name.isEmpty() || !Character.isDigit(name.charAt(0))) {
                continue;
            }
            GradleVersion version = GradleVersion.tryParse(name);
            if (version != null && (allowPreview || !version.isPreview()
                    && (filter == null || filter.test(version)))
                    && (maxVersion == null || version.compareTo(maxVersion) > 0)) {
                maxVersion = version;
            }
        }

        return maxVersion;
    }

    /**
     * Decides whether a given {@link GradleCoordinate} is considered preview.
     *
     * <p>This is mostly compatible with {@link GradleCoordinate#isPreview()}, but there is one edge
     * case that we need to handle, related to Play Services. (See https://b.android.com/75292)
     */
    public static boolean isPreview(GradleCoordinate coordinate) {
        //noinspection SimplifiableIfStatement
        if (coordinate.isPreview()) {
            return true;
        }

        return "com.google.android.gms".equals(coordinate.getGroupId())
                && "play-services".equals(coordinate.getArtifactId())
                && "5.2.08".equals(coordinate.getRevision());
    }

    /**
     * Returns true if the group is an AndroidX group.
     *
     * @param groupId
     */
    public static boolean isAndroidX(@NonNull String groupId) {
        return groupId.startsWith("androidx.");
    }

    public static File getArtifactIdDirectory(
            @NonNull File repository,
            @NonNull String groupId,
            @NonNull String artifactId) {
        return new File(repository,
                groupId.replace('.', separatorChar) + separator + artifactId);
    }

    public static File getArtifactDirectory(
            @NonNull File repository,
            @NonNull GradleCoordinate coordinate) {
        File artifactIdDirectory = getArtifactIdDirectory(
                repository, coordinate.getGroupId(), coordinate.getArtifactId());

        return new File(artifactIdDirectory, coordinate.getRevision());
    }

    public static File getMavenMetadataFile(
            @NonNull File repository,
            @NonNull String groupId,
            @NonNull String artifactId) {
        return new File(getArtifactIdDirectory(repository, groupId, artifactId),
                MAVEN_METADATA_FILE_NAME);
    }
}
