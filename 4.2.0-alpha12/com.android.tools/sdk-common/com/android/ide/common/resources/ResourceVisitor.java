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

/** @see ResourceRepository#accept(ResourceVisitor). */
@FunctionalInterface
public interface ResourceVisitor {
    enum VisitResult {
        CONTINUE,
        ABORT
    }

    /**
     * Called for each resource. Should not perform any long running operations or operations
     * involving locks.
     *
     * @param resourceItem the resource to visit
     * @return {@link VisitResult#CONTINUE} to continue visiting resources,
     *         {@link VisitResult#ABORT} to stop visiting
     */
    @NonNull
    VisitResult visit(@NonNull ResourceItem resourceItem);

    /**
     * Checks if resources belonging to the given namespace should be visited or not
     *
     * @param namespace the namespace to check
     * @return true to visit the namespace resources, false otherwise
     */
    default boolean shouldVisitNamespace(@NonNull ResourceNamespace namespace) {
        return true;
    }

    /**
     * Checks if resources of the given type should be visited or not
     *
     * @param resourceType the resource type to check
     * @return true to visit the resources, false otherwise
     */
    default boolean shouldVisitResourceType(@NonNull ResourceType resourceType) {
        return true;
    }
}
