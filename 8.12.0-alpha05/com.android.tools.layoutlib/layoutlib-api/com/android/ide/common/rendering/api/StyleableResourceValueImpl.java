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
import java.util.ArrayList;
import java.util.List;

/** A resource value representing a declare-styleable resource. */
public class StyleableResourceValueImpl extends ResourceValueImpl
        implements StyleableResourceValue {
    @NonNull private final List<AttrResourceValue> attrs = new ArrayList<>();

    public StyleableResourceValueImpl(
            @NonNull ResourceNamespace namespace,
            @NonNull String name,
            @Nullable String value,
            @Nullable String libraryName) {
        super(namespace, ResourceType.STYLEABLE, name, value, libraryName);
    }

    public StyleableResourceValueImpl(
            @NonNull ResourceReference reference,
            @Nullable String value,
            @Nullable String libraryName) {
        super(reference, value, libraryName);
        assert reference.getResourceType() == ResourceType.STYLEABLE;
    }

    @Override
    @NonNull
    public List<AttrResourceValue> getAllAttributes() {
        return attrs;
    }

    public void addValue(@NonNull AttrResourceValue attr) {
        assert attr.isFramework() || !isFramework()
                : "Can't add non-framework attributes to framework resource.";
        attrs.add(attr);
    }
}
