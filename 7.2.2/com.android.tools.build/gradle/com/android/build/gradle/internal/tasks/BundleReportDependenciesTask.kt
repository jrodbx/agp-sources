/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.tools.build.libraries.metadata.AppDependencies
import com.android.tools.build.libraries.metadata.Library
import com.android.tools.build.libraries.metadata.LibraryDependencies
import com.android.tools.build.libraries.metadata.ModuleDependencies
import com.android.tools.build.libraries.metadata.Repository
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.OutputFile
import java.io.FileOutputStream
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.util.LinkedList

/**
 * Task that generates the final bundle dependencies, combining all the module dependencies.
 */
@DisableCachingByDefault
abstract class BundleReportDependenciesTask : NonIncrementalTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val baseDeps: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var featureDeps: FileCollection
        internal set

    @get:OutputFile
    abstract val dependenciesList: RegularFileProperty

    public override fun doTaskAction() {

        val baseAppDeps =  BufferedInputStream(FileInputStream(baseDeps.get().asFile)).use {
            AppDependencies.parseFrom(it)
        }

        val featureAppDeps = LinkedList<AppDependencies>()

        featureDeps.files.forEach {
            featureAppDeps.add(AppDependencies.parseFrom(FileInputStream(it)))
        }

        val repoToIndexMap = mutableMapOf<Repository, Int>()
        val repoList = baseAppDeps.repositoriesList.toMutableList()

        val libraryToIndexMap = HashMap<Library, Integer>()
        val libraryList = LinkedList(baseAppDeps.libraryList)
        for ((index, lib) in libraryList.withIndex()) {
            libraryToIndexMap.put(lib, Integer(index))
        }
        val libraryDeps = LinkedList(baseAppDeps.libraryDependenciesList)
        val moduleDeps = LinkedList(baseAppDeps.moduleDependenciesList)

        for (featureAppDep in featureAppDeps) {
            val libIndexDict = HashMap<Integer, Integer>()
            val repoIndexDict = mutableMapOf<Int,Int>()
            val featureLibraryDeps = featureAppDep.libraryList

            // update the repos list
            featureAppDep.repositoriesList.forEachIndexed { origIndex, repo ->
                val newIndex = repoToIndexMap.computeIfAbsent(repo) {
                    repoList += it
                    repoList.size-1
                }
                repoIndexDict[origIndex] = newIndex
            }

            // update the library list indices
            for ((origIndex, lib) in featureLibraryDeps.withIndex()) {
                var newIndex = libraryToIndexMap.get(lib)
                if (newIndex == null) {
                    newIndex = Integer(libraryList.size)
                    libraryToIndexMap.put(lib, newIndex)
                    // If lib has a repo_index, build a copy with corrected value of repo_index
                    if (lib.hasRepoIndex())
                        libraryList.add(lib.toBuilder().also { libCopy ->
                            libCopy.repoIndexBuilder.value = repoIndexDict[lib.repoIndex.value]!!
                        }.build())
                    else
                        libraryList.add(lib)
                }
                libIndexDict.put(Integer(origIndex), newIndex)
            }
            // update the library dependencies list
            for(libraryDep in featureAppDep.libraryDependenciesList) {
                val transformedDepBuilder = LibraryDependencies.newBuilder()
                    .setLibraryIndex(libIndexDict[Integer(libraryDep.libraryIndex)]!!.toInt())
                for(depIndex in libraryDep.libraryDepIndexList) {
                    transformedDepBuilder
                        .addLibraryDepIndex(libIndexDict[Integer(depIndex)]!!.toInt())
                }
                val transformedDep = transformedDepBuilder.build()
                if (!libraryDeps.contains(transformedDep)) {
                    libraryDeps.add(transformedDep)
                }
            }
            // update the indices for the module dependencies
            for(moduleDep in featureAppDep.moduleDependenciesList) {
                val moduleDepBuilder = ModuleDependencies.newBuilder()
                    .setModuleName(moduleDep.moduleName)
                for(depIndex in moduleDep.dependencyIndexList) {
                    moduleDepBuilder
                        .addDependencyIndex(libIndexDict[Integer(depIndex)]!!.toInt())
                }
                moduleDeps.add(moduleDepBuilder.build())
            }

        }
        val appDeps = AppDependencies.newBuilder()
            .addAllLibrary(libraryList)
            .addAllLibraryDependencies(libraryDeps)
            .addAllModuleDependencies(moduleDeps)
            .addAllRepositories(repoList)
            .build()

        FileOutputStream(dependenciesList.get().asFile).use {
            appDeps.writeTo(it)
        }
    }


    class CreationAction(
        creationConfig: VariantCreationConfig
    ) : VariantTaskCreationAction<BundleReportDependenciesTask, VariantCreationConfig>(
        creationConfig
    ) {
        override val name: String = computeTaskName("configure", "Dependencies")
        override val type: Class<BundleReportDependenciesTask> = BundleReportDependenciesTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<BundleReportDependenciesTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                BundleReportDependenciesTask::dependenciesList
            ).withName("dependencies.pb").on(InternalArtifactType.BUNDLE_DEPENDENCY_REPORT)
        }

        override fun configure(
            task: BundleReportDependenciesTask
        ) {
            super.configure(task)
            creationConfig.artifacts.setTaskInputToFinalProduct(
                SingleArtifact.METADATA_LIBRARY_DEPENDENCIES_REPORT, task.baseDeps)
            task.featureDeps = creationConfig.variantDependencies.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
                AndroidArtifacts.ArtifactScope.ALL,
                AndroidArtifacts.ArtifactType.LIB_DEPENDENCIES
            )
        }
    }
}
