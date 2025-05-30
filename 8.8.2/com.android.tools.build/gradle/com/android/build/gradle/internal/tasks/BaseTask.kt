/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.caching.DisabledCachingReason
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.work.DisableCachingByDefault
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

/**
 * Root Task class for all of AGP.
 *
 * DO NOT EXTEND THIS METHOD DIRECTLY. Instead, extend:
 * - [NewIncrementalTask] -- variant aware task
 * - [NonIncrementalTask] -- variant aware task
 * - [NonIncrementalGlobalTask] -- non variant aware task
 */
@DisableCachingByDefault(because = DisabledCachingReason.BASE_TASK)
abstract class BaseTask : DefaultTask(), UsesAnalytics {
    @get:Internal("only for task execution")
    abstract val projectPath: Property<String>

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    /**
     * Called by subclasses that want to record something.
     *
     * The task execution will use [GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION_ALL_PHASES]
     * as the span type to record the [AndroidVariantTask.recordedTaskAction].
     */
    protected inline fun recordTaskAction(crossinline block: () -> Unit) {
        Blocks.recordSpan<Exception>(
            path,
            GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION_ALL_PHASES,
            analyticsService.get()
        ) { block() }
    }

    abstract class CreationAction<TaskT: BaseTask> : TaskCreationAction<TaskT>() {

        override fun configure(task: TaskT) {
            super.configure(task)
            ConfigureAction.configure(task)
        }
    }

    object ConfigureAction {

        fun configure(task: BaseTask) {
            UsesAnalytics.ConfigureAction.configure(task)
            task.projectPath.setDisallowChanges(task.project.path)
        }
    }
}
