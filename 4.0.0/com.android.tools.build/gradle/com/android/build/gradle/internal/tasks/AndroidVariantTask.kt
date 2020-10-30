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

package com.android.build.gradle.internal.tasks

import com.android.ide.common.workers.WorkerExecutorFacade
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

/**
 * Base Android task with a variant name and support for analytics
 *
 * DO NOT EXTEND THIS METHOD DIRECTLY. Instead extend:
 * - [NewIncrementalTask]
 * - [NonIncrementalTask]
 *
 * */
abstract class AndroidVariantTask : DefaultTask(), VariantAwareTask {

    @Internal("No influence on output, this is for our build stats reporting mechanism")
    override lateinit var variantName: String

    @get:Internal
    val projectName: String = project.name

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @Internal
    fun getWorkerFacadeWithWorkers(): WorkerExecutorFacade {
        return Workers.preferWorkers(projectName, path, workerExecutor, enableGradleWorkers.get())
    }

    fun getWorkerFacadeWithThreads(useGradleExecutor: Boolean = false): WorkerExecutorFacade {
        return if (useGradleExecutor) {
            Workers.preferThreads(projectName, path, workerExecutor, enableGradleWorkers.get())
        } else {
            Workers.withThreads(projectName, path)
        }
    }

    /**
     * Called by subclasses that want to record something.
     *
     * The task execution will use [GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION_ALL_PHASES]
     * as the span type to record the [AndroidVariantTask.recordedTaskAction].
     */
    protected inline fun recordTaskAction(crossinline block: () -> Unit) {
        Blocks.recordSpan<Unit, Exception>(
            project.name,
            path,
            GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION_ALL_PHASES
        ) { block() }
    }
}
