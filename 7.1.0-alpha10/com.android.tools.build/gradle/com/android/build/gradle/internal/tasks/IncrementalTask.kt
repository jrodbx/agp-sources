/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.ide.common.resources.FileStatus.CHANGED
import com.android.ide.common.resources.FileStatus.NEW
import com.android.ide.common.resources.FileStatus.REMOVED
import com.android.ide.common.resources.FileStatus
import com.google.common.collect.Maps
import org.apache.log4j.Logger
import java.io.File
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.work.DisableCachingByDefault

/**
 * A helper class to support the writing of tasks that support doing less work if they have already
 * been fully built, for example if they only need to operate on the files that have changed between
 * the previous build and the current one.
 *
 *
 * The API that inheriting classes are to implement consists of three methods, two of which are
 * optional:
 *
 * <pre>`public class MyTask extends IncrementalTask {
 * // This is the only non-optional method. This will be run when it's not possible to run
 * // this task incrementally. By default, it is never possible to run your task
 * // incrementally. You must implement the next method to determine that.
 *
 * protected void doFullTaskAction() throws Exception {}
 *
 * // This is the method that determines if your task can be run incrementally. If it returns
 * // true, the next and last override method is run instead of doFullTaskAction().
 *
 * protected boolean isIncremental() {}
 *
 * // If you've determined that it's possible to save some time and only operate on the files
 * // that have changed between the previous build and now, you can define that in this
 * // method.
 *
 * protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs)
 * throws Exception {}
 * }
 *
`</pre> *
 *
 * @deprecate Use [NewIncrementalTask]
 */
@Deprecated("Use [NewIncrementalTask]")
@DisableCachingByDefault
abstract class IncrementalTask : AndroidVariantTask() {

    @get:OutputDirectory
    @get:Optional
    var incrementalFolder: File? = null

    /**
     * @return whether this task can support incremental update.
     */
    @get:Internal
    protected open val incremental: Boolean
        get() = false

    /**
     * This method will be called in inheriting classes if it is determined that it is not possible
     * to do this task incrementally.
     */
    @Throws(Exception::class)
    protected abstract fun doFullTaskAction()

    /**
     * Optional incremental task action. Only used if [.isIncremental] returns true.
     *
     * @param changedInputs input files that have changed since the last run of this task.
     */
    @Throws(Exception::class)
    protected open fun doIncrementalTaskAction(changedInputs: Map<File, FileStatus>) {
        // do nothing.
    }

    /**
     * Gradle's entry-point into this task. Determines whether or not it's possible to do this task
     * incrementally and calls either doIncrementalTaskAction() if an incremental build is possible,
     * and doFullTaskAction() if not.
     */
    @TaskAction
    internal fun taskAction(inputs: IncrementalTaskInputs) {
        recordTaskAction(analyticsService.get()) { handleIncrementalInputs(inputs) }
    }

    private fun handleIncrementalInputs(inputs: IncrementalTaskInputs) {
        if (!incremental || !inputs.isIncremental) {
            Logger.getLogger(IncrementalTask::class.java)
                .info("Unable do incremental execution: full task run")
            cleanUpTaskOutputs()
            doFullTaskAction()
            return
        }

        doIncrementalTaskAction(getChangedInputs(inputs))
    }

    private fun getChangedInputs(inputs: IncrementalTaskInputs): Map<File, FileStatus> {
        val changedInputs = Maps.newHashMap<File, FileStatus>()

        inputs.outOfDate { change ->
            val status = if (change.isAdded) NEW else CHANGED
            changedInputs[change.file] = status
        }

        inputs.removed { change ->
            // If input is added *and* removed, that's Gradle's way of saying the input's
            // order has changed relative to other inputs, and it also *might* have changed,
            // so we mark it as changed.
            val status = if (changedInputs[change.file] == NEW) CHANGED else REMOVED
            changedInputs[change.file] = status
        }

        return changedInputs
    }
}
