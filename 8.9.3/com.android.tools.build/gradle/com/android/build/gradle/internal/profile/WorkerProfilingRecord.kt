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

package com.android.build.gradle.internal.profile

import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import java.io.Serializable
import java.lang.IllegalStateException
import java.time.Duration
import java.time.Instant

/**
 * Gradle [org.gradle.workers.WorkerExecutor] Worker item book keeping record.
 *
 * @param taskName the task name for the [org.gradle.api.Task] that submitted the Worker Item.
 * @param submissionTime the absolute time in ms the Worker Item was submitted to the executor.
 */
class WorkerProfilingRecord(
    private val taskName: String,
    private val type: GradleBuildProfileSpan.ExecutionType,
    private val submissionTime: Instant
) : Serializable {

    private var startTime: Instant = Instant.MIN
    internal var endTime: Instant = Instant.MIN

    @Synchronized
    fun isStarted() = startTime != Instant.MIN

    fun isFinished() = endTime != Instant.MIN

    fun waitTime(): Duration = if (isStarted())
        Duration.between(submissionTime, startTime) else Duration.ZERO

    fun duration(): Duration = if (isStarted()&& isFinished())
        Duration.between(startTime, endTime) else Duration.ZERO

    /**
     * Notification that the Worker Item has started execution.
     */
    @Synchronized
    fun executionStarted() {
        startTime = TaskProfilingRecord.clock.instant()
    }

    /**
     * Notification that the Worker Item has finished execution.
     */
    fun executionFinished() {
        endTime = TaskProfilingRecord.clock.instant()
    }

    fun fillSpanRecord(span: GradleBuildProfileSpan.Builder) {

        // due to some asynchronicity, it is possible that we have not been
        // notified of the startTime by the time this code executes, in that case,
        // just use the end time (which we are sure to have) and duration should be
        // effectively zero.
        val effectiveStartTime = if (isStarted()) startTime else endTime
        // create the span for the worker item itself
        span.setThreadId(Thread.currentThread().id)
            .setStartTimeInMs(effectiveStartTime.toEpochMilli())
            .setDurationInMs(duration().toMillis())
            .setType(type)
    }

    override fun toString(): String {
        return "Worker for $taskName"
    }
}
