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
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.resources.ResourceType;
import com.google.common.collect.ListMultimap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Common interface for all Android resource repositories. */
public interface ResourceRepository {
    /**
     * Returns the resources with the given namespace, type and name.
     *
     * @param namespace the namespace of the resources to return
     * @param resourceType the type of the resources to return
     * @param resourceName the bane of the resources to return
     * @return the resources matching the namespace, type, and satisfying the name filter
     */
    @NonNull
    List<ResourceItem> getResources(
            @NonNull ResourceNamespace namespace,
            @NonNull ResourceType resourceType,
            @NonNull String resourceName);

    @NonNull
    default List<ResourceItem> getResources(@NonNull ResourceReference reference) {
        return getResources(
                reference.getNamespace(), reference.getResourceType(), reference.getName());
    }

    /**
     * Returns the resources with the given namespace, type and satisfying the given predicate.
     *
     * @param namespace the namespace of the resources to return
     * @param resourceType the type of the resources to return
     * @param filter the predicate for checking resource items
     * @return the resources matching the namespace, type, and satisfying the name filter
     */
    @NonNull
    List<ResourceItem> getResources(
            @NonNull ResourceNamespace namespace,
            @NonNull ResourceType resourceType,
            @NonNull Predicate<ResourceItem> filter);

    /**
     * Returns the resources with the given namespace and type keyed by resource names.
     * If you need only the names of the resources, but not the resources themselves, call
     * {@link #getResourceNames(ResourceNamespace, ResourceType)} instead.
     *
     * @param namespace the namespace of the resources to return
     * @param resourceType the type of the resources to return
     * @return the resources matching the namespace and type
     */
    @NonNull
    ListMultimap<String, ResourceItem> getResources(
            @NonNull ResourceNamespace namespace, @NonNull ResourceType resourceType);

    /**
     * Returns the names of resources with the given namespace and type. For some resource
     * repositories calling this method can be more efficient than calling
     * {@link #getResources(ResourceNamespace, ResourceType)} and then
     * {@link ListMultimap#keySet()}.
     *
     * @param namespace the namespace of the resources to return
     * @param resourceType the type of the resources to return
     * @return the names of the resources matching the namespace and type
     */
    @NonNull
    default Set<String> getResourceNames(
            @NonNull ResourceNamespace namespace, @NonNull ResourceType resourceType) {
        return getResources(namespace, resourceType).keySet();
    }

    /**
     * Calls the {@link ResourceVisitor#visit(ResourceItem)} method for all resources in the
     * repository. The visitor should not perform any long running operations or operations
     * involving locks.
     *
     * @param visitor the visitor object
     * @return the last value returned by the visitor, i.e. {@link
     *     ResourceVisitor.VisitResult#ABORT} if the method finished because the visitor requested
     *     it or {@link ResourceVisitor.VisitResult#CONTINUE} otherwise.
     */
    @NonNull
    ResourceVisitor.VisitResult accept(@NonNull ResourceVisitor visitor);

    /**
     * Returns a list of all resources in the repository.
     *
     * <p>This method is expensive. Consider using {@link #accept(ResourceVisitor)} instead.
     *
     * @return a list of all resources in the repository
     */
    @NonNull
    default List<ResourceItem> getAllResources() {
        List<ResourceItem> result = new ArrayList<>();
        accept(item -> {
            result.add(item);
            return ResourceVisitor.VisitResult.CONTINUE;
        });
        return result;
    }

    /**
     * Returns a collection of <b>public</b> resource items with the given namespace and type.
     *
     * @param namespace the namespace of the resources to return
     * @param type the type of the resources to return
     * @return a collection of items, possibly empty.
     */
    @NonNull
    Collection<ResourceItem> getPublicResources(
            @NonNull ResourceNamespace namespace, @NonNull ResourceType type);

    /**
     * Checks if the repository contains resources with the given namespace, type and name.
     *
     * @param namespace the namespace of the resources to check
     * @param resourceType the type of the resources to check
     * @param resourceName the name of the resources to check
     * @return true if there is at least one resource with the given namespace, type and name in
     *         the repository
     */
    boolean hasResources(
            @NonNull ResourceNamespace namespace,
            @NonNull ResourceType resourceType,
            @NonNull String resourceName);

    /**
     * Checks if the repository contains resources with the given namespace and type.
     *
     * @param namespace the namespace of the resources to check
     * @param resourceType the type of the resources to check
     * @return true if there is at least one resource with the given namespace and type in
     *         the repository
     */
    boolean hasResources(@NonNull ResourceNamespace namespace, @NonNull ResourceType resourceType);

    /**
     * Returns types of the resources in the given namespace.
     *
     * @param namespace the namespace to get resource types for
     * @return the set of resource types
     */
    @NonNull
    Set<ResourceType> getResourceTypes(@NonNull ResourceNamespace namespace);

    /**
     * Returns the namespaces that the resources in this repository belong to. The returned set may
     * include namespaces that don't contain any resource items.
     */
    @NonNull
    Set<ResourceNamespace> getNamespaces();

    /**
     * Returns all leaf resource repositories contained in this repository, or this repository
     * itself, if it does not contain any other repositories and implements
     * {@link SingleNamespaceResourceRepository}.
     */
    @NonNull
    Collection<SingleNamespaceResourceRepository> getLeafResourceRepositories();
}
