/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceType;
import java.util.Collections;
import java.util.List;

/**
 * A class containing all the resources needed to do a rendering.
 *
 * <p>Contains both the project specific resources and the framework resources, and provides
 * convenience methods to resolve resource and theme references.
 */
public class RenderResources {
    public static final String REFERENCE_NULL = SdkConstants.NULL_RESOURCE;
    public static final String REFERENCE_EMPTY = "@empty";
    public static final String REFERENCE_UNDEFINED = "@undefined";

    public void setLogger(LayoutLog logger) {
    }

    /** Returns the {@link StyleResourceValue} representing the default theme. */
    @Nullable
    public StyleResourceValue getDefaultTheme() {
        return null;
    }

    /**
     * Use this theme to resolve resources.
     *
     * <p>Remember to call {@link #clearStyles()} to clear the applied styles, so the default theme
     * may be restored.
     *
     * @param theme The style to use for resource resolution in addition to the the default theme
     *     and the styles applied earlier. If null, the operation is a no-op.
     * @param useAsPrimary If true, the {@code theme} is used first to resolve attributes. If false,
     *     the theme is used if the resource cannot be resolved using the default theme and all the
     *     themes that have been applied prior to this call.
     */
    public void applyStyle(@Nullable StyleResourceValue theme, boolean useAsPrimary) {}

    /**
     * Clear all the themes applied with {@link #applyStyle(StyleResourceValue, boolean)}
     */
    public void clearStyles() {
    }

    /**
     * Returns a list of {@link StyleResourceValue} containing a list of themes to be used for
     * resolving resources. The order of the themes in the list specifies the order in which they
     * should be used to resolve resources.
     */
    @NonNull
    public List<StyleResourceValue> getAllThemes() {
        return Collections.emptyList();
    }

    /**
     * Returns the {@link ResourceValue} for a given attr in the all themes returned by {@link
     * #getAllThemes()}. If the item is not directly available in a theme, its parent theme is
     * used before checking the next theme from the list.
     */
    @Nullable
    public ResourceValue findItemInTheme(@NonNull ResourceReference attr) {
        List<StyleResourceValue> allThemes = getAllThemes();
        for (StyleResourceValue theme : allThemes) {
            ResourceValue value = findItemInStyle(theme, attr);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * Returns the {@link ResourceValue} matching a given attribute in a given style. If the item is
     * not directly available in the style, the method looks in its parent style.
     */
    @Nullable
    public ResourceValue findItemInStyle(
            @NonNull StyleResourceValue style, @NonNull ResourceReference attr) {
        return null;
    }

    /**
     * @deprecated Use {@link #dereference(ResourceValue)} instead, to provide context necessary to
     *     handle namespaces correctly, like the "current" namespace or namespace prefix lookup
     *     logic. Alternatively, use {@link #getUnresolvedResource(ResourceReference)} or {@link
     *     #getResolvedResource(ResourceReference)} if you already know exactly what you're looking
     *     for.
     */
    @Deprecated
    @Nullable
    public ResourceValue findResValue(@Nullable String reference, boolean forceFrameworkOnly) {
        if (reference == null) {
            return null;
        }

        // Type is ignored. We don't call setNamespaceResolver, because this method is called from code that's not namespace aware anyway.
        return dereference(
                new ResourceValueImpl(
                        ResourceNamespace.fromBoolean(forceFrameworkOnly),
                        ResourceType.ID,
                        "com.android.ide.common.rendering.api.RenderResources",
                        reference));
    }

    /**
     * Searches for a {@link ResourceValue} referenced by the given value. This method doesn't
     * perform recursive resolution, so the returned {@link ResourceValue} (if not null) may be just
     * another reference.
     *
     * <p>References to theme attributes is supported and resolved against the theme from {@link
     * #getDefaultTheme()}. For more details see <a
     * href="https://developer.android.com/guide/topics/resources/accessing-resources.html#ReferencesToThemeAttributes">Referencing
     * style attributes</a>
     *
     * <p>Unlike {@link #resolveResValue(ResourceValue)}, this method returns null if the input is
     * not a reference (i.e. doesn't start with '@' or '?').
     *
     * @param resourceValue the value to dereference. Its namespace and namespace lookup logic are
     *     used to handle namespaces when interpreting the textual value. The type is ignored and
     *     will not affect the type of the returned value.
     * @see #resolveResValue(ResourceValue)
     */
    @Nullable
    public ResourceValue dereference(@NonNull ResourceValue resourceValue) {
        return null;
    }

    /** Returns a resource by namespace, type and name. The returned resource is unresolved. */
    @Nullable
    public ResourceValue getUnresolvedResource(@NonNull ResourceReference reference) {
        return null;
    }

    /**
     * Returns a resource by namespace, type and name. The returned resource is resolved, as defined
     * in {@link #resolveResValue(ResourceValue)}.
     *
     * @see #resolveResValue(ResourceValue)
     */
    @Nullable
    public ResourceValue getResolvedResource(@NonNull ResourceReference reference) {
        ResourceValue referencedValue = getUnresolvedResource(reference);
        if (referencedValue == null) {
            return null;
        }

        return resolveResValue(referencedValue);
    }

    /**
     * Returns the "final" {@link ResourceValue} referenced by the value of <var>value</var>.
     *
     * <p>This method ensures that the returned {@link ResourceValue} object is not a valid
     * reference to another resource. It may be just a leaf value (e.g. "#000000") or a reference
     * that could not be dereferenced.
     *
     * <p>If a value that does not need to be resolved is given, the method will return the input
     * value.
     *
     * @param value the value containing the reference to resolve.
     * @return a {@link ResourceValue} object or <code>null</code>
     */
    @Nullable
    public ResourceValue resolveResValue(@Nullable ResourceValue value) {
        return null;
    }

    /**
     * Returns the parent style of the given style, if any
     *
     * @param style the style to look up
     * @return the parent style, or null
     */
    public StyleResourceValue getParent(@NonNull StyleResourceValue style) {
        return null;
    }

    /** Returns the style matching the given reference. */
    @Nullable
    public StyleResourceValue getStyle(@NonNull ResourceReference reference) {
        return null;
    }
}
