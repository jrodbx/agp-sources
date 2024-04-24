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

import com.android.SdkConstants
import com.android.SdkConstants.FD_ASSETS
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.packaging.JarCreator
import com.android.builder.packaging.JarFlinger
import com.android.utils.FileUtils
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.nio.file.Paths
import java.util.function.Predicate
import java.util.zip.Deflater

/**
 * Task that collects the processed manifest file for the asset pack and the asset files to be
 * included and uses a JarCreator to package them together for the base app to include in the
 * Android App Bundle.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.BUNDLE_PACKAGING)
abstract class AssetPackPreBundleTask : NonIncrementalTask() {
    /**
     * Where to put the final archive containing all the files for the asset pack, to be included
     * in the bundle build task for the base module.
     */
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    /**
     * The processed manifest file to be included in the app bundle for the asset pack.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val manifestFiles: DirectoryProperty
    /**
     * The asset files to be included in the app bundle as the contents of the asset pack.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assetsFiles: ConfigurableFileCollection

    override fun doTaskAction() {
        for (manifestFile: File in manifestFiles.asFileTree.files) {
            val assetPackName = manifestFile.parentFile.name
            val packDir = outputDir.dir(assetPackName).get()
            workerExecutor.noIsolation().submit(AssetPackPreBundleTaskRunnable::class.java) {
                it.initializeFromAndroidVariantTask(this)
                it.packDir.set(packDir)
                it.packFile.set(packDir.file("${assetPackName}.zip"))
                it.assetsFilesPath.set(assetsFiles.filter { assetPack ->
                    assetPack.absolutePath.contains(
                        assetPackName + File.separator + "src" + File.separator + "main" + File.separator + "assets"
                    )
                }.asPath)
                it.manifestFile.set(manifestFile)
            }
        }
    }

    class CreationForAssetPackBundleAction(
        private val artifacts: ArtifactsImpl,
        private val assetFileCollection: FileCollection
    ) : TaskCreationAction<AssetPackPreBundleTask>() {
        override val type = AssetPackPreBundleTask::class.java
        override val name = "assetPackPreBundleTask"

        override fun handleProvider(
            taskProvider: TaskProvider<AssetPackPreBundleTask>
        ) {
            super.handleProvider(taskProvider)
            artifacts.setInitialProvider(
                taskProvider,
                AssetPackPreBundleTask::outputDir
            ).on(InternalArtifactType.ASSET_PACK_BUNDLE)
        }

        override fun configure(
            task: AssetPackPreBundleTask
        ) {
            task.configureVariantProperties(variantName = "", task.project.gradle.sharedServices)
            artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.LINKED_RES_FOR_ASSET_PACK, task.manifestFiles)
            task.assetsFiles.from(assetFileCollection)
        }
    }

    class CreationAction(
        creationConfig: VariantCreationConfig,
        private val assetFileCollection: FileCollection
    ) : VariantTaskCreationAction<AssetPackPreBundleTask, VariantCreationConfig>(
        creationConfig
    ) {
        override val type = AssetPackPreBundleTask::class.java
        override val name = computeTaskName("assetPack", "PreBundleTask")

        override fun handleProvider(
            taskProvider: TaskProvider<AssetPackPreBundleTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                AssetPackPreBundleTask::outputDir
            ).on(InternalArtifactType.ASSET_PACK_BUNDLE)
        }

        override fun configure(
            task: AssetPackPreBundleTask
        ) {
            super.configure(task)
            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.LINKED_RES_FOR_ASSET_PACK, task.manifestFiles)

            task.assetsFiles.from(assetFileCollection)
        }
    }
}

private class AssetRelocator(private val prefix: String): JarCreator.Relocator {
    override fun relocate(entryPath: String) = "$prefix/$entryPath"
}

private class ManifestRelocator : JarCreator.Relocator {
    override fun relocate(entryPath: String) = when(entryPath) {
        SdkConstants.FN_ANDROID_MANIFEST_XML -> "manifest/" + SdkConstants.FN_ANDROID_MANIFEST_XML
        else -> entryPath
    }
}

abstract class AssetPackPreBundleTaskRunnable :
    ProfileAwareWorkAction<AssetPackPreBundleTaskRunnable.Params>() {
    override fun run() {
        parameters.packDir.asFile.get().mkdirs()
        FileUtils.cleanOutputDir(parameters.packDir.asFile.get())
        val jarCreator = JarFlinger(parameters.packFile.asFile.get().toPath(), null)

        // Disable compression for module zips, since this will only be used in bundletool and it
        // will need to uncompress them anyway.
        jarCreator.setCompressionLevel(Deflater.NO_COMPRESSION)

        jarCreator.use {
            if (parameters.assetsFilesPath.get().isNotEmpty()) {
                it.addDirectory(
                    Paths.get(parameters.assetsFilesPath.get()),
                    null,
                    null,
                    AssetRelocator(FD_ASSETS)
                )
            }

            it.addJar(
                parameters.manifestFile.asFile.get().toPath(),
                Predicate { file -> file.endsWith(SdkConstants.FN_ANDROID_MANIFEST_XML) },
                ManifestRelocator()
            )
        }
    }

    abstract class Params : ProfileAwareWorkAction.Parameters() {
        abstract val packDir: DirectoryProperty
        abstract val packFile: RegularFileProperty
        abstract val assetsFilesPath: Property<String>
        abstract val manifestFile: RegularFileProperty
    }
}
