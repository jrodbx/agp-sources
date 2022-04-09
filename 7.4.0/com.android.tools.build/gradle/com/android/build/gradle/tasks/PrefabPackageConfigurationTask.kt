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

import com.android.build.gradle.internal.component.LibraryCreationConfig
import com.android.build.gradle.internal.cxx.prefab.PREFAB_PUBLICATION_FILE
import com.android.build.gradle.internal.cxx.prefab.PrefabPublication
import com.android.build.gradle.internal.cxx.prefab.PrefabPublicationType.Configuration
import com.android.build.gradle.internal.cxx.prefab.writePublicationFile
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.TaskCategory
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

/**
 * Task write a [PrefabPublication] with library information added to it to disk.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.NATIVE)
abstract class PrefabPackageConfigurationTask : NonIncrementalTask() {

    @get:Nested
    lateinit var publication: PrefabPublication
        private set

    @get:OutputFile
    abstract val publicationFile: RegularFileProperty

    override fun doTaskAction() = Configuration.writePublicationFile(publication)

    class CreationAction(
        private val publication: PrefabPublication,
        private val taskName : String,
        componentProperties: LibraryCreationConfig,
    ) : VariantTaskCreationAction<PrefabPackageConfigurationTask, LibraryCreationConfig>(
        componentProperties
    ) {
        override val name: String
            get() = taskName

        override val type: Class<PrefabPackageConfigurationTask>
            get() = PrefabPackageConfigurationTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<PrefabPackageConfigurationTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                PrefabPackageConfigurationTask::publicationFile
            ).withName(PREFAB_PUBLICATION_FILE).on(InternalArtifactType.PREFAB_PACKAGE_CONFIGURATION)
        }

        override fun configure(task: PrefabPackageConfigurationTask) {
            super.configure(task)
            task.description = "Creates a configuration for Prefab package"
            task.publication= publication
        }
    }
}

