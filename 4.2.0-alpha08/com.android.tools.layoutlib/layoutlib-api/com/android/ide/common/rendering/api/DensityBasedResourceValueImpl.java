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
import com.android.annotations.Nullable;
import com.android.resources.Density;
import com.android.resources.ResourceType;
import com.android.utils.HashCodes;
import java.util.Objects;

public class DensityBasedResourceValueImpl extends ResourceValueImpl
        implements DensityBasedResourceValue {
    @NonNull private final Density density;

    public DensityBasedResourceValueImpl(
            @NonNull ResourceNamespace namespace,
            @NonNull ResourceType type,
            @NonNull String name,
            @Nullable String value,
            @NonNull Density density,
            @Nullable String libraryName) {
        super(namespace, type, name, value, libraryName);
        this.density = density;
    }

    public DensityBasedResourceValueImpl(
            @NonNull ResourceReference reference,
            @Nullable String value,
            @NonNull Density density) {
        super(reference, value);
        this.density = density;
    }

    @Override
    @NonNull
    public final Density getResourceDensity() {
        return density;
    }

    @Override
    @NonNull
    public String toString() {
        return "DensityBasedResourceValue ["
               + getResourceType() + "/" + getName() + " = " + getValue()
               + " (density:" + density + ", framework:" + isFramework() + ")]";
    }

    @Override
    public int hashCode() {
        return HashCodes.mix(super.hashCode(), Objects.hashCode(density));
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        DensityBasedResourceValueImpl other = (DensityBasedResourceValueImpl) obj;
        return Objects.equals(density, other.density);
    }
}
