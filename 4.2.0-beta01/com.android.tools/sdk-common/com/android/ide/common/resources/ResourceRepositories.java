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
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Ordering;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;

/** Utilities for dealing with {@link ResourceRepository} instances. */
public class ResourceRepositories {
    /** Compares {@link ResourceItem} instances using {@link ResourceItem#getKey()}. */
    private static final Ordering<ResourceItem> ORDERING_BY_KEY =
            Ordering.from(Comparator.comparing(ResourceItem::getKey));

    private ResourceRepositories() {}

    /**
     * Sorts values of the given multimap according to values returned by
     * the {@link ResourceItem#getKey()} method.
     */
    public static void sortItemLists(@NonNull ListMultimap<String, ResourceItem> multimap) {
        ListMultimap<String, ResourceItem> sorted = ArrayListMultimap.create();
        for (Map.Entry<String, Collection<ResourceItem>> entry : multimap.asMap().entrySet()) {
            sorted.putAll(entry.getKey(), ORDERING_BY_KEY.sortedCopy(entry.getValue()));
        }

        multimap.clear();
        multimap.putAll(sorted);
    }

    /**
     * Adds and removes items from the given {@link ResourceTable} according to events emitted by
     * the given merger.
     *
     * @see MergeConsumer
     * @see ResourceMerger#mergeData(MergeConsumer, boolean)
     */
    public static void updateTableFromMerger(
            @NonNull ResourceMerger merger, @NonNull ResourceTable fullTable) {
        MergeConsumer<ResourceMergerItem> consumer =
                new MergeConsumer<ResourceMergerItem>() {
                    @Override
                    public void start(@NonNull DocumentBuilderFactory factory) {}

                    @Override
                    public void end() {}

                    @Override
                    public void addItem(@NonNull ResourceMergerItem item) {
                        if (item.isTouched()) {
                            ListMultimap<String, ResourceItem> multimap =
                                    fullTable.getOrPutEmpty(item.getNamespace(), item.getType());

                            if (!multimap.containsEntry(item.getName(), item)) {
                                multimap.put(item.getName(), item);
                            }
                        }
                    }

                    @Override
                    public void removeItem(
                            @NonNull ResourceMergerItem removedItem,
                            @Nullable ResourceMergerItem replacedBy) {
                        fullTable.remove(removedItem);
                    }

                    @Override
                    public boolean ignoreItemInMerge(ResourceMergerItem item) {
                        // we never ignore any item.
                        return false;
                    }
                };

        try {
            merger.mergeData(consumer, true);
        } catch (MergingException e) {
            throw new RuntimeException(e);
        }
    }
}
