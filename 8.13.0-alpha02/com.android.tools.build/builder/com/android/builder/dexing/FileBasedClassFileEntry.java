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
import com.android.utils.PathUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;

final class FileBasedClassFileEntry implements ClassFileEntry {

    @NonNull private final String relativePath;
    @NonNull private final Path fullPath;
    @NonNull private final DirectoryBasedClassFileInput input;

    public FileBasedClassFileEntry(
            @NonNull Path rootPath,
            @NonNull Path fullPath,
            @NonNull DirectoryBasedClassFileInput input) {
        this.relativePath = PathUtils.toSystemIndependentPath(rootPath.relativize(fullPath));
        this.fullPath = fullPath;
        this.input = input;
    }

    @Override
    public String name() {
        return fullPath.getFileName().toString();
    }

    @Override
    public long getSize() throws IOException {
        return Files.size(fullPath);
    }

    @NonNull
    @Override
    public String getRelativePath() {
        return relativePath;
    }

    @NonNull
    @Override
    public ClassFileInput getInput() {
        return input;
    }

    @Override
    public byte[] readAllBytes() throws IOException {
        return Files.readAllBytes(fullPath);
    }

    @Override
    public int readAllBytes(byte[] bytes) throws IOException {
        try (SeekableByteChannel sbc = Files.newByteChannel(fullPath);
                InputStream in = Channels.newInputStream(sbc)) {
            long size = sbc.size();
            if (size > bytes.length) throw new OutOfMemoryError("Required array size too large");

            return in.read(bytes, 0, (int) size);
        }
    }
}
