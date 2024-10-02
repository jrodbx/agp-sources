/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.build.api.dsl.Device
import com.android.build.api.instrumentation.manageddevice.DeviceSetupConfigureAction
import com.android.build.api.instrumentation.manageddevice.DeviceSetupInput
import com.android.build.api.instrumentation.manageddevice.DeviceSetupTaskAction
import com.android.build.gradle.internal.setupTaskName
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import javax.inject.Inject
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.work.DisableCachingByDefault
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters

@DisableCachingByDefault(because = "The Setup Task is expected to get values external to " +
        "the Gradle Project. As such, it can never be considered up-to-date.")
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class ManagedDeviceSetupTask : UnsafeOutputsTask(
    "The Setup Task is expected to get values external to the Gradle Project. " +
            "As such, it can never be considered up-to-date.") {

    @get:Inject
    abstract val objectFactory: ObjectFactory

    @get:Internal
    abstract val setupAction: Property<Class<out DeviceSetupTaskAction<out DeviceSetupInput>>>

    @get:Nested
    abstract val deviceInput: Property<DeviceSetupInput>

    @get:OutputDirectory
    abstract val setupResultDir: DirectoryProperty

    public override fun doTaskAction() {
        workerExecutor.noIsolation().submit(SetupTaskWorkAction::class.java) { params ->
            params.setupAction.setDisallowChanges(
                objectFactory.newInstance(
                    setupAction.get()) as DeviceSetupTaskAction<DeviceSetupInput>)
            params.deviceInput.setDisallowChanges(deviceInput)
            params.setupResultDir.setDisallowChanges(setupResultDir)
        }
    }

    interface SetupTaskWorkParameters : WorkParameters {
        val setupAction: Property<DeviceSetupTaskAction<DeviceSetupInput>>
        val deviceInput: Property<DeviceSetupInput>
        val setupResultDir: DirectoryProperty
    }

    abstract class SetupTaskWorkAction : WorkAction<SetupTaskWorkParameters> {
        override fun execute() {
            val setupAction = parameters.setupAction.get()
            val deviceInput = parameters.deviceInput.get()
            val setupResultDir = parameters.setupResultDir.get()
            setupAction.setup(deviceInput, setupResultDir)
        }
    }

    class CreationAction<DeviceT: Device>(
        private val setupTaskResultOutputDir: Provider<Directory>,
        private val setupConfigAction : Class<out DeviceSetupConfigureAction<DeviceT, *>>,
        private val setupTaskAction: Class<out DeviceSetupTaskAction<*>>,
        private val dslDevice: DeviceT,
        creationConfig: GlobalTaskCreationConfig
    ): GlobalTaskCreationAction<ManagedDeviceSetupTask>(creationConfig) {

        override val name: String
            get() = setupTaskName(dslDevice)

        override val type: Class<ManagedDeviceSetupTask>
            get() = ManagedDeviceSetupTask::class.java

        override fun configure(task: ManagedDeviceSetupTask) {
            super.configure(task)

            task.variantName = ""
            task.deviceInput.setDisallowChanges(
                task.objectFactory.newInstance(setupConfigAction).configureTaskInput(dslDevice))
            task.setupAction.setDisallowChanges(setupTaskAction)
            task.setupResultDir.set(setupTaskResultOutputDir)
        }
    }
}
