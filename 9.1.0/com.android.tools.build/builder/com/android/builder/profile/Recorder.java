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

/**
 * A {@link GradleBuildProfileSpan} recorder for a block execution.
 *
 * <p>A block is some code that produces a result and may throw exceptions.
 */
public interface Recorder {

    interface VoidBlock {
        void call() throws Exception;
    }

    /**
     * Creates a {@link GradleBuildProfileSpan} to record a block execution.
     *
     * @param executionType the task type, so aggregation can be performed.
     * @param projectId the id of the project that contains this span.
     * @param variantId the id of the variant that contains this span.
     * @param recordId the id allocated for this span.
     * @param block the block of code to execution and measure.
     * @return the {@link GradleBuildProfileSpan} created for this block execution
     */
    @Nullable
    GradleBuildProfileSpan record(
            @NonNull ExecutionType executionType,
            @NonNull Long projectId,
            @NonNull Long variantId,
            @NonNull Long recordId,
            @NonNull VoidBlock block);

    /**
     * Creates a {@link GradleBuildProfileSpan} to record a block execution.
     *
     * @param executionType the task type, so aggregation can be performed.
     * @param transform the gradle transform execution
     * @param projectId the id of the project that contains this span.
     * @param variantId the id of the variant that contains this span.
     * @param recordId the id allocated for this span.
     * @param block the block of code to execution and measure.
     * @return the {@link GradleBuildProfileSpan} created for this block execution
     */
    @Nullable
    GradleBuildProfileSpan record(
            @NonNull ExecutionType executionType,
            @Nullable GradleTransformExecution transform,
            @Nullable Long projectId,
            @Nullable Long variantId,
            @NonNull Long recordId,
            @NonNull VoidBlock block);
}
