/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.ide.common.resources;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.util.List;
import org.w3c.dom.Node;

/**
 * Implementation of {@link DataMerger} for {@link AssetSet}, {@link AssetItem}, and
 * {@link AssetFile}.
 */
public class AssetMerger extends DataMerger<AssetItem, AssetFile, AssetSet> {

    @Override
    protected AssetSet createFromXml(Node node, @Nullable String aaptEnv) throws MergingException {
        AssetSet set = new AssetSet("", aaptEnv);
        return (AssetSet) set.createFromXml(node, aaptEnv);
    }

    @Override
    protected boolean requiresMerge(@NonNull String dataItemKey) {
        return false;
    }

    @Override
    protected void mergeItems(
            @NonNull String dataItemKey,
            @NonNull List<AssetItem> items,
            @NonNull MergeConsumer<AssetItem> consumer) {
        // nothing to do
    }
}
