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
import com.google.common.io.ByteStreams;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class NoCacheJarClassFileEntry implements ClassFileEntry {

    @NonNull private final ZipEntry entry;
    @NonNull private final ZipFile zipFile;
    @NonNull private final ClassFileInput input;

    public NoCacheJarClassFileEntry(
            @NonNull ZipEntry entry, @NonNull ZipFile zipFile, @NonNull ClassFileInput input) {
        this.entry = entry;
        this.zipFile = zipFile;
        this.input = input;
    }

    @Override
    public String name() {
        return "Zip:" + entry.getName();
    }

    @Override
    public long getSize() {
        return entry.getSize();
    }

    @Override
    public String getRelativePath() {
        return entry.getName();
    }

    @NonNull
    @Override
    public ClassFileInput getInput() {
        return input;
    }

    @Override
    public byte[] readAllBytes() throws IOException {
        return ByteStreams.toByteArray(new BufferedInputStream(zipFile.getInputStream(entry)));
    }

    @Override
    public int readAllBytes(byte[] bytes) throws IOException {
        try (InputStream is = new BufferedInputStream(zipFile.getInputStream(entry))) {
            return ByteStreams.read(is, bytes, 0, bytes.length);
        }
    }
}
