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
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.nio.charset.StandardCharsets

/**
 * Task to write an android manifest for the res.apk static library
 *
 * Caching disabled by default for this task because the task does very little work.
 * Calculating cache hit/miss and fetching results is likely more expensive than
 * simply executing the task.
 */
@DisableCachingByDefault
abstract class StaticLibraryManifestTask : NonIncrementalTask() {

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputFile
    abstract val manifestFile: RegularFileProperty

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(StaticLibraryManifestWorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.manifestFile.set(manifestFile)
            it.packageName.set(packageName)
        }
    }

    class CreationAction(
        creationConfig: ComponentCreationConfig
    ) : VariantTaskCreationAction<StaticLibraryManifestTask, ComponentCreationConfig>(
        creationConfig
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
            ).withName(SdkConstants.ANDROID_MANIFEST_XML)
                .on(InternalArtifactType.STATIC_LIBRARY_MANIFEST)
        }

        override fun configure(
            task: StaticLibraryManifestTask
        ) {
            super.configure(task)
            task.packageName.setDisallowChanges(creationConfig.namespace)
        }
    }
}

abstract class StaticLibraryManifestWorkAction :
    ProfileAwareWorkAction<StaticLibraryManifestRequest>() {
    override fun run() {
        parameters.manifestFile.asFile.get().outputStream().writer(StandardCharsets.UTF_8)
            .buffered().use {
                it.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
                    .append("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n")
                    .append("    package=\"${parameters.packageName.get()}\"/>\n")
            }
    }
}

abstract class StaticLibraryManifestRequest : ProfileAwareWorkAction.Parameters() {
    abstract val manifestFile: RegularFileProperty
    abstract val packageName: Property<String>
}
