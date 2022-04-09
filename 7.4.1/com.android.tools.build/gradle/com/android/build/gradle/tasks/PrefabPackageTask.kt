/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.component.LibraryCreationConfig
import com.android.build.gradle.internal.cxx.prefab.PrefabPublication
import com.android.build.gradle.internal.cxx.prefab.buildPrefabPackage
import com.android.build.gradle.internal.cxx.prefab.copyWithLibraryInformationAdded
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.internal.tasks.TaskCategory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/**
 * Publishes this module's native build outputs and specified headers
 * into a prefab-defined folder structure. This can be consumed either
 * by tasks that produce AAR or by other modules in this project.
 *
 * The exported artifacts are named [InternalArtifactType.PREFAB_PACKAGE]
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.NATIVE)
abstract class PrefabPackageTask : NonIncrementalTask() {
    @Inject
    protected abstract fun getFileOperations(): FileOperations

    @get:Internal
    abstract val sdkComponents: Property<SdkComponentsBuildService>

    @get:Nested
    lateinit var publication: PrefabPublication
        private set

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    override fun doTaskAction() {
        val patched = publication.copyWithLibraryInformationAdded()
        buildPrefabPackage(
            fileOperations = getFileOperations(),
            publication = patched
        )
    }

    class CreationAction(
        private val publication: PrefabPublication,
        private val taskName : String,
        componentProperties: LibraryCreationConfig
    ) : VariantTaskCreationAction<PrefabPackageTask, LibraryCreationConfig>(
        componentProperties
    ) {
        override val name: String
            get() = taskName

        override val type: Class<PrefabPackageTask>
            get() = PrefabPackageTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<PrefabPackageTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                PrefabPackageTask::outputDirectory
            ).withName("prefab")
             .atLocation(publication.installationFolder.parent)
             .on(InternalArtifactType.PREFAB_PACKAGE)
        }

        override fun configure(task: PrefabPackageTask) {
            super.configure(task)
            task.description = "Creates a Prefab package for inclusion in an AAR"
            task.publication = publication
            task.sdkComponents.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry)
            )
        }
    }
}
