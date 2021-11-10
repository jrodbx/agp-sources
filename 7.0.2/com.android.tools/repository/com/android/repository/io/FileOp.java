/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.repository.io;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.io.CancellableFileIo;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
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

    /**
     * Sets the executable Unix permission (+x) on a file or folder.
     *
     * <p>Caller must make sure to not invoke this under Windows.
     *
     * @param file The file to set permissions on.
     * @throws IOException If an I/O error occurs
     */
    public final void setExecutablePermission(@NonNull File file) throws IOException {
        Path path = toPath(file);
        Set<PosixFilePermission> permissions = EnumSet.copyOf(Files.getPosixFilePermissions(path));
        permissions.add(PosixFilePermission.OWNER_EXECUTE);
        permissions.add(PosixFilePermission.GROUP_EXECUTE);
        permissions.add(PosixFilePermission.OTHERS_EXECUTE);
        Files.setPosixFilePermissions(path, permissions);
    }

    /**
     * Sets the file or directory as read-only.
     *
     * @param file The file or directory to set permissions on.
     */
    public final void setReadOnly(@NonNull File file) throws IOException {
        Path path = toPath(file);
        if (FileOpUtils.isWindows()) {
            Files.getFileAttributeView(path, DosFileAttributeView.class).setReadOnly(true);
        } else {
            Set<PosixFilePermission> permissions =
                    EnumSet.copyOf(Files.getPosixFilePermissions(path));
            permissions.remove(PosixFilePermission.OWNER_WRITE);
            permissions.remove(PosixFilePermission.GROUP_WRITE);
            permissions.remove(PosixFilePermission.OTHERS_WRITE);
            Files.setPosixFilePermissions(path, permissions);
        }
    }

    // TODO: make this final so we can migrate from FileOp to using Paths directly
    public void copyFile(@NonNull File source, @NonNull File dest) throws IOException {
        Files.copy(toPath(source), toPath(dest));
    }

    /**
     * Checks whether 2 binary files are the same.
     *
     * @param file1 the source file to copy
     * @param file2 the destination file to write
     * @throws IOException if there's a problem reading the files.
     */
    public final boolean isSameFile(@NonNull File file1, @NonNull File file2) throws IOException {
        return CancellableFileIo.isSameFile(toPath(file1), toPath(file2));
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

    /**
     * Invokes {@link CancellableFileIo#isWritable(Path)}on the given {@code file}.
     *
     * <p>TODO: make this final so we can migrate from FileOp to using Paths directly
     */
    public boolean canWrite(@NonNull File file) {
        return CancellableFileIo.isWritable(toPath(file));
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
     * Uses {@link Files#move(Path, Path, CopyOption...)} to rename {@code oldFile} to {
     *
     * @code newFile}.
     *     <p>TODO: make this final so we can migrate from FileOp to using Paths directly
     */
    public boolean renameTo(@NonNull File oldFile, @NonNull File newFile) {
        try {
            Files.move(toPath(oldFile), toPath(newFile));
            return true;
        } catch (IOException e) {
            return false;
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

    /** Creates a new file. See {@link Files#createFile(Path, FileAttribute[])}. */
    public final boolean createNewFile(@NonNull File file) throws IOException {
        try {
            Path path = toPath(file);
            Files.createDirectories(path.getParent());
            Files.createFile(path);
            return true;
        } catch (FileAlreadyExistsException e) {
            return false;
        }
    }

    /** @see Files#isExecutable(Path) */
    public final boolean canExecute(@NonNull File file) {
        return CancellableFileIo.isExecutable(toPath(file));
    }

    /**
     * If {@code in} is an in-memory file, write it out as a proper file and return it. Otherwise
     * just return {@code in}.
     */
    public abstract File ensureRealFile(@NonNull File in) throws IOException;

    /** @see CancellableFileIo#readString(Path). */
    @NonNull
    public final String readText(@NonNull File f) throws IOException {
        return CancellableFileIo.readString(toPath(f));
    }

    /** @see File#list(FilenameFilter) */
    public final String[] list(@NonNull File folder, @Nullable FilenameFilter filenameFilter) {
        File[] contents = listFiles(folder);
        String[] names = new String[contents.length];
        for (int i = 0; i < contents.length; i++) {
            names[i] = contents[i].getName();
        }
        if (filenameFilter == null) {
            return names;
        }
        List<String> result = new ArrayList<>();
        for (String name : names) {
            if (filenameFilter.accept(folder, name)) {
                result.add(name);
            }
        }
        return result.toArray(new String[0]);
    }

    /** @see File#listFiles(FilenameFilter) */
    public final File[] listFiles(@NonNull File folder, @Nullable FilenameFilter filenameFilter) {
        File[] contents = listFiles(folder);
        if (filenameFilter == null) {
            return contents;
        }
        List<File> result = new ArrayList<>();
        for (File f : contents) {
            if (filenameFilter.accept(folder, f.getName())) {
                result.add(f);
            }
        }
        return result.toArray(new File[0]);
    }

    /**
     * @see File#deleteOnExit()
     * @deprecated The application may not exit for a very long time. Prefer explicit cleanup.
     */
    @Deprecated
    public abstract void deleteOnExit(File file);

    /**
     * @see Files#setLastModifiedTime(Path, FileTime)
     * @throws IOException if there is an error setting the modification time.
     */
    public final boolean setLastModified(@NonNull File file, long time) throws IOException {
        return setLastModified(toPath(file), time);
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

    /**
     * Temporary functionality to help with File-to-Path migration. Should only be called when a
     * FileOp is not available and with a Path that is backed by the default FileSystem (notably not
     * in the context of any tests that use MockFileOp or jimfs).
     *
     * @throws UnsupportedOperationException if the Path is backed by a non-default FileSystem.
     */
    @NonNull
    public abstract File toFile(@NonNull Path path);
}
