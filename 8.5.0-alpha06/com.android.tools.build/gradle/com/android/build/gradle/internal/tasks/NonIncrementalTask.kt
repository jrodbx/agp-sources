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

import com.android.utils.FileUtils
import org.gradle.api.Task
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.lang.RuntimeException

/**
 * Base variant-aware non-incremental task
 */
@DisableCachingByDefault
abstract class NonIncrementalTask : AndroidVariantTask() {

    @Throws(Exception::class)
    protected abstract fun doTaskAction()

    @TaskAction
    fun taskAction() {
        recordTaskAction(analyticsService.orNull) {
            cleanUpTaskOutputs()
            doTaskAction()
        }
    }
}

/**
 * Base non variant-aware non-incremental task
 *
 * This currently needs to implement [VariantAwareTask] because our analytics
 * require [variantName] to be present so that we can ensure that it's always
 * set for variant aware tasks. For global task it's meant to be empty.
 *
 * TODO figure out a better mechanism that does not require global tasks to have a variant name
 */
@DisableCachingByDefault
abstract class NonIncrementalGlobalTask : BaseTask(), VariantAwareTask {

    // FIXME should be final once LintPerVariantTask is changed to not inherit from this.
    @get:Internal
    override var variantName: String
        get() = ""
        set(value) {
            throw RuntimeException("Do not set variant name on NonIncrementalGlobalTask")
        }

    @Throws(Exception::class)
    protected abstract fun doTaskAction()

    @TaskAction
    fun taskAction() {
        recordTaskAction(analyticsService.get()) {
            cleanUpTaskOutputs()
            doTaskAction()
        }
    }
}

/**
 * Used to ensure task outputs are deleted before a task is run
 * non-incrementally.
 *
 * To avoid issues such as http://issuetracker.google.com/150274427#comment17
 * where the current workaround is for users to delete build directories manually after AGP updates,
 */
fun Task.cleanUpTaskOutputs() {
    for (file in outputs.files) {
        if (file.isDirectory) {
            // Only clear output directory contents, keep the directory.
            FileUtils.deleteDirectoryContents(file)
        } else {
            FileUtils.deletePath(file)
        }
    }
}
