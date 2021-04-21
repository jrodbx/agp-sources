/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File

@CacheableTask
abstract class ProcessAssetPackManifestTask : NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assetPackManifests: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val processedManifests: DirectoryProperty

    @get:Input
    abstract val applicationId: Property<String>

    private lateinit var assetPackNames: Set<String>

    override fun doTaskAction() {
        for (assetPackManifest: File in assetPackManifests.files) {
            val assetPackName = assetPackNames.first { assetPackName ->
                assetPackManifest.absolutePath.contains(assetPackName)
            }

            workerExecutor.noIsolation().submit(ProcessAssetPackManifestWorkAction::class.java) {
                it.initializeFromAndroidVariantTask(this)
                it.assetPackManifest.set(assetPackManifest)
                it.assetPackName.set(assetPackName)
                it.applicationId.set(applicationId)
                it.processedManifestsDir.set(processedManifests)
            }
        }
    }

    internal class CreationAction(
        creationConfig: ApkCreationConfig,
        private val assetPackManifestFileCollection: FileCollection,
        private val assetPackNames: Set<String>
    ) : VariantTaskCreationAction<ProcessAssetPackManifestTask, ApkCreationConfig>(
        creationConfig
    ) {
        override val type = ProcessAssetPackManifestTask::class.java
        override val name = computeTaskName("process", "AssetPackManifests")

        override fun handleProvider(
            taskProvider: TaskProvider<ProcessAssetPackManifestTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                ProcessAssetPackManifestTask::processedManifests
            ).on(InternalArtifactType.ASSET_PACK_MANIFESTS)
        }

        override fun configure(
            task: ProcessAssetPackManifestTask
        ) {
            super.configure(task)

            task.applicationId.setDisallowChanges(creationConfig.applicationId)
            task.assetPackManifests.from(assetPackManifestFileCollection)
            task.assetPackNames = assetPackNames
        }
    }
}

abstract class ProcessAssetPackManifestWorkAction :
    ProfileAwareWorkAction<ProcessAssetPackManifestWorkAction.Params>() {
    override fun run() {
        // Write application ID in manifest.
        val assetPackManifest = parameters.assetPackManifest.asFile.get()
        val manifest = assetPackManifest.readText()

        val processedManifest = manifest.replace(
            "package=\"basePackage\"",
            "package=\"${parameters.applicationId.get()}\""
        )
        val processedManifestDir =
            File(parameters.processedManifestsDir.asFile.get(), parameters.assetPackName.get())
        processedManifestDir.mkdirs()
        val processedManifestFile = File(processedManifestDir, assetPackManifest.name)
        processedManifestFile.writeText(processedManifest)
    }

    abstract class Params : ProfileAwareWorkAction.Parameters() {
        abstract val assetPackManifest: RegularFileProperty
        abstract val assetPackName: Property<String>
        abstract val applicationId: Property<String>
        abstract val processedManifestsDir: DirectoryProperty
    }
}