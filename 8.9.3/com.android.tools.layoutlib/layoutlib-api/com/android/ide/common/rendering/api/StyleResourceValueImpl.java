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
import com.android.resources.ResourceType;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import java.util.Collection;

/**
 * Represents an Android style resource with a name and a list of children {@link ResourceValue}.
 */
public class StyleResourceValueImpl extends ResourceValueImpl implements StyleResourceValue {
    /**
     * Contents of the {@code parent} XML attribute. May be empty or null.
     *
     * @see #StyleResourceValueImpl(ResourceReference, String, String)
     */
    @Nullable private String parentStyle;

    /**
     * Items defined in this style, indexed by the namespace and name of the attribute they define.
     */
    @NonNull private final Table<ResourceNamespace, String, StyleItemResourceValue> styleItems =
            HashBasedTable.create();

    /**
     * Creates a new {@link StyleResourceValueImpl}.
     *
     * <p>Note that names of styles have more meaning than other resources: if the parent attribute
     * is not set, aapt looks for a dot in the style name and treats the string up to the last dot
     * as the name of a parent style. So {@code <style name="Foo.Bar.Baz">} has an implicit parent
     * called {@code Foo.Bar}. Setting the {@code parent} XML attribute disables this feature, even
     * if it's set to an empty string. See {@code ResourceParser::ParseStyle} in aapt for details.
     */
    public StyleResourceValueImpl(
            @NonNull ResourceNamespace namespace,
            @NonNull String name,
            @Nullable String parentStyle,
            @Nullable String libraryName) {
        super(namespace, ResourceType.STYLE, name, null, libraryName);
        this.parentStyle = parentStyle;
    }

    /**
     * Creates a new {@link StyleResourceValueImpl}.
     *
     * @see #StyleResourceValueImpl(ResourceNamespace, String, String, String)
     */
    public StyleResourceValueImpl(
            @NonNull ResourceReference reference,
            @Nullable String parentStyle,
            @Nullable String libraryName) {
        super(reference, null, libraryName);
        assert reference.getResourceType() == ResourceType.STYLE;
        this.parentStyle = parentStyle;
    }

    /** Creates a copy of the given style. */
    @NonNull
    public static StyleResourceValueImpl copyOf(@NonNull StyleResourceValue style) {
        StyleResourceValueImpl copy = new StyleResourceValueImpl(
                style.getNamespace(),
                style.getName(),
                style.getParentStyleName(),
                style.getLibraryName());
        for (StyleItemResourceValue item : style.getDefinedItems()) {
            copy.addItem(item);
        }
        return copy;
    }

    @Override
    @Nullable
    public String getParentStyleName() {
        return parentStyle;
    }

    @Override
    @Nullable
    public StyleItemResourceValue getItem(
            @NonNull ResourceNamespace namespace, @NonNull String name) {
        return styleItems.get(namespace, name);
    }

    @Override
    @Nullable
    public StyleItemResourceValue getItem(@NonNull ResourceReference attr) {
        assert attr.getResourceType() == ResourceType.ATTR;
        return styleItems.get(attr.getNamespace(), attr.getName());
    }

    @Override
    @NonNull
    public Collection<StyleItemResourceValue> getDefinedItems() {
        return styleItems.values();
    }

    /**
     * Adds a style item to this style.
     *
     * @param item the style item to add
     */
    public void addItem(@NonNull StyleItemResourceValue item) {
        ResourceReference attr = item.getAttr();
        if (attr != null) {
            styleItems.put(attr.getNamespace(), attr.getName(), item);
        }
    }

    @Override
    public void replaceWith(@NonNull ResourceValue style) {
        assert style instanceof StyleResourceValueImpl
                : style.getClass() + " is not StyleResourceValue";
        super.replaceWith(style);

        //noinspection ConstantConditions
        if (style instanceof StyleResourceValueImpl) {
            styleItems.clear();
            styleItems.putAll(((StyleResourceValueImpl) style).styleItems);
        }
    }
}
