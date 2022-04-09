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

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.featuresplit.toIdString
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.project.ProjectIdentifier
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.lang.RuntimeException

@CacheableTask
abstract class ProcessAssetPackManifestTask : NonIncrementalTask() {

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getAssetPackManifestFiles(): FileCollection = assetPackManifests.artifactFiles

    @get:OutputDirectory
    abstract val processedManifests: DirectoryProperty

    @get:Input
    abstract val applicationId: Property<String>

    @get:Input
    val assetPackIds: Set<String>
        get() = assetPackManifests.map { it.toIdString() }.toSet()

    private lateinit var assetPackManifests: ArtifactCollection

    override fun doTaskAction() {
        assetPackManifests.forEach { assetPackManifestArtifact ->
            val projectId = assetPackManifestArtifact.id.componentIdentifier as?
                    ProjectComponentIdentifier ?: throw RuntimeException("unexpected identifier type for $assetPackManifestArtifact")

            workerExecutor.noIsolation().submit(ProcessAssetPackManifestWorkAction::class.java) {
                it.initializeFromAndroidVariantTask(this)
                it.assetPackManifest.set(assetPackManifestArtifact.file)
                it.assetPackName.set(projectId.projectPath.replace(":", File.separator))
                it.applicationId.set(applicationId)
                it.processedManifestsDir.set(processedManifests)
            }
        }
    }

    internal class CreationForAssetPackBundleAction(
        private val artifacts: ArtifactsImpl,
        private val applicationId: String,
        private val assetPackManifestFileCollection: ArtifactCollection
    ) : TaskCreationAction<ProcessAssetPackManifestTask>() {

        override val type = ProcessAssetPackManifestTask::class.java
        override val name = "processAssetPackManifests"

        override fun handleProvider(taskProvider: TaskProvider<ProcessAssetPackManifestTask>) {
            artifacts.setInitialProvider(
                taskProvider,
                ProcessAssetPackManifestTask::processedManifests
            ).on(InternalArtifactType.ASSET_PACK_MANIFESTS)
        }

        override fun configure(task: ProcessAssetPackManifestTask) {
            task.configureVariantProperties(variantName = "", task.project.gradle.sharedServices)
            task.applicationId.setDisallowChanges(applicationId)
            task.assetPackManifests = assetPackManifestFileCollection
        }
    }

    internal class CreationAction(
        creationConfig: ApkCreationConfig,
        private val assetPackManifestFileCollection: ArtifactCollection
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
            task.assetPackManifests = assetPackManifestFileCollection
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
