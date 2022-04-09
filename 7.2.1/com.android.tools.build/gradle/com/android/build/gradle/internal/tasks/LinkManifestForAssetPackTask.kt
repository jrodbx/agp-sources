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
import com.android.build.gradle.internal.AndroidJarInput
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.initialize
import com.android.build.gradle.internal.res.Aapt2ProcessResourcesRunnable
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.Aapt2Input
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.services.registerAaptService
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.SyncOptions
import com.android.builder.core.ToolsRevisionUtils
import com.android.builder.core.VariantTypeImpl
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Task that passes the generated manifest file for the asset pack to AAPT2 for processing,
 * producing a linked manifest file suitable for packaging in the Android App Bundle.
 */
@DisableCachingByDefault
abstract class LinkManifestForAssetPackTask : NonIncrementalTask() {

    /**
     * The manifest file previously generated for this asset pack by the
     * AssetPackManifestGenerationTask.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val manifestsDirectory: DirectoryProperty

    @get:Nested
    abstract val androidJarInput: AndroidJarInput

    @get:Nested
    abstract val aapt2: Aapt2Input

    /**
     * A directory containing the archives for each asset pack that contains the linked manifest file and resources.pb file produced by AAPT2.
     */
    @get:OutputDirectory
    abstract val linkedManifestsDirectory: DirectoryProperty

    override fun doTaskAction() {
        for (manifestFile: File in manifestsDirectory.asFileTree.files) {
            val assetPackName = manifestFile.parentFile.name
            val config = AaptPackageConfig(
                androidJarPath = androidJarInput.getAndroidJar().get().absolutePath,
                generateProtos = true,
                manifestFile = manifestFile,
                options = AaptOptions(),
                resourceOutputApk = File(File(linkedManifestsDirectory.get().asFile, assetPackName), "${assetPackName}.ap_"),
                variantType = VariantTypeImpl.BASE_APK,
                //debuggable = false,
                // Bundletool assumes this field will be filled in for the module, even though it won't be used for the asset pack.
                packageId = 0xFF
            )

            val aapt2ServiceKey = aapt2.registerAaptService()
            workerExecutor.noIsolation().submit(Aapt2ProcessResourcesRunnable::class.java) {
                it.initializeFromAndroidVariantTask(this)
                it.aapt2ServiceKey.set(aapt2ServiceKey)
                it.request.set(config)
                it.errorFormatMode.set(SyncOptions.ErrorFormatMode.HUMAN_READABLE)
            }
        }
    }

    internal class CreationForAssetPackBundleAction(
        private val artifacts: ArtifactsImpl,
        private val projectServices: ProjectServices,
        private val compileSdk: Int
    ) : TaskCreationAction<LinkManifestForAssetPackTask>() {

        override val type = LinkManifestForAssetPackTask::class.java
        override val name = "linkManifestForAssetPacks"

        override fun handleProvider(taskProvider: TaskProvider<LinkManifestForAssetPackTask>) {
            super.handleProvider(taskProvider)
            artifacts.setInitialProvider(
                taskProvider,
                LinkManifestForAssetPackTask::linkedManifestsDirectory
            ).on(InternalArtifactType.LINKED_RES_FOR_ASSET_PACK)
        }

        override fun configure(task: LinkManifestForAssetPackTask) {
            task.configureVariantProperties(variantName = "", projectServices.buildServiceRegistry)
            artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.ASSET_PACK_MANIFESTS, task.manifestsDirectory
            )
            projectServices.initializeAapt2Input(task.aapt2)

            task.androidJarInput.sdkBuildService.setDisallowChanges(
                getBuildService(projectServices.buildServiceRegistry)
            )
            task.androidJarInput.buildToolsRevision.setDisallowChanges(
                ToolsRevisionUtils.DEFAULT_BUILD_TOOLS_REVISION
            )
            task.androidJarInput.compileSdkVersion.setDisallowChanges("android-${compileSdk}")
        }
    }

    internal class CreationAction(
        creationConfig: VariantCreationConfig
    ) : VariantTaskCreationAction<LinkManifestForAssetPackTask, VariantCreationConfig>(
        creationConfig
    ) {

        override val type = LinkManifestForAssetPackTask::class.java
        override val name = computeTaskName("link", "ManifestForAssetPacks")

        override fun handleProvider(
            taskProvider: TaskProvider<LinkManifestForAssetPackTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                LinkManifestForAssetPackTask::linkedManifestsDirectory
            ).on(InternalArtifactType.LINKED_RES_FOR_ASSET_PACK)
        }

        override fun configure(
            task: LinkManifestForAssetPackTask
        ) {
            super.configure(task)

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.ASSET_PACK_MANIFESTS, task.manifestsDirectory)

            creationConfig.services.initializeAapt2Input(task.aapt2)
            task.androidJarInput.initialize(creationConfig)
        }
    }
}
