/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks;

import com.android.annotations.Nullable;
import com.android.build.gradle.internal.profile.AnalyticsService;
import com.android.build.gradle.internal.profile.TaskProfilingRecord;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import java.time.Duration;
import java.time.Instant;

public class Blocks {

    @FunctionalInterface
    public interface ThrowingBlock<E extends Exception> {
        void invoke() throws E;
    }

    /**
     * Record execution span for a task phase.
     *
     * @param taskPath the task path executing
     * @param type the type of the execution phase
     * @param analyticsService the build service to record execution spans
     * @param block the block of code to record execution on.
     * @param <E> exception thrown by the block
     * @throws E re-thrown exception if the block threw it.
     */
    public static <E extends Exception> void recordSpan(
            String taskPath,
            GradleBuildProfileSpan.ExecutionType type,
            @Nullable AnalyticsService analyticsService,
            ThrowingBlock<E> block)
            throws E {

        Instant before = TaskProfilingRecord.Companion.getClock().instant();
        block.invoke();
        Instant after = TaskProfilingRecord.Companion.getClock().instant();
        if (analyticsService != null) {
            analyticsService.registerSpan(
                    taskPath,
                    GradleBuildProfileSpan.newBuilder()
                            .setType(type)
                            .setThreadId(Thread.currentThread().getId())
                            .setStartTimeInMs(before.toEpochMilli())
                            .setDurationInMs(Duration.between(before, after).toMillis()));
        }
    }
}
