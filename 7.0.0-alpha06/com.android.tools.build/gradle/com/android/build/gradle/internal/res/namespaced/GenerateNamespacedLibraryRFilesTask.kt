/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.res.namespaced

import com.android.SdkConstants
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.builder.symbols.exportToCompiledJava
import com.android.builder.symbols.writeSymbolListWithPackageName
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolTable
import com.google.common.collect.ImmutableList
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import javax.inject.Inject

/**
 * Class generating the R.jar and resource text files for a resource namespace aware library.
 */
@CacheableTask
abstract class GenerateNamespacedLibraryRFilesTask @Inject constructor(objects: ObjectFactory) :
    NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val partialRFiles: ListProperty<Directory>

    @get:Input
    abstract val namespace: Property<String>

    @get:Optional
    @get:OutputFile
    val rJarFile: RegularFileProperty = objects.fileProperty()

    @get:Optional
    @get:OutputFile
    abstract val textSymbolFile: RegularFileProperty

    @get:Optional
    @get:OutputFile
    abstract val symbolsWithPackageNameFile: RegularFileProperty

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(TaskAction::class.java) { parameters ->
            parameters.initializeFromAndroidVariantTask(this)
            parameters.partialRFiles.set(partialRFiles)
            parameters.namespace.set(namespace)
            parameters.rJarFile.set(rJarFile)
            parameters.textSymbolOutputFile.set(textSymbolFile)
            parameters.symbolsWithPackageNameOutputFile.set(symbolsWithPackageNameFile)
        }
    }

    abstract class Params : ProfileAwareWorkAction.Parameters() {
        abstract val partialRFiles: ListProperty<Directory>
        abstract val namespace: Property<String>
        abstract val rJarFile: RegularFileProperty
        abstract val textSymbolOutputFile: RegularFileProperty
        abstract val symbolsWithPackageNameOutputFile: RegularFileProperty
    }

    abstract class TaskAction: ProfileAwareWorkAction<Params>() {
        override fun run() {
            // Keeping the order is important.
            val partialRFiles: MutableList<File> = ArrayList(parameters.partialRFiles.get().size)
            val visitor = object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    partialRFiles.add(file.toFile())
                    return FileVisitResult.CONTINUE
                }
            }
            parameters.partialRFiles.get().forEach { directory ->
                Files.walkFileTree(directory.asFile.toPath(), emptySet(), 1, visitor)
            }
            // Read the symbol tables from the partial R.txt files and merge them into one.
            val resources = SymbolTable.mergePartialTables(partialRFiles, parameters.namespace.get())

            parameters.rJarFile.orNull?.apply {
                exportToCompiledJava(ImmutableList.of(resources), asFile.toPath())
            }
            parameters.textSymbolOutputFile.orNull?.apply {
                SymbolIo.writeForAar(resources, asFile)
            }
            parameters.symbolsWithPackageNameOutputFile.orNull?.apply {
                asFile.bufferedWriter().use { writer ->
                    writeSymbolListWithPackageName(resources, writer)
                }
            }
        }
    }


    class CreationAction(creationConfig: ComponentCreationConfig) :
        VariantTaskCreationAction<GenerateNamespacedLibraryRFilesTask, ComponentCreationConfig>(
            creationConfig
        ) {
        override val name: String get() = computeTaskName("generate", "RFile")

        override val type: Class<GenerateNamespacedLibraryRFilesTask>
            get() = GenerateNamespacedLibraryRFilesTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<GenerateNamespacedLibraryRFilesTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                GenerateNamespacedLibraryRFilesTask::rJarFile
            ).withName("R.jar").on(InternalArtifactType.COMPILE_R_CLASS_JAR)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                GenerateNamespacedLibraryRFilesTask::textSymbolFile
            ).withName(SdkConstants.FN_RESOURCE_TEXT).on(InternalArtifactType.COMPILE_SYMBOL_LIST)

            // Synthetic output for AARs (see SymbolTableWithPackageNameTransform), and created in
            // process resources for local subprojects.
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                GenerateNamespacedLibraryRFilesTask::symbolsWithPackageNameFile
            ).withName("package-aware-r.txt").on(InternalArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME)
        }

        override fun configure(
            task: GenerateNamespacedLibraryRFilesTask
        ) {
            super.configure(task)

            task.partialRFiles.setDisallowChanges(
                creationConfig.artifacts.getAll(
                InternalMultipleArtifactType.PARTIAL_R_FILES))
            task.namespace.setDisallowChanges(creationConfig.namespace)
        }
    }
}
