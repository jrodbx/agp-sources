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
package com.android.ide.common.rendering.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.google.common.base.MoreObjects;

/**
 * A straightforward implementation of the {@link StyleItemResourceValue} interface.
 */
public class StyleItemResourceValueImpl extends ResourceValueImpl implements StyleItemResourceValue {
    @NonNull private final String attributeName;

    public StyleItemResourceValueImpl(
            @NonNull ResourceNamespace namespace,
            @NonNull String attributeName,
            @Nullable String value,
            @Nullable String libraryName) {
        super(namespace, ResourceType.STYLE_ITEM, "<item>", value, libraryName);
        this.attributeName = attributeName;
    }

    /**
     * Returns contents of the {@code name} XML attribute that defined this style item. This is
     * supposed to be a reference to an {@code attr} resource.
     */
    @Override
    @NonNull
    public String getAttrName() {
        return attributeName;
    }

    /**
     * Returns a {@link ResourceReference} to the {@code attr} resource this item is defined for, if
     * the name was specified using the correct syntax.
     */
    @Override
    @Nullable
    public ResourceReference getAttr() {
        ResourceUrl url = ResourceUrl.parseAttrReference(attributeName);
        if (url == null) {
            return null;
        }

        return url.resolve(getNamespace(), mNamespaceResolver);
    }

    /**
     * Returns just the name part of the attribute being referenced, for backwards compatibility
     * with layoutlib. Don't call this method, the item may be in a different namespace than the
     * attribute and the value being referenced, use {@link #getAttr()} instead.
     *
     * @deprecated TODO(namespaces): Throw in this method, once layoutlib correctly calls {@link
     *     #getAttr()} instead.
     */
    @Deprecated
    @Override
    @NonNull
    public String getName() {
        ResourceUrl url = ResourceUrl.parseAttrReference(attributeName);
        if (url != null) {
            return url.name;
        } else {
            return attributeName;
        }
    }

    @Override
    @NonNull
    public String toString() {
        return MoreObjects.toStringHelper(this)
                          .add("namespace", getNamespace())
                          .add("attribute", attributeName)
                          .add("value", getValue())
                          .toString();
    }
}
