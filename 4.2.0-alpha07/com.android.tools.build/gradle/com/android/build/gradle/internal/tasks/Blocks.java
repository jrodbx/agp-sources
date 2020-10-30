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

import com.android.build.gradle.internal.profile.TaskProfilingRecord;
import com.android.ide.common.workers.GradlePluginMBeans;
import com.android.ide.common.workers.ProfileMBean;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class Blocks {

    @FunctionalInterface
    public interface ThrowingBlockWithReturn<T, E extends Exception> {
        T invoke() throws E;
    }

    @FunctionalInterface
    public interface ThrowingBlock<E extends Exception> {
        void invoke() throws E;
    }

    /**
     * Record execution span for a task phase.
     *
     * @param projectName the project name executing
     * @param taskPath the task path executing
     * @param type the type of the execution phase
     * @param block the block of code to record execution on.
     * @param <E> exception thrown by the block
     * @throws E re-thrown exception if the block threw it.
     */
    public static <E extends Exception> void recordSpan(
            String projectName,
            String taskPath,
            GradleBuildProfileSpan.ExecutionType type,
            ThrowingBlock<E> block)
            throws E {

        ProfileMBean profileMBean = GradlePluginMBeans.INSTANCE.getProfileMBean(projectName);
        Instant before = TaskProfilingRecord.Companion.getClock().instant();
        block.invoke();
        Instant after = TaskProfilingRecord.Companion.getClock().instant();
        if (profileMBean != null) {
            profileMBean.registerSpan(
                    taskPath,
                    GradleBuildProfileSpan.newBuilder()
                        .setType(type)
                        .setThreadId(Thread.currentThread().getId())
                        .setStartTimeInMs(before.toEpochMilli())
                        .setDurationInMs(Duration.between(before, after).toMillis()));
        }
    }

    /**
     * Record execution span for a task phase that returns a value.
     *
     * @param projectName the project name executing
     * @param taskPath the task path executing
     * @param type the type of the execution phase
     * @param block the block of code to record execution on.
     * @param <T> the type of return value from the block.
     * @param <E> exception thrown by the block
     * @throws E re-thrown exception if the block threw it.
     */
    public static <T, E extends Exception> T recordSpan(
            String projectName,
            String taskPath,
            GradleBuildProfileSpan.ExecutionType type,
            ThrowingBlockWithReturn<T, E> block)
            throws E {

        ProfileMBean profileMBean = GradlePluginMBeans.INSTANCE.getProfileMBean(projectName);
        Instant before = Clock.systemDefaultZone().instant();
        T t = block.invoke();
        Instant after = TaskProfilingRecord.Companion.getClock().instant();
        if (profileMBean != null) {
            profileMBean.registerSpan(
                    taskPath,
                    GradleBuildProfileSpan.newBuilder()
                            .setType(type)
                            .setThreadId(Thread.currentThread().getId())
                            .setStartTimeInMs(before.toEpochMilli())
                            .setDurationInMs(Duration.between(before, after).toMillis()));
        }
        return t;
    }
}
