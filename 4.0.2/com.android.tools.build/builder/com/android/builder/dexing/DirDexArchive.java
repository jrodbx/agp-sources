/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.builder.dexing;

import com.android.annotations.NonNull;
import com.android.utils.FileUtils;
import com.android.utils.PathUtils;
import com.google.common.collect.ImmutableList;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Directory representing a dex archive. All dex entries, {@link DexArchiveEntry}, are stored under
 * the directory {@link #getRootPath()}
 */
final class DirDexArchive implements DexArchive {

    @NonNull private final Path rootDir;

    public DirDexArchive(@NonNull Path rootDir) {
        this.rootDir = rootDir;
    }

    @NonNull
    @Override
    public Path getRootPath() {
        return rootDir;
    }

    @Override
    public void addFile(@NonNull String relativePath, byte[] bytes, int offset, int end)
            throws IOException {
        Path finalPath = rootDir.resolve(relativePath);
        Files.createDirectories(finalPath.getParent());
        try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(finalPath))) {
            os.write(bytes, offset, end);
            os.flush();
        }
    }

    @Override
    @NonNull
    public List<DexArchiveEntry> getFiles() throws IOException {
        ImmutableList.Builder<DexArchiveEntry> builder = ImmutableList.builder();

        Iterator<Path> files;
        try (Stream<Path> paths = Files.walk(getRootPath())) {
            files = paths.filter(DexArchives.DEX_ENTRY_FILTER).iterator();
            while (files.hasNext()) {
                builder.add(createEntry(files.next()));
            }
        }

        return builder.build();
    }

    @Override
    public void close() throws IOException {
        // do nothing
    }

    private DexArchiveEntry createEntry(@NonNull Path dexFile) throws IOException {
        byte[] content = Files.readAllBytes(dexFile);
        Path relativePath = getRootPath().relativize(dexFile);

        return new DexArchiveEntry(content, PathUtils.toSystemIndependentPath(relativePath), this);
    }
}
