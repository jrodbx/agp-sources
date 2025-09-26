/*
 * Copyright (C) 2008 The Android Open Source Project
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
package com.android.ide.common.rendering.api;

import com.android.annotations.NonNull;
import com.android.resources.Density;
import com.android.resources.ResourceType;

/** Represents an Android resource value associated with a particular screen density. */
public interface DensityBasedResourceValue extends ResourceValue {
    /** Returns the density for which this resource is configured. */
    @NonNull
    Density getResourceDensity();

    /**
     * Checks if resources of the given resource type should be created as density based when they
     * belong to a folder with a density qualifier.
     */
    static boolean isDensityBasedResourceType(@NonNull ResourceType resourceType) {
        // It is not clear why only drawables and mipmaps are treated as density dependent.
        // This logic has been moved from ResourceMergerItem.getResourceValue.
        return resourceType == ResourceType.DRAWABLE || resourceType == ResourceType.MIPMAP;
    }
}
