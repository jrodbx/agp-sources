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

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.res.Aapt2ProcessResourcesRunnable
import com.android.build.gradle.internal.res.getAapt2FromMavenAndVersion
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.Aapt2DaemonBuildService
import com.android.build.gradle.internal.services.getAapt2DaemonBuildService
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.options.SyncOptions
import com.android.builder.core.VariantTypeImpl
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File

/**
 * Task that passes the generated manifest file for the asset pack to AAPT2 for processing,
 * producing a linked manifest file suitable for packaging in the Android App Bundle.
 */
abstract class LinkManifestForAssetPackTask : NonIncrementalTask() {
    /**
     * The manifest file previously generated for this asset pack by the
     * AssetPackManifestGenerationTask.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val manifestsDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var androidJar: Provider<File>
        private set

    @get:Input
    lateinit var aapt2Version: String
        private set

    @get:Internal
    abstract val aapt2FromMaven: ConfigurableFileCollection

    /**
     * A directory containing the archives for each asset pack that contains the linked manifest file and resources.pb file produced by AAPT2.
     */
    @get:OutputDirectory
    abstract val linkedManifestsDirectory: DirectoryProperty

    @get:Internal
    abstract val aapt2DaemonBuildService: Property<Aapt2DaemonBuildService>

    override fun doTaskAction() {
        for (manifestFile: File in manifestsDirectory.asFileTree.files) {
            val assetPackName = manifestFile.parentFile.name
            val config = AaptPackageConfig(
                androidJarPath = androidJar.get().absolutePath,
                generateProtos = true,
                manifestFile = manifestFile,
                options = AaptOptions(null, false, null),
                resourceOutputApk = File(File(linkedManifestsDirectory.get().asFile, assetPackName), "${assetPackName}.ap_"),
                variantType = VariantTypeImpl.BASE_APK,
                debuggable = false,
                // Bundletool assumes this field will be filled in for the module, even though it won't be used for the asset pack.
                packageId = 0xFF
            )

            val aapt2ServiceKey = aapt2DaemonBuildService.get().registerAaptService(
                aapt2FromMaven = aapt2FromMaven.singleFile,
                logger = LoggerWrapper(logger)
            )

            getWorkerFacadeWithWorkers().use {
                it.submit(
                    Aapt2ProcessResourcesRunnable::class.java,
                    Aapt2ProcessResourcesRunnable.Params(
                        aapt2ServiceKey,
                        config,
                        SyncOptions.ErrorFormatMode.HUMAN_READABLE,
                        null,
                        null
                    )
                )
            }
        }
    }

    internal class CreationAction(
        variantScope: VariantScope
    ) : VariantTaskCreationAction<LinkManifestForAssetPackTask>(variantScope) {
        override val type = LinkManifestForAssetPackTask::class.java
        override val name = variantScope.getTaskName("link", "ManifestForAssetPacks")

        override fun handleProvider(taskProvider: TaskProvider<out LinkManifestForAssetPackTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesDir(
                InternalArtifactType.LINKED_RES_FOR_ASSET_PACK,
                taskProvider,
                LinkManifestForAssetPackTask::linkedManifestsDirectory
            )
        }

        override fun configure(task: LinkManifestForAssetPackTask) {
            super.configure(task)

            val artifacts = variantScope.artifacts

            artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.ASSET_PACK_MANIFESTS, task.manifestsDirectory)

            val (aapt2FromMaven, aapt2Version) = getAapt2FromMavenAndVersion(variantScope.globalScope)
            task.aapt2FromMaven.from(aapt2FromMaven)
            task.aapt2Version = aapt2Version

            task.androidJar = variantScope.globalScope.sdkComponents.androidJarProvider
            task.aapt2DaemonBuildService.set(getAapt2DaemonBuildService(task.project))
        }
    }
}
