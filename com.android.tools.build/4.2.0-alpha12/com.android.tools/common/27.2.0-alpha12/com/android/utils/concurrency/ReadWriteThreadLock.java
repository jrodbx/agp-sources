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
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.reflect.TypeToken;
import java.io.File;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A lock similar to {@link ReentrantReadWriteLock} used to synchronize threads within the same JVM,
 * even when classes (including this class) are loaded multiple times by different class loaders.
 *
 * <p>Motivation of {@code ReadWriteThreadLock}: When attempting to synchronize threads within the
 * same JVM (e.g., making sure that only one instance of a class executes some action at a time), we
 * may choose to create a lock associated with the class and require instances of the class to
 * acquire the lock before executing. However, if that class is loaded multiple times by different
 * class loaders, the JVM considers them as different classes, and there will be multiple locks
 * associated with those classes. The desired effect of synchronizing all threads within the JVM is
 * not achieved; instead, each lock can only take effect for instances of the same class loaded by
 * the same class loader.
 *
 * <p>We create {@code ReadWriteThreadLock} to address that limitation. A {@code
 * ReadWriteThreadLock} can be used to synchronize *all* threads in a JVM, even when a class using
 * {@code ReadWriteThreadLock} or the {@code ReadWriteThreadLock} class itself is loaded multiple
 * times by different class loaders.
 *
 * <p>Threads will be synchronized on the same lock object (two lock objects are the same if one
 * equals() the other). The client using {@code ReadWriteThreadLock} will provide a lock object when
 * constructing a {@code ReadWriteThreadLock} instance. Then, different threads using the same or
 * different {@code ReadWriteThreadLock} instances on the same lock object can be synchronized.
 *
 * <p>The basic usage of this class is similar to {@link ReentrantReadWriteLock} and {@link
 * java.util.concurrent.locks.Lock}. Below is a typical example.
 *
 * <pre>{@code
 * ReadWriteThreadLock readWriteThreadLock = new ReadWriteThreadLock(lockObject);
 * ReadWriteThreadLock.Lock lock =
 *     useSharedLock
 *         ? readWriteThreadLock.readLock()
 *         : readWriteThreadLock.writeLock();
 * lock.lock();
 * try {
 *     runnable.run();
 * } finally {
 *     lock.unlock();
 * }
 * }</pre>
 *
 * <p>The key usage differences between {@code ReadWriteThreadLock} and a regular Java lock such as
 * {@link ReentrantReadWriteLock} are:
 *
 * <ol>
 *   <li>{@code ReadWriteThreadLock} is itself not a lock object (which threads are directly
 *       synchronized on), but a proxy to the actual lock object. Therefore, there could be multiple
 *       instances of {@code ReadWriteThreadLock} on the same lock object.
 *   <li>Two lock objects are considered the same if one equals() the other.
 * </ol>
 *
 * <p>This lock is reentrant, down-gradable, but not upgradable (similar to {@link
 * ReentrantReadWriteLock}).
 *
 * <p>This class is thread-safe.
 */
@Immutable
public final class ReadWriteThreadLock {

    /** The lock used for reading. */
    @NonNull private final ReadWriteThreadLock.Lock readLock = new ReadWriteThreadLock.ReadLock();

    /** The lock used for writing. */
    @NonNull private final ReadWriteThreadLock.Lock writeLock = new ReadWriteThreadLock.WriteLock();

    /** The lock object. */
    @NonNull private final Object lockObject;

    /**
     * A lock used to synchronize threads within the current JVM. This lock is shared across all
     * instances of {@code ReadWriteThreadLock} (and also across class loaders) in the current JVM
     * for the given lock object.
     */
    @NonNull private final ReentrantReadWriteLock lock;

    /**
     * Creates a {@code ReadWriteThreadLock} instance for the given lock object. Threads will be
     * synchronized on the same lock object (two lock objects are the same if one equals() the
     * other).
     *
     * <p>The class of the lock object must be loaded only once, to avoid accidentally comparing two
     * objects of the same class where the class is loaded multiple times by different class
     * loaders, and therefore one never equals() the other.
     *
     * <p>If the client uses a file as the lock object, in order for this class to detect same
     * physical files via equals() the client needs to normalize the lock file's path when
     * constructing a {@code ReadWriteThreadLock} (preferably using {@link
     * Path#toRealPath(LinkOption...)} with follow-link or {@link File#getCanonicalFile()}, as there
     * are subtle issues with other methods such as {@link Path#normalize()} or {@link
     * File#getCanonicalPath()}). {@link Path} is slightly preferred to {@link File} for consistency
     * with {@link ReadWriteProcessLock}.
     *
     * @param lockObject the lock object, whose class must be loaded only once
     */
    public ReadWriteThreadLock(@NonNull Object lockObject) {
        // Check that the lock object's class is loaded only once
        Class lockObjectClass =
                JvmWideVariable.getJvmWideObjectPerKey(
                        ReadWriteThreadLock.class,
                        "lockObjectClass",
                        TypeToken.of(String.class),
                        TypeToken.of(Class.class),
                        lockObject.getClass().getName(),
                        lockObject::getClass);
        Preconditions.checkArgument(
                lockObject.getClass() == lockObjectClass,
                String.format(
                        "Lock object's class %1$s must be loaded once but is loaded twice",
                        lockObject.getClass().getName()));

        this.lockObject = lockObject;
        this.lock =
                JvmWideVariable.getJvmWideObjectPerKey(
                        ReadWriteThreadLock.class,
                        "lock",
                        TypeToken.of(Object.class),
                        TypeToken.of(ReentrantReadWriteLock.class),
                        lockObject,
                        ReentrantReadWriteLock::new);
    }

    /** Returns the lock used for reading. */
    @NonNull
    public ReadWriteThreadLock.Lock readLock() {
        return readLock;
    }

    /** Returns the lock used for writing. */
    @NonNull
    public ReadWriteThreadLock.Lock writeLock() {
        return writeLock;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("lockObject", lockObject).toString();
    }

    public interface Lock {

        /**
         * Acquires the lock, blocking to wait until the lock can be acquired.
         *
         * <p>If the thread executing this method is interrupted, this method will throw a runtime
         * exception.
         */
        void lock();

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
        boolean tryLock(long timeout, @NonNull TimeUnit timeUnit);

        /** Releases the lock. This method does not block. */
        void unlock();
    }

    @Immutable
    private final class ReadLock implements Lock {

        @Override
        public void lock() {
            lock.readLock().lock();
        }

        @Override
        public boolean tryLock(long timeout, @NonNull TimeUnit timeUnit) {
            try {
                return lock.readLock().tryLock(timeout, timeUnit);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void unlock() {
            lock.readLock().unlock();
        }
    }

    @Immutable
    private final class WriteLock implements Lock {

        @Override
        public void lock() {
            lock.writeLock().lock();
        }

        @Override
        public boolean tryLock(long timeout, @NonNull TimeUnit timeUnit) {
            try {
                return lock.writeLock().tryLock(timeout, timeUnit);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void unlock() {
            lock.writeLock().unlock();
        }
    }
}
