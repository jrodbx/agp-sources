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

package com.android.support;

import com.android.annotations.NonNull;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Ints;
import java.util.Collection;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AndroidxNameUtils {
    private static final Logger LOG = Logger.getLogger(AndroidxName.class.getName());

    static final String ANDROID_SUPPORT_PKG = "android.support.";
    static final String ANDROID_ARCH_PKG = "android.arch.";
    static final String ANDROID_DATABINDING_PKG = "android.databinding.";

    /** Package mappings for package that have been just renamed */
    static final ImmutableMap<String, String> ANDROIDX_PKG_MAPPING;

    /** Mappings for class names that have been moved to a different package */
    static final ImmutableMap<String, String> ANDROIDX_FULL_CLASS_MAPPING;

    static final ImmutableMap<String, String> ANDROIDX_COORDINATES_MAPPING;

    /** Ordered list of old android support packages sorted by decreasing length */
    static final ImmutableList<String> ANDROIDX_OLD_PKGS;

    static {
        ImmutableMap.Builder<String, String> classTransformMap = ImmutableMap.builder();
        ImmutableMap.Builder<String, String> packageTransformMap = ImmutableMap.builder();
        ImmutableMap.Builder<String, String> coordinatesTransformMap = ImmutableMap.builder();
        try {
            AndroidxMigrationParserKt.parseMigrationFile(
                    new MigrationParserVisitor() {
                        @Override
                        public void visitGradleCoordinateUpgrade(
                                @NonNull String groupName,
                                @NonNull String artifactName,
                                @NonNull String newBaseVersion) {
                            // AndroidxName does not use the coordinate upgrades
                        }

                        @Override
                        public void visitGradleCoordinate(
                                @NonNull String oldGroupName,
                                @NonNull String oldArtifactName,
                                @NonNull String newGroupName,
                                @NonNull String newArtifactName,
                                @NonNull String newBaseVersion) {
                            coordinatesTransformMap.put(
                                    oldGroupName + ":" + oldArtifactName,
                                    newGroupName + ":" + newArtifactName);
                        }

                        @Override
                        public void visitClass(@NonNull String old, @NonNull String newName) {
                            classTransformMap.put(old, newName);
                        }

                        @Override
                        public void visitPackage(@NonNull String old, @NonNull String newName) {
                            packageTransformMap.put(old, newName);
                        }
                    });
        } catch (Throwable e) {
            LOG.severe("Error loading androidx migration mapping: " + e.getLocalizedMessage());
        }

        ANDROIDX_FULL_CLASS_MAPPING = classTransformMap.build();
        ANDROIDX_PKG_MAPPING = packageTransformMap.build();
        ANDROIDX_OLD_PKGS =
                Ordering.from(
                                (Comparator<String>)
                                        (left, right) -> {
                                            // Short with the longest names first
                                            return Ints.compare(right.length(), left.length());
                                        })
                        .immutableSortedCopy(ANDROIDX_PKG_MAPPING.keySet());
        ANDROIDX_COORDINATES_MAPPING = coordinatesTransformMap.build();
    }

    @NonNull
    static String getPackageMapping(@NonNull String oldPkgName, boolean strictChecking) {
        for (int i = 0, n = ANDROIDX_OLD_PKGS.size(); i < n; i++) {
            String prefix = ANDROIDX_OLD_PKGS.get(i);
            if (oldPkgName.startsWith(prefix)) {
                return ANDROIDX_PKG_MAPPING.get(prefix) + oldPkgName.substring(prefix.length());
            }
        }

        if (strictChecking && LOG.isLoggable(Level.FINE)) {
            LOG.fine("support library package not found: " + oldPkgName);
        }
        return oldPkgName;
    }

    /** Returns a {@link Collection} of all the possible {@code androidx} maven coordinates */
    @NonNull
    public static Collection<String> getAllAndroidxCoordinates() {
        return ANDROIDX_COORDINATES_MAPPING.values();
    }

    /**
     * Returns the mapping of a given coordinate into the new {@code androidx} maven coordinates. If
     * the coordinate does not belong to the support library, the method will just return the passed
     * coordinate.
     */
    @NonNull
    public static String getCoordinateMapping(@NonNull String coordinate) {
        return ANDROIDX_COORDINATES_MAPPING.getOrDefault(coordinate, coordinate);
    }

    /**
     * Returns the mapping of a given coordinate into the new {@code androidx} maven coordinates,
     * including the package version. If the coordinate does not belong to the support library,
     * the method will just return the passed coordinate.
     * @param coordinate
     * @return
     */
    @NonNull
    public static String getVersionedCoordinateMapping(@NonNull String coordinate) {
        // Strip version
        String[] components = coordinate.split(":");
        if (components.length < 3) {
            return coordinate;
        }
        String canonicalCoordinate = components[0] + ":" + components[1];
        String result = ANDROIDX_COORDINATES_MAPPING.getOrDefault(canonicalCoordinate, null);
        return result == null ? coordinate : result + ":+";
    }

    @NonNull
    public static String getNewName(@NonNull String oldName) {
        int innerClassSymbol = oldName.indexOf('$');
        if (innerClassSymbol != -1) {
            String outerClassName = oldName.substring(0, innerClassSymbol);
            String innerClassName = oldName.substring(innerClassSymbol);
            return getNewName(outerClassName) + innerClassName;
        }

        String newName = ANDROIDX_FULL_CLASS_MAPPING.get(oldName);
        if (newName != null) {
            return newName;
        }

        int lastDot = oldName.lastIndexOf('.');
        return getPackageMapping(oldName.substring(0, lastDot + 1), false)
                + oldName.substring(lastDot + 1);
    }
}
