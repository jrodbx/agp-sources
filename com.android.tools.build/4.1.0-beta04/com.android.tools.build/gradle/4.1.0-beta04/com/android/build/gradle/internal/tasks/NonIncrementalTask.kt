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
import org.gradle.api.tasks.TaskAction

/**
 * Base non-incremental task.
 */
abstract class NonIncrementalTask : AndroidVariantTask() {

    @Throws(Exception::class)
    protected abstract fun doTaskAction()

    @TaskAction
    fun taskAction() {
        recordTaskAction {
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