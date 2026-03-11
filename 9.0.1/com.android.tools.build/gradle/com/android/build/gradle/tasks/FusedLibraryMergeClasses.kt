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

import com.android.build.gradle.internal.fusedlibrary.FusedLibraryGlobalScope
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalGlobalTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.dexing.ClassFileInput.CLASS_MATCHER
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicateFileCopyingException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.jar.JarFile

/**
 * Merge jars containing classes (e.g classes.jar, lint.jar) coming from included libraries.
 */
@DisableCachingByDefault(because = "No calculation is made, merging classes. ")
@BuildAnalyzer(primaryTaskCategory = TaskCategory.COMPILED_CLASSES, secondaryTaskCategories = [TaskCategory.MERGING, TaskCategory.FUSING])
abstract class FusedLibraryMergeClasses: NonIncrementalGlobalTask() {

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val includeManifest: Property<Boolean>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val incoming: ConfigurableFileCollection

    override fun doTaskAction() {
        incoming.files.filter { it.exists() }.sortedBy { it.path }.asSequence().forEach { file ->
            JarFile(file).use { jarFile ->
                jarFile.entries().asSequence().sortedBy { it.name }.forEach { jarEntry ->
                    jarFile.getInputStream(jarEntry).use { inputStream ->
                        val outputDir =
                            outputDirectory.get().dir(jarEntry.name.substringBeforeLast('/'))
                        val fileName = jarEntry.name.substringAfterLast('/')
                        if (CLASS_MATCHER.test(fileName)
                            || (includeManifest.get() && fileName == "MANIFEST.MF")) {
                            val outputFile = File(outputDir.asFile, fileName)

                            if (!outputFile.exists()) {
                                outputFile.parentFile.mkdirs()
                                outputFile.writeBytes(inputStream.readBytes())
                                logger.info("Merged File: ${file.absolutePath}")
                            } else {
                                val isExactDuplicate =
                                    jarEntry.name == outputFile.relativeTo(outputDirectory.asFile.get()).invariantSeparatorsPath &&
                                            outputFile.readBytes()
                                                .contentEquals(inputStream.readBytes())
                                if (!isExactDuplicate) {
                                    throw DuplicateFileCopyingException(
                                        "${jarEntry.name} is present in multiple jars with " +
                                                "different contents, cannot merge from $file."
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    internal class FusedLibraryCreationAction(
        private val creationConfig: FusedLibraryGlobalScope,
        private val inputDependenciesJarArtifactType: AndroidArtifacts.ArtifactType,
        private val outputMergedOutputArtifactType: FusedLibraryInternalArtifactType<Directory>,
        private val includeManifest: Boolean
    ) : GlobalTaskCreationAction<FusedLibraryMergeClasses>() {

        override val name: String
            get() = "merge${inputDependenciesJarArtifactType.name}"
        override val type: Class<FusedLibraryMergeClasses>
            get() = FusedLibraryMergeClasses::class.java

        override fun handleProvider(taskProvider: TaskProvider<FusedLibraryMergeClasses>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    FusedLibraryMergeClasses::outputDirectory
            ).on(outputMergedOutputArtifactType)
        }

        override fun configure(task: FusedLibraryMergeClasses) {
            super.configure(task)
            task.incoming.setFrom(
                    creationConfig.dependencies.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        inputDependenciesJarArtifactType
                    )
            )
            task.includeManifest.setDisallowChanges(includeManifest)
        }
    }
}
