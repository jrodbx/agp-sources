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
import com.android.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class JarClassFileInput implements ClassFileInput {

    /** If we are unable to read .class files from the input. */
    public static final class JarClassFileInputsException extends RuntimeException {

        public JarClassFileInputsException(@NonNull String s, @NonNull IOException e) {
            super(s, e);
        }
    }

    @NonNull private final Path rootPath;
    @Nullable private ZipFile jarFile;

    public JarClassFileInput(@NonNull Path rootPath) {
        this.rootPath = rootPath;
    }

    @Override
    public void close() throws IOException {
        if (jarFile != null) {
            jarFile.close();
        }
    }

    @Override
    @NonNull
    public Stream<ClassFileEntry> entries(BiPredicate<Path, String> filter) {
        if (jarFile == null) {
            try {
                jarFile = new ZipFile(rootPath.toFile());
            } catch (IOException e) {
                throw new JarClassFileInputsException(
                        "Unable to read jar file " + rootPath.toString(), e);
            }
        }

        List<ZipEntry> entryList = new ArrayList<>(jarFile.size());
        Enumeration<? extends ZipEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry zipEntry = entries.nextElement();
            if (CLASS_MATCHER.test(zipEntry.getName())
                    && filter.test(rootPath, zipEntry.getName())) {
                entryList.add(zipEntry);
            }
        }

        return entryList.stream().map(this::createEntryFromEntry);
    }

    @Override
    public Path getPath() {
        return rootPath;
    }

    @NonNull
    private ClassFileEntry createEntryFromEntry(@NonNull ZipEntry entry) {
        return new NoCacheJarClassFileEntry(entry, Objects.requireNonNull(jarFile), this);
    }
}
