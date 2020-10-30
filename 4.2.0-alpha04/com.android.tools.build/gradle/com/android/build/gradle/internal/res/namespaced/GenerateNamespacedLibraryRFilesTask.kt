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
import com.android.build.api.component.impl.ComponentPropertiesImpl
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
import java.io.Serializable
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
    abstract val packageForR: Property<String>

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
        getWorkerFacadeWithWorkers().use {
            it.submit(
                TaskAction::class.java,
                Params(
                    partialRFiles = partialRFiles.get().map(Directory::getAsFile),
                    packageForR = packageForR.get(),
                    rJarFile = rJarFile.orNull?.asFile,
                    textSymbolOutputFile = textSymbolFile.orNull?.asFile,
                    symbolsWithPackageNameOutputFile = symbolsWithPackageNameFile.orNull?.asFile
                )
            )
        }
    }

    private class Params(
        val partialRFiles: List<File>,
        val packageForR: String,
        val rJarFile: File?,
        val textSymbolOutputFile: File?,
        val symbolsWithPackageNameOutputFile: File?
    ) : Serializable

    private class TaskAction @Inject constructor(private val params: Params) : Runnable {
        override fun run() {
            // Keeping the order is important.
            val partialRFiles: MutableList<File> = ArrayList(params.partialRFiles.size)
            val visitor = object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    partialRFiles.add(file.toFile())
                    return FileVisitResult.CONTINUE
                }
            }
            params.partialRFiles.forEach { directory ->
                Files.walkFileTree(directory.toPath(), emptySet(), 1, visitor)
            }
            // Read the symbol tables from the partial R.txt files and merge them into one.
            val resources = SymbolTable.mergePartialTables(partialRFiles, params.packageForR)

            if (params.rJarFile != null) {
                // Generate the R.jar file containing compiled R class and its' inner classes.
                exportToCompiledJava(ImmutableList.of(resources), params.rJarFile.toPath())
            }
            if (params.textSymbolOutputFile != null) {
                SymbolIo.writeForAar(resources, params.textSymbolOutputFile)
            }
            if (params.symbolsWithPackageNameOutputFile != null) {
                params.symbolsWithPackageNameOutputFile.bufferedWriter().use { writer ->
                    writeSymbolListWithPackageName(resources, writer)
                }
            }
        }
    }


    class CreationAction(componentProperties: ComponentPropertiesImpl) :
        VariantTaskCreationAction<GenerateNamespacedLibraryRFilesTask, ComponentPropertiesImpl>(
            componentProperties
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
            task.packageForR.setDisallowChanges(creationConfig.packageName)
        }
    }
}
