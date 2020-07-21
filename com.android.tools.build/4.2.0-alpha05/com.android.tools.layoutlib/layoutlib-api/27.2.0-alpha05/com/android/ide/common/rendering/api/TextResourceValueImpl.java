/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.utils.HashCodes;
import java.util.Objects;

/** A {@link ResourceValue} intended for text nodes where we need access to the raw XML text. */
public class TextResourceValueImpl extends ResourceValueImpl implements TextResourceValue {
    @Nullable private String rawXmlValue;

    public TextResourceValueImpl(
            @NonNull ResourceNamespace namespace,
            @NonNull String name,
            @Nullable String textValue,
            @Nullable String rawXmlValue,
            @Nullable String libraryName) {
        super(namespace, ResourceType.STRING, name, textValue, libraryName);
        this.rawXmlValue = rawXmlValue;
    }

    public TextResourceValueImpl(
            @NonNull ResourceReference reference,
            @Nullable String textValue,
            @Nullable String rawXmlValue,
            @Nullable String libraryName) {
        super(reference, textValue, libraryName);
        this.rawXmlValue = rawXmlValue;
        assert reference.getResourceType() == ResourceType.STRING;
    }

    @Override
    @Nullable
    public String getRawXmlValue() {
        if (rawXmlValue != null) {
            return rawXmlValue;
        }
        return super.getValue();
    }

    /**
     * Sets the raw XML text.
     *
     * @param value the text to set
     *
     * @see #getRawXmlValue()
     */
    public void setRawXmlValue(@Nullable String value) {
        rawXmlValue = value;
    }

    @Override
    public int hashCode() {
        return HashCodes.mix(super.hashCode(), Objects.hashCode(rawXmlValue));
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        TextResourceValueImpl other = (TextResourceValueImpl) obj;
        return Objects.equals(rawXmlValue, other.rawXmlValue);
    }
}
