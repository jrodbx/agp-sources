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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.PathUtils;
import com.android.zipflinger.Entry;
import com.android.zipflinger.Source;
import com.android.zipflinger.Sources;
import com.android.zipflinger.ZipArchive;
import com.android.zipflinger.ZipSource;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class JarFlinger implements Closeable {
    private final ZipArchive zipArchive;
    private final Predicate<String> filter;

    // Compress inputs when building the jar archive.
    private int compressionLevel = Deflater.DEFAULT_COMPRESSION;

    public static final Predicate<String> CLASSES_ONLY =
            archivePath -> archivePath.endsWith(SdkConstants.DOT_CLASS);
    public static final Predicate<String> EXCLUDE_CLASSES =
            archivePath -> !CLASSES_ONLY.test(archivePath);

    /**
     * A filter that keeps everything but ignores duplicate resources.
     *
     * <p>Stateful, hence a factory method rather than an instance.
     */
    public static Predicate<String> allIgnoringDuplicateResources() {
        // Keep track of resources to avoid failing on collisions.
        Set<String> resources = new HashSet<>();
        return archivePath ->
                archivePath.endsWith(SdkConstants.DOT_CLASS) || resources.add(archivePath);
    }

    public interface Transformer {
        /**
         * Transforms the given file.
         *
         * @param entryPath the path within the jar file
         * @param input an input stream of the contents of the file
         * @return a new input stream if the file is transformed in some way, the same input stream
         *     if the file is to be kept as is and null if the file should not be packaged.
         */
        @Nullable
        InputStream filter(@NonNull String entryPath, @NonNull InputStream input);
    }

    public interface Relocator {
        @NonNull
        String relocate(@NonNull String entryPath);
    }

    public JarFlinger(@NonNull Path jarFile) throws IOException {
        this(jarFile, null);
    }

    public JarFlinger(@NonNull Path jarFile, @Nullable Predicate<String> filter)
            throws IOException {
        Files.deleteIfExists(jarFile);
        zipArchive = new ZipArchive(jarFile);
        this.filter = filter;
    }

    private static class NoOpRelocator implements Relocator {
        @NonNull
        @Override
        public String relocate(@NonNull String entryPath) {
            return entryPath;
        }
    }

    public void addDirectory(@NonNull Path directory) throws IOException {
        addDirectory(directory, filter, null);
    }

    public void addDirectory(
            @NonNull Path directory,
            @Nullable Predicate<String> filterOverride,
            @Nullable Transformer transformer)
            throws IOException {
        addDirectory(directory, filterOverride, transformer, new NoOpRelocator());
    }

    public void addDirectory(
            @NonNull Path directory,
            @Nullable Predicate<String> filterOverride,
            @Nullable Transformer transformer,
            @NonNull Relocator relocator)
            throws IOException {
        ImmutableSortedMap.Builder<String, Path> candidateFiles = ImmutableSortedMap.naturalOrder();
        ImmutableSortedSet.Builder<String> foldersEncountered = ImmutableSortedSet.naturalOrder();

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

                        entryPath = relocator.relocate(entryPath);

                        candidateFiles.put(entryPath, file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        String entryPath =
                                PathUtils.toSystemIndependentPath(directory.relativize(dir));
                        // Check if the directory is the root of the tree being traversed in which
                        // case its relative path is equal to"".
                        if (!entryPath.isEmpty()) {
                            foldersEncountered.add(entryPath);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });

        ImmutableSortedSet<String> sortedDirectories = foldersEncountered.build();
        for (String dirName : sortedDirectories) {
            zipArchive.add(Sources.dir(relocator.relocate(dirName)));
        }

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

    public void addJar(@NonNull Path file) throws IOException {
        addJar(file, filter, null);
    }

    public void addJar(@NonNull InputStream inputJar) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(inputJar)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // do not take directories
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (filter != null && !filter.test(name)) {
                    continue;
                }
                if (name.contains("../")) {
                    throw new InvalidPathException(name, "Entry name contains invalid characters");
                }
                // b/246948010: Read all bytes and create a new input stream as ZipFlinger closes
                // the stream once it is done reading entry.
                addEntry(entry.getName(), new ByteArrayInputStream(zis.readAllBytes()));
            }
        }
    }

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

    public void addFile(@NonNull String entryPath, @NonNull Path path) throws IOException {
        Source source = Sources.from(path, entryPath, compressionLevel);
        zipArchive.add(source);
    }

    public void addEntry(@NonNull String entryPath, @NonNull InputStream input) throws IOException {
        Source source = Sources.from(input, entryPath, compressionLevel);
        zipArchive.add(source);
    }

    public void setCompressionLevel(int compressionLevel) {
        this.compressionLevel = compressionLevel;
    }

    public void close() throws IOException {
        zipArchive.close();
    }
}
