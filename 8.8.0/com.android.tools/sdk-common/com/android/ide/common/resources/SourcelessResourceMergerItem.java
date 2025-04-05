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
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import org.w3c.dom.Node;

/**
 * Resource items that have no source file (qualifiers and folder configuration supplied by other
 * means).
 */
public abstract class SourcelessResourceMergerItem extends ResourceMergerItem {

    public SourcelessResourceMergerItem(
            @NonNull String name,
            @NonNull ResourceNamespace namespace,
            @NonNull ResourceType type,
            @Nullable Node value,
            @Nullable String libraryName) {
        super(name, namespace, type, value, null, libraryName);
    }

    /**
     * Determine the FolderConfiguration from the item's qualifiers instead of from {@link
     * #getSourceFile()}.
     *
     * @return the folder configuration
     */
    @NonNull
    @Override
    public FolderConfiguration getConfiguration() {
        String qualifier = getQualifiers();
        FolderConfiguration fromString = FolderConfiguration.getConfigForQualifierString(qualifier);
        return fromString != null ? fromString : new FolderConfiguration();
    }
}
