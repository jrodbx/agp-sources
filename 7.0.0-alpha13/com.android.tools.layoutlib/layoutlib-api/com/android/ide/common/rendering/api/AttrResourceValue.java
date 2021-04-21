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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.util.Map;
import java.util.Set;

/**
 * A resource value representing an attr resource.
 *
 * <p>The {@link #getValue()} method of this class always returns null. To get the numeric values
 * associated with flags or enums use {@link #getAttributeValues()}.
 */
public interface AttrResourceValue extends ResourceValue {
    /**
     * Returns the enum or flag integer values keyed by the value names. Some of the values in the
     * returned map may be null. The returned map is guaranteed to contain the names of all declared
     * values, even the ones that don't have corresponding numeric values.
     *
     * @return the map of (name, integer) values
     */
    @NonNull
    Map<String, Integer> getAttributeValues();

    /**
     * Returns the description of a enum/flag value with the given name.
     */
    @Nullable
    String getValueDescription(@NonNull String valueName);

    /**
     * Returns the description of the attr resource obtained from an XML comment.
     *
     * @return the description, or null if there was no comment for this attr in XML
     */
    @Nullable
    String getDescription();

    /**
     * Returns the name of the attr group obtained from an XML comment. For example, the following
     * XML will produce "textAppearance" attr resource with the group name "Text styles":
     *
     * <pre>
     *   &lt;!-- =========== -->
     *   &lt;!-- Text styles -->
     *   &lt;!-- =========== -->
     *   &lt;eat-comment /&gt;
     *
     *   &lt;!-- Default appearance of text: color, typeface, size, and style. -->
     *   &lt;attr name="textAppearance" format="reference" /&gt;
     * </pre>
     *
     * <p>Attr grouping is available only for the framework resources.
     *
     * @return the group name, or null no grouping information is available
     */
    @Nullable
    String getGroupName();

    /** Returns the formats allowed for the values of the attribute. */
    @NonNull
    Set<AttributeFormat> getFormats();
}
