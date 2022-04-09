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

package com.android.utils.concurrency;

import com.android.annotations.NonNull;
import com.android.annotations.concurrency.Immutable;
import com.android.utils.JvmWideVariable;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Verify;
import com.google.common.reflect.TypeToken;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A lock similar to {@link ReentrantReadWriteLock} used to synchronize threads both within the same
 * JVM and across different JVMs, even when classes (including this class) are loaded multiple times
 * by different class loaders.
 *
 * <p>Typically, there exists only one JVM in a process. Therefore, we call this class {@code
 * ReadWriteProcessLock}, although in a multi-JVM process, this is not strictly correct. Also, from
 * here we will use the term JVM and process interchangeably.
 *
 * <p>Threads and processes will be synchronized on the same lock file (two lock files are the same
 * if they refer to the same physical file). The client using {@code ReadWriteProcessLock} will
 * provide a lock file when constructing a {@code ReadWriteProcessLock} instance. Then, different
 * threads and processes using the same or different {@code ReadWriteProcessLock} instances on the
 * same lock file can be synchronized.
 *
 * <p>The basic usage of this class is similar to {@link ReentrantReadWriteLock} and {@link
 * java.util.concurrent.locks.Lock}. Below is a typical example.
 *
 * <pre>{@code
 * ReadWriteProcessLock readWriteProcessLock = new ReadWriteProcessLock(lockFile);
 * ReadWriteProcessLock.Lock lock =
 *     useSharedLock
 *         ? readWriteProcessLock.readLock()
 *         : readWriteProcessLock.writeLock();
 * lock.lock();
 * try {
 *     runnable.run();
 * } finally {
 *     lock.unlock();
 * }
 * }</pre>
 *
 * <p>The key usage differences between {@code ReadWriteProcessLock} and a regular Java lock such as
 * {@link ReentrantReadWriteLock} are:
 *
 * <ol>
 *   <li>{@code ReadWriteProcessLock} is itself not a lock object (which threads and processes are
 *       directly synchronized on), but a proxy to the actual lock object (a lock file in this
 *       case). Therefore, there could be multiple instances of {@code ReadWriteProcessLock} on the
 *       same lock file.
 *   <li>Two lock files are considered the same if they refer to the same physical file.
 * </ol>
 *
 * <p>This lock is not reentrant.
 *
 * <p>This class is thread-safe.
 */
@Immutable
public final class ReadWriteProcessLock {

    /** The lock used for reading. */
    @NonNull private final ReadWriteProcessLock.Lock readLock = new ReadWriteProcessLock.ReadLock();

    /** The lock used for writing. */
    @NonNull
    private final ReadWriteProcessLock.Lock writeLock = new ReadWriteProcessLock.WriteLock();

    /** The lock file, used solely for synchronization purposes. */
    @NonNull private final Path lockFile;

    /**
     * A lock used to synchronize threads within the current process. This lock is shared across all
     * instances of {@code ReadWriteProcessLock} (and also across class loaders) in the current
     * process for the given lock file.
     */
    @NonNull private final ReentrantReadWriteLock withinProcessLock;

    /**
     * A lock used to provide exclusive access to a critical section. This lock is shared across all
     * instances of {@code ReadWriteProcessLock} (and also across class loaders) in the current
     * process for the given lock file.
     */
    @NonNull private final ReentrantLock criticalSectionLock;

    /**
     * A map from threads to the type of lock (shared or exclusive) that the threads are currently
     * holding. This map is shared across all instances of {@code ReadWriteProcessLock} (and also
     * across class loaders) in the current process for the given lock file.
     *
     * <p>At any given point during execution, this map must either be empty, or contain entries
     * with only {@code true} values (multiple threads are holding shared locks), or contain exactly
     * one entry with {@code false} value (exactly one thread is holding an exclusive lock).
     */
    @NonNull private final Map<Thread, Boolean> threadToLockTypeMap;

    /**
     * A reference to the file channel that provides a file lock. This reference is shared across
     * all instances of {@code ReadWriteProcessLock} (and also across class loaders) in the current
     * process for the given lock file.
     */
    @NonNull private final AtomicReference<FileChannel> sharedFileChannel;

    /**
     * A reference to the file lock which is used to synchronize threads across different processes.
     * This reference is shared across all instances of {@code ReadWriteProcessLock} (and also
     * across class loaders) in the current process for the given lock file.
     */
    @NonNull private final AtomicReference<FileLock> sharedFileLock;

    /**
     * Creates a {@code ReadWriteProcessLock} instance for the given lock file. Threads and
     * processes will be synchronized on the same lock file (two lock files are the same if they
     * refer to the same physical file).
     *
     * <p>The lock file may or may not exist when this constructor is called. It will be created if
     * it does not yet exist and will not be deleted after this constructor is called.
     *
     * <p>In order for the lock file to be created (if it does not yet exist), the parent directory
     * of the lock file must exist when this constructor is called.
     *
     * <p>IMPORTANT: The lock file must be used solely for synchronization purposes. The client of
     * this class must not access (read, write, or delete) the lock file. The client may delete the
     * lock file only when the locking mechanism is no longer in use.
     *
     * <p>This constructor will normalize the lock file's path first to detect same physical files
     * via equals(). However, it is still recommended that the client normalize the file's path in
     * advance before calling this constructor.
     *
     * @param lockFile the lock file, used solely for synchronization purposes; it may not yet
     *     exist, but its parent directory must exist
     * @throws IllegalArgumentException if the parent directory of the lock file does not exist, or
     *     if a directory or a regular (non-empty) file with the same path as the lock file
     *     accidentally exists
     */
    public ReadWriteProcessLock(@NonNull Path lockFile) {
        try {
            lockFile = createAndNormalizeLockFile(lockFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        this.lockFile = lockFile;

        this.withinProcessLock =
                JvmWideVariable.getJvmWideObjectPerKey(
                        ReadWriteProcessLock.class,
                        "withinProcessLock",
                        TypeToken.of(Path.class),
                        TypeToken.of(ReentrantReadWriteLock.class),
                        lockFile,
                        ReentrantReadWriteLock::new);
        this.criticalSectionLock =
                JvmWideVariable.getJvmWideObjectPerKey(
                        ReadWriteProcessLock.class,
                        "criticalSectionLock",
                        TypeToken.of(Path.class),
                        TypeToken.of(ReentrantLock.class),
                        lockFile,
                        ReentrantLock::new);
        this.threadToLockTypeMap =
                JvmWideVariable.getJvmWideObjectPerKey(
                        ReadWriteProcessLock.class,
                        "threadToLockTypeMap",
                        TypeToken.of(Path.class),
                        new TypeToken<Map<Thread, Boolean>>() {},
                        lockFile,
                        HashMap::new);
        this.sharedFileChannel =
                JvmWideVariable.getJvmWideObjectPerKey(
                        ReadWriteProcessLock.class,
                        "sharedFileChannel",
                        TypeToken.of(Path.class),
                        new TypeToken<AtomicReference<FileChannel>>() {},
                        lockFile,
                        AtomicReference::new);
        this.sharedFileLock =
                JvmWideVariable.getJvmWideObjectPerKey(
                        ReadWriteProcessLock.class,
                        "sharedFileLock",
                        TypeToken.of(Path.class),
                        new TypeToken<AtomicReference<FileLock>>() {},
                        lockFile,
                        AtomicReference::new);
    }

    /**
     * Creates the lock file if it does not yet exist and normalizes its path.
     *
     * @return the normalized path to an existing lock file
     */
    @NonNull
    @VisibleForTesting
    static Path createAndNormalizeLockFile(@NonNull Path lockFilePath) throws IOException {
        // Create the lock file if it does not yet exist
        if (!Files.exists(lockFilePath)) {
            // This is needed for Files.exist() and Files.createFile() below to work properly
            lockFilePath = lockFilePath.normalize();

            Preconditions.checkArgument(
                    Files.exists(Verify.verifyNotNull(lockFilePath.getParent())),
                    "Parent directory of " + lockFilePath.toAbsolutePath() + " does not exist");
            try {
                Files.createFile(lockFilePath);
            } catch (FileAlreadyExistsException e) {
                // It's okay if the file has already been created (although we checked that the file
                // did not exist before creating the file, it is still possible that the file was
                // immediately created after the check by some other thread/process).
            }
        }

        // Normalize the lock file's path with Path.toRealPath() (Path.normalize() does not resolve
        // symbolic links)
        lockFilePath = lockFilePath.toRealPath();

        // Make sure that no directory with the same path as the lock file accidentally exists
        Preconditions.checkArgument(
                !Files.isDirectory(lockFilePath),
                lockFilePath.toAbsolutePath() + " is a directory.");

        // Make sure that no regular (non-empty) file with the same path as the lock file
        // accidentally exists
        long lockFileSize = Files.size(lockFilePath);
        Preconditions.checkArgument(
                lockFileSize == 0,
                String.format(
                        "File '%1$s' with size=%2$d cannot be used as a lock file.",
                        lockFilePath.toAbsolutePath(), lockFileSize));

        return lockFilePath;
    }

    /** Returns the lock used for reading. */
    @NonNull
    public ReadWriteProcessLock.Lock readLock() {
        return readLock;
    }

    /** Returns the lock used for writing. */
    @NonNull
    public ReadWriteProcessLock.Lock writeLock() {
        return writeLock;
    }

    private void acquireLock(boolean shared) throws IOException {
        /*
         * We synchronize threads across different processes using Java's file locking API (a
         * FileLock returned by FileChannel.lock()). It is not possible to use FileLock to also
         * synchronize threads within the same process as a FileLock is held on behalf of the entire
         * process and the javadoc of FileLock explicitly says that it cannot be used for
         * within-process synchronization.
         *
         * Therefore, we use a separate within-process lock to synchronize threads within the same
         * process first before using a FileLock to synchronize threads across different processes.
         */
        java.util.concurrent.locks.Lock lock =
                shared ? withinProcessLock.readLock() : withinProcessLock.writeLock();
        lock.lock();
        try {
            acquireInterProcessLock(shared);
        } catch (Throwable throwable) {
            // If an error occurred, release the within-process lock
            lock.unlock();
            throw throwable;
        }
    }

    private boolean tryAcquireLock(boolean shared, long nanosTimeout) throws IOException {
        // The implementation of this method is similar to acquireLock(), except that we return
        // early if the lock cannot be acquired after the specified time
        Stopwatch stopwatch = Stopwatch.createStarted();
        java.util.concurrent.locks.Lock lock =
                shared ? withinProcessLock.readLock() : withinProcessLock.writeLock();
        try {
            if (!lock.tryLock(nanosTimeout, TimeUnit.NANOSECONDS)) {
                return false;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        boolean interProcessLockAcquired;
        try {
            stopwatch.stop();
            long nanosRemainingTimeout = nanosTimeout - stopwatch.elapsed(TimeUnit.NANOSECONDS);
            interProcessLockAcquired = tryAcquireInterProcessLock(shared, nanosRemainingTimeout);
        } catch (Throwable throwable) {
            lock.unlock();
            throw throwable;
        }

        if (!interProcessLockAcquired) {
            lock.unlock();
        }
        return interProcessLockAcquired;
    }

    private void releaseLock(boolean shared) throws IOException {
        try {
            // Release the inter-process lock first
            releaseInterProcessLock(shared);
        } finally {
            // Whether an error occurred or not, release the within-process lock
            java.util.concurrent.locks.Lock lock =
                    shared ? withinProcessLock.readLock() : withinProcessLock.writeLock();
            lock.unlock();
        }
    }

    private void acquireInterProcessLock(boolean shared) throws IOException {
        // There could be multiple threads attempting to run this method. Therefore, we acquire an
        // exclusive lock so that only one of them can run at a time.
        criticalSectionLock.lock();
        try {
            Preconditions.checkState(
                    !threadToLockTypeMap.containsKey(Thread.currentThread()),
                    "ReadWriteProcessLock is not reentrant, violated by thread "
                            + Thread.currentThread());
            // This method is called when a within-process lock has already been acquired (see
            // acquireLock()). If it is a shared lock, then other threads (in the current process)
            // may be holding shared locks but not exclusive locks. If it is an exclusive lock, then
            // no other threads can be holding any locks.
            if (shared) {
                Preconditions.checkState(!threadToLockTypeMap.values().contains(false));
            } else {
                Preconditions.checkState(threadToLockTypeMap.isEmpty());
            }

            // Although multiple threads may want to acquire a FileLock, it is enough to allow only
            // the first thread to actually acquire it, as FileLock is held on behalf of the entire
            // process. The acquired FileLock can then be shared by multiple threads in the current
            // process. (In fact, it is also not possible to acquire a FileLock twice as it will
            // result in an OverlappingFileLockException.)
            if (threadToLockTypeMap.isEmpty()) {
                acquireFileLock(shared);
            }

            // We update the map after acquiring the FileLock so that if the acquiring step throws
            // an exception, the map doesn't get updated.
            // This method call is guaranteed to not throw an exception (if it did, we would need
            // to release the acquired FileLock before returning).
            threadToLockTypeMap.put(Thread.currentThread(), shared);
        } finally {
            criticalSectionLock.unlock();
        }
    }

    private boolean tryAcquireInterProcessLock(boolean shared, long nanosTimeout)
            throws IOException {
        // The implementation of this method is similar to acquireInterProcessLock() except that we
        // return early if the lock cannot be acquired after the specified time
        Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            if (!criticalSectionLock.tryLock(nanosTimeout, TimeUnit.NANOSECONDS)) {
                return false;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        boolean fileLockAcquired;
        try {
            Preconditions.checkState(
                    !threadToLockTypeMap.containsKey(Thread.currentThread()),
                    "ReadWriteProcessLock is not reentrant, violated by thread "
                            + Thread.currentThread());
            if (shared) {
                Preconditions.checkState(!threadToLockTypeMap.values().contains(false));
            } else {
                Preconditions.checkState(threadToLockTypeMap.isEmpty());
            }

            if (threadToLockTypeMap.isEmpty()) {
                stopwatch.stop();
                long nanosRemainingTimeout = nanosTimeout - stopwatch.elapsed(TimeUnit.NANOSECONDS);
                fileLockAcquired = tryAcquireFileLock(shared, nanosRemainingTimeout);
            } else {
                fileLockAcquired = true;
            }

            if (fileLockAcquired) {
                threadToLockTypeMap.put(Thread.currentThread(), shared);
            }
        } finally {
            criticalSectionLock.unlock();
        }

        return fileLockAcquired;
    }

    private void releaseInterProcessLock(boolean shared) throws IOException {
        criticalSectionLock.lock();
        try {
            Preconditions.checkState(threadToLockTypeMap.containsKey(Thread.currentThread()));
            if (shared) {
                Preconditions.checkState(!threadToLockTypeMap.values().contains(false));
            } else {
                Preconditions.checkState(
                        threadToLockTypeMap.size() == 1
                                && threadToLockTypeMap.containsValue(false));
            }

            // We update the map before releasing the FileLock so that even if the releasing step
            // throws an exception, the map still gets updated.
            // This method call is guaranteed to not throw an exception (if it did, we would need
            // to still attempt releasing the FileLock before returning).
            threadToLockTypeMap.remove(Thread.currentThread());

            // Only release the FileLock if the current thread was the only one using it
            if (threadToLockTypeMap.isEmpty()) {
                releaseFileLock();
            }
        } finally {
            criticalSectionLock.unlock();
        }
    }

    private void acquireFileLock(boolean shared) throws IOException {
        /*
         * This method and the releaseFileLock() method are called from an exclusive critical
         * section, so only one of them can be executed, and executed by a single thread at a time
         * in the current process.
         *
         * However, it is still possible that both methods are executed concurrently or each of them
         * is executed multiple times concurrently by different processes. If the lock file is
         * deleted while one of these two methods is being executed or when a process has acquired
         * the FileLock but has not yet released it, the acquired FileLock can become ineffective
         * (even when no exception is thrown).
         *
         * Therefore, this class does not delete lock files and requires that the client must not
         * access (read, write, or delete) the lock files while the locking mechanism is in use. If
         * this requirement is met, the two methods are safe to be executed concurrently by
         * different processes.
         */
        Preconditions.checkState(sharedFileChannel.get() == null && sharedFileLock.get() == null);

        FileChannel fileChannel =
                FileChannel.open(
                        lockFile,
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE);
        try {
            FileLock fileLock;
            try {
                fileLock = fileChannel.lock(0L, Long.MAX_VALUE, shared);
            } catch (OverlappingFileLockException e) {
                // This error is common so we want to print out the lock file's path when it happens
                throw new RuntimeException(
                        "Unable to acquire a file lock for " + lockFile.toAbsolutePath(), e);
            }

            // We update the references after acquiring the FileLock so that if the acquiring step
            // throws an exception, the references don't get updated.
            // These method calls are guaranteed to not throw an exception (if they did, we would
            // need to release the acquired FileLock before returning).
            sharedFileChannel.set(fileChannel);
            sharedFileLock.set(fileLock);
        } catch (Throwable throwable) {
            fileChannel.close();
            throw throwable;
        }
    }

    private boolean tryAcquireFileLock(boolean shared, long nanosTimeout) throws IOException {
        // The implementation of this method is similar to acquireFileLock() except that we return
        // early if the lock cannot be acquired after the specified time
        Stopwatch stopwatch = Stopwatch.createStarted();
        Preconditions.checkState(sharedFileChannel.get() == null && sharedFileLock.get() == null);

        FileChannel fileChannel =
                FileChannel.open(
                        lockFile,
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE);
        FileLock fileLock;
        try {
            // FileLock does not have built-in support for tryLock() with timeout so we need to
            // support it here using a loop.
            // Make sure we try acquiring the lock first *before* checking for timeout, as stated in
            // the javadoc of our locking API.
            long maxSleepTime = nanosTimeout / 10;
            while (true) {
                try {
                    fileLock = fileChannel.tryLock(0L, Long.MAX_VALUE, shared);
                } catch (OverlappingFileLockException e) {
                    throw new RuntimeException(
                            "Unable to acquire a file lock for " + lockFile.toAbsolutePath(), e);
                }

                if (fileLock != null) {
                    sharedFileChannel.set(fileChannel);
                    sharedFileLock.set(fileLock);
                    break;
                } else {
                    long nanosRemainingTimeout =
                            nanosTimeout - stopwatch.elapsed(TimeUnit.NANOSECONDS);
                    if (nanosRemainingTimeout <= 0) {
                        break;
                    } else {
                        // Sleep a little before retrying to avoid polling continuously
                        try {
                            Thread.sleep(
                                    TimeUnit.MILLISECONDS.convert(
                                            Math.min(nanosRemainingTimeout, maxSleepTime),
                                            TimeUnit.NANOSECONDS));
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        } catch (Throwable throwable) {
            fileChannel.close();
            throw throwable;
        }

        if (fileLock == null) {
            fileChannel.close();
        }
        return fileLock != null;
    }

    private void releaseFileLock() throws IOException {
        // As commented in acquireFileLock(), that method and this method are never executed
        // concurrently by multiple threads in the same process, but it is possible and safe for
        // them to be executed concurrently by different processes (as long as lock files are not
        // deleted).
        FileChannel fileChannel = Preconditions.checkNotNull(sharedFileChannel.get());
        FileLock fileLock = Preconditions.checkNotNull(sharedFileLock.get());

        // We update the references before releasing the FileLock so that even if the releasing step
        // throws an exception, the references still get updated.
        // These method calls are guaranteed to not throw an exception (if they did, we would need
        // to still attempt releasing the FileLock before returning).
        sharedFileChannel.set(null);
        sharedFileLock.set(null);

        // We release the resources but do not delete the lock file as doing so would be unsafe to
        // other processes which might be using the lock file (see comments in acquireFileLock()).
        //noinspection TryFinallyCanBeTryWithResources
        try {
            fileLock.release();
        } finally {
            fileChannel.close();
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("lockFile", lockFile).toString();
    }

    public interface Lock {

        /**
         * Acquires the lock, blocking to wait until the lock can be acquired.
         *
         * <p>If the thread executing this method is interrupted, this method will throw a runtime
         * exception.
         */
        void lock() throws IOException;

        /**
         * Tries acquiring the lock, blocking to wait until the lock can be acquired or the
         * specified time has passed, then returns {@code true} in the first case and {@code false}
         * in the second case.
         *
         * <p>If the thread executing this method is interrupted, this method will throw a runtime
         * exception.
         *
         * <p>This method will try acquiring the lock first before checking for timeout. A
         * non-positive timeout means the method will return immediately after the first try. This
         * behavior is similar to a {@link ReentrantReadWriteLock}.
         *
         * @param timeout the maximum time to wait for the lock
         * @param timeUnit the unit of the timeout
         * @return {@code true} if the lock was acquired
         */
        @SuppressWarnings("SameParameterValue")
        boolean tryLock(long timeout, @NonNull TimeUnit timeUnit) throws IOException;

        /** Releases the lock. This method does not block. */
        void unlock() throws IOException;
    }

    @Immutable
    private final class ReadLock implements Lock {

        @Override
        public void lock() throws IOException {
            acquireLock(true);
        }

        @Override
        public boolean tryLock(long timeout, @NonNull TimeUnit timeUnit) throws IOException {
            return tryAcquireLock(true, TimeUnit.NANOSECONDS.convert(timeout, timeUnit));
        }

        @Override
        public void unlock() throws IOException {
            releaseLock(true);
        }
    }

    @Immutable
    private final class WriteLock implements Lock {

        @Override
        public void lock() throws IOException {
            acquireLock(false);
        }

        @Override
        public boolean tryLock(long timeout, @NonNull TimeUnit timeUnit) throws IOException {
            return tryAcquireLock(false, TimeUnit.NANOSECONDS.convert(timeout, timeUnit));
        }

        @Override
        public void unlock() throws IOException {
            releaseLock(false);
        }
    }
}
