/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.repository.io.impl;

import com.android.annotations.NonNull;
import com.android.repository.io.FileOpUtils;

import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;

/**
 * Wraps some common {@link File} operations on files and folders.
 * <p>
 * This makes it possible to override/mock/stub some file operations in unit tests.
 * <p>
 * Instances should be obtained through {@link FileOpUtils#create()}
 */
public class FileOpImpl extends FileSystemFileOp {

    @Override
    public FileSystem getFileSystem() {
        return FileSystems.getDefault();
    }

    @Override
    public File ensureRealFile(@NonNull File in) {
        return in;
    }

    @Override
    public void deleteOnExit(File file) {
        file.deleteOnExit();
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FileOpImpl;
    }
}
