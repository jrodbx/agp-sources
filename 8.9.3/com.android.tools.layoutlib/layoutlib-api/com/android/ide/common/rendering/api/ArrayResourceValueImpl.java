/*
 * Copyright (C) 2013 The Android Open Source Project
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
import com.android.resources.ResourceType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Represents an Android array resource with a name and a list of children {@link ResourceValue}
 * items, one for array element.
 */
public class ArrayResourceValueImpl extends ResourceValueImpl implements ArrayResourceValue {
    @NonNull private final List<String> elements = new ArrayList<>();

    public ArrayResourceValueImpl(
            @NonNull ResourceNamespace namespace,
            @NonNull String name,
            @Nullable String libraryName) {
        super(namespace, ResourceType.ARRAY, name, null, libraryName);
    }

    public ArrayResourceValueImpl(
            @NonNull ResourceReference reference, @Nullable String libraryName) {
        super(reference, null, libraryName);
        assert reference.getResourceType() == ResourceType.ARRAY;
    }

    @Override
    public int getElementCount() {
        return elements.size();
    }

    @Override
    @NonNull
    public String getElement(int index) {
        return elements.get(index);
    }

    /** Adds an element into the array. */
    public void addElement(@NonNull String value) {
        elements.add(value);
    }

    @Override
    public Iterator<String> iterator() {
        return elements.iterator();
    }

    /**
     * Returns the index of the element to pick by default if a client of layoutlib asks for the
     * {@link #getValue()} rather than the more specific {@linkplain ArrayResourceValue} iteration
     * methods
     */
    protected int getDefaultIndex() {
        return 0;
    }

    @Override
    @Nullable
    public String getValue() {
        // Clients should normally not call this method on ArrayResourceValues; they should
        // pick the specific array element they want. However, for compatibility with older
        // layout libs, return the first array element's value instead.

        //noinspection VariableNotUsedInsideIf
        if (super.getValue() == null) {
            if (!elements.isEmpty()) {
                return elements.get(getDefaultIndex());
            }
        }

        return super.getValue();
    }
}
