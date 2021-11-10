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


import com.android.build.api.artifact.SingleArtifact
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.PROJECT
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.REVERSE_METADATA_CLASSES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.dexing.DexSplitterTool
import com.android.utils.FileUtils
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.nio.file.Files

// TODO(b/135700303): Add workers
/**
 * Task that splits dex files depending on their feature sources
 */
@CacheableTask
abstract class DexSplitterTask : NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var featureJars: FileCollection
        private set

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val baseJar: RegularFileProperty

    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mappingFileSrc: RegularFileProperty

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mainDexList: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDirs: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val featureDexDir: DirectoryProperty

    @get:OutputDirectory
    abstract val baseDexDir: DirectoryProperty

    override fun doTaskAction() {

        splitDex(
            featureJars = featureJars.files,
            baseJar = baseJar.get().asFile,
            mappingFileSrc = mappingFileSrc.orNull?.asFile?.takeIf { it.exists() && it.isFile },
            mainDexList = mainDexList.orNull?.asFile,
            featureDexDir = featureDexDir.get().asFile,
            baseDexDir =  baseDexDir.get().asFile,
            inputDirs = inputDirs.toList()
        )
    }

    class CreationAction(
        creationConfig: ApkCreationConfig
    ) : VariantTaskCreationAction<DexSplitterTask, ApkCreationConfig>(
        creationConfig
    )  {
        override val type = DexSplitterTask::class.java
        override val name =  computeTaskName("split", "Dex")

        override fun handleProvider(
            taskProvider: TaskProvider<DexSplitterTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                DexSplitterTask::featureDexDir
            ).on(InternalArtifactType.FEATURE_DEX)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                DexSplitterTask::baseDexDir
            ).on(InternalArtifactType.BASE_DEX)
        }

        override fun configure(
            task: DexSplitterTask
        ) {
            super.configure(task)

            val artifacts = creationConfig.artifacts

            task.featureJars =
                creationConfig.variantDependencies.getArtifactFileCollection(REVERSE_METADATA_VALUES, PROJECT, REVERSE_METADATA_CLASSES)

            artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.MODULE_AND_RUNTIME_DEPS_CLASSES,
                task.baseJar)

            artifacts.setTaskInputToFinalProduct(
                SingleArtifact.OBFUSCATION_MAPPING_FILE,
                task.mappingFileSrc)

            artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.MAIN_DEX_LIST_FOR_BUNDLE,
                    task.mainDexList)

            task.inputDirs.from(artifacts.getAll(InternalMultipleArtifactType.DEX))
        }
    }

    companion object {

        fun splitDex(
            featureJars: Set<File>,
            baseJar: File,
            mappingFileSrc: File?,
            mainDexList: File?,
            featureDexDir: File,
            baseDexDir: File,
            inputDirs: List<File>
        ) {
            val processedMappingFileSrc = mappingFileSrc?.takeIf { it.exists() && it.isFile }

            FileUtils.deleteRecursivelyIfExists(baseDexDir)
            FileUtils.deleteRecursivelyIfExists(featureDexDir)

            val builder = DexSplitterTool.Builder(
                featureDexDir.toPath(), processedMappingFileSrc?.toPath(), mainDexList?.toPath()
            )

            for (inputDir in inputDirs) {
                inputDir.listFiles()?.toList()?.map { it.toPath() }
                    ?.forEach { builder.addInputArchive(it) }
            }

            featureJars.forEach { file ->
                builder.addFeatureJar(file.toPath(), file.nameWithoutExtension)
                Files.createDirectories(File(featureDexDir, file.nameWithoutExtension).toPath())
            }

            builder.addBaseJar(baseJar.toPath())

            builder.build().run()

            Files.createDirectories(baseDexDir.toPath())

            featureDexDir.listFiles().find { it.name == "base" }?.let {
                FileUtils.copyDirectory(it, baseDexDir)
                FileUtils.deleteRecursivelyIfExists(it)
            }
        }
    }
}
