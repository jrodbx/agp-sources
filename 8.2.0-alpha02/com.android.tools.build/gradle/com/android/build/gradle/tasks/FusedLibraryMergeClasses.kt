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

import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryVariantScope
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.dexing.ClassFileInput.CLASS_MATCHER
import com.android.utils.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.attributes.Usage
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicateFileCopyingException
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarFile

/**
 * merge classes.jar coming from included libraries in fused libraries  plugin.
 */
@DisableCachingByDefault(because = "No calculation is made, merging classes. ")
@BuildAnalyzer(primaryTaskCategory = TaskCategory.COMPILED_CLASSES, secondaryTaskCategories = [TaskCategory.MERGING, TaskCategory.FUSING])
abstract class FusedLibraryMergeClasses: DefaultTask() {

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val incoming: ConfigurableFileCollection

    @TaskAction
    fun taskAction() {
        FileUtils.cleanOutputDir(outputDirectory.get().asFile)

        incoming.files.forEach { file ->
            logger.info("Merging file: ${file.absolutePath}")
            JarFile(file).use { jarFile ->
                jarFile.entries().asSequence().forEach { jarEntry ->
                    jarFile.getInputStream(jarEntry).use { inputStream ->
                        val outputDir = outputDirectory.get().dir(jarEntry.name.substringBeforeLast('/'))
                        val fileName = jarEntry.name.substringAfterLast('/')
                        if (CLASS_MATCHER.test(fileName)) {
                            val outputFile = File(outputDir.asFile, fileName)
                            if (outputFile.exists()) {
                                throw DuplicateFileCopyingException(
                                        "${jarEntry.name} is present in multiple jar files.")
                            }
                            outputFile.parentFile.mkdirs()
                            FileOutputStream(outputFile).use { outputStream ->
                                outputStream.write(inputStream.readBytes())
                            }
                        }
                    }
                }
            }
        }
    }

    class FusedLibraryCreationAction(val creationConfig: FusedLibraryVariantScope) :
        TaskCreationAction<FusedLibraryMergeClasses>() {
        override val name: String
            get() = "mergeClasses"
        override val type: Class<FusedLibraryMergeClasses>
            get() = FusedLibraryMergeClasses::class.java

        override fun handleProvider(taskProvider: TaskProvider<FusedLibraryMergeClasses>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                FusedLibraryMergeClasses::outputDirectory
            ).on(FusedLibraryInternalArtifactType.MERGED_CLASSES)
        }

        override fun configure(task: FusedLibraryMergeClasses) {
            task.incoming.setFrom(
                creationConfig.dependencies.getArtifactFileCollection(
                    Usage.JAVA_RUNTIME,
                    creationConfig.mergeSpec,
                    AndroidArtifacts.ArtifactType.CLASSES_JAR)
            )
        }
    }

    class PrivacySandboxSdkCreationAction(val creationConfig: PrivacySandboxSdkVariantScope) :
            TaskCreationAction<FusedLibraryMergeClasses>() {
        override val name: String
            get() = "mergeClasses"
        override val type: Class<FusedLibraryMergeClasses>
            get() = FusedLibraryMergeClasses::class.java

        override fun handleProvider(taskProvider: TaskProvider<FusedLibraryMergeClasses>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    FusedLibraryMergeClasses::outputDirectory
            ).on(FusedLibraryInternalArtifactType.MERGED_CLASSES)
        }

        override fun configure(task: FusedLibraryMergeClasses) {
            task.incoming.from(
                    creationConfig.dependencies.getArtifactFileCollection(
                            Usage.JAVA_RUNTIME,
                            creationConfig.mergeSpec,
                            AndroidArtifacts.ArtifactType.CLASSES_JAR)
            )
            task.incoming.from(creationConfig.artifacts.get(PrivacySandboxSdkInternalArtifactType.RUNTIME_R_CLASS))
            task.incoming.disallowChanges()
        }
    }
}
