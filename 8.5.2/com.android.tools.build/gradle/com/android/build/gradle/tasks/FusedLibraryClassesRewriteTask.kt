/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryVariantScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.res.namespaced.NamespaceRewriter
import com.android.build.gradle.internal.services.SymbolTableBuildService
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.configureVariantProperties
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.internal.utils.toImmutableList
import com.android.buildanalyzer.common.TaskCategory
import com.android.ide.common.symbols.SymbolTable
import com.android.utils.FileUtils
import org.gradle.api.attributes.Usage
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File

/*
   Task intended to perform bytecode rewriting of compiled merged classes before publishing to
   a fused library.

   Types of rewriting performed:
   1. Creating an R class with all fused library module resource
   symbols and then pointing all references from library classes to reference
   the fused library R class rather than the library's local R class.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.COMPILED_CLASSES, secondaryTaskCategories = [TaskCategory.SOURCE_PROCESSING])
abstract class FusedLibraryClassesRewriteTask : NonIncrementalTask() {

    @get:OutputDirectory
    abstract val rewrittenClassesDirectory: DirectoryProperty

    @get:OutputFile
    abstract val fusedLibraryRClass: RegularFileProperty

    @get:Input
    abstract val fusedLibraryNamespace: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val librariesSymbolLists: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mergedClasses: ConfigurableFileCollection

    @get:Internal
    abstract val symbolTableBuildService: Property<SymbolTableBuildService>

    override fun doTaskAction() {
        val rewrittenClasses = rewrittenClassesDirectory.get().asFile
        val dependencyTables = symbolTableBuildService.get()
            .loadClasspath(librariesSymbolLists.files)
            .filterNot { it.symbols.isEmpty }
        val fusedLibSymbolTable = getFusedSymbolTable(fusedLibraryNamespace.get(), dependencyTables)
        val namespaceRewriter =
            NamespaceRewriter(listOf(fusedLibSymbolTable).plus(dependencyTables).toImmutableList())
        namespaceRewriter.writeRClass(fusedLibraryRClass.get().asFile.toPath())
        rewriteRClassReferencesToFusedLibraryNamespace(
            mergedClasses.asFileTree.files,
            rewrittenClasses,
            namespaceRewriter
        )
    }

    private fun rewriteRClassReferencesToFusedLibraryNamespace(
        incomingClassesFiles: Collection<File>,
        outgoingClassesDir: File,
        namespaceRewriter: NamespaceRewriter
    ) {
        val mergedClassFiles =
            incomingClassesFiles.filter { it.extension == SdkConstants.EXT_CLASS }
        for (incomingClass in mergedClassFiles) {
            val incomingClasspath = incomingClass.relativeTo(mergedClasses.singleFile)
            val rewriteOutputPath =
                FileUtils.join(outgoingClassesDir, incomingClasspath.toString())
            FileUtils.createFile(rewriteOutputPath, "")
            namespaceRewriter.rewriteClass(
                incomingClass.toPath(),
                rewriteOutputPath.toPath()
            )
        }
    }

    private fun getFusedSymbolTable(
        fusedLibraryNamespace: String,
        dependencySymbolTables: List<SymbolTable>
    ): SymbolTable {
        return SymbolTable.builder()
            .tablePackage(fusedLibraryNamespace)
            .addAll(dependencySymbolTables.flatMap { it.symbols.values() }.toSet()).build()
    }

    class CreationAction(val creationConfig: FusedLibraryVariantScope) :
        TaskCreationAction<FusedLibraryClassesRewriteTask>() {

        override val name: String
            get() = "rewriteClasses"
        override val type: Class<FusedLibraryClassesRewriteTask>
            get() = FusedLibraryClassesRewriteTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<FusedLibraryClassesRewriteTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                FusedLibraryClassesRewriteTask::rewrittenClassesDirectory
            ).on(FusedLibraryInternalArtifactType.CLASSES_WITH_REWRITTEN_R_CLASS_REFS)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                FusedLibraryClassesRewriteTask::fusedLibraryRClass
            ).withName(SdkConstants.FN_R_CLASS_JAR)
                .on(FusedLibraryInternalArtifactType.FUSED_R_CLASS)
        }

        override fun configure(task: FusedLibraryClassesRewriteTask) {
            task.configureVariantProperties("", task.project.gradle.sharedServices)
            task.fusedLibraryNamespace.setDisallowChanges(
                creationConfig.extension.namespace
            )
            task.librariesSymbolLists.from(
                creationConfig.dependencies.getArtifactFileCollection(
                    Usage.JAVA_RUNTIME,
                    creationConfig.mergeSpec,
                    AndroidArtifacts.ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME
                )
            )
            task.mergedClasses.setFrom(
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_CLASSES)
            )
            task.symbolTableBuildService.setDisallowChanges(
                SymbolTableBuildService.RegistrationAction(task.project).execute()
            )
        }
    }
}
