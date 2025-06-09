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
import com.android.resources.ResourceType;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A resource value representing an attr resource.
 */
public class AttrResourceValueImpl extends ResourceValueImpl implements AttrResourceValue {
    /** The keys are enum or flag names, the values are corresponding numeric values. */
    @Nullable private Map<String, Integer> valueMap;
    /** The keys are enum or flag names, the values are the value descriptions. */
    @Nullable private Map<String, String> valueDescriptionMap;
    @Nullable private String description;
    @Nullable private String groupName;
    @NonNull private Set<AttributeFormat> formats = EnumSet.noneOf(AttributeFormat.class);

    public AttrResourceValueImpl(
            @NonNull ResourceNamespace namespace,
            @NonNull String name,
            @Nullable String libraryName) {
        super(namespace, ResourceType.ATTR, name, null, libraryName);
    }

    public AttrResourceValueImpl(
            @NonNull ResourceReference reference, @Nullable String libraryName) {
        super(reference, null, libraryName);
    }

    @Override
    @NonNull
    public Map<String, Integer> getAttributeValues() {
        return valueMap == null ? Collections.emptyMap() : valueMap;
    }

    @Override
    @Nullable
    public String getValueDescription(@NonNull String valueName) {
        return valueDescriptionMap == null ? null : valueDescriptionMap.get(valueName);
    }

    @Override
    @Nullable
    public String getDescription() {
        return description;
    }

    @Override
    @Nullable
    public String getGroupName() {
        return groupName;
    }

    @Override
    @NonNull
    public Set<AttributeFormat> getFormats() {
        return formats;
    }

    /**
     * Adds a possible value of the flag or enum attribute.
     *
     * @param valueName the name of the value
     * @param numericValue the corresponding numeric value
     * @param valueName the description of the value
     */
    public void addValue(@NonNull String valueName, @Nullable Integer numericValue, @Nullable String description) {
        if (valueMap == null) {
          valueMap = new LinkedHashMap<>();
        }

        valueMap.put(valueName, numericValue);

        if (description != null) {
            if (valueDescriptionMap == null) {
              valueDescriptionMap = new HashMap<>();
            }

            valueDescriptionMap.put(valueName, description);
        }
    }

    /**
     * Sets the description of the attr resource.
     *
     * @param description the description to set
     */
    public void setDescription(@Nullable String description) {
      this.description = description;
    }

    /**
     * Sets the name of group the attr resource belongs to.
     *
     * @param groupName the name of the group to set
     */
    public void setGroupName(@Nullable String groupName) {
      this.groupName = groupName;
    }

    /**
     * Sets the formats allowed for the attribute.
     *
     * @param formats the formats to set
     */
    public void setFormats(@NonNull Collection<AttributeFormat> formats) {
        this.formats = EnumSet.copyOf(formats);
    }
}
