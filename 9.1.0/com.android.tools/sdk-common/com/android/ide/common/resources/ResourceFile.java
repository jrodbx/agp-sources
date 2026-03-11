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
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import java.io.File;
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * Represents a file in a resource folders.
 *
 * <p>It contains a link to the {@link File}, the qualifier string (which is the name of the folder
 * after the first '-' character), a list of {@link ResourceMergerItem}s and a type.
 *
 * <p>The type of the file is based on whether the file is located in a values folder ({@link
 * FileType#XML_VALUES}) or in another folder ({@link FileType#SINGLE_FILE} or {@link
 * FileType#GENERATED_FILES}).
 */
public class ResourceFile extends DataFile<ResourceMergerItem> {
    static final String ATTR_QUALIFIER = "qualifiers";

    private FolderConfiguration mFolderConfiguration;

    /**
     * Creates a resource file with a single resource item.
     *
     * <p>The source file is set on the item with {@link ResourceMergerItem#setSourceFile(DataFile)}
     *
     * <p>The type of the ResourceFile will be {@link FileType#SINGLE_FILE}.
     *
     * @param file the File
     * @param item the resource item
     * @param folderConfiguration the folder configuration
     */
    public ResourceFile(
            @NonNull File file,
            @NonNull ResourceMergerItem item,
            @NonNull FolderConfiguration folderConfiguration) {
        super(file, FileType.SINGLE_FILE);
        mFolderConfiguration = folderConfiguration;
        init(item);
    }

    /**
     * Creates a resource file with a list of resource items.
     *
     * <p>The source file is set on the items with {@link ResourceMergerItem#setSourceFile(DataFile)}
     *
     * <p>The type of the ResourceFile will be {@link FileType#XML_VALUES}.
     *
     * @param file the File
     * @param items the resource items
     * @param folderConfiguration the folder configuration
     */
    public ResourceFile(
            @NonNull File file,
            @NonNull List<ResourceMergerItem> items,
            @NonNull FolderConfiguration folderConfiguration) {
        this(file, items, folderConfiguration, FileType.XML_VALUES);
    }

    private ResourceFile(
            @NonNull File file,
            @NonNull List<ResourceMergerItem> items,
            @NonNull FolderConfiguration folderConfiguration,
            @NonNull FileType fileType) {
        super(file, fileType);
        mFolderConfiguration = folderConfiguration;
        init(items);
    }

    public static ResourceFile generatedFiles(
            @NonNull File file,
            @NonNull List<ResourceMergerItem> items,
            @NonNull FolderConfiguration folderConfiguration) {
        // TODO: Replace other constructors with named methods.
        return new ResourceFile(file, items, folderConfiguration, FileType.GENERATED_FILES);
    }

    /**
     * Creates a resource file with a single resource item.
     *
     * <p>This method parses the folder configuration from qualifiers for each file independently
     * (which may be less performant than parsing it once for all files in a folder and supplying
     * the parsed configuration).
     */
    @VisibleForTesting
    public static ResourceFile createSingle(
            @NonNull File file, @NonNull ResourceMergerItem item, @NonNull String qualifiers) {
        FolderConfiguration folderConfiguration = FolderConfiguration.getConfigForQualifierString(qualifiers);
        assert folderConfiguration != null;
        return new ResourceFile(file, item, folderConfiguration);
    }

    @NonNull
    public String getQualifiers() {
        return mFolderConfiguration.getQualifierString();
    }

    // Used in Studio
    public void setQualifiers(@NonNull String qualifiers) {
        mFolderConfiguration = FolderConfiguration.getConfigForQualifierString(qualifiers);
    }

    @NonNull
    public FolderConfiguration getFolderConfiguration() {
        return mFolderConfiguration;
    }

    @Override
    void addExtraAttributes(Document document, Node node, String namespaceUri) {
        NodeUtils.addAttribute(document, node, namespaceUri, ATTR_QUALIFIER, getQualifiers());

        if (getType() == FileType.GENERATED_FILES) {
            NodeUtils.addAttribute(document, node, namespaceUri, SdkConstants.ATTR_PREPROCESSING, "true");
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
                .add("mFile", mFile)
                .toString();
    }
}
