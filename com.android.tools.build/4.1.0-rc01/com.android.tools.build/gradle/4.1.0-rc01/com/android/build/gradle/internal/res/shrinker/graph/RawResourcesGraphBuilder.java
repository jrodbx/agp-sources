/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.res.shrinker.graph;

import static com.android.SdkConstants.DOT_XML;
import static com.android.utils.ImmutableCollectors.toImmutableList;
import static com.android.utils.SdkUtils.endsWithIgnoreCase;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.res.shrinker.ResourceShrinkerModel;
import com.android.resources.ResourceFolderType;
import com.android.utils.XmlUtils;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Builds resources graph via parsing XML, HTML and JS resources inside resource directories.
 * Processes tools:keep and tools:discard attributes.
 */
public class RawResourcesGraphBuilder implements ResourcesGraphBuilder {

    private final Path resourceDir;

    public RawResourcesGraphBuilder(@NonNull Path resourceDir) {
        this.resourceDir = resourceDir;
    }

    @Override
    public void buildGraph(@NonNull ResourceShrinkerModel model) throws IOException {
        ImmutableList<Path> subDirs =
                Files.list(resourceDir)
                        .filter(path -> Files.isDirectory(path))
                        .collect(toImmutableList());
        for (Path subDir : subDirs) {
            ResourceFolderType folderType =
                    ResourceFolderType.getFolderType(subDir.getFileName().toString());
            if (folderType != null) {
                recordResources(model, folderType, subDir);
            }
        }
    }

    private void recordResources(
            ResourceShrinkerModel model, ResourceFolderType folderType, Path folder)
            throws IOException {
        ImmutableList<Path> resourceFiles =
                Files.list(folder)
                        .filter(path -> Files.isRegularFile(path))
                        .collect(toImmutableList());
        for (Path resourceFile : resourceFiles) {
            File file = resourceFile.toFile();
            String path = resourceFile.toString();

            model.getUsageModel().file = file;
            try {
                boolean isXml = endsWithIgnoreCase(path, DOT_XML);
                if (isXml) {
                    String xml =
                            new String(Files.readAllBytes(resourceFile), StandardCharsets.UTF_8);
                    Document document = XmlUtils.parseDocument(xml, true);
                    model.getUsageModel().visitXmlDocument(file, folderType, document);
                } else {
                    model.getUsageModel().visitBinaryResource(folderType, file);
                }
            } catch (SAXException e) {
                throw new IOException(e);
            } finally {
                model.getUsageModel().file = null;
            }
        }
    }
}
