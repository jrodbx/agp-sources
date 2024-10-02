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

package com.android.build.gradle.internal.profile

import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters

/** A work action for AGP worker action to allow better reporting. */
abstract class ProfileAwareWorkAction<T : ProfileAwareWorkAction.Parameters> : WorkAction<T> {

    abstract class Parameters : WorkParameters {
        abstract val projectPath: Property<String>
        abstract val taskOwner: Property<String>
        abstract val workerKey: Property<String>
        abstract val analyticsService: Property<AnalyticsService>
        fun initializeFromAndroidVariantTask(task: AndroidVariantTask) {
            initializeWith(task.projectPath, task.path, task.analyticsService)
        }
        fun initializeWith(
            projectPath: Provider<String>,
            taskOwner:  String,
            analyticsService: Provider<AnalyticsService>
        ) {
            val workerKeyString = "$taskOwner{${this.javaClass.name}${this.hashCode()}"
            initAllProperties(projectPath, taskOwner, workerKeyString, analyticsService)
        }
        fun initializeFromProfileAwareWorkAction(workAction: Parameters) {
            val workerKeyString = "${workAction.workerKey.get()}${this.hashCode()}"
            initAllProperties(
                workAction.projectPath,
                workAction.taskOwner.get(),
                workerKeyString,
                workAction.analyticsService
            )
        }
        private fun initAllProperties(
            projectPath: Provider<String>,
            taskOwner: String,
            workerKey: String,
            analyticsService: Provider<AnalyticsService>
        ) {
            this.projectPath.setDisallowChanges(projectPath)
            this.taskOwner.setDisallowChanges(taskOwner)
            this.workerKey.setDisallowChanges(workerKey)
            this.analyticsService.setDisallowChanges(analyticsService)
            this.analyticsService.get()
                .getTaskRecord(taskOwner)
                ?.addWorker(workerKey, GradleBuildProfileSpan.ExecutionType.WORKER_EXECUTION)
        }
    }

    final override fun execute() {
        parameters.analyticsService.get()
            .workerStarted(parameters.taskOwner.get(), parameters.workerKey.get())
        run()
        parameters.analyticsService.get()
            .workerFinished(parameters.taskOwner.get(), parameters.workerKey.get())
    }

    abstract fun run()
}
