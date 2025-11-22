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
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceVisitor.VisitResult;
import com.android.resources.ResourceType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import java.util.Collection;
import java.util.Map;

/** Simple repository implementation that just stores what the {@link ResourceMerger} emits. */
public final class TestResourceRepository extends AbstractResourceRepository
        implements SingleNamespaceResourceRepository {
    private final ResourceNamespace namespace;
    private final ResourceTable resourceTable = new ResourceTable();

    @VisibleForTesting
    public TestResourceRepository(@NonNull ResourceNamespace namespace) {
        this.namespace = namespace;
    }

    @Override
    @NonNull
    protected ListMultimap<String, ResourceItem> getResourcesInternal(
            @NonNull ResourceNamespace namespace, @NonNull ResourceType resourceType) {
        if (!namespace.equals(this.namespace)) {
            return ImmutableListMultimap.of();
        }
        return getMap(namespace, resourceType);
    }

    @NonNull
    public ListMultimap<String, ResourceItem> getMap(
            @NonNull ResourceNamespace namespace, @NonNull ResourceType resourceType) {
        ListMultimap<String, ResourceItem> map = resourceTable.get(namespace, resourceType);
        return map == null ? ImmutableListMultimap.of() : map;
    }

    @Override
    @NonNull
    public ResourceVisitor.VisitResult accept(@NonNull ResourceVisitor visitor) {
        if (visitor.shouldVisitNamespace(namespace)) {
            for (Map.Entry<ResourceNamespace, Map<ResourceType, ListMultimap<String, ResourceItem>>>
                    entry : resourceTable.rowMap().entrySet()) {
                if (acceptByResources(entry.getValue(), visitor) == VisitResult.ABORT) {
                    return VisitResult.ABORT;
                }
            }
        }

        return VisitResult.CONTINUE;
    }

    @Override
    @NonNull
    public ResourceNamespace getNamespace() {
        return namespace;
    }

    @Override
    @Nullable
    public String getPackageName() {
        return namespace.getPackageName();
    }

    @NonNull
    public ResourceTable getResourceTable() {
        return resourceTable;
    }

    @Override
    @NonNull
    public Collection<ResourceItem> getPublicResources(
            @NonNull ResourceNamespace namespace, @NonNull ResourceType type) {
        throw new UnsupportedOperationException();
    }

    @VisibleForTesting
    public void update(@NonNull ResourceMerger merger) {
        ResourceRepositories.updateTableFromMerger(merger, resourceTable);
    }
}
