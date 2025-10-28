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
import com.android.resources.ResourceUrl;
import java.util.Collection;

/**
 * Represents an Android style resource with a name and a list of children {@link ResourceValue}.
 */
public interface StyleResourceValue extends ResourceValue {
    /**
     * Returns value of the {@code parent} XML attribute of this style. Unlike
     * {@link #getParentStyle()}, does not try to determine the name of the parent style by
     * removing the last component of the name of this style.
     */
    @Nullable
    String getParentStyleName();

    /**
     * Returns a reference to the parent style, if it can be determined based on the explicit parent
     * reference in XML, or by using the part of the name of this style before the last dot.
     *
     * <p>Note that names of styles have more meaning than other resources: if the parent attribute
     * is not set, aapt looks for a dot in the style name and treats the string up to the last dot
     * as the name of a parent style. So {@code <style name="Foo.Bar.Baz">} has an implicit parent
     * called {@code Foo.Bar}. Setting the {@code parent} XML attribute disables this feature, even
     * if it's set to an empty string. See {@code ResourceParser::ParseStyle} in aapt for details.
     */
    @Nullable
    default ResourceReference getParentStyle() {
        String parentStyleName = getParentStyleName();
        if (parentStyleName != null) {
            ResourceUrl url = ResourceUrl.parseStyleParentReference(parentStyleName);
            if (url == null) {
                return null;
            }

            return url.resolve(getNamespace(), getNamespaceResolver());
        }

        String styleName = getName();
        int lastDot = styleName.lastIndexOf('.');
        if (lastDot >= 0) {
            String parent = styleName.substring(0, lastDot);
            if (parent.isEmpty()) {
                return null;
            }

            return ResourceReference.style(getNamespace(), parent);
        }

        return null;
    }

    static boolean isDefaultParentStyleName(
            @NonNull String parentStyleName, @NonNull String styleName) {
        return styleName.lastIndexOf('.') == parentStyleName.length()
                && styleName.startsWith(parentStyleName);
    }

    /**
     * Finds the item for the given qualified attr name in this style (if it's defined in this
     * style).
     */
    @Nullable
    StyleItemResourceValue getItem(@NonNull ResourceNamespace namespace, @NonNull String name);

    /** Finds the item for the given attr in this style (if it's defined in this style). */
    @Nullable
    StyleItemResourceValue getItem(@NonNull ResourceReference attr);

    /**
     * Returns a list of all items defined in this Style. This doesn't return items inherited from
     * the parent.
     */
    @NonNull
    Collection<StyleItemResourceValue> getDefinedItems();
}
