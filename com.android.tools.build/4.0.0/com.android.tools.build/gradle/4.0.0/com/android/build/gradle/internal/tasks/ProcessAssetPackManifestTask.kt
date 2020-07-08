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

import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.Serializable
import javax.inject.Inject

@CacheableTask
abstract class ProcessAssetPackManifestTask : NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assetPackManifests: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val processedManifests: DirectoryProperty

    private lateinit var applicationId: Property<String>

    private lateinit var assetPackNames: Set<String>

    override fun doTaskAction() {
        for (assetPackManifest: File in assetPackManifests.files) {
            val assetPackName = assetPackNames.first { assetPackName -> assetPackManifest.absolutePath.contains(assetPackName) }

            getWorkerFacadeWithWorkers().use {
                it.submit(
                    ProcessAssetPackManifestRunnable::class.java,
                    ProcessAssetPackManifestRunnable.Params(
                        assetPackManifest,
                        assetPackName,
                        applicationId.get(),
                        processedManifests.get().asFile
                    )
                )
            }
        }
    }

    internal class CreationAction(
        private val componentProperties: ComponentPropertiesImpl,
        private val assetPackManifestFileCollection: FileCollection,
        private val assetPackNames: Set<String>
    ) : VariantTaskCreationAction<ProcessAssetPackManifestTask>(componentProperties.variantScope) {
        override val type = ProcessAssetPackManifestTask::class.java
        override val name = variantScope.getTaskName("process", "AssetPackManifests")

        override fun handleProvider(taskProvider: TaskProvider<out ProcessAssetPackManifestTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesDir(
                InternalArtifactType.ASSET_PACK_MANIFESTS,
                taskProvider,
                ProcessAssetPackManifestTask::processedManifests
            )
        }

        override fun configure(task: ProcessAssetPackManifestTask) {
            super.configure(task)

            task.applicationId = componentProperties.applicationId
            task.assetPackManifests.from(assetPackManifestFileCollection)
            task.assetPackNames = assetPackNames
        }
    }
}

class ProcessAssetPackManifestRunnable @Inject constructor(private val params: Params) : Runnable {
    override fun run() {
        // Write application ID in manifest.
        val manifest = params.assetPackManifest.readText()

        val processedManifest = manifest.replace("package=\"basePackage\"", "package=\"${params.applicationId}\"")
        val processedManifestDir = File(params.processedManifestsDir, params.assetPackName)
        processedManifestDir.mkdirs()
        val processedManifestFile = File(processedManifestDir, params.assetPackManifest.name)
        processedManifestFile.writeText(processedManifest)
    }

    class Params(
        val assetPackManifest: File,
        val assetPackName: String,
        val applicationId: String,
        val processedManifestsDir: File
    ) : Serializable
}