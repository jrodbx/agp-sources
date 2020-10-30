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
import com.android.build.gradle.internal.packaging.JarCreatorFactory
import com.android.build.gradle.internal.packaging.JarCreatorType
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.packaging.JarCreator
import com.android.utils.FileUtils
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.Serializable
import java.nio.file.Paths
import java.util.function.Predicate
import java.util.zip.Deflater
import javax.inject.Inject

/**
 * Task that collects the processed manifest file for the asset pack and the asset files to be
 * included and uses a JarCreator to package them together for the base app to include in the
 * Android App Bundle.
 */
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
            getWorkerFacadeWithWorkers().use {
                it.submit(
                    AssetPackPreBundleTaskRunnable::class.java,
                    AssetPackPreBundleTaskRunnable.Params(
                        packDir = packDir.asFile,
                        packFile = packDir.file("${assetPackName}.zip").asFile,
                        assetsFilesPath = assetsFiles.filter{assetPack -> assetPack.absolutePath.contains(
                                                       assetPackName + File.separator + "src" + File.separator + "main" + File.separator + "assets")}.asPath,
                        manifestFile = manifestFile
                    )
                )
            }
        }
    }

    class CreationAction(
        variantScope: VariantScope,
        private val assetFileCollection: FileCollection
    ) : VariantTaskCreationAction<AssetPackPreBundleTask>(variantScope) {
        override val type = AssetPackPreBundleTask::class.java
        override val name = variantScope.getTaskName("assetPack", "PreBundleTask")

        override fun handleProvider(taskProvider: TaskProvider<out AssetPackPreBundleTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesDir(
                InternalArtifactType.ASSET_PACK_BUNDLE,
                taskProvider,
                AssetPackPreBundleTask::outputDir
            )
        }

        override fun configure(task: AssetPackPreBundleTask) {
            super.configure(task)
            val artifacts = variantScope.artifacts

            artifacts.setTaskInputToFinalProduct(
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

class AssetPackPreBundleTaskRunnable @Inject constructor(private val params: Params) : Runnable {
    override fun run() {
        params.packDir.mkdirs()
        FileUtils.cleanOutputDir(params.packDir)
        val jarCreator =
            JarCreatorFactory.make(params.packFile.toPath(), JarCreatorType.JAR_FLINGER)

        // Disable compression for module zips, since this will only be used in bundletool and it
        // will need to uncompress them anyway.
        jarCreator.setCompressionLevel(Deflater.NO_COMPRESSION)

        jarCreator.use {
            if (params.assetsFilesPath.isNotEmpty()) {
                it.addDirectory(
                    Paths.get(params.assetsFilesPath),
                    null,
                    null,
                    AssetRelocator(FD_ASSETS)
                )
            }

            it.addJar(params.manifestFile.toPath(), Predicate { file -> file.endsWith(SdkConstants.FN_ANDROID_MANIFEST_XML) }, ManifestRelocator())
        }
    }

    class Params(
        val packDir: File,
        val packFile: File,
        val assetsFilesPath: String,
        val manifestFile: File
    ) : Serializable
}
