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
import com.android.ide.common.workers.GradlePluginMBeans
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import org.gradle.api.provider.Property
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters

/** A work action for AGP worker action to allow better reporting. */
abstract class ProfileAwareWorkAction<T : ProfileAwareWorkAction.Parameters> : WorkAction<T> {

    abstract class Parameters : WorkParameters {
        abstract val projectName: Property<String>
        abstract val taskOwner: Property<String>
        abstract val workerKey: Property<String>
        fun initializeFromAndroidVariantTask(task: AndroidVariantTask) {
            projectName.setDisallowChanges(task.projectName)
            val taskOwnerString = task.path
            taskOwner.setDisallowChanges(taskOwnerString)
            val workerKeyString = "$taskOwner{${this.javaClass.name}${this.hashCode()}"
            workerKey.setDisallowChanges(workerKeyString)
            ProfilerInitializer.getListener()
                ?.getTaskRecord(taskOwnerString)
                ?.addWorker(workerKeyString, GradleBuildProfileSpan.ExecutionType.WORKER_EXECUTION)
        }
    }

    final override fun execute() {
        GradlePluginMBeans.getProfileMBean(parameters.projectName.get())
            ?.workerStarted(parameters.taskOwner.get(), parameters.workerKey.get())
        run()
        GradlePluginMBeans.getProfileMBean(parameters.projectName.get())
            ?.workerFinished(parameters.taskOwner.get(), parameters.workerKey.get())
    }

    abstract fun run()
}
