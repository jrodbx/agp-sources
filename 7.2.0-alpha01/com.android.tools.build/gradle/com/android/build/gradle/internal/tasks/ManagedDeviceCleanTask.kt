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

import com.android.build.gradle.internal.AvdComponentsBuildService
import com.android.build.gradle.internal.computeAvdName
import com.android.build.gradle.internal.dsl.ManagedVirtualDevice
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

/**
 * Task for clearing the gradle avd folder of avd devices.
 */
@DisableCachingByDefault
abstract class ManagedDeviceCleanTask: NonIncrementalGlobalTask() {
    @get: Internal
    abstract val avdService: Property<AvdComponentsBuildService>

    @get: Input
    abstract val dslDevices: ListProperty<String>

    @get: Input
    abstract val preserveDefined: Property<Boolean>

    @Option(
        option="unused-only",
        description = "Remove only the avds that are not defined in the dsl for this project." +
                " This may remove avds that are being used in other projects, requiring those" +
                " managed devices to be rebuilt when tests are run.")
    fun setPreserveDefinedOption(value: Boolean) = preserveDefined.set(value)

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(ManagedDeviceCleanRunnable::class.java) {
            it.initializeWith(projectPath, path, analyticsService)
            it.avdService.set(avdService)
            it.ignoredDevices.set(if (preserveDefined.get()) dslDevices.get() else listOf<String>())
        }
    }

    abstract class ManagedDeviceCleanRunnable : ProfileAwareWorkAction<ManagedDeviceCleanParams>() {
        override fun run() {
            val allAvds = parameters.avdService.get().allAvds()
            parameters.avdService.get().deleteAvds(allAvds.filterNot {
                parameters.ignoredDevices.get().contains(it)
            })
        }
    }

    abstract class ManagedDeviceCleanParams : ProfileAwareWorkAction.Parameters() {
        abstract val avdService: Property<AvdComponentsBuildService>
        abstract val ignoredDevices: ListProperty<String>
    }


    class CreationAction @JvmOverloads constructor(
        override val name: String,
        globalScope: GlobalScope,
        private val definedDevices: List<ManagedVirtualDevice> = listOf()
    ) : GlobalTaskCreationAction<ManagedDeviceCleanTask>(globalScope) {

        override val type: Class<ManagedDeviceCleanTask>
            get() = ManagedDeviceCleanTask::class.java

        override fun configure(task: ManagedDeviceCleanTask) {
            task.preserveDefined.convention(false)
            task.avdService.setDisallowChanges(globalScope.avdComponents)
            task.dslDevices.setDisallowChanges(
                definedDevices.map {
                    computeAvdName(it)
                }
            )
            task.analyticsService.set(
                getBuildService(
                    task.project.gradle.sharedServices, AnalyticsService::class.java
                )
            )
        }
    }
}
