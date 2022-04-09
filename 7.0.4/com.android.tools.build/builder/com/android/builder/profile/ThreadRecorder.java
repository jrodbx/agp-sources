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
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType;
import com.google.wireless.android.sdk.stats.GradleTransformExecution;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.Deque;
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

    /**
     * Do not put anything else than JDK classes in the ThreadLocal as it prevents that class and
     * therefore the plugin classloader to be gc'ed leading to OOM or PermGen issues.
     */
    protected final ThreadLocal<Deque<Long>> recordStacks =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Override
    public GradleBuildProfileSpan record(
            @NonNull ExecutionType executionType,
            @NonNull Long projectId,
            @NonNull Long variantId,
            @NonNull Long recordId,
            @NonNull VoidBlock block) {
        return record(executionType, null, projectId, variantId, recordId, block);
    }

    @Override
    public GradleBuildProfileSpan record(
            @NonNull ExecutionType executionType,
            @Nullable GradleTransformExecution transform,
            @Nullable Long projectId,
            @Nullable Long variantId,
            @NonNull Long recordId,
            @NonNull VoidBlock block) {

        GradleBuildProfileSpan.Builder currentRecord =
                create(recordId, executionType, transform);
        try {
            block.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            // pop this record from the stack.
            if (recordStacks.get().pop() != currentRecord.getId()) {
                Logger.getLogger(ThreadRecorder.class.getName())
                        .log(Level.SEVERE, "Profiler stack corrupted");
            }
            currentRecord.setDurationInMs(
                    System.currentTimeMillis() - currentRecord.getStartTimeInMs());
            if (projectId != null) {
                currentRecord.setProject(projectId);
            }
            if (variantId != null) {
                currentRecord.setVariant(variantId);
            }
            if (recordStacks.get().isEmpty()) {
                recordStacks.remove();
            }
        }
        return currentRecord.build();
    }

    private GradleBuildProfileSpan.Builder create(
            @NonNull Long recordId,
            @NonNull ExecutionType executionType,
            @Nullable GradleTransformExecution transform) {

        // am I a child ?
        @Nullable
        Long parentId = recordStacks.get().peek();

        long startTimeInMs = System.currentTimeMillis();

        final GradleBuildProfileSpan.Builder currentRecord =
                GradleBuildProfileSpan.newBuilder()
                        .setId(recordId)
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
        recordStacks.get().push(recordId);
        return currentRecord;
    }
}
