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

package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.AnchorOutputType
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.core.VariantTypeImpl
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File

// TODO: Make incremental
abstract class AnalyzeDependenciesTask : NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var variantArtifact: Provider<FileCollection>
        private set

    private lateinit var externalArtifactCollection: ArtifactCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val externalArtifacts: FileCollection by lazy { externalArtifactCollection.artifactFiles }

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    // Don't need to be marked as input as they are represented in externalArtifacts
    private var apiDirectDependenciesConfiguration: Configuration? = null
    private lateinit var allDirectDependencies: Collection<Dependency>

    private var isVariantLibrary: Boolean? = null

    override fun doTaskAction() {
        val variantDepsHolder = VariantDependenciesHolder(
            allDirectDependencies,
            apiDirectDependenciesConfiguration?.allDependencies)
        val variantClassHolder = VariantClassesHolder(variantArtifact.get())
        val classFinder = ClassFinder(externalArtifactCollection)

        val depsUsageFinder =
            DependencyUsageFinder(classFinder, variantClassHolder, variantDepsHolder)

        val compileClasspathConfig = project.configurations.getAt("${variantName}CompileClasspath")
        if (compileClasspathConfig.isCanBeResolved) {
            val graphAnalyzer = DependencyGraphAnalyzer(
                compileClasspathConfig,
                depsUsageFinder)

            val reporter = DependencyUsageReporter(
                variantClassHolder,
                variantDepsHolder,
                classFinder,
                depsUsageFinder,
                graphAnalyzer)

            reporter.writeUnusedDependencies(
                File(
                    outputDirectory.asFile.get(),
                    "dependenciesReport.json"))

            // Report misconfigured dependencies only for library modules
            if (isVariantLibrary == true) {
                reporter.writeMisconfiguredDependencies(
                    File(outputDirectory.asFile.get(), "apiToImplementation.json")
                )
            }
        }
    }

    class VariantDependenciesHolder(
        _directAllDependencies: Collection<Dependency>,
        _directApiDependencies: Collection<Dependency>?) {

        val all = getDependenciesIds(_directAllDependencies)
        val api = getDependenciesIds(_directApiDependencies)

        private fun getDependenciesIds(dependencies: Collection<Dependency>?) =
            dependencies?.mapNotNull { buildDependencyId(it) }?.toSet() ?: emptySet()

        @VisibleForTesting
        internal fun buildDependencyId(dependency: Dependency): String? {
            if (dependency.group == null) {
                return null
            }

            var id = "${dependency.group}:${dependency.name}"
            if (dependency.version != null) {
                id += ":${dependency.version}"
            }

            return id
        }
    }

    class VariantClassesHolder(private val variantArtifact: FileCollection) {

        private enum class CLASS_TYPE { ALL, PUBLIC }

        private val analyzer = DependenciesAnalyzer()

        private val classesByType: Map<CLASS_TYPE, Set<String>> by lazy {
            val classesUsedInVariant = mutableSetOf<String>()
            val classesExposedByPublicApis = mutableSetOf<String>()

            variantArtifact.files.forEach { file ->
                file.walk().forEach { classFile ->
                    val name = classFile.name
                    if (classFile.isFile && name.endsWith(SdkConstants.DOT_CLASS)) {
                        classesUsedInVariant.addAll(
                            analyzer.findAllDependencies(classFile.inputStream()))
                        classesExposedByPublicApis.addAll(
                            analyzer.findPublicDependencies(classFile.inputStream()))
                    }
                }
            }

            mapOf(
                CLASS_TYPE.ALL to classesUsedInVariant,
                CLASS_TYPE.PUBLIC to classesUsedInVariant.minus(classesExposedByPublicApis))
        }

        /** Returns classes used inside our variant code. */
        fun getUsedClasses() = classesByType[CLASS_TYPE.ALL] ?: emptySet()

        /** Returns classes not exposed in any public method/fields/etc in our variant code. */
        fun getPublicClasses() = classesByType[CLASS_TYPE.PUBLIC] ?: emptySet()
    }

    class CreationAction(val scope: VariantScope) :
        VariantTaskCreationAction<AnalyzeDependenciesTask>(scope) {

        override val name: String
            get() = scope.getTaskName("analyze", "Dependencies")
        override val type: Class<AnalyzeDependenciesTask>
            get() = AnalyzeDependenciesTask::class.java

        override fun configure(task: AnalyzeDependenciesTask) {
            super.configure(task)

            task.variantArtifact = scope.artifacts
                .getFinalProductAsFileCollection(AnchorOutputType.ALL_CLASSES)

            task.externalArtifactCollection = scope
                .getArtifactCollection(
                    AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.CLASSES_JAR)

            task.apiDirectDependenciesConfiguration = scope
                .variantDependencies
                .getElements(AndroidArtifacts.PublishedConfigType.API_ELEMENTS)

            task.allDirectDependencies = scope
                .variantDependencies
                .incomingRuntimeDependencies

            task.isVariantLibrary = (scope.type == VariantTypeImpl.LIBRARY)
        }

        override fun handleProvider(taskProvider: TaskProvider<out AnalyzeDependenciesTask>) {
            super.handleProvider(taskProvider)

            variantScope.artifacts.producesDir(
                artifactType = InternalArtifactType.ANALYZE_DEPENDENCIES_REPORT,
                taskProvider = taskProvider,
                productProvider = AnalyzeDependenciesTask::outputDirectory,
                fileName = "analyzeDependencies"
            )
        }
    }

}