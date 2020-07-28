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

package com.android.builder.dexing;

import com.android.annotations.NonNull;
import com.android.dex.Dex;
import com.google.common.collect.ImmutableList;

/**
 * Strategy used to merge dex archives into a final DEX file(s). It is used to track how many DEX
 * files can be merged together so that they fit into a single DEX file.
 */
public interface DexMergingStrategy {

    /**
     * Tries to add a new DEX file to the existing list of DEX files to be merged. In case it is not
     * possible to add it, it returns {@code false}. If it is possible to add it, {@code true} is
     * returned.
     *
     * @return if adding this DEX file for merging is possible, or a new DEX file should be created
     */
    boolean tryToAddForMerging(@NonNull Dex dexFile);

    /** Starts a new list of DEX file(s) that can be merged. This list is empty when created. */
    void startNewDex();

    /** Returns the current list of DEX files that can be merged. */
    @NonNull
    ImmutableList<Dex> getAllDexToMerge();
}
