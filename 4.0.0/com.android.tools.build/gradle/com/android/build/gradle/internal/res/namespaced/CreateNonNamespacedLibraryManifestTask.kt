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
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider

/**
 * Task to strip resource namespaces from the library's android manifest. This stripped manifest
 * needs to be bundled in the AAR as the AndroidManifest.xml artifact, so that it's consumable by
 * non-namespaced projects.
 */
@CacheableTask
abstract class CreateNonNamespacedLibraryManifestTask : NonIncrementalTask() {

    @get:OutputFile
    abstract val outputStrippedManifestFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val libraryManifest: RegularFileProperty

    override fun doTaskAction() {
        getWorkerFacadeWithWorkers().use {
            it.submit(
                CreateNonNamespacedLibraryManifestRunnable::class.java,
                CreateNonNamespacedLibraryManifestRequest(
                    libraryManifest.get().asFile, outputStrippedManifestFile.get().asFile)
            )
        }
    }

    class CreationAction(
        variantScope: VariantScope
    ) : VariantTaskCreationAction<CreateNonNamespacedLibraryManifestTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("create", "NonNamespacedLibraryManifest")
        override val type: Class<CreateNonNamespacedLibraryManifestTask>
            get() = CreateNonNamespacedLibraryManifestTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out CreateNonNamespacedLibraryManifestTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesFile(
                InternalArtifactType.NON_NAMESPACED_LIBRARY_MANIFEST,
                taskProvider,
                CreateNonNamespacedLibraryManifestTask::outputStrippedManifestFile,
                SdkConstants.ANDROID_MANIFEST_XML
            )
        }

        override fun configure(task: CreateNonNamespacedLibraryManifestTask) {
            super.configure(task)
            variantScope.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.LIBRARY_MANIFEST, task.libraryManifest)
        }
    }
}