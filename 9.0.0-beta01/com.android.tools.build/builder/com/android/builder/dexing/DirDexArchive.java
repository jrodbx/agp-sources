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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.utils.PathUtils;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
    public List<DexArchiveEntry> getSortedDexArchiveEntries() {
        List<Path> dexFiles =
                DexUtilsKt.getSortedFilesInDir(
                        rootDir,
                        relativePath ->
                                relativePath
                                        .toLowerCase(Locale.ENGLISH)
                                        .endsWith(SdkConstants.DOT_DEX));
        List<DexArchiveEntry> dexArchiveEntries = new ArrayList<>(dexFiles.size());
        for (Path dexFile : dexFiles) {
            dexArchiveEntries.add(createEntry(dexFile));
        }
        return dexArchiveEntries;
    }

    @Override
    public void close() {
        // do nothing
    }

    private DexArchiveEntry createEntry(@NonNull Path dexFile) {
        byte[] content;
        try {
            content = Files.readAllBytes(dexFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Path relativePath = getRootPath().relativize(dexFile);

        return new DexArchiveEntry(content, PathUtils.toSystemIndependentPath(relativePath), this);
    }
}
