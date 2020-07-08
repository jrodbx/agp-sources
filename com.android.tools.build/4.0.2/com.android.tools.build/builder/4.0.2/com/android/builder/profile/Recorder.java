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
import java.util.concurrent.Callable;

/**
 * A {@link GradleBuildProfileSpan} recorder for a block execution.
 *
 * <p>A block is some code that produces a result and may throw exceptions.
 */
public interface Recorder {

    /**
     * Abstraction of a block of code that produces a result of type T and may throw exceptions. Any
     * exception thrown by {@link Callable#call()} will be passed to the {@link
     * #handleException(Exception)} method. Default implementation of this method is to repackage
     * the exception as {@link RuntimeException} unless it already is one.
     *
     * @param <T> the type of result produced by executing this block of code.
     */
    interface Block<T> extends Callable<T> {

        /**
         * Notification that an exception was raised during the {@link #call()} method invocation.
         * Default behavior is to repackage as a {@link RuntimeException}, subclasses can choose
         * differently including swallowing the exception. Swallowing the exception will make the
         * {@link Recorder#record(ExecutionType, String, String, Block)} return null.
         *
         * @param e the exception raised during the {@link #call()} execution.
         */
        default void handleException(@NonNull Exception e) {
            // by default we rethrow as a runtime exception, implementations should override for
            // more precise handling.
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }

    interface VoidBlock {
        void call() throws IOException;
    }

    /**
     * Records the time elapsed while executing a {@link Block} and saves the resulting {@link
     * GradleBuildProfileSpan} to {@link ProcessProfileWriter}.
     *
     * @param <T> the type of the returned value from the block.
     * @param executionType the task type, so aggregation can be performed.
     * @param projectPath the full path of the project that contains this span. (e.g. ":a:b")
     * @param variant the variant that contains this span.
     * @param block the block of code to execution and measure.
     * @return the value returned from the block (including null) or null if the block execution
     *     raised an exception which was subsequently swallowed by {@link
     *     Block#handleException(Exception)}
     */
    @Nullable
    <T> T record(
            @NonNull ExecutionType executionType,
            @NonNull String projectPath,
            @Nullable String variant,
            @NonNull Block<T> block);

    /**
     * Records the time elapsed while executing a {@link VoidBlock} and saves the resulting {@link
     * GradleBuildProfileSpan} to {@link ProfileRecordWriter}.
     *
     * @param executionType the task type, so aggregation can be performed.
     * @param projectPath the full path of the project that contains this span. (e.g. ":a:b")
     * @param variant the variant that contains this span.
     * @param block the block of code to execution and measure.
     */
    void record(
            @NonNull ExecutionType executionType,
            @NonNull String projectPath,
            @Nullable String variant,
            @NonNull VoidBlock block);

    /**
     * Records the time elapsed while executing a {@link Block} and saves the resulting {@link
     * GradleBuildProfileSpan} to {@link ProcessProfileWriter}.
     *
     * @param <T> the type of the returned value from the block.
     * @param executionType the task type, so aggregation can be performed.
     * @param projectPath the full path of the project that contains this span. (e.g. ":a:b")
     * @param variant the variant that contains this span.
     * @param block the block of code to execution and measure.
     * @return the value returned from the block (including null) or null if the block execution
     *     raised an exception which was subsequently swallowed by {@link
     *     Block#handleException(Exception)}
     */
    @Nullable
    <T> T record(
            @NonNull ExecutionType executionType,
            @Nullable GradleTransformExecution transform,
            @NonNull String projectPath,
            @Nullable String variant,
            @NonNull Block<T> block);

}
