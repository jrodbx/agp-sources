/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.sdklib.repository.legacy;

import com.android.annotations.NonNull;
import com.android.io.CancellableFileIo;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.util.stream.Stream;

/**
 * Wraps some common {@link File} operations on files and folders.
 *
 * <p>This makes it possible to override/mock/stub some file operations in unit tests. Uses {@link
 * CancellableFileIo} to check for cancellation before read I/O operations.
 *
 * @deprecated Use {@link Path}s, {@link CancellableFileIo} and (for testing) {@code
 *     InMemoryFileSystems} directly.
 */
public abstract class FileOp {
    /** Returns the {@link FileSystem} this is based on. */
    public abstract FileSystem getFileSystem();

    /**
     * Helper to delete a file or a directory. For a directory, recursively deletes all of its
     * content. Files that cannot be deleted right away are marked for deletion on exit. It's ok for
     * the file or folder to not exist at all.
     */
    public final void deleteFileOrFolder(@NonNull File fileOrFolder) {
        if (isDirectory(fileOrFolder)) {
            // Must delete content recursively first
            for (File item : listFiles(fileOrFolder)) {
                deleteFileOrFolder(item);
            }
        }
        delete(fileOrFolder);
    }

    /** Invokes {@link CancellableFileIo#exists(Path, LinkOption...)} on the given {@code file}. */
    public final boolean exists(@NonNull File file) {
        return CancellableFileIo.exists(toPath(file));
    }

    /**
     * Invokes {@link CancellableFileIo#isRegularFile(Path, LinkOption...)} on the given {@code
     * file}.
     */
    public final boolean isFile(@NonNull File file) {
        return CancellableFileIo.isRegularFile(toPath(file));
    }

    /**
     * Invokes {@link CancellableFileIo#isDirectory(Path, LinkOption...)} (Path, LinkOption...)} on
     * the given {@code file}.
     */
    public final boolean isDirectory(@NonNull File file) {
        return CancellableFileIo.isDirectory(toPath(file));
    }

    /** Invokes {@link CancellableFileIo#size(Path)} on the given {@code file}. */
    public final long length(@NonNull File file) throws IOException {
        return CancellableFileIo.size(toPath(file));
    }

    /**
     * Invokes {@link Files#delete(Path)} on the given {@code file}. Note: for a recursive folder
     * version, consider {@link #deleteFileOrFolder(File)}.
     *
     * <p>TODO: make this final so we can migrate from FileOp to using Paths directly
     */
    public boolean delete(@NonNull File file) {
        try {
            Files.delete(toPath(file));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Invokes {@link Files#createDirectories(Path, FileAttribute[])} on the given {@code file}. */
    public final boolean mkdirs(@NonNull File file) {
        try {
            Files.createDirectories(toPath(file));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Invokes {@link CancellableFileIo#list(Path)} on the given {@code file}. Contrary to the Java
     * API, this returns an empty array instead of null when the directory does not exist.
     */
    @NonNull
    public final File[] listFiles(@NonNull File file) {
        try (Stream<Path> children = CancellableFileIo.list(toPath(file))) {
            return children.map(path -> new File(path.toString())).toArray(File[]::new);
        } catch (IOException e) {
            return new File[0];
        }
    }

    /**
     * Creates a new {@link OutputStream} for the given {@code file}.
     *
     * <p>TODO: make this final so we can migrate from FileOp to using Paths directly
     */
    @NonNull
    public OutputStream newFileOutputStream(@NonNull File file) throws IOException {
        return newFileOutputStream(file, false);
    }

    /**
     * Creates a new {@link OutputStream} for the given {@code file}, either truncating an existing
     * file or appending to it.
     *
     * <p>TODO: make this final so we can migrate from FileOp to using Paths directly
     */
    @NonNull
    public OutputStream newFileOutputStream(@NonNull File file, boolean append) throws IOException {
        if (append) {
            return Files.newOutputStream(
                    toPath(file),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND);
        } else {
            return Files.newOutputStream(
                    toPath(file),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    /** Creates a new {@link InputStream} for the given {@code file}. */
    @NonNull
    public final InputStream newFileInputStream(@NonNull File file) throws IOException {
        return newFileInputStream(toPath(file));
    }

    /** Creates a new {@link InputStream} for the given {@code path}. */
    @NonNull
    public final InputStream newFileInputStream(@NonNull Path path) throws IOException {
        return CancellableFileIo.newInputStream(path);
    }

    /**
     * Returns the lastModified attribute of the file.
     *
     * @see Files#getLastModifiedTime(Path, LinkOption...)
     * @param file The non-null file of which to retrieve the lastModified attribute.
     * @return The last-modified attribute of the file, in milliseconds since The Epoch.
     */
    public final long lastModified(@NonNull File file) {
        try {
            return CancellableFileIo.getLastModifiedTime(toPath(file)).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    /** @see CancellableFileIo#readString(Path). */
    @NonNull
    public final String readText(@NonNull File f) throws IOException {
        return CancellableFileIo.readString(toPath(f));
    }

    /**
     * @see Files#setLastModifiedTime(Path, FileTime)
     * @throws IOException if there is an error setting the modification time.
     */
    public final boolean setLastModified(@NonNull Path file, long time) throws IOException {
        Files.setLastModifiedTime(file, FileTime.fromMillis(time));
        return true;
    }

    /**
     * Convert the given {@code File} into a {@code Path}, using some means appropriate to this
     * {@code FileOp}.
     *
     * <p>TODO: make final once tests can work on windows without special-casing.
     */
    @NonNull
    public Path toPath(@NonNull File file) {
        return toPath(file.getPath());
    }

    /**
     * Convert the given {@code String} into a {@code Path}, using some means appropriate to this
     * {@code FileOp}.
     */
    @NonNull
    public Path toPath(@NonNull String path) {
        return getFileSystem().getPath(path);
    }
}
