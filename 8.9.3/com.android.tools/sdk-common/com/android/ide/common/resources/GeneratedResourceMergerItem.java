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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.ResourceType;
import java.io.File;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * A {@link ResourceMergerItem} that is generated, it knows its generated file path, which is not
 * the same as as the owner ResourceFile.
 */
public class GeneratedResourceMergerItem extends SourcelessResourceMergerItem {
    private final File mGeneratedFile;
    private final String mQualifiers;

    public GeneratedResourceMergerItem(
            @NonNull String name,
            @NonNull ResourceNamespace namespace,
            @NonNull File generatedFile,
            @NonNull ResourceType type,
            @NonNull String qualifiers,
            @Nullable String libraryName) {
        super(name, namespace, type, null, libraryName);
        mGeneratedFile = generatedFile;
        mQualifiers = qualifiers;
    }

    @NonNull
    @Override
    public String getQualifiers() {
        return mQualifiers;
    }

    @Override
    public File getFile() {
        return mGeneratedFile;
    }

    @Override
    Node getDetailsXml(Document document) {
        Element element = document.createElement("generated-file");
        element.setAttribute(SdkConstants.ATTR_PATH, mGeneratedFile.getAbsolutePath());
        element.setAttribute(SdkConstants.ATTR_TYPE, getType().getName());
        element.setAttribute(ResourceFile.ATTR_QUALIFIER, mQualifiers);
        return element;
    }
}
