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

package com.android.build.gradle.internal.res.shrinker;

import static com.android.SdkConstants.DOT_9PNG;
import static com.android.SdkConstants.DOT_PNG;
import static com.android.SdkConstants.DOT_XML;
import static com.android.build.gradle.internal.res.shrinker.DummyContent.TINY_9PNG;
import static com.android.build.gradle.internal.res.shrinker.DummyContent.TINY_9PNG_CRC;
import static com.android.build.gradle.internal.res.shrinker.DummyContent.TINY_BINARY_XML;
import static com.android.build.gradle.internal.res.shrinker.DummyContent.TINY_BINARY_XML_CRC;
import static com.android.build.gradle.internal.res.shrinker.DummyContent.TINY_PNG;
import static com.android.build.gradle.internal.res.shrinker.DummyContent.TINY_PNG_CRC;
import static com.android.build.gradle.internal.res.shrinker.DummyContent.TINY_PROTO_XML;
import static com.android.build.gradle.internal.res.shrinker.DummyContent.TINY_PROTO_XML_CRC;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.res.shrinker.gatherer.ResourcesGatherer;
import com.android.build.gradle.internal.res.shrinker.graph.ResourcesGraphBuilder;
import com.android.build.gradle.internal.res.shrinker.obfuscation.ObfuscationMappingsRecorder;
import com.android.build.gradle.internal.res.shrinker.usages.ResourceUsageRecorder;
import com.android.ide.common.resources.usage.ResourceUsageModel.Resource;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Unit that analyzes all resources (after resource merging, compilation and code shrinking has
 * been completed) and figures out which resources are unused, and replaces them with dummy content
 * inside zip archive file.
 *
 * Resource shrinker implementation that allows to customize:
 * <ul>
 *     <li>application resource gatherer (from R files, resource tables, etc);
 *     <li>recorder for mappings from obfuscated class/methods to original class/methods;
 *     <li>sources from which resource usages are recorded (Dex files, compiled JVM classes,
 *         AndroidManifests, etc);
 *     <li>resources graph builder that connects resources dependent on each other (analyzing
 *         raw resources content in XML, HTML, CSS, JS, analyzing resource content in proto
 *         compiled format);
 * </ul>
 */
public class ResourceShrinkerImpl implements ResourceShrinker {

    private final ResourcesGatherer resourcesGatherer;
    private final ObfuscationMappingsRecorder obfuscationMappingsRecorder;
    private final List<ResourceUsageRecorder> usageRecorders;
    private final ResourcesGraphBuilder graphBuilder;
    private final ShrinkerDebugReporter debugReporter;
    private final ApkFormat apkFormat;

    @VisibleForTesting final ResourceShrinkerModel model;
    List<Resource> unused = null;

    public ResourceShrinkerImpl(
            @NonNull ResourcesGatherer resourcesGatherer,
            @Nullable ObfuscationMappingsRecorder obfuscationMappingsRecorder,
            @NonNull List<ResourceUsageRecorder> usageRecorders,
            @NonNull ResourcesGraphBuilder graphBuilder,
            @NonNull ShrinkerDebugReporter debugReporter,
            @NonNull ApkFormat apkFormat) {
        this.resourcesGatherer = resourcesGatherer;
        this.obfuscationMappingsRecorder = obfuscationMappingsRecorder;
        this.usageRecorders = usageRecorders;
        this.graphBuilder = graphBuilder;
        this.debugReporter = debugReporter;
        this.apkFormat = apkFormat;
        model = new ResourceShrinkerModel(debugReporter);
    }

    @Override
    public void analyze() throws IOException {
        resourcesGatherer.gatherResourceValues(model);
        if (obfuscationMappingsRecorder != null) {
            obfuscationMappingsRecorder.recordObfuscationMappings(model);
        }
        for (ResourceUsageRecorder usageRecorder : usageRecorders) {
            usageRecorder.recordUsages(model);
        }
        graphBuilder.buildGraph(model);

        model.getUsageModel().processToolsAttributes();
        model.keepPossiblyReferencedResources();

        debugReporter.debug(() -> model.getUsageModel().dumpResourceModel());

        unused = model.findUnused();
    }

    @Override
    public void close() throws Exception {
        debugReporter.close();
    }

    @Override
    public int getUnusedResourceCount() {
        return unused.size();
    }

    @Override
    public void rewriteResourceZip(@NonNull File source, @NonNull File dest) throws IOException {
        if (dest.exists()) {
            boolean deleted = dest.delete();
            if (!deleted) {
                throw new IOException("Could not delete " + dest);
            }
        }
        try (ZipFile zipFile = new ZipFile(source);
             JarOutputStream zos =
                     new JarOutputStream(new BufferedOutputStream(new FileOutputStream(dest)))) {

            // Rather than using Deflater.DEFAULT_COMPRESSION we use 9 here,
            // since that seems to match the compressed sizes we observe in source
            // .ap_ files encountered by the resource shrinker:
            zos.setLevel(9);

            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry current = entries.nextElement();
                if (shouldBeReplacedWithDummy(current)) {
                    replaceWithDummyEntry(zos, current);
                } else {
                    copyToOutput(zipFile.getInputStream(current), zos, current);
                }
            }
        }

        // If net negative, copy original back. This is unusual, but can happen
        // in some circumstances, such as the one described in
        // https://plus.google.com/+SaidTahsinDane/posts/X9sTSwoVUhB
        // "Removed unused resources: Binary resource data reduced from 588KB to 595KB: Removed -1%"
        // Guard against that, and worst case, just use the original.
        long before = source.length();
        long after = dest.length();
        if (after > before) {
            debugReporter.info(() -> "Resource shrinking did not work (grew from "
                    + before + " to " + after + "); using original instead");

            Files.copy(source, dest);
        }
    }

    private boolean shouldBeReplacedWithDummy(ZipEntry entry) {
        if (entry.isDirectory() || !entry.getName().startsWith("res/")) {
            return false;
        }
        Resource resource = getResourceByJarPath(entry.getName());
        Preconditions.checkNotNull(resource,
                "Resource for entry '" + entry.getName() + "' was not gathered.");
        return !resource.isReachable();
    }

    /** Replaces the given entry with a minimal valid file of that type. */
    private void replaceWithDummyEntry(JarOutputStream zos, ZipEntry entry)
            throws IOException {
        // Create a new entry so that the compressed len is recomputed.
        String name = entry.getName();
        byte[] bytes;
        long crc;
        if (name.endsWith(DOT_9PNG)) {
            bytes = TINY_9PNG;
            crc = TINY_9PNG_CRC;
        } else if (name.endsWith(DOT_PNG)) {
            bytes = TINY_PNG;
            crc = TINY_PNG_CRC;
        } else if (name.endsWith(DOT_XML)) {
            switch (apkFormat) {
               case BINARY:
                   bytes = TINY_BINARY_XML;
                    crc = TINY_BINARY_XML_CRC;
                    break;
                case PROTO:
                    bytes = TINY_PROTO_XML;
                    crc = TINY_PROTO_XML_CRC;
                    break;
                default:
                    throw new IllegalStateException("");
             }
        } else {
            bytes = new byte[0];
            crc = 0L;
        }
        JarEntry outEntry = new JarEntry(name);
        if (entry.getTime() != -1L) {
            outEntry.setTime(entry.getTime());
        }
        if (entry.getMethod() == JarEntry.STORED) {
            outEntry.setMethod(JarEntry.STORED);
            outEntry.setSize(bytes.length);
            outEntry.setCrc(crc);
        }
        zos.putNextEntry(outEntry);
        zos.write(bytes);
        zos.closeEntry();

        debugReporter.info(() -> "Skipped unused resource " + name + ": " + entry.getSize()
                + " bytes (replaced with small dummy file of size " + bytes.length + " bytes)");
    }

    private static void copyToOutput(InputStream zis, JarOutputStream zos, ZipEntry entry)
            throws IOException {
        // We can't just compress all files; files that are not
        // compressed in the source .ap_ file must be left uncompressed
        // here, since for example RAW files need to remain uncompressed in
        // the APK such that they can be mmap'ed at runtime.
        // Preserve the STORED method of the input entry.
        JarEntry outEntry;
        if (entry.getMethod() == JarEntry.STORED) {
            outEntry = new JarEntry(entry);
        } else {
            // Create a new entry so that the compressed len is recomputed.
            outEntry = new JarEntry(entry.getName());
            if (entry.getTime() != -1L) {
                outEntry.setTime(entry.getTime());
            }
        }

        zos.putNextEntry(outEntry);

        if (!entry.isDirectory()) {
            byte[] bytes = ByteStreams.toByteArray(zis);
            if (bytes != null) {
                zos.write(bytes);
            }
        }

        zos.closeEntry();
    }

    @Nullable
    private Resource getResourceByJarPath(String path) {
        if (!path.startsWith("res/")) {
            return null;
        }

        // Jars use forward slash paths, not File.separator
        int folderStart = 4; // "res/".length
        int folderEnd = path.indexOf('/', folderStart);
        if (folderEnd == -1) {
            return null;
        }

        String folderName = path.substring(folderStart, folderEnd);
        ResourceFolderType folderType = ResourceFolderType.getFolderType(folderName);
        if (folderType == null) {
            return null;
        }

        int nameStart = folderEnd + 1;
        int nameEnd = path.indexOf('.', nameStart);
        if (nameEnd == -1) {
            nameEnd = path.length();
        }

        String name = path.substring(nameStart, nameEnd);
        for (ResourceType type : FolderTypeRelationship.getRelatedResourceTypes(folderType)) {
            if (type == ResourceType.ID) {
                continue;
            }

            Resource resource = model.getUsageModel().getResource(type, name);
            if (resource != null) {
                return resource;
            }
        }

        return null;
    }
}
