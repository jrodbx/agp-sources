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

package com.android.repository.io;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.io.CancellableFileIo;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.io.impl.FileOpImpl;
import com.android.utils.PathUtils;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Some convenience methods for working with {@link File}s/{@link FileOp}s.
 */
public final class FileOpUtils {
    private static final boolean IS_WINDOWS = System.getProperty("os.name").startsWith("Windows");

    /**
     * The standard way to create a {@link FileOp} that interacts with the real filesystem.
     *
     * @deprecated Use {@link Path}s, {@link CancellableFileIo} and (for testing) {@code
     *     InMemoryFileSystems} directly.
     */
    @NonNull
    public static FileOp create() {
        return new FileOpImpl();
    }

    /**
     * Create a {@link FileOp} that supports the provided {@code path}. This should be used where
     * code using only {@link Path}s directly has to interact with code that still uses {@link
     * FileOp}.
     *
     * @deprecated Use {@link Path}s, {@link CancellableFileIo} and (for testing) {@code
     *     InMemoryFileSystems} directly.
     */
    @NonNull
    public static FileOp create(@Nullable Path path) {
        if (path != null) {
            try {
                FileSystem desiredFs = path.getFileSystem();
                if (desiredFs != FileSystems.getDefault()) {
                    Class<?> mockFileOp =
                            FileOp.class
                                    .getClassLoader()
                                    .loadClass("com.android.repository.testframework.MockFileOp");
                    return (FileOp)
                            mockFileOp.getConstructor(FileSystem.class).newInstance(desiredFs);
                }
            } catch (Exception ignore) {
                // We'll just return the default
            }
        }
        return new FileOpImpl();
    }

    /**
     * Copies a file or directory tree to the given location. {@code dest} should not exist: with
     * the file system currently looking like
     *
     * <pre>{@code
     * /
     *   dir1/
     *     a.txt
     *   dir2/
     *
     * }</pre>
     *
     * Running {@code recursiveCopy(new File("/dir1"), new File("/dir2"), fOp)} will result in an
     * exception, while {@code recursiveCopy(new File("/dir1"), new File("/dir2/foo")} will result
     * in
     *
     * <pre>{@code
     * /
     *   dir1/
     *     a.txt
     *   dir2/
     *     foo/
     *       a.txt
     *
     * }</pre>
     *
     * This is equivalent to the behavior of {@code cp -r} when the target does not exist.
     *
     * @param src File to copy
     * @param dest Destination.
     * @param fop The FileOp to use for file operations.
     * @throws IOException If the destination already exists, or if there is a problem copying the
     *     files or creating directories.
     * @deprecated Use {@link #recursiveCopy(Path, Path, boolean, ProgressIndicator)}.
     */
    public static void recursiveCopy(
            @NonNull File src,
            @NonNull File dest,
            @NonNull FileOp fop,
            @NonNull ProgressIndicator progress)
            throws IOException {
        recursiveCopy(src, dest, false, fop, progress);
    }

    @VisibleForTesting
    static void recursiveCopy(@NonNull File src, @NonNull File dest, boolean merge,
            @NonNull FileOp fop, @NonNull ProgressIndicator progress) throws IOException {
        recursiveCopy(fop.toPath(src), fop.toPath(dest), merge, progress);
    }

    @VisibleForTesting
    public static void recursiveCopy(
            @NonNull Path src,
            @NonNull Path dest,
            boolean merge,
            @NonNull ProgressIndicator progress)
            throws IOException {
        if (CancellableFileIo.exists(dest)) {
            if (!merge) {
                throw new IOException(dest + " already exists!");
            } else if (CancellableFileIo.isDirectory(src) != CancellableFileIo.isDirectory(dest)) {
                throw new IOException(
                        String.format(
                                "%s already exists but %s a directory!",
                                dest, CancellableFileIo.isDirectory(dest) ? "is" : "is not"));
            }
        }
        if (progress.isCanceled()) {
            throw new IOException("Operation cancelled");
        }
        if (CancellableFileIo.isDirectory(src)) {
            Files.createDirectories(dest);

            for (Path child : FileOpUtils.listFiles(src)) {
                Path newDest = dest.resolve(child.getFileName());
                recursiveCopy(child, newDest, merge, progress);
            }
        } else if (CancellableFileIo.isRegularFile(src) && !CancellableFileIo.exists(dest)) {
            Files.copy(src, dest);
            if (!isWindows() && CancellableFileIo.isExecutable(src)) {
                FileOpUtils.setExecutablePermission(dest);
            }
        }
    }

    /**
     * Invokes {@link CancellableFileIo#list(Path)} on the given {@code file}. Contrary to the Java
     * API, this returns an empty array instead of null when the directory does not exist.
     */
    @NonNull
    public static Path[] listFiles(@NonNull Path file) {
        try (Stream<Path> children = CancellableFileIo.list(file)) {
            return children.toArray(Path[]::new);
        } catch (IOException e) {
            return new Path[0];
        }
    }

    /**
     * Moves a file or directory from one location to another. If the destination exists, it is
     * moved away, and once the operation has completed successfully, deleted. If there is a problem
     * during the copy, the original files are moved back into place.
     *
     * @param src File to move
     * @param dest Destination. Follows the same rules as {@link #recursiveCopy(File, File, FileOp,
     *     ProgressIndicator)}}.
     * @param progress Currently only used for error logging.
     * @throws IOException If some problem occurs during copies or directory creation.
     */
    public static void safeRecursiveOverwrite(
            @NonNull Path src, @NonNull Path dest, @NonNull ProgressIndicator progress)
            throws IOException {
        Path destBackup = getTempDir(dest, "backup");
        boolean success = false;
        try {
            if (CancellableFileIo.exists(dest)) {
                moveOrCopyAndDelete(dest, destBackup, progress);
                if (CancellableFileIo.exists(dest)) {
                    throw new IOException(String.format(
                      "Failed to move away or delete existing target file: %s%n" +
                      "Move it away manually and try again.", dest));
                }
                success = true;
            }
        } finally {
            if (!success && CancellableFileIo.exists(destBackup)) {
                try {
                    // Merge in case some things had been moved and some not.
                    recursiveCopy(destBackup, dest, true, progress);
                    FileOpUtils.deleteFileOrFolder(destBackup);
                }
                catch (IOException e) {
                    // we're already throwing the exception from the original "try", and there's
                    // nothing more to be done here. Just log it.
                    progress.logWarning(String.format(
                            "Failed to move original content of %s back into place! "
                                    + "It should be available at %s", dest, destBackup), e);
                }
            }
            // If the backup doesn't exist we failed before doing anything at all, so there's no
            // cleanup needed.
        }

        success = false;
        // At this point the target should be moved away.
        try {
            // Actually move src to dest.
            moveOrCopyAndDelete(src, dest, progress);
            success = true;
        } finally {
            if (!success) {
                // It failed. Now we have to restore the backup.
                FileOpUtils.deleteFileOrFolder(dest);
                if (CancellableFileIo.exists(dest)) {
                    // Failed to delete the new stuff. Move it away and delete later.
                    Path toDelete = getTempDir(dest, "delete");
                    Files.move(dest, toDelete);
                    PathUtils.addRemovePathHook(toDelete);
                }
                try {
                    if (CancellableFileIo.exists(dest)) {
                        // Couldn't get rid of the new, partial stuff. Try merging the old ones back
                        // over.
                        recursiveCopy(destBackup, dest, true, progress);
                    } else {
                        // dest is cleared. Move temp stuff back into place
                        moveOrCopyAndDelete(destBackup, dest, progress);
                    }
                } catch (IOException e) {
                    // we're already throwing the exception from the original "try", and there's
                    // nothing more to be done here. Just log it.
                    progress.logWarning(String.format(
                            "Failed to move original content of %s back into place! "
                                    + "It should be available at %s", dest, destBackup), e);
                }
            }
        }

        // done, delete the backup
        deleteFileOrFolder(destBackup);
    }

    private static void moveOrCopyAndDelete(
            @NonNull Path src, @NonNull Path dest, ProgressIndicator progress) throws IOException {
        try {
            Files.move(src, dest);
        } catch (IOException ignore) {
            // Failed to move. Try copy/delete, with merge in case something already got moved.
            recursiveCopy(src, dest, true, progress);
            if (!FileOpUtils.deleteFileOrFolder(src)) {
                throw new IOException("Failed to delete" + src);
            }
        }
    }

    private static Path getTempDir(Path orig, String suffix) {
        Path result = orig.getFileSystem().getPath(orig + "." + suffix);
        int i = 1;
        while (CancellableFileIo.exists(result)) {
            // The dir is already there. Try to delete it.
            deleteFileOrFolder(result);
            if (!CancellableFileIo.exists(result)) {
                break;
            }
            // We couldn't delete it. Make a new dir.
            result = orig.getFileSystem().getPath(result + "-" + i++);
        }
        return result;
    }

    /**
     * Creates a new subdirectory of the system temp directory. The directory will be named {@code
     * <base> + NN}, where NN makes the directory distinct from any existing directories.
     *
     * @deprecated Use {@link #getNewTempDir(String, FileSystem)}.
     */
    @Nullable
    public static Path getNewTempDir(@NonNull String base, @NonNull FileOp fileOp) {
        return getNewTempDir(base, fileOp.getFileSystem());
    }

    @Nullable
    public static Path getNewTempDir(@NonNull String base, @NonNull FileSystem fileSystem) {
        for (int i = 1; i < 100; i++) {
            Path rootTempDir = fileSystem.getPath(System.getProperty("java.io.tmpdir"));
            Path folder = rootTempDir.resolve(String.format(Locale.US, "%1$s%2$02d", base, i));
            if (CancellableFileIo.notExists(folder)) {
                try {
                    Files.createDirectories(folder);
                    return folder;
                } catch (IOException e) {
                    // keep trying
                }
            }
        }
        return null;
    }

    /**
     * Appends the given {@code segments} to the {@code base} file.
     *
     * @param base     A base file, non-null.
     * @param segments Individual folder or filename segments to append to the base file.
     * @return A new file representing the concatenation of the base path with all the segments.
     */
    @NonNull
    public static File append(@NonNull File base, @NonNull String... segments) {
        for (String segment : segments) {
            base = new File(base, segment);
        }
        return base;
    }

    /**
     * Appends the given {@code segments} to the {@code base} file.
     *
     * @param base     A base file path, non-empty and non-null.
     * @param segments Individual folder or filename segments to append to the base path.
     * @return A new file representing the concatenation of the base path with all the segments.
     */
    @NonNull
    public static File append(@NonNull String base, @NonNull String... segments) {
        return append(new File(base), segments);
    }

    /**
     * Computes a relative path from "toBeRelative" relative to "baseDir".
     *
     * <p>Rule: - let relative2 = makeRelative(path1, path2) - then pathJoin(path1 + relative2) ==
     * path2 after canonicalization.
     *
     * <p>Principle: - let base = /c1/c2.../cN/a1/a2../aN - let toBeRelative =
     * /c1/c2.../cN/b1/b2../bN - result is removes the common paths, goes back from aN to cN then to
     * bN: - result = ../..../../1/b2../bN
     *
     * @param baseDir The base directory to be relative to.
     * @param toBeRelative The file or directory to make relative to the base.
     * @return A path that makes toBeRelative relative to baseDir.
     * @throws IOException If drive letters don't match on Windows or path canonicalization fails.
     */
    @NonNull
    public static String makeRelative(@NonNull File baseDir, @NonNull File toBeRelative)
            throws IOException {
        return makeRelativeImpl(
                baseDir.getCanonicalPath(),
                toBeRelative.getCanonicalPath(),
                File.separator);
    }

    /**
     * Implementation detail of makeRelative to make it testable Independently of the platform.
     */
    @VisibleForTesting
    @NonNull
    static String makeRelativeImpl(@NonNull String path1,
            @NonNull String path2,
            @NonNull String dirSeparator)
            throws IOException {
        if (isWindows()) {
            // Check whether both path are on the same drive letter, if any.
            char drive1 = (path1.length() >= 2 && path1.charAt(1) == ':') ? path1.charAt(0) : 0;
            char drive2 = (path2.length() >= 2 && path2.charAt(1) == ':') ? path2.charAt(0) : 0;
            if (drive1 != drive2) {
                // Either a mix of UNC vs drive or not the same drives.
                throw new IOException("makeRelative: incompatible drive letters");
            }
        }

        String[] segments1 = path1.split(Pattern.quote(dirSeparator));
        String[] segments2 = path2.split(Pattern.quote(dirSeparator));

        int len1 = segments1.length;
        int len2 = segments2.length;
        int len = Math.min(len1, len2);
        int start = 0;
        for (; start < len; start++) {
            // On Windows should compare in case-insensitive.
            // Mac and Linux file systems can be both type, although their default
            // is generally to have a case-sensitive file system.
            if ((isWindows() && !segments1[start].equalsIgnoreCase(segments2[start]))
                    || (!isWindows() && !segments1[start].equals(segments2[start]))) {
                break;
            }
        }

        StringBuilder result = new StringBuilder();
        for (int i = start; i < len1; i++) {
            result.append("..").append(dirSeparator);
        }
        while (start < len2) {
            result.append(segments2[start]);
            if (++start < len2) {
                result.append(dirSeparator);
            }
        }

        return result.toString();
    }

    /**
     * Deletes the given file if it exists. Does nothing and returns successfully if the file didn't
     * exist to begin with.
     *
     * @return true if the file no longer exists, false if we failed to delete it
     * @deprecated Use {@link Files#deleteIfExists(Path)}.
     */
    public static boolean deleteIfExists(File file, FileOp fop) {
        return !fop.exists(file) || fop.delete(file);
    }

    private FileOpUtils() {
    }

    public static boolean isWindows() {
        return IS_WINDOWS;
    }

    /**
     * Helper to delete a file or a directory. For a directory, recursively deletes all of its
     * content. It's ok for the file or folder to not exist at all.
     *
     * @return true if the delete was successful
     */
    public static boolean deleteFileOrFolder(@NonNull Path fileOrFolder) {
        boolean[] sawException = new boolean[1];
        try {
            CancellableFileIo.walkFileTree(
                    fileOrFolder,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            try {
                                Files.delete(file);
                            } catch (IOException ignore) {
                                sawException[0] = true;
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exc) {
                            sawException[0] = true;
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                            try {
                                Files.delete(dir);
                            } catch (IOException ignore) {
                                sawException[0] = true;
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException e) {
            return false;
        }
        return !sawException[0];
    }

    /**
     * Temporary functionality to help with File-to-Path migration. Should only be called when a
     * FileOp is not available and with a Path that is backed by the default FileSystem (notably not
     * in the context of any tests that use MockFileOp or jimfs).
     *
     * @throws UnsupportedOperationException if the Path is backed by a non-default FileSystem.
     */
    @NonNull
    public static File toFileUnsafe(@NonNull Path path) {
        return path.toFile();
    }

    public static void setExecutablePermission(@NonNull Path path) throws IOException {
        Set<PosixFilePermission> permissions = EnumSet.copyOf(Files.getPosixFilePermissions(path));
        permissions.add(PosixFilePermission.OWNER_EXECUTE);
        permissions.add(PosixFilePermission.GROUP_EXECUTE);
        permissions.add(PosixFilePermission.OTHERS_EXECUTE);
        Files.setPosixFilePermissions(path, permissions);
    }

    /**
     * Convenience method to allow Path-based code to interop with legacy File-based code.
     *
     * <p>Note that the resulting File may not refer to an actual file on the default FileSystem.
     */
    public static @NonNull File toFile(@NonNull Path path) {
        if (path.getFileSystem() == FileSystems.getDefault()) {
            return path.toFile();
        }
        return new File(path.toString());
    }
}
