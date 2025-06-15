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
package com.android.ide.common.resources;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.ResourceType;
import org.w3c.dom.Node;

/**
 * Override of the normal ResourceItem to handle merged item cases. This is mostly to deal with
 * items that do not have a matching source file. This class overrides the method returning the
 * qualifier or the source type, to directly return a value instead of relying on a source file
 * (since merged items don't have any).
 */
public class MergedResourceMergerItem extends SourcelessResourceMergerItem {
    @NonNull private final String mQualifiers;

    /**
     * Constructs the object with a name, type and optional value.
     *
     * <p>Note that the object is not fully usable as-is. It must be added to a ResourceFile first.
     *
     * @param name the name of the resource
     * @param type the type of the resource
     * @param qualifiers the qualifiers of the resource
     * @param value an optional Node that represents the resource value.
     * @param libraryName name of library where resource came from if any
     */
    public MergedResourceMergerItem(
            @NonNull String name,
            @NonNull ResourceNamespace namespace,
            @NonNull ResourceType type,
            @NonNull String qualifiers,
            @Nullable Node value,
            @Nullable String libraryName) {
        super(name, namespace, type, value, libraryName);
        mQualifiers = qualifiers;
    }

    @NonNull
    @Override
    public String getQualifiers() {
        return mQualifiers;
    }

    @Override
    @NonNull
    public DataFile.FileType getSourceType() {
        return DataFile.FileType.XML_VALUES;
    }
}
