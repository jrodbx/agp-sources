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

package com.android.build.gradle.internal.tasks

import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Base class for tasks that don't use gradle's up-to-date checks.
 *
 * Be careful using this class, strongly prefer extending [NonIncrementalTask].
 *
 * Such tasks always run when in the task graph, and rely on the task implementation to handle
 * up-to-date checks e.g. the external native build tasks, where the underlying external build
 * system handles those checks. Lint does not use this class, as while it is never up-to-date
 * currently as it doesn't model its inputs, it should clean its outputs before running.
 *
 * Unlike [NonIncrementalTask], this task does **not** clean up its outputs before the task is run.
 * This means that the task implementation is responsible for ensuring that the outputs are correct
 * in that case.
 */
@DisableCachingByDefault
abstract class UnsafeOutputsTask(reasonToLog: String) : AndroidVariantTask() {

    init {
        outputs.upToDateWhen { task ->
            task.logger.debug(reasonToLog)
            return@upToDateWhen false
        }
    }

    @Throws(Exception::class)
    protected abstract fun doTaskAction()

    @TaskAction
    fun taskAction() {
        recordTaskAction(analyticsService.get()) {
            doTaskAction()
        }
    }
}

