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

package com.android.build.gradle.internal.profile

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.tasks.VariantAwareTask
import com.android.builder.profile.ProcessProfileWriter
import com.android.builder.profile.ProfileRecordWriter
import com.android.builder.profile.Recorder
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.tasks.TaskState
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of the [TaskExecutionListener] that records the execution span of
 * tasks execution and records such spans using the [Recorder] facilities.
 */
class RecordingBuildListener internal constructor(
    private val projectName: String,
    private val recordWriter: ProfileRecordWriter) :
    TaskExecutionListener {
    // map of outstanding tasks executing, keyed by their path.
    private val taskRecords = ConcurrentHashMap<String, TaskProfilingRecord>()

    override fun beforeExecute(task: Task) {
        logger.verbose("Task ${task.path} in $projectName Starting")
        val builder = GradleBuildProfileSpan.newBuilder()
        builder.type = ExecutionType.TASK_EXECUTION
        builder.id = recordWriter.allocateRecordId()
        builder.threadId = Thread.currentThread().id

        val taskRecord = TaskProfilingRecord(
            recordWriter,
            builder,
            task.path,
            task.project.path,
            getVariantName(task)
        )

        taskRecords[task.path] = taskRecord
    }

    override fun afterExecute(task: Task, taskState: TaskState) {
        logger.verbose("Task ${task.path} in $projectName Finished")
        try {
            val taskRecord = taskRecords[task.path] ?: return
            val record = taskRecord.spanBuilder

            record.taskBuilder
                    .setType(
                        AnalyticsUtil.getTaskExecutionType(task.javaClass).number
                    )
                    .setDidWork(taskState.didWork)
                    .setSkipped(taskState.skipped)
                    .setUpToDate(taskState.upToDate)
                    .setFailed(taskState.failure != null)

            taskRecord.setTaskFinished()
            // check that all workers are done before closing this span.
            if (taskRecord.allWorkersFinished()) {
                taskRecord.writeTaskSpan()
                closeTaskRecord(task.path)
            }
        } finally {
            ProcessProfileWriter.recordMemorySample()
        }
    }

    fun getTaskRecord(taskPath: String): TaskProfilingRecord? = taskRecords[taskPath]

    private fun closeTaskRecord(taskPath: String) {
        taskRecords.remove(taskPath)
    }

    fun getWorkerRecord(taskPath: String, worker: String): WorkerProfilingRecord? =
        getTaskRecord(taskPath)?.get(worker)

    fun recordAnonymousSpan(builder: GradleBuildProfileSpan.Builder) {
        recordWriter.writeRecord(":$projectName", null,
            builder.setId(recordWriter.allocateRecordId()),
            listOf())

    }

    companion object {
        private val logger = LoggerWrapper.getLogger(RecordingBuildListener::class.java)

        private fun getVariantName(task: Task): String? {
            return if (task is VariantAwareTask) {
                task.variantName.takeIf { it.isNotEmpty() }
            } else {
                task.extensions.findByName(PROPERTY_VARIANT_NAME_KEY) as String?
            }
        }
    }
}

const val PROPERTY_VARIANT_NAME_KEY = "AGP_VARIANT_NAME"
