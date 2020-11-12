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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Keeps track of types, and paths that define them.
 *
 * <p>For a type, it returns paths that define it. Typically, there is only a single path, but there
 * might be multiple.
 *
 * <p>For a path, it returns a set of types that it defines. In case of a .class file, this will be
 * a single type, but for a .jar, there are multiple.
 */
final class TypePaths {
    /**
     * Map from path to types defined in that path. For jars this is list of types in that jar, for
     * class files, this is a single type.
     */
    @NonNull private final Map<Path, Set<String>> pathToTypes = Maps.newHashMap();
    /**
     * Map from type to paths defining that type. Each type should be defined only in one file, but
     * there is no mechanism to enforce that. This field should be accessed only using {@link
     * #reverseMapping()}.
     */
    @NonNull private final Map<String, Set<Path>> typeToPaths = Maps.newHashMap();

    boolean isReverseMappingValid = false;

    void add(@NonNull Path path, @NonNull String internalName) {
        Set<String> types = pathToTypes.getOrDefault(path, getNewSetForPath(path));
        types.add(internalName);
        pathToTypes.put(path, types);

        invalidateReverseMapping();
    }

    @NonNull
    Set<String> getTypes(@NonNull Path path) {
        return pathToTypes.getOrDefault(path, ImmutableSet.of());
    }

    /**
     * Remove path information, and a list of types that should be removed. Not all types defined in
     * a path will be removed, as a single type might be defined in multiple paths.
     */
    @Nullable
    Set<String> remove(@NonNull Path path, @NonNull Set<Path> removedPaths) {
        Set<String> allInPath = pathToTypes.remove(path);
        if (allInPath == null) {
            return ImmutableSet.of();
        }

        invalidateReverseMapping();

        Set<String> toRemove = new HashSet<>(allInPath.size());
        for (String type : allInPath) {
            Set<Path> definedInPaths = getPaths(type);
            if (Sets.difference(definedInPaths, removedPaths).isEmpty()) {
                // all paths containing this type have been removed
                toRemove.add(type);
            }
        }

        return toRemove;
    }

    @NonNull
    Set<Path> getPaths(@NonNull String internalName) {
        return reverseMapping().getOrDefault(internalName, ImmutableSet.of());
    }

    private void invalidateReverseMapping() {
        isReverseMappingValid = false;
    }

    @NonNull
    private Map<String, Set<Path>> reverseMapping() {
        if (isReverseMappingValid) {
            return typeToPaths;
        }

        typeToPaths.clear();
        for (Map.Entry<Path, Set<String>> pathToType : pathToTypes.entrySet()) {
            for (String type : pathToType.getValue()) {
                Set<Path> paths =
                        typeToPaths.getOrDefault(type, Sets.newHashSetWithExpectedSize(1));
                paths.add(pathToType.getKey());

                typeToPaths.put(type, paths);
            }
        }
        isReverseMappingValid = true;
        return typeToPaths;
    }

    @NonNull
    private static Set<String> getNewSetForPath(@NonNull Path path) {
        if (path.toString().endsWith(SdkConstants.DOT_CLASS)) {
            return Sets.newHashSetWithExpectedSize(1);
        } else {
            return Sets.newHashSet();
        }
    }
}
