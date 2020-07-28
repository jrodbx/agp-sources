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
import com.android.dex.DexFormat;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.List;

/**
 * Naive DEX merging strategy. It will just add up references found in all DEX files it tries to
 * merge, although many of these can be duplicates. However, its advantages are speed and low memory
 * consumption.
 */
public class NaiveDexMergingStrategy implements DexMergingStrategy {

    @VisibleForTesting static final int MAX_NUMBER_OF_IDS_IN_DEX = DexFormat.MAX_MEMBER_IDX + 1;

    @NonNull private final List<Dex> currentDexesToMerge = Lists.newArrayList();
    private int currentMethodIdsUsed = 0;
    private int currentFieldIdsUsed = 0;

    @Override
    public boolean tryToAddForMerging(@NonNull Dex dexFile) {
        int dexMethodIds = dexFile.getTableOfContents().methodIds.size;
        int dexFieldIds = dexFile.getTableOfContents().fieldIds.size;

        if (dexMethodIds + currentMethodIdsUsed > MAX_NUMBER_OF_IDS_IN_DEX) {
            return false;
        }

        if (dexFieldIds + currentFieldIdsUsed > MAX_NUMBER_OF_IDS_IN_DEX) {
            return false;
        }

        currentMethodIdsUsed += dexMethodIds;
        currentFieldIdsUsed += dexFieldIds;

        currentDexesToMerge.add(dexFile);
        return true;
    }

    @Override
    public void startNewDex() {
        currentMethodIdsUsed = 0;
        currentFieldIdsUsed = 0;
        currentDexesToMerge.clear();
    }

    @NonNull
    @Override
    public ImmutableList<Dex> getAllDexToMerge() {
        return ImmutableList.copyOf(currentDexesToMerge);
    }
}
