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
import com.android.build.gradle.internal.cxx.io.synchronizeFile
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons.getNativeBuildMiniConfig
import com.android.build.gradle.internal.cxx.json.NativeLibraryValueMini
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.jsonFile
import com.android.build.gradle.internal.cxx.prefab.PrefabModuleTaskData
import com.android.build.gradle.internal.cxx.prefab.versionOrError
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject

/**
 * Publishes this module's native build outputs and specified headers
 * into a prefab-defined folder structure. This can be consumed either
 * by tasks that produce AAR or by other modules in this project.
 *
 * The exported artifacts are named [InternalArtifactType.PREFAB_PACKAGE]
 */
@DisableCachingByDefault
abstract class PrefabPackageTask : NonIncrementalTask() {
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

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val configurationOnlyDirectory: DirectoryProperty

    override fun doTaskAction() {
        // Sync files from the configuration-only folder created by PrefabPackageConfigurationTask.
        // This includes the prefab.json and other supporting files.
        getFileOperations().sync { spec ->
            spec.from(configurationOnlyDirectory)
            spec.into(outputDirectory)
        }
        for (module in modules) {
            createModule(module, outputDirectory.get().asFile)
        }
    }

    private fun createModule(
        module: PrefabModuleTaskData,
        packageDir: File) {
        val installDir = packageDir.resolve("modules/${module.name}").apply { mkdirs() }
        installLibs(module, installDir)
    }

    private fun findLibraryForAbi(moduleName: String, abi: CxxAbiModel): NativeLibraryValueMini? {
        val config = getNativeBuildMiniConfig(abi, null)
        val matchingLibs = config.libraries.filterValues { it.artifactName == moduleName }.values
        return matchingLibs.singleOrNull()
    }

    private fun installLibs(module: PrefabModuleTaskData, installDir: File) {
        val libsDir = installDir.resolve("libs")
        for (abiData in configurationModel.activeAbis) {
            val srcLibrary = findLibraryForAbi(module.name, abiData)?.output
            if (srcLibrary != null) {
                val libDir = libsDir.resolve("android.${abiData.abi.tag}")
                val dest = libDir.resolve(srcLibrary.name)
                infoln("Installing $srcLibrary to $dest")
                synchronizeFile(srcLibrary, dest)
            }
        }
    }

    class CreationAction(
        private val taskName : String,
        private val location : String,
        private val modules: List<PrefabModuleTaskData>,
        private val config : CxxConfigurationModel,
        componentProperties: LibraryVariantImpl
    ) : VariantTaskCreationAction<PrefabPackageTask, LibraryVariantImpl>(
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
             .atLocation(location)
             .on(InternalArtifactType.PREFAB_PACKAGE)
        }

        override fun configure(task: PrefabPackageTask) {
            super.configure(task)
            val projectInfo = creationConfig.services.projectInfo
            task.description = "Creates a Prefab package for inclusion in an AAR"
            task.packageName = projectInfo.name
            task.packageVersion = versionOrError(projectInfo.version)
            task.modules = modules

            task.configurationModel = config
            task.sdkComponents.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry)
            )

            task.configurationOnlyDirectory.setDisallowChanges(
                creationConfig.artifacts.get(InternalArtifactType.PREFAB_PACKAGE_CONFIGURATION)
            )
        }
    }
}
