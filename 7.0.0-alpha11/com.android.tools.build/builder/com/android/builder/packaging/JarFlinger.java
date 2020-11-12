/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.builder.packaging;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.PathUtils;
import com.android.zipflinger.Entry;
import com.android.zipflinger.NoCopyByteArrayOutputStream;
import com.android.zipflinger.Source;
import com.android.zipflinger.Sources;
import com.android.zipflinger.ZipArchive;
import com.android.zipflinger.ZipSource;
import com.google.common.collect.ImmutableSortedMap;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.Deflater;

public class JarFlinger implements JarCreator {
    private final ZipArchive zipArchive;
    private final Predicate<String> filter;

    // Compress inputs when building the jar archive.
    private int compressionLevel = Deflater.DEFAULT_COMPRESSION;

    public JarFlinger(@NonNull Path jarFile) throws IOException {
        this(jarFile, null);
    }

    public JarFlinger(@NonNull Path jarFile, @Nullable Predicate<String> filter)
            throws IOException {
        Files.deleteIfExists(jarFile);
        zipArchive = new ZipArchive(jarFile);
        this.filter = filter;
    }

    @Override
    public void addDirectory(@NonNull Path directory) throws IOException {
        addDirectory(directory, filter, null, null);
    }

    @Override
    public void addDirectory(
            @NonNull Path directory,
            @Nullable Predicate<String> filterOverride,
            @Nullable Transformer transformer,
            @Nullable Relocator relocator)
            throws IOException {
        ImmutableSortedMap.Builder<String, Path> candidateFiles = ImmutableSortedMap.naturalOrder();
        Files.walkFileTree(
                directory,
                EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                Integer.MAX_VALUE,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        String entryPath =
                                PathUtils.toSystemIndependentPath(directory.relativize(file));
                        if (filterOverride != null && !filterOverride.test(entryPath)) {
                            return FileVisitResult.CONTINUE;
                        }

                        if (relocator != null) {
                            entryPath = relocator.relocate(entryPath);
                        }

                        candidateFiles.put(entryPath, file);
                        return FileVisitResult.CONTINUE;
                    }
                });

        // Why do we even sort these?
        ImmutableSortedMap<String, Path> sortedFiles = candidateFiles.build();
        for (Map.Entry<String, Path> entry : sortedFiles.entrySet()) {
            String entryPath = entry.getKey();
                if (transformer != null) {
                try (InputStream is =
                        new BufferedInputStream(Files.newInputStream(entry.getValue()))) {
                    @Nullable InputStream is2 = transformer.filter(entryPath, is);
                    if (is2 != null) {
                        Source source = Sources.from(is2, entryPath, compressionLevel);
                        zipArchive.add(source);
                    }
                }
                } else {
                Source source = Sources.from(entry.getValue(), entryPath, compressionLevel);
                    zipArchive.add(source);
                }
        }
    }

    @Override
    public void addJar(@NonNull Path file) throws IOException {
        addJar(file, filter, null);
    }

    @Override
    public void addJar(
            @NonNull Path path,
            @Nullable Predicate<String> filterOverride,
            @Nullable Relocator relocator)
            throws IOException {
        ZipSource source = new ZipSource(path);
        Map<String, Entry> entries = source.entries();
        for (Entry entry : entries.values()) {
            if (entry.isDirectory()) {
                continue;
            }
            String name = entry.getName();
            if (filterOverride != null && !filterOverride.test(name)) {
                continue;
            }
            if (relocator != null) {
                name = relocator.relocate(name);
            }
            if (name.contains("../")) {
                throw new InvalidPathException(name, "Entry name contains invalid characters");
            }
            source.select(entry.getName(), name);
        }
        zipArchive.add(source);
    }

    @Override
    public void addFile(@NonNull String entryPath, @NonNull Path path) throws IOException {
        Source source = Sources.from(path, entryPath, compressionLevel);
        zipArchive.add(source);
    }

    @Override
    public void addEntry(@NonNull String entryPath, @NonNull InputStream input) throws IOException {
        Source source = Sources.from(input, entryPath, compressionLevel);
        zipArchive.add(source);
    }

    @Override
    public void setCompressionLevel(int compressionLevel) {
        this.compressionLevel = compressionLevel;
    }

    @Override
    public void close() throws IOException {
        zipArchive.close();
    }

    @Override
    public void setManifestProperties(Map<String, String> properties) throws IOException {
        Manifest manifest = new Manifest();
        Attributes global = manifest.getMainAttributes();
        global.put(Attributes.Name.MANIFEST_VERSION, "1.0.0");
        properties.forEach(
                (attributeName, attributeValue) ->
                        global.put(new Attributes.Name(attributeName), attributeValue));

        NoCopyByteArrayOutputStream os = new NoCopyByteArrayOutputStream(200);
        manifest.write(os);

        ByteArrayInputStream is = new ByteArrayInputStream(os.buf(), 0, os.getCount());
        Source source = Sources.from(is, JarFile.MANIFEST_NAME, Deflater.NO_COMPRESSION);
        zipArchive.add(source);
    }
}
