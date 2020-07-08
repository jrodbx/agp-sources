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

package com.android.builder.profile;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType;
import com.google.wireless.android.sdk.stats.GradleTransformExecution;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Facility to record block execution time on a single thread. Threads should not be spawned during
 * the block execution as its processing will not be recorded as of the parent's execution time.
 *
 * <p>// TODO : provide facilities to create a new ThreadRecorder using a parent so the slave
 * threads can be connected to the parent's task.
 */
public final class ThreadRecorder implements Recorder {

    // Dummy implementation that records nothing but comply to the overall recording contracts.
    private static final Recorder NO_OP_RECORDER = new NoOpRecorder();
    private static final AtomicLong THREAD_ID_ALLOCATOR = new AtomicLong(1);
    private static final Recorder RECORDER = new ThreadRecorder();

    /**
     * Do not put anything else than JDK classes in the ThreadLocal as it prevents that class and
     * therefore the plugin classloader to be gc'ed leading to OOM or PermGen issues.
     */
    protected final ThreadLocal<Deque<Long>> recordStacks =
            ThreadLocal.withInitial(ArrayDeque::new);

    //protected final ThreadLocal<Long> threadId =
    //        ThreadLocal.withInitial(THREAD_ID_ALLOCATOR::getAndIncrement);

    public static Recorder get() {
        return ProcessProfileWriterFactory.getFactory().isInitialized() ? RECORDER : NO_OP_RECORDER;
    }


    @Nullable
    @Override
    public <T> T record(
            @NonNull ExecutionType executionType,
            @NonNull String projectPath,
            @Nullable String variant,
            @NonNull Block<T> block) {
        return record(executionType, null, projectPath, variant, block);
    }

    @Override
    public void record(
            @NonNull ExecutionType executionType,
            @NonNull String projectPath,
            @Nullable String variant,
            @NonNull VoidBlock block) {
        ProfileRecordWriter profileRecordWriter = ProcessProfileWriter.get();
        GradleBuildProfileSpan.Builder currentRecord =
                create(profileRecordWriter, executionType, null);
        try {
            block.call();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            write(profileRecordWriter, currentRecord, projectPath, variant);
            if (recordStacks.get().isEmpty()) {
                recordStacks.remove();
            }
        }
    }

    @Nullable
    @Override
    public <T> T record(
            @NonNull ExecutionType executionType,
            @Nullable GradleTransformExecution transform,
            @NonNull String projectPath,
            @Nullable String variant,
            @NonNull Block<T> block) {
        ProfileRecordWriter profileRecordWriter = ProcessProfileWriter.get();

        GradleBuildProfileSpan.Builder currentRecord =
                create(profileRecordWriter, executionType, transform);
        try {
            return block.call();
        } catch (Exception e) {
            block.handleException(e);
        } finally {
            write(profileRecordWriter, currentRecord, projectPath, variant);
            if (recordStacks.get().isEmpty()) {
                recordStacks.remove();
            }
        }
        // we always return null when an exception occurred and was not rethrown.
        return null;
    }

    private GradleBuildProfileSpan.Builder create(
            @NonNull ProfileRecordWriter profileRecordWriter,
            @NonNull ExecutionType executionType,
            @Nullable GradleTransformExecution transform) {
        long thisRecordId = profileRecordWriter.allocateRecordId();

        // am I a child ?
        @Nullable
        Long parentId = recordStacks.get().peek();

        long startTimeInMs = System.currentTimeMillis();

        final GradleBuildProfileSpan.Builder currentRecord =
                GradleBuildProfileSpan.newBuilder()
                        .setId(thisRecordId)
                        .setType(executionType)
                        .setThreadId(Thread.currentThread().getId())
                        .setStartTimeInMs(startTimeInMs);

        if (transform != null) {
            currentRecord.setTransform(transform);
        }

        if (parentId != null) {
            currentRecord.setParentId(parentId);
        }

        currentRecord.setThreadId(Thread.currentThread().getId());
        recordStacks.get().push(thisRecordId);
        return currentRecord;
    }

    private void write(
            @NonNull ProfileRecordWriter profileRecordWriter,
            @NonNull GradleBuildProfileSpan.Builder currentRecord,
            @NonNull String projectPath,
            @Nullable String variant) {
        // pop this record from the stack.
        if (recordStacks.get().pop() != currentRecord.getId()) {
            Logger.getLogger(ThreadRecorder.class.getName())
                    .log(Level.SEVERE, "Profiler stack corrupted");
        }
        currentRecord.setDurationInMs(
                System.currentTimeMillis() - currentRecord.getStartTimeInMs());
        profileRecordWriter.writeRecord(projectPath, variant, currentRecord, ImmutableList.of());
    }
}
