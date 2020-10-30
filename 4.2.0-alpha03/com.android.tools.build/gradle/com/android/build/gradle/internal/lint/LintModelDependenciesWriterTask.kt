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

package com.android.build.gradle.internal.lint

import com.android.build.gradle.internal.component.BaseCreationConfig
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.tools.lint.model.LintModelDependencies
import org.gradle.api.tasks.TaskProvider

/**
 * Task to write the [LintModelDependencies] representation of the variant dependencies on disk.
 *
 * This serialized [LintModelDependencies] file is then consumed by Lint in consuming projects to get
 * all the information about this variant's dependencies.
 */
abstract class LintModelDependenciesWriterTask : NonIncrementalTask() {

    override fun doTaskAction() {
     //   TODO("Not yet implemented")
    }

    class CreationAction(creationConfig: BaseCreationConfig) :
        VariantTaskCreationAction<LintModelDependenciesWriterTask, BaseCreationConfig>(creationConfig) {

        override val name: String
            get() = "" //TODO("Not yet implemented")
        override val type: Class<LintModelDependenciesWriterTask>
            get() = LintModelDependenciesWriterTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<LintModelDependenciesWriterTask>) {
            super.handleProvider(taskProvider)
        }

        override fun configure(task: LintModelDependenciesWriterTask) {
            super.configure(task)
        }
    }
}