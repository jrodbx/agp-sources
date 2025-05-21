/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryGlobalScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.services.SymbolTableBuildService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalGlobalTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.symbols.processLibraryMainSymbolTable
import com.android.ide.common.symbols.IdProvider
import com.android.ide.common.symbols.SymbolTable
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider

@BuildAnalyzer(primaryTaskCategory = TaskCategory.ANDROID_RESOURCES, [TaskCategory.MERGING])
@CacheableTask
abstract class FusedLibraryMergeResourceCompileSymbolsTask : NonIncrementalGlobalTask() {

    @get:Input
    abstract val namespace: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val symbolDependencyTables: ConfigurableFileCollection

    @get:Internal
    abstract val symbolTableBuildService: Property<SymbolTableBuildService>

    @get:OutputFile
    abstract val fusedSymbolFile: RegularFileProperty

    override fun doTaskAction() {
        processLibraryMainSymbolTable(
            librarySymbols = SymbolTable.EMPTY, // No sources in Fused Library
            depSymbolTables = symbolTableBuildService.get().loadClasspath(symbolDependencyTables),
            namespace.get(),
            rClassOutputJar = null,
            symbolFileOut = fusedSymbolFile.get().asFile,
            platformSymbols = SymbolTable.EMPTY,
            nonTransitiveRClass = false,
            generateDependencyRClasses = false,
            idProvider = IdProvider.constant()
        )
    }

    class CreationAction(private val creationConfig: FusedLibraryGlobalScope) :
        GlobalTaskCreationAction<FusedLibraryMergeResourceCompileSymbolsTask>() {

        override val name: String
            get() = "fusedLibraryMergeResourceCompileSymbols"
        override val type: Class<FusedLibraryMergeResourceCompileSymbolsTask>
            get() = FusedLibraryMergeResourceCompileSymbolsTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<FusedLibraryMergeResourceCompileSymbolsTask>) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                FusedLibraryMergeResourceCompileSymbolsTask::fusedSymbolFile
            ).withName(SdkConstants.FN_RESOURCE_TEXT)
                .on(FusedLibraryInternalArtifactType.COMPILE_SYMBOL_LIST)
        }

        override fun configure(task: FusedLibraryMergeResourceCompileSymbolsTask) {
            super.configure(task)
            task.namespace.setDisallowChanges(creationConfig.extension.namespace)
            task.symbolTableBuildService.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry))
            task.symbolDependencyTables.fromDisallowChanges(
                creationConfig.dependencies.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME
                )
            )
        }
    }

}
