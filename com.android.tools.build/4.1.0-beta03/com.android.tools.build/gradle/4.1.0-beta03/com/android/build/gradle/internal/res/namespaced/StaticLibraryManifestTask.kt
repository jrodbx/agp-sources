/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.res.namespaced

import com.android.SdkConstants
import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider

/**
 * Task to write an android manifest for the res.apk static library
 */
@CacheableTask
abstract class StaticLibraryManifestTask : NonIncrementalTask() {

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputFile abstract val manifestFile: RegularFileProperty

    override fun doTaskAction() {
        getWorkerFacadeWithWorkers().use {
            it.submit(
                StaticLibraryManifestRunnable::class.java,
                StaticLibraryManifestRequest(manifestFile.get().asFile, packageName.get())
            )
        }
    }

    class CreationAction(
        componentProperties: ComponentPropertiesImpl
    ) : VariantTaskCreationAction<StaticLibraryManifestTask, ComponentPropertiesImpl>(
        componentProperties
    ) {

        override val name: String
            get() = computeTaskName("create", "StaticLibraryManifest")
        override val type: Class<StaticLibraryManifestTask>
            get() = StaticLibraryManifestTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<StaticLibraryManifestTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                StaticLibraryManifestTask::manifestFile
            ).withName(SdkConstants.ANDROID_MANIFEST_XML).on(InternalArtifactType.STATIC_LIBRARY_MANIFEST)
        }

        override fun configure(
            task: StaticLibraryManifestTask
        ) {
            super.configure(task)
            task.packageName.setDisallowChanges(creationConfig.packageName)
        }
    }
}