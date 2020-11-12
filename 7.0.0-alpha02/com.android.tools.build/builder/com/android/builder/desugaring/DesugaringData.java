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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * This contains desugaring information for a single type, defined in a .class file or .jar whose
 * path is {@link #getPath()}. Combining {@link DesugaringData} for all runtime classes, we are able
 * to build the desugaring graph.
 *
 * <p>Information is produced by analyzing the class file that, and looking for all types in that
 * file that might impact desugaring of the class. Those are the following:
 *
 * <ul>
 *   <li>A direct superclass and all directly implemented interfaces.
 *   <li>For every lambda body defined in a class, type of the functional interface it implements.
 * </ul>
 *
 * <p>Please note that for the removed paths, {@link #isLive()} returns false.
 */
public final class DesugaringData {
    @NonNull private final Path path;
    @Nullable private final String internalName;
    @NonNull private final Set<String> dependencies;

    DesugaringData(@NonNull Path path) {
        this.path = path;
        this.internalName = null;
        this.dependencies = ImmutableSet.of();
    }

    DesugaringData(
            @NonNull Path path, @NonNull String internalName, @NonNull Set<String> dependencies) {
        this.path = path;
        this.internalName = internalName;
        this.dependencies = dependencies;
    }

    /** This can be either a jar or .class file defining the type. */
    @NonNull
    public Path getPath() {
        return path;
    }

    boolean isLive() {
        return Files.exists(path);
    }

    @NonNull
    String getInternalName() {
        return Preconditions.checkNotNull(internalName, "First check if isLive().");
    }

    @NonNull
    Set<String> getDependencies() {
        return dependencies;
    }
}
