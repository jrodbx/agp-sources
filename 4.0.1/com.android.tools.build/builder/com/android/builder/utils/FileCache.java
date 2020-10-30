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

package com.android.builder.utils;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.utils.FileUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.base.Verify;
import com.google.common.collect.Maps;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * A cache for already-created files/directories.
 *
 * <p>This class is used to avoid creating the same file/directory multiple times. The main API
 * method {@link #createFile(File, Inputs, ExceptionRunnable)} creates an output file/directory by
 * either copying it from the cache, or creating it first and caching it if the cached
 * file/directory does not yet exist. The other API method, {@link
 * #createFileInCacheIfAbsent(Inputs, ExceptionConsumer)}, returns the cached output file/directory
 * directly, and creates it first if the cached file/directory does not yet exist.
 *
 * <p>Access to the cache is synchronized. Synchronization can take effect for threads within the
 * same process or across different processes. The client can configure this locking scope when
 * constructing a {@code FileCache}. If the cache is never accessed by more than one process at a
 * time, the client should configure the cache with {@code SINGLE_PROCESS} locking scope since there
 * will be less synchronization overhead. However, if the cache may be accessed by more than one
 * process at a time, the client must configure the cache with {@code MULTI_PROCESS} locking scope.
 *
 * <p>In any case, synchronization takes effect only for the same cache (i.e., threads/processes
 * accessing different caches are not synchronized). Also, the client must access the cache via
 * {@code FileCache}'s API; otherwise, the previous concurrency guarantees will not hold.
 *
 * <p>Two caches are considered the same if they refer to the same physical cache directory. There
 * could be multiple instances of {@code FileCache} for the same physical cache directory, and as
 * long as they refer to the same physical cache directory, access to them will be synchronized.
 * (The client does not need to normalize the cache directory's path when constructing a {@code
 * FileCache}.)
 *
 * <p>Multiple threads/processes can read the same cache entry at the same time. However, once a
 * thread/process starts writing to the cache entry, the other threads/processes will block. This
 * behavior is similar to a {@link java.util.concurrent.locks.ReadWriteLock}.
 *
 * <p>Note that we often use the term "process", although the term "JVM" would be more correct since
 * there could exist multiple JVMs in a process.
 *
 * <p>If a cache entry exists but is found to be corrupted, the cache entry will be deleted and
 * recreated.
 *
 * <p>This class is thread-safe.
 */
@Immutable
public class FileCache {

    /** The scope of the locking facility. */
    private enum LockingScope {

        /**
         * Synchronization takes effect for threads both within the same process and across
         * different processes.
         */
        MULTI_PROCESS,

        /**
         * Synchronization takes effect for threads within the same process but not for threads
         * across different processes.
         */
        SINGLE_PROCESS
    }

    @NonNull private final File cacheDirectory;

    @NonNull private final LockingScope lockingScope;

    // Additional fields used for testing only
    @NonNull private final AtomicInteger missCount = new AtomicInteger(0);
    @NonNull private final AtomicInteger hitCount = new AtomicInteger(0);

    private FileCache(@NonNull File cacheDirectory, @NonNull LockingScope lockingScope) {
        this.cacheDirectory = cacheDirectory;
        this.lockingScope = lockingScope;
    }

    /**
     * Returns a {@code FileCache} instance where synchronization takes effect for threads both
     * within the same process and across different processes.
     *
     * <p>Inter-process synchronization is provided via {@link SynchronizedFile}, which requires
     * lock files to be created. One lock file will be placed next to the cache directory and the
     * other lock files will be placed next to the cache entry directories (inside the cache
     * directory). Note that currently it is not possible for the underlying locking mechanism to
     * delete these lock files. The lock files should only be deleted together with the entire cache
     * directory.
     *
     * <p>The given cache directory may or may not already exist. If it does not yet exist, this
     * method will not create the cache directory here (the cache directory will be created when the
     * cache is actually used).
     *
     * <p>Note: If the cache is never accessed by more than one process at a time, the client should
     * use the {@link #getInstanceWithSingleProcessLocking(File)} method instead since there will be
     * less synchronization overhead.
     *
     * @param cacheDirectory the cache directory, which may not yet exist
     * @see #getInstanceWithSingleProcessLocking(File)
     */
    @NonNull
    public static FileCache getInstanceWithMultiProcessLocking(@NonNull File cacheDirectory) {
        return new FileCache(cacheDirectory, LockingScope.MULTI_PROCESS);
    }

    /**
     * Returns a {@code FileCache} instance where synchronization takes effect for threads within
     * the same process but not for threads across different processes.
     *
     * <p>The given cache directory may or may not already exist. If it does not yet exist, this
     * method will not create the cache directory here (the cache directory will be created when the
     * cache is actually used).
     *
     * <p>Note: If the cache may be accessed by more than one process at a time, the client must use
     * the {@link #getInstanceWithMultiProcessLocking(File)} method instead.
     *
     * @param cacheDirectory the cache directory, which may not yet exist
     * @see #getInstanceWithMultiProcessLocking(File)
     */
    @NonNull
    public static FileCache getInstanceWithSingleProcessLocking(@NonNull File cacheDirectory) {
        return new FileCache(cacheDirectory, LockingScope.SINGLE_PROCESS);
    }

    @NonNull
    public File getCacheDirectory() {
        return cacheDirectory;
    }

    /**
     * Creates an output file/directory by either copying it from the cache, or creating it first
     * via the given file creator callback function and caching it if the cached file/directory does
     * not yet exist.
     *
     * <p>The output file/directory must not reside in, contain, or be identical to the cache
     * directory.
     *
     * <p>To determine whether to reuse a cached file/directory or create a new file/directory, the
     * client needs to provide all the inputs that affect the creation of the output file/directory
     * to the {@link Inputs} object.
     *
     * <p>If this method is called multiple times on the same list of inputs, on the first call, the
     * cache will delete the output file/directory (if it exists), create its parent directories,
     * invoke the file creator to create the output file/directory, and copy the output
     * file/directory to the cache. On subsequent calls, the cache will create/replace the output
     * file/directory with the cached result without invoking the file creator.
     *
     * <p>Note that the file creator is not required to always create an output. If the file creator
     * does not create an output on the first call, the cache will remember this result and produce
     * no output on subsequent calls (it will still delete the output file/directory if it exists
     * and creates its parent directory on both the first and subsequent calls).
     *
     * <p>Depending on whether there are other threads/processes concurrently accessing this cache
     * and the type of locking scope configured for this cache, this method may block until it is
     * allowed to continue.
     *
     * <p>NOTE ON THREAD SAFETY: The cache is responsible for synchronizing access within the cache
     * directory only. When the clients of the cache use this method to create an output
     * file/directory (which must be outside the cache directory), they need to make sure that
     * multiple threads/processes do not create the same output file/directory, or they need to have
     * a way to manage that situation.
     *
     * @param outputFile the output file/directory
     * @param inputs all the inputs that affect the creation of the output file/directory
     * @param fileCreator the callback function to create the output file/directory
     * @return the result of this query (which does not include the path to the cached output
     *     file/directory)
     * @throws ExecutionException if an exception occurred during the execution of the file creator
     * @throws IOException if an I/O exception occurred, but not during the execution of the file
     *     creator (or the file creator was not executed)
     * @throws RuntimeException if a runtime exception occurred, but not during the execution of the
     *     file creator (or the file creator was not executed)
     */
    @NonNull
    public QueryResult createFile(
            @NonNull File outputFile,
            @NonNull Inputs inputs,
            @NonNull ExceptionRunnable fileCreator)
            throws ExecutionException, IOException {
        Preconditions.checkArgument(
                !FileUtils.isFileInDirectory(outputFile, cacheDirectory),
                String.format(
                        "Output file/directory '%1$s' must not be located"
                                + " in the cache directory '%2$s'",
                        outputFile.getAbsolutePath(), cacheDirectory.getAbsolutePath()));
        Preconditions.checkArgument(
                !FileUtils.isFileInDirectory(cacheDirectory, outputFile),
                String.format(
                        "Output directory '%1$s' must not contain the cache directory '%2$s'",
                        outputFile.getAbsolutePath(), cacheDirectory.getAbsolutePath()));
        Preconditions.checkArgument(
                !FileUtils.isSameFile(outputFile, cacheDirectory),
                String.format(
                        "Output directory must not be the same as the cache directory '%1$s'",
                        cacheDirectory.getAbsolutePath()));

        File cacheEntryDir = getCacheEntryDir(inputs);
        File cachedFile = getCachedFile(cacheEntryDir);

        // If the cache is hit, copy the cached file to the output file. The cached file should have
        // been guarded with a READ or WRITE lock when this callable is invoked (see method
        // queryCacheEntry). Note that as stated in the contract of this method, we don't guard the
        // output file with a WRITE lock.
        Callable<Void> actionIfCacheHit =
                () -> {
                    // Delete the output file and create its parent directory according to the
                    // contract of this method
                    FileUtils.deletePath(outputFile);
                    Files.createParentDirs(outputFile);
                    // Only copy if the cached file exist as file creator may not have produced an
                    // output during the first time this cache is called on the given inputs
                    if (cachedFile.exists()) {
                        copyFileOrDirectory(cachedFile, outputFile);
                    }
                    return null;
                };

        // If the cache is missed or corrupted, create the output file and copy it to the cached
        // file. The cached file should have been guarded with a WRITE lock when this callable
        // is invoked (see method queryCacheEntry). We again don't guard the output file with a
        // WRITE lock.
        Callable<Void> actionIfCacheMissedOrCorrupted =
                () -> {
                    // Delete the output file and create its parent directory first according to the
                    // contract of this method
                    FileUtils.deletePath(outputFile);
                    Files.createParentDirs(outputFile);
                    try {
                        fileCreator.run();
                    } catch (Exception exception) {
                        throw new FileCreatorException(exception);
                    }

                    // Only copy if the output file exists as file creator is not required to always
                    // produce an output
                    if (outputFile.exists()) {
                        copyFileOrDirectory(outputFile, cachedFile);
                    }
                    return null;
                };

        return queryCacheEntry(
                inputs, cacheEntryDir, actionIfCacheHit, actionIfCacheMissedOrCorrupted);
    }

    /**
     * Creates the cached output file/directory via the given file creator callback function if it
     * does not yet exist. The returned result also contains the path to the cached file/directory.
     *
     * <p>To determine whether a cached file/directory exists, the client needs to provide all the
     * inputs that affect the creation of the output file/directory to the {@link Inputs} object.
     *
     * <p>If this method is called multiple times on the same list of inputs, the first call will
     * invoke the file creator to create the cached output file/directory (which is given as the
     * argument to the file creator callback function), and subsequent calls will return the cached
     * file/directory without invoking the file creator.
     *
     * <p>Note that the file creator is not required to always create an output. If the file creator
     * does not create an output on the first call, the cache will remember this result and return a
     * cached file that does not exist on subsequent calls.
     *
     * <p>Depending on whether there are other threads/processes concurrently accessing this cache
     * and the type of locking scope configured for this cache, this method may block until it is
     * allowed to continue.
     *
     * <p>Note that this method returns a cached file/directory that is located inside the cache
     * directory. To avoid corrupting the cache, the client must never write to the returned cached
     * file/directory (or any other files/directories inside the cache directory) without using the
     * cache's API. If the client wants to read the returned cached file/directory, it must ensure
     * that another thread (of the same or a different process) is not overwriting or deleting the
     * same cached file/directory at the same time.
     *
     * <p>WARNING: DO NOT use this method if the returned cached file/directory will be annotated
     * with Gradle's {@code @OutputFile} or {@code @OutputDirectory} annotations as it is undefined
     * behavior of Gradle incremental builds when multiple Gradle tasks have the same
     * output-annotated file/directory.
     *
     * @param inputs all the inputs that affect the creation of the output file/directory
     * @param fileCreator the callback function to create the output file/directory
     * @return the result of this query, which includes the path to the cached output file/directory
     * @throws ExecutionException if an exception occurred during the execution of the file creator
     * @throws IOException if an I/O exception occurred, but not during the execution of the file
     *     creator (or the file creator was not executed)
     * @throws RuntimeException if a runtime exception occurred, but not during the execution of the
     *     file creator (or the file creator was not executed)
     */
    @NonNull
    public QueryResult createFileInCacheIfAbsent(
            @NonNull Inputs inputs,
            @NonNull ExceptionConsumer<File> fileCreator)
            throws ExecutionException, IOException {
        File cacheEntryDir = getCacheEntryDir(inputs);
        File cachedFile = getCachedFile(cacheEntryDir);

        // If the cache is hit, do nothing. If the cache is missed or corrupted, create the cached
        // output file. The cached file should have been guarded with a WRITE lock when this
        // callable is invoked (see method queryCacheEntry).
        Callable<Void> actionIfCacheMissedOrCorrupted =
                () -> {
                    try {
                        fileCreator.accept(cachedFile);
                    } catch (Exception exception) {
                        throw new FileCreatorException(exception);
                    }
                    return null;
                };

        QueryResult queryResult =
                queryCacheEntry(inputs, cacheEntryDir, () -> null, actionIfCacheMissedOrCorrupted);
        return new QueryResult(
                queryResult.getQueryEvent(), queryResult.getCauseOfCorruption(), cachedFile);
    }

    /**
     * Queries the cache entry to see if it exists (and whether it is corrupted) and invokes the
     * respective provided actions.
     *
     * @param inputs all the inputs that affect the creation of the output file/directory
     * @param cacheEntryDir the cache entry directory
     * @param actionIfCacheHit the action to invoke if the cache entry exists and is not corrupted
     * @param actionIfCacheMissedOrCorrupted the action to invoke if the cache entry either does not
     *     exist or exists but is corrupted
     * @return the result of this query (which does not include the path to the cached output
     *     file/directory)
     * @throws ExecutionException if an exception occurred during the execution of the file creator
     *     (i.e., a {@link FileCreatorException} was thrown)
     * @throws IOException if an I/O exception occurred, but not during the execution of the file
     *     creator (or the file creator was not executed)
     * @throws RuntimeException if a runtime exception occurred, but not during the execution of the
     *     file creator (or the file creator was not executed)
     */
    @NonNull
    private QueryResult queryCacheEntry(
            @NonNull Inputs inputs,
            @NonNull File cacheEntryDir,
            @NonNull Callable<Void> actionIfCacheHit,
            @NonNull Callable<Void> actionIfCacheMissedOrCorrupted)
            throws ExecutionException, IOException {
        // The underlying facility for multi-process locking (SynchronizedFile) requires that the
        // parent directory of the file/directory being synchronized exist (see method
        // getSynchronizedFile), so we create the parent directory first (if it does not yet exist).
        // The following method call is thread-safe and process-safe.
        if (lockingScope == LockingScope.MULTI_PROCESS) {
            // We cannot create a parent directory if the cache directory is at root. We also don't
            // want the cache directory to be at root, so let's fail early.
            Preconditions.checkNotNull(
                    cacheDirectory.getCanonicalFile().getParentFile(),
                    "Cache directory must not be the root directory");
            FileUtils.mkdirs(cacheDirectory.getCanonicalFile().getParentFile());
        }

        // In this method, we use two levels of locking: A READ lock on the cache directory and a
        // READ or WRITE lock on the cache entry directory.
        try {
            // Guard the cache directory with a READ lock so that other threads/processes can read
            // or write to the cache at the same time but cannot delete the cache while it is being
            // read/written to. (Further locking within the cache will make sure multiple
            // threads/processes can read but cannot write to the same cache entry at the same
            // time.)
            return getSynchronizedFile(cacheDirectory).read(sameCacheDirectory -> {
                // Create (or recreate) the cache directory since it may not exist or might have
                // been deleted. The following method call is thread-safe and process-safe.
                FileUtils.mkdirs(cacheDirectory);

                // Guard the cache entry directory with a READ lock so that multiple
                // threads/processes can read it at the same time
                QueryResult queryResult = getSynchronizedFile(cacheEntryDir).read(
                        (sameCacheEntryDir) -> {
                            QueryResult result = checkCacheEntry(inputs, cacheEntryDir);
                            // If the cache entry is HIT, run the given action
                            if (result.getQueryEvent().equals(QueryEvent.HIT)) {
                                hitCount.incrementAndGet();
                                actionIfCacheHit.call();
                            }
                            return result;
                        });
                // If the cache entry is HIT, return immediately
                if (queryResult.getQueryEvent().equals(QueryEvent.HIT)) {
                    return queryResult;
                }

                // Guard the cache entry directory with a WRITE lock so that only one thread/process
                // can write to it
                return getSynchronizedFile(cacheEntryDir).write(sameCacheEntryDir -> {
                    // Check the cache entry again as it might have been changed by another
                    // thread/process since the last time we checked it.
                    QueryResult result = checkCacheEntry(inputs, cacheEntryDir);

                    // If the cache entry is HIT, run the given action and return immediately
                    if (result.getQueryEvent().equals(QueryEvent.HIT)) {
                        hitCount.incrementAndGet();
                        actionIfCacheHit.call();
                        return result;
                    }

                    // If the cache entry is CORRUPTED, delete the cache entry
                    if (result.getQueryEvent().equals(QueryEvent.CORRUPTED)) {
                        FileUtils.deletePath(cacheEntryDir);
                    }

                    // If the cache entry is MISSED or CORRUPTED, create or recreate the cache entry
                    missCount.incrementAndGet();
                    FileUtils.mkdirs(cacheEntryDir);

                    // The following method to create the cache entry's contents might be canceled
                    // abruptly due to an exception (or maybe a sudden process kill or power
                    // outage). However, if it happens, we don't roll back and delete the cache
                    // entry directory immediately because (1) the corrupted contents may provide
                    // important clues for debugging, and (2) the next time the cache is used, it
                    // will detect that the cache entry is corrupted and will delete and recreate
                    // the cache entry anyway.
                    actionIfCacheMissedOrCorrupted.call();

                    // Write the inputs to the inputs file for diagnostic purposes. We also use it
                    // to check whether a cache entry is corrupted or not.
                    Files.asCharSink(getInputsFile(cacheEntryDir), StandardCharsets.UTF_8)
                            .write(inputs.toString());

                    return result;
                });
            });
        } catch (ExecutionException exception) {
            // We need to figure out whether the exception comes from the file creator (i.e., a
            // FileCreatorException was thrown). If so, we rethrow the ExecutionException;
            // otherwise, we rethrow the exception as an IOException or a RuntimeException (as
            // documented in the javadoc of this method).
            for (Throwable exceptionInCausalChain : Throwables.getCausalChain(exception)) {
                if (exceptionInCausalChain instanceof FileCreatorException) {
                    throw exception;
                }
            }
            for (Throwable exceptionInCausalChain : Throwables.getCausalChain(exception)) {
                if (exceptionInCausalChain instanceof IOException) {
                    throw new IOException(exception);
                }
            }
            throw new RuntimeException(exception);
        }
    }

    /**
     * Returns {@code true} if the cache entry for the given list of inputs exists and is not
     * corrupted, and {@code false} otherwise. This method will block if the cache/cache entry is
     * being created/deleted by another thread/process.
     *
     * @param inputs all the inputs that affect the creation of the output file/directory
     */
    public boolean cacheEntryExists(@NonNull Inputs inputs) throws IOException {
        // This method is a stripped-down version of queryCacheEntry(). See queryCacheEntry() for
        // an explanation of this code.
        if (lockingScope == LockingScope.MULTI_PROCESS) {
            Preconditions.checkNotNull(
                    cacheDirectory.getCanonicalFile().getParentFile(),
                    "Cache directory must not be the root directory");
            FileUtils.mkdirs(cacheDirectory.getCanonicalFile().getParentFile());
        }

        try {
            QueryResult queryResult =
                    getSynchronizedFile(cacheDirectory).read(
                            sameCacheDirectory -> {
                                FileUtils.mkdirs(cacheDirectory);
                                return getSynchronizedFile(getCacheEntryDir(inputs)).read(
                                        (cacheEntryDir) -> checkCacheEntry(inputs, cacheEntryDir));
                            });
            return queryResult.getQueryEvent().equals(QueryEvent.HIT);
        } catch (ExecutionException exception) {
            for (Throwable exceptionInCausalChain : Throwables.getCausalChain(exception)) {
                if (exceptionInCausalChain instanceof IOException) {
                    throw new IOException(exception);
                }
            }
            throw new RuntimeException(exception);
        }
    }

    /**
     * Checks the cache entry to see if it exists (and whether it is corrupted). If it is corrupted,
     * the returned result also contains its cause.
     *
     * <p>This method should usually return a {@link QueryEvent#HIT} result or a {@link
     * QueryEvent#MISSED} result. In some rare cases, it may return a {@link QueryEvent#CORRUPTED}
     * result if the cache entry is corrupted (e.g., if an error occurred while the cache entry was
     * being created, or the previous build was canceled abruptly by the user or by a power outage).
     *
     * @return the result of this query (which does not include the path to the cached output
     *     file/directory)
     */
    @NonNull
    private static QueryResult checkCacheEntry(
            @NonNull Inputs inputs, @NonNull File cacheEntryDir) {
        if (!cacheEntryDir.exists()) {
            return new QueryResult(QueryEvent.MISSED);
        }

        // The absence of the inputs file indicates a corrupted cache entry
        File inputsFile = getInputsFile(cacheEntryDir);
        if (!inputsFile.exists()) {
            return new QueryResult(
                    QueryEvent.CORRUPTED,
                    new IllegalStateException(
                            String.format(
                                    "Inputs file '%s' does not exist",
                                    inputsFile.getAbsolutePath())));
        }

        // It is extremely unlikely that the contents of the inputs file are different from the
        // current inputs. If it happens, it may be because (1) some I/O error occurred when writing
        // to the inputs file, or (2) there is a hash collision (two lists of inputs are hashed into
        // the same key). In either case, we also report it as a corrupted cache entry.
        String inputsInCacheEntry;
        try {
            inputsInCacheEntry = Files.asCharSource(inputsFile, StandardCharsets.UTF_8).read();
        } catch (IOException e) {
            return new QueryResult(QueryEvent.CORRUPTED, e);
        }
        if (!inputs.toString().equals(inputsInCacheEntry)) {
            return new QueryResult(
                    QueryEvent.CORRUPTED,
                    new IllegalStateException(
                            String.format(
                                    "Expected contents '%s' but found '%s' in inputs file '%s'",
                                    inputs.toString(),
                                    inputsInCacheEntry,
                                    inputsFile.getAbsolutePath())));
        }

        // If the inputs file is valid, report a HIT
        return new QueryResult(QueryEvent.HIT);
    }

    /**
     * Returns the path of the cache entry directory, which is a directory containing the actual
     * cached file/directory and possibly some other info files. The cache entry directory is unique
     * to the given list of inputs (different lists of inputs correspond to different cache entry
     * directories).
     */
    @NonNull
    private File getCacheEntryDir(@NonNull Inputs inputs) {
        return new File(cacheDirectory, inputs.getKey());
    }

    /** Returns the path of the cached output file/directory inside the cache entry directory. */
    @NonNull
    private static File getCachedFile(@NonNull File cacheEntryDir) {
        return new File(cacheEntryDir, "output");
    }

    /**
     * Returns the path of an inputs file inside the cache entry directory, which will be used to
     * describe the inputs to an API call on the cache.
     */
    @NonNull
    private static File getInputsFile(@NonNull File cacheEntryDir) {
        return new File(cacheEntryDir, "inputs");
    }

    /**
     * Returns the path of the cached output file/directory that is unique to the given list of
     * inputs (different lists of inputs correspond to different cached files/directories).
     *
     * <p>Note that this method returns the path only, the cached file/directory may or may not have
     * been created.
     *
     * <p>This method is typically used together with the
     * {@link #createFileInCacheIfAbsent(Inputs, ExceptionConsumer)} method to get the path of the
     * cached file/directory in advance, before attempting to create it at a later time. The
     * returned path of this method is guaranteed to be the same as the returned path by that
     * method. The client calling this method should take precautions in handling the returned
     * cached file/directory; refer to the javadoc of
     * {@link #createFileInCacheIfAbsent(Inputs, ExceptionConsumer)} for more details.
     *
     * @param inputs all the inputs that affect the creation of the output file/directory
     */
    @NonNull
    public File getFileInCache(@NonNull Inputs inputs) {
        return getCachedFile(getCacheEntryDir(inputs));
    }

    /**
     * Deletes all the cache entries that were last modified before or at the given timestamp.
     *
     * <p>This method may block if the cache is being accessed by another thread/process.
     *
     * @param lastTimestamp the timestamp to determine whether a cache entry will be kept or deleted
     */
    public void deleteOldCacheEntries(long lastTimestamp) {
        // Check the parent directory of the cache directory, similarly to FileCache.delete()
        if (lockingScope == LockingScope.MULTI_PROCESS) {
            if (!FileUtils.parentDirExists(cacheDirectory)) {
                return;
            }
        }

        try {
            getSynchronizedFile(cacheDirectory).write(sameCacheDirectory -> {
                if (!cacheDirectory.exists()) {
                    return null;
                }
                for (File fileInDir : Verify.verifyNotNull(cacheDirectory.listFiles())) {
                    if (fileInDir.isDirectory() && getInputsFile(fileInDir).isFile()) {
                        //noinspection UnnecessaryLocalVariable - Use it for clarity
                        File cacheEntryDir = fileInDir;

                        if (cacheEntryDir.lastModified() <= lastTimestamp) {
                            FileUtils.deletePath(cacheEntryDir);
                            // Also delete the lock file in the case of MULTI_PROCESS locking
                            if (lockingScope == LockingScope.MULTI_PROCESS) {
                                FileUtils.deleteIfExists(
                                        SynchronizedFile.getLockFile(cacheEntryDir));
                            }
                        }
                    }
                }
                return null;
            });
        } catch (ExecutionException exception) {
            throw new RuntimeException(exception);
        }
    }

    /**
     * Deletes the cache directory and its contents.
     *
     * <p>This method may block if the cache is being accessed by another thread/process.
     */
    public void delete() throws IOException {
        // The underlying facility for multi-process locking (SynchronizedFile) requires that the
        // parent directory of the file/directory being synchronized exist (see method
        // getSynchronizedFile), so we make sure the parent directory exists first. If not, we
        // simply return immediately. The existence check may not be thread-safe and process-safe,
        // but it's okay since we're checking the parent directory, not the cache directory itself.
        if (lockingScope == LockingScope.MULTI_PROCESS) {
            if (!FileUtils.parentDirExists(cacheDirectory)) {
                return;
            }
        }

        try {
            getSynchronizedFile(cacheDirectory)
                    .write(
                            sameCacheDirectory -> {
                                FileUtils.deletePath(cacheDirectory);
                                return null;
                            });
        } catch (ExecutionException exception) {
            // We need to figure out whether the exception comes from the deletion action. If so, we
            // rethrow the exception as an IOException; otherwise, we rethrow the exception as a
            // RuntimeException.
            for (Throwable exceptionInCausalChain : Throwables.getCausalChain(exception)) {
                if (exceptionInCausalChain instanceof IOException) {
                    throw new IOException(exception);
                }
            }
            throw new RuntimeException(exception);
        }
    }

    /**
     * Returns a {@link SynchronizedFile} to synchronize access to the given file/directory.
     *
     * @param fileToSynchronize the file/directory whose access will be synchronized, which may not
     *     yet exist. If the cache is configured with {@code MULTI_PROCESS} locking scope, as
     *     required by {@link SynchronizedFile}, the parent directory of the file/directory being
     *     synchronized must exist.
     */
    @NonNull
    private SynchronizedFile getSynchronizedFile(@NonNull File fileToSynchronize) {
        if (lockingScope == LockingScope.MULTI_PROCESS) {
            Preconditions.checkArgument(
                    FileUtils.parentDirExists(fileToSynchronize),
                    "Parent directory of "
                            + fileToSynchronize.getAbsolutePath()
                            + " does not exist");
            return SynchronizedFile.getInstanceWithMultiProcessLocking(fileToSynchronize);
        } else {
            return SynchronizedFile.getInstanceWithSingleProcessLocking(fileToSynchronize);
        }
    }

    @VisibleForTesting
    int getMisses() {
        return missCount.get();
    }

    @VisibleForTesting
    int getHits() {
        return hitCount.get();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("cacheDirectory", cacheDirectory)
                .add("lockingScope", lockingScope)
                .toString();
    }

    /**
     * Copies a file or a directory's contents to another file or directory, which can have a
     * different name. The target file/directory is replaced if it already exists.
     *
     * <p>The source file/directory must exist and must not reside in, contain, or be identical to
     * the target file/directory.
     */
    private static void copyFileOrDirectory(@NonNull File from, @NonNull File to)
            throws IOException {
        Preconditions.checkArgument(
                from.exists(), "Source path " + from.getAbsolutePath() + " does not exist");
        Preconditions.checkArgument(!FileUtils.isFileInDirectory(from, to));
        Preconditions.checkArgument(!FileUtils.isFileInDirectory(to, from));
        Preconditions.checkArgument(!FileUtils.isSameFile(from, to));

        if (from.isFile()) {
            Files.createParentDirs(to);
            FileUtils.copyFile(from, to);
        } else if (from.isDirectory()) {
            FileUtils.deletePath(to);
            FileUtils.copyDirectory(from, to);
        }
    }

    /**
     * Checked exception thrown when the file creator callback function aborts due to an {@link
     * Exception}. This class is a private sub-class of {@link ExecutionException} and is used to
     * distinguish itself from other execution exceptions thrown elsewhere.
     */
    @Immutable
    private static final class FileCreatorException extends ExecutionException {

        public FileCreatorException(@NonNull Exception exception) {
            super(exception);
        }
    }

    /**
     * List of input parameters to be provided by the client when using {@link FileCache}.
     *
     * <p>The clients of {@link FileCache} need to exhaustively specify all the inputs that affect
     * the creation of an output file/directory to the {@link Inputs} object, including a command
     * for the file creator callback function, the input files/directories and other input
     * parameters. A command identifies a file creator (which usually corresponds to a Gradle task)
     * and is used to separate the cached results of different file creators when they use the same
     * cache. If the client uses a different file creator, or changes the file creator's
     * implementation, then it needs to provide a new unique command. For input files/directories,
     * it is important to select a combination of properties that uniquely identifies an input
     * file/directory with consideration of their impact on correctness and performance (see {@link
     * FileProperties} and {@link DirectoryProperties}).
     *
     * <p>When constructing the {@link Inputs} object, the client is required to provide the command
     * (via the {@link Inputs.Builder} constructor) and at least one other input parameter.
     *
     * <p>The input parameters have the order in which they were added to this {@code Inputs}
     * object. They are not allowed to have the same name (an exception will be thrown if a
     * parameter with the same name already exists).
     *
     * <p>Note that if some inputs are missing, the cache may return a cached file/directory that is
     * incorrect. On the other hand, if some irrelevant inputs are included, the cache may create a
     * new cached file/directory even though an existing one can be used. In other words, missing
     * inputs affect correctness, and irrelevant inputs affect performance. Thus, the client needs
     * to consider carefully what to include and exclude in these inputs.
     */
    @Immutable
    public static final class Inputs {

        @NonNull private static final String COMMAND = "COMMAND";

        @NonNull private final Command command;

        @NonNull private final LinkedHashMap<String, String> parameters;

        /** Builder of {@link FileCache.Inputs}. */
        public static final class Builder {

            @NonNull private final Command command;

            @NonNull private final CacheSession session;

            @NonNull
            private final LinkedHashMap<String, String> parameters = Maps.newLinkedHashMap();

            /**
             * Creates a {@link Builder} instance to construct an {@link Inputs} object.
             *
             * @param command the command that identifies a file creator callback function (which
             *     usually corresponds to a Gradle task)
             */
            public Builder(@NonNull Command command) {
                this(
                        command,
                        new CacheSession() {

                            @Override
                            @NonNull
                            String getDirectoryHash(@NonNull File directory) {
                                return Builder.getDirectoryHash(directory);
                            }

                            @Override
                            @NonNull
                            String getRegularFileHash(@NonNull File regularFile) {
                                return Builder.getFileHash(regularFile);
                            }
                        });
            }

            /**
             * Creates a {@link Builder} instance to construct an {@link Inputs} object.
             *
             * @param command the command that identifies a file creator callback function (which
             *     usually corresponds to a Gradle task)
             * @param session The session the newly created Builder will belong to.
             */
            public Builder(@NonNull Command command, @NonNull CacheSession session) {
                this.command = command;
                this.session = session;
            }

            /**
             * Adds an input parameter with a String value.
             *
             * @throws IllegalStateException if a parameter with the same name already exists
             */
            @NonNull
            public Builder putString(@NonNull String name, @NonNull String value) {
                Preconditions.checkState(
                        !parameters.containsKey(name), "Input parameter %s already exists", name);
                parameters.put(name, value);
                return this;
            }

            /**
             * Adds an input parameter with a Boolean value.
             *
             * @throws IllegalStateException if a parameter with the same name already exists
             */
            @NonNull
            public Builder putBoolean(@NonNull String name, boolean value) {
                return putString(name, String.valueOf(value));
            }

            /**
             * Adds an input parameter with a Long value.
             *
             * @throws IllegalStateException if a parameter with the same name already exists
             */
            @NonNull
            public Builder putLong(@NonNull String name, long value) {
                return putString(name, String.valueOf(value));
            }

            /**
             * Adds an input parameter to identify a regular file (not a directory) using the given
             * properties.
             *
             * @throws IllegalStateException if a parameter with the same name already exists
             */
            @NonNull
            public Builder putFile(
                    @NonNull String name,
                    @NonNull File file,
                    @NonNull FileProperties fileProperties) {
                Preconditions.checkArgument(file.isFile(), file + " is not a file.");
                switch (fileProperties) {
                    case HASH:
                        putString(name, session.getRegularFileHash(file));
                        break;
                    case PATH_HASH:
                        putString(name + ".path", file.getPath());
                        putString(name + ".hash", session.getRegularFileHash(file));
                        break;
                    case PATH_SIZE_TIMESTAMP:
                        putString(name + ".path", file.getPath());
                        putLong(name + ".size", file.length());
                        putLong(name + ".timestamp", file.lastModified());
                        break;
                    default:
                        throw new RuntimeException("Unknown enum " + fileProperties);
                }
                return this;
            }

            /**
             * Adds an input parameter to identify a directory using the given properties.
             *
             * @throws IllegalStateException if a parameter with the same name already exists
             */
            @SuppressWarnings("UnusedReturnValue")
            @NonNull
            public Builder putDirectory(
                    @NonNull String name,
                    @NonNull File directory,
                    @NonNull DirectoryProperties directoryProperties) {
                Preconditions.checkArgument(
                        directory.isDirectory(), directory + " is not a directory.");
                switch (directoryProperties) {
                    case HASH:
                        putString(name, session.getDirectoryHash(directory));
                        break;
                    case PATH_HASH:
                        putString(name + ".path", directory.getPath());
                        putString(name + ".hash", session.getDirectoryHash(directory));
                        break;
                    default:
                        throw new RuntimeException("Unknown enum " + directoryProperties);
                }
                return this;
            }

            /**
             * Returns the hash of the contents of the given regular file (not a directory).
             *
             * <p>Note that the hash computation does not consider the path or name of the given
             * file.
             */
            @NonNull
            @VisibleForTesting
            static String getFileHash(@NonNull File file) {
                try {
                    return Hashing.sha256()
                            .hashBytes(java.nio.file.Files.readAllBytes(file.toPath()))
                            .toString();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            /**
             * Returns the hash of the contents of the given directory.
             *
             * <p>The directory's hash is computed from the following:
             *
             * <ol>
             *   <li>The relative paths, in lexical order, of all the regular files and directories
             *       at all depths below the given directory (symbolic links are not followed)
             *   <li>The contents of all the regular files in the sorted list above
             * </ol>
             *
             * <p>Note that the hash computation does not consider the path or name of the given
             * directory.
             */
            @NonNull
            @VisibleForTesting
            static String getDirectoryHash(@NonNull File directory) {
                Hasher hasher = Hashing.sha256().newHasher();
                try (Stream<Path> entries = java.nio.file.Files.walk(directory.toPath())) {
                    // Filter out the root directory and sort the entries (as Files.walk() does not
                    // guarantee that the returned entries has deterministic order)
                    Stream<Path> sortedEntries =
                            entries.filter(e -> !FileUtils.isSameFile(e.toFile(), directory))
                                    .sorted();
                    sortedEntries.forEach(
                            entry -> {
                                hasher.putUnencodedChars("$$$DIRECTORY_ENTRY_RELATIVE_PATH$$$");
                                hasher.putUnencodedChars(
                                        directory.toPath().relativize(entry).toString());

                                if (java.nio.file.Files.isRegularFile(entry)) {
                                    hasher.putUnencodedChars("$$$DIRECTORY_ENTRY_FILE_CONTENTS$$$");
                                    try {
                                        hasher.putBytes(java.nio.file.Files.readAllBytes(entry));
                                    } catch (IOException e) {
                                        throw new UncheckedIOException(e);
                                    }
                                }
                            });
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return hasher.hash().toString();
            }

            /**
             * Builds an {@code Inputs} instance.
             *
             * @throws IllegalStateException if the inputs are empty
             */
            @NonNull
            public Inputs build() {
                Preconditions.checkState(!parameters.isEmpty(), "Inputs must not be empty.");
                return new Inputs(this);
            }
        }

        private Inputs(@NonNull Builder builder) {
            command = builder.command;
            parameters = Maps.newLinkedHashMap(builder.parameters);
        }

        @Override
        @NonNull
        public String toString() {
            return COMMAND + "=" + command.name() + System.lineSeparator() +
                    Joiner.on(System.lineSeparator()).withKeyValueSeparator("=").join(parameters);
        }

        /**
         * Returns a key representing this list of input parameters.
         *
         * <p>Two lists of input parameters are considered different if the input parameters are
         * different in size, names, order, or values. This method is guaranteed to return different
         * keys for different lists of inputs.
         */
        @NonNull
        public String getKey() {
            return Hashing.sha256().hashUnencodedChars(this.toString()).toString();
        }
    }

    /**
     * Command to be provided by the client when using {@link FileCache}. A command identifies a
     * file creator callback function (which usually corresponds to a Gradle task) and is used to
     * separate the cached results of different file creators when they use the same cache.
     */
    public enum Command {

        /** Command used for testing only. */
        TEST,

        /** Mockable jars used for unit testing. */
        GENERATE_MOCKABLE_JAR,

        /** Pre-dex a library to a dex archive. */
        PREDEX_LIBRARY_TO_DEX_ARCHIVE,

        /** Desugar a library. */
        DESUGAR_LIBRARY,

        /** Extract the AAPT2 JNI libraries so they can be loaded. */
        EXTRACT_AAPT2_JNI,

        /** Extract the Desugar jar so it can be used for processing. */
        EXTRACT_DESUGAR_JAR,

        /** Fix stack frames. */
        FIX_STACK_FRAMES,
    }

    /**
     * Properties of a regular file (not a directory) to be used when constructing the cache inputs.
     */
    public enum FileProperties {

        /**
         * The properties include the hash of the given regular file (not a directory).
         *
         * <p>This is the recommended way of constructing the cache inputs for a regular file. Note
         * that the properties do not include the path or name of the given file. Clients can
         * consider using {@link #PATH_HASH} to also include the file's path in the cache inputs
         * (typically for debugging purposes).
         */
        HASH,

        /**
         * The properties include the (not-yet-normalized) path and hash of the given regular file
         * (not a directory).
         *
         * <p>This option is similar to {@link #HASH} except that the file's path is also included
         * in the cache inputs, typically for debugging purposes (e.g., to find out what file a
         * cache entry is created from).
         */
        PATH_HASH,

        /**
         * The properties include the (not-yet-normalized) path, size, and timestamp of a regular
         * file (not a directory).
         *
         * <p>WARNING: Although this option is faster than using {@link #HASH}, it is not as safe.
         * The main reason is that, due to the granularity of filesystem timestamps, timestamps may
         * actually not change between two consecutive writes. This will lead to incorrect cache
         * entries. This option should only be used after careful consideration and with knowledge
         * of its limitations. Otherwise, {@link #HASH} should be used instead.
         */
        PATH_SIZE_TIMESTAMP,
    }

    /** Properties of a directory to be used when constructing the cache inputs. */
    public enum DirectoryProperties {

        /**
         * The properties include the hash of the given directory.
         *
         * <p>The directory's hash is computed from the following:
         *
         * <ol>
         *   <li>The relative paths, in lexical order, of all the regular files and directories at
         *       all depths below the given directory (symbolic links are not followed)
         *   <li>The contents of all the regular files in the sorted list above
         * </ol>
         *
         * <p>This is the recommended way of constructing the cache inputs for a directory. Note
         * that the properties do not include the path or name of the given directory. Clients can
         * consider using {@link #PATH_HASH} to also include the directory's path in the cache
         * inputs (typically for debugging purposes).
         */
        HASH,

        /**
         * The properties include the (not-yet-normalized) path and hash of the given directory.
         *
         * <p>The directory's hash is defined as in {@link #HASH}.
         *
         * <p>This option is similar to {@link #HASH} except that the directory's path is also
         * included in the cache inputs, typically for debugging purposes (e.g., to find out what
         * directory a cache entry is created from).
         */
        PATH_HASH,
    }

    /**
     * The result of a cache query, which includes a {@link QueryEvent} indicating whether the cache
     * is hit, missed, or corrupted, a cause if the cache is corrupted, and an (optional) path to
     * the cached output file/directory.
     */
    @Immutable
    public static final class QueryResult {

        @NonNull private final QueryEvent queryEvent;

        @Nullable private final Throwable causeOfCorruption;

        @Nullable private final File cachedFile;

        /**
         * Creates a {@code QueryResult} instance.
         *
         * @param queryEvent the query event
         * @param causeOfCorruption a cause if the cache is corrupted, or null otherwise
         * @param cachedFile the path to cached output file/directory, can be null if the cache does
         *     not want to expose this information
         */
        QueryResult(
                @NonNull QueryEvent queryEvent,
                @Nullable Throwable causeOfCorruption,
                @Nullable File cachedFile) {
            Preconditions.checkState(
                    (queryEvent.equals(QueryEvent.CORRUPTED) && causeOfCorruption != null)
                            || (!queryEvent.equals(QueryEvent.CORRUPTED)
                                    && causeOfCorruption == null));

            this.queryEvent = queryEvent;
            this.causeOfCorruption = causeOfCorruption;
            this.cachedFile = cachedFile;
        }

        private QueryResult(@NonNull QueryEvent queryEvent, @NonNull Throwable causeOfCorruption) {
            this(queryEvent, causeOfCorruption, null);
        }

        private QueryResult(@NonNull QueryEvent queryEvent) {
            this(queryEvent, null, null);
        }

        /**
         * Returns the {@link QueryEvent} indicating whether the cache is hit, missed, or corrupted.
         */
        @NonNull
        public QueryEvent getQueryEvent() {
            return queryEvent;
        }

        /** Returns a cause if the cache is corrupted, and null otherwise. */
        @Nullable
        public Throwable getCauseOfCorruption() {
            return causeOfCorruption;
        }

        /**
         * Returns the path to the cached output file/directory, can be null if the cache does not
         * want to expose this information.
         */
        @Nullable
        public File getCachedFile() {
            return cachedFile;
        }
    }

    /**
     * The event that happens when the client queries a cache entry: the cache entry may be hit,
     * missed, or corrupted.
     */
    public enum QueryEvent {

        /** The cache entry exists and is not corrupted. */
        HIT,

        /** The cache entry does not exist. */
        MISSED,

        /** The cache entry exists and is corrupted. */
        CORRUPTED,
    }

    /**
     * A common point between different cache operations occurring during one single task, the cache
     * session allows to factorize some operations, For example file hash are computed only once per
     * session. Files used as input of the cache operations are supposed to stay unchanged during
     * the usage of one {@link CacheSession} instance.
     */
    public abstract static class CacheSession {
        private CacheSession() {}

        @NonNull
        abstract String getDirectoryHash(@NonNull File directory);

        @NonNull
        abstract String getRegularFileHash(@NonNull File regularFile);
    }

    /** Create a new {@link CacheSession}. */
    public static CacheSession newSession() {
        return new CacheSession() {
            @NonNull
            private final ConcurrentHashMap<File, String> pathHashes = new ConcurrentHashMap<>();

            @Override
            @NonNull
            String getDirectoryHash(@NonNull File directory) {
                return pathHashes.computeIfAbsent(directory, Inputs.Builder::getDirectoryHash);
            }

            @Override
            @NonNull
            String getRegularFileHash(@NonNull File regularFile) {
                return pathHashes.computeIfAbsent(regularFile, Inputs.Builder::getFileHash);
            }
        };
    }
}
