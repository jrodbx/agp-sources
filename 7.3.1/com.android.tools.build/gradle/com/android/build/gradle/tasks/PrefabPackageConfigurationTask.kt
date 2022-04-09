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

import com.android.build.api.variant.impl.LibraryVariantImpl
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel
import com.android.build.gradle.internal.cxx.model.jsonFile
import com.android.build.gradle.internal.cxx.prefab.PrefabModuleTaskData
import com.android.build.gradle.internal.cxx.prefab.configurePrefab
import com.android.build.gradle.internal.cxx.prefab.versionOrError
import com.android.build.gradle.internal.scope.InternalArtifactType.PREFAB_PACKAGE_CONFIGURATION
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/**
 * Publishes only the configuration information for a prefab
 * package. This is intended to be consumed by other module's
 * in this project during their configuration phase.
 *
 * The exported artifacts are named [PREFAB_PACKAGE_CONFIGURATION]
 */
@DisableCachingByDefault
abstract class PrefabPackageConfigurationTask : NonIncrementalTask() {
    @Inject
    protected abstract fun getFileOperations(): FileOperations

    @get:Internal
    abstract val sdkComponents: Property<SdkComponentsBuildService>

    private lateinit var configurationModel: CxxConfigurationModel

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    lateinit var packageName: String
        private set

    @get:Input
    @get:Optional
    var packageVersion: String? = null
        private set

    @get:Nested
    lateinit var modules: List<PrefabModuleTaskData>
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    val jsonFiles get() = configurationModel.activeAbis.map { it.jsonFile }

    @get:Input
    val ndkAbiFilters get() = configurationModel.variant.validAbiList

    override fun doTaskAction() {
        configurePrefab(
            getFileOperations(),
            outputDirectory.get().asFile,
            packageName,
            packageVersion,
            modules,
            configurationModel
        )
    }

    class CreationAction(
        private val taskName : String,
        private val location : String,
        private val modules : List<PrefabModuleTaskData>,
        private val config : CxxConfigurationModel,
        componentProperties: LibraryVariantImpl
    ) : VariantTaskCreationAction<PrefabPackageConfigurationTask, LibraryVariantImpl>(
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
                PrefabPackageConfigurationTask::outputDirectory
            ).withName("prefab")
             .atLocation(location)
             .on(PREFAB_PACKAGE_CONFIGURATION)
        }

        override fun configure(task: PrefabPackageConfigurationTask) {
            super.configure(task)
            val projectInfo = creationConfig.services.projectInfo
            task.description = "Creates a configuration for Prefab package"
            task.packageName = projectInfo.name
            task.packageVersion = versionOrError(projectInfo.version)
            task.modules = modules

            task.configurationModel = config
            task.sdkComponents.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry)
            )
        }
    }
}

