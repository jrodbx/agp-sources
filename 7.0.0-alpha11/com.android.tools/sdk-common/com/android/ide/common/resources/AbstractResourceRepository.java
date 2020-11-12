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
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.ResourceType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Base class for resource repository classes. Provides implementations of resource lookup methods.
 */
public abstract class AbstractResourceRepository implements ResourceRepository {
    /**
     * Returns the {@link ListMultimap} containing resources with the given namespace and type keyed
     * by resource names. Unlike {@link #getResources(ResourceNamespace, ResourceType)}, this method
     * is expected to return the map directly backed by the internal resource storage, although
     * the returned map doesn't have to be mutable.
     *
     * @param namespace the namespace of the resources to return
     * @param resourceType the type of the resources to return
     * @return the resources matching the namespace and type
     */
    @NonNull
    protected abstract ListMultimap<String, ResourceItem> getResourcesInternal(
            @NonNull ResourceNamespace namespace, @NonNull ResourceType resourceType);

    @Override
    @NonNull
    public List<ResourceItem> getResources(
            @NonNull ResourceNamespace namespace,
            @NonNull ResourceType resourceType,
            @NonNull String resourceName) {
        ListMultimap<String, ResourceItem> map = getResourcesInternal(namespace, resourceType);
        List<ResourceItem> items = map.get(resourceName);
        return items == null ? ImmutableList.of() : ImmutableList.copyOf(items);
    }

    @Override
    @NonNull
    public List<ResourceItem> getResources(
            @NonNull ResourceNamespace namespace,
            @NonNull ResourceType resourceType,
            @NonNull Predicate<ResourceItem> filter) {
        List<ResourceItem> result = null;
        ListMultimap<String, ResourceItem> map = getResourcesInternal(namespace, resourceType);
        for (ResourceItem item : map.values()) {
            if (filter.test(item)) {
                if (result == null) {
                    result = new ArrayList<>();
                }
                result.add(item);
            }
        }

        return result == null ? ImmutableList.of() : result;
    }

    @Override
    @NonNull
    public ListMultimap<String, ResourceItem> getResources(
            @NonNull ResourceNamespace namespace, @NonNull ResourceType resourceType) {
        ListMultimap<String, ResourceItem> map = getResourcesInternal(namespace, resourceType);
        return ImmutableListMultimap.copyOf(map);
    }

    @Override
    public boolean hasResources(
            @NonNull ResourceNamespace namespace,
            @NonNull ResourceType resourceType,
            @NonNull String resourceName) {
        ListMultimap<String, ResourceItem> map = getResourcesInternal(namespace, resourceType);
        List<ResourceItem> items = map.get(resourceName);
        return items != null && !items.isEmpty();
    }

    @Override
    public boolean hasResources(
            @NonNull ResourceNamespace namespace, @NonNull ResourceType resourceType) {
        ListMultimap<String, ResourceItem> map = getResourcesInternal(namespace, resourceType);
        return !map.isEmpty();
    }

    @Override
    @NonNull
    public Set<ResourceType> getResourceTypes(@NonNull ResourceNamespace namespace) {
        EnumSet<ResourceType> result = EnumSet.noneOf(ResourceType.class);
        for (ResourceType resourceType : ResourceType.values()) {
            if (hasResources(namespace, resourceType)) {
                result.add(resourceType);
            }
        }
        return Sets.immutableEnumSet(result);
    }

    /**
     * Helper method to be used by implementations of the {@link #accept(ResourceVisitor)} method.
     */
    protected static ResourceVisitor.VisitResult acceptByResources(
            @NonNull Map<ResourceType, ListMultimap<String, ResourceItem>> map,
            @NonNull ResourceVisitor visitor) {
        for (Map.Entry<ResourceType, ListMultimap<String, ResourceItem>> entry : map.entrySet()) {
            if (visitor.shouldVisitResourceType(entry.getKey())) {
                for (ResourceItem item : entry.getValue().values()) {
                    if (visitor.visit(item) == ResourceVisitor.VisitResult.ABORT) {
                        return ResourceVisitor.VisitResult.ABORT;
                    }
                }
            }
        }
        return ResourceVisitor.VisitResult.CONTINUE;
    }
}
