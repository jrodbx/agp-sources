/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.SymbolTableBuildService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.symbols.exportToCompiledJava
import com.android.ide.common.symbols.SymbolTable
import com.android.tools.r8.ClassFileResourceProvider
import com.android.tools.r8.ProgramResource
import com.android.tools.r8.origin.Origin
import com.android.tools.r8.tracereferences.TraceReferences
import com.android.tools.r8.tracereferences.TraceReferencesCheckConsumer
import com.android.tools.r8.tracereferences.TraceReferencesCommand
import com.android.tools.r8.tracereferences.TraceReferencesConsumer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Files

/**
 * Task to verify that all the classes in an AAR link against the stated dependency
 *
 * This uses R8's TraceDependencies to check that all references can be resolved.
 *
 * This currently doesn't handle compileOnly dependencies, they will lead to validation
 * errors.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.VERIFICATION)
abstract class VerifyLibraryClassesTask : NonIncrementalTask() {

    @get:OutputDirectory
    abstract val verifiedOutputDirectory: DirectoryProperty

    @get:Classpath
    abstract val aarMainJar: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val aarLibsDirectory: DirectoryProperty

    @get:Classpath
    abstract val runtime: ConfigurableFileCollection

    @get:Classpath
    abstract val bootclasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val runtimeRSymbolLists: ConfigurableFileCollection

    @get:Internal
    abstract val symbolTableBuildService: Property<SymbolTableBuildService>

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(TraceReferencesWorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.aarMainJar.set(aarMainJar)
            it.aarLibsDirectory.set(aarLibsDirectory)
            it.runtime.from(runtime)
            it.bootclasspath.from(bootclasspath)
            it.runtimeSymbolTables.from(runtimeRSymbolLists)
            it.symbolTableBuildService.set(symbolTableBuildService)
        }
    }

    abstract class Params: ProfileAwareWorkAction.Parameters() {
        abstract val aarMainJar: RegularFileProperty
        abstract val aarLibsDirectory: DirectoryProperty
        abstract val runtime: ConfigurableFileCollection
        abstract val bootclasspath: ConfigurableFileCollection
        abstract val runtimeSymbolTables: ConfigurableFileCollection
        abstract val symbolTableBuildService: Property<SymbolTableBuildService>
    }

    abstract class TraceReferencesWorkAction: ProfileAwareWorkAction<Params>() {
        override fun run() {
            val symbolTables: List<SymbolTable> =
                    parameters.symbolTableBuildService.get()
                            .loadClasspath(parameters.runtimeSymbolTables)

            TraceReferences.run(TraceReferencesCommand.builder().apply {
                addSourceFiles(parameters.aarMainJar.get().asFile.toPath())
                val libsDir = parameters.aarLibsDirectory.get().asFile.toPath()
                Files.list(libsDir).use { jars ->
                    jars.forEach { addSourceFiles(it) }
                }
                addLibraryFiles(parameters.runtime.map { it.toPath() })
                addLibraryFiles(parameters.bootclasspath.map { it.toPath() })
                addLibraryResourceProvider(SymbolTableRProvider(symbolTables))
                setConsumer(TraceReferencesCheckConsumer(TraceReferencesConsumer.emptyConsumer()))
            }.build())
        }
    }

    class SymbolTableRProvider(private val classes: Map<String, ByteArray>) : ClassFileResourceProvider {

        constructor(symbolTables: List<SymbolTable>) : this(mutableMapOf<String, ByteArray>().apply {
            exportToCompiledJava(symbolTables, false, null) { key, value ->
                this["L${key.removeSuffix(".class")};"] = value
            }
        })

        private val origin: Origin = object : Origin(root()) {
            override fun part(): String = "Runtime R class that will be generated by consuming app"
        }

        override fun getClassDescriptors(): Set<String> = classes.keys

        override fun getProgramResource(descriptor: String): ProgramResource? {
            return classes[descriptor]?.let {
                ProgramResource.fromBytes(origin, ProgramResource.Kind.CF, it, setOf())
            }
        }
    }

    class CreationAction(
            creationConfig: VariantCreationConfig
    ) : VariantTaskCreationAction<VerifyLibraryClassesTask, ComponentCreationConfig>(
            creationConfig
    ) {

        override val name: String = computeTaskName("verify", "Classes")
        override val type: Class<VerifyLibraryClassesTask> get() = VerifyLibraryClassesTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<VerifyLibraryClassesTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider = taskProvider,
                    property = VerifyLibraryClassesTask::verifiedOutputDirectory
            ).on(InternalArtifactType.VERIFIED_LIBRARY_CLASSES)
        }

        override fun configure(task: VerifyLibraryClassesTask) {
            super.configure(task)
            task.aarMainJar.set(creationConfig.artifacts.get(InternalArtifactType.AAR_MAIN_JAR))
            task.aarLibsDirectory.set(creationConfig.artifacts.get(InternalArtifactType.AAR_LIBS_DIRECTORY))
            task.runtime.from(creationConfig.variantDependencies.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH, // Should be equivalent in normal cases
                    AndroidArtifacts.ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.CLASSES_JAR,
            ))
            task.bootclasspath.from(creationConfig.global.fullBootClasspath)
            task.runtimeRSymbolLists.fromDisallowChanges(
                    creationConfig.artifacts.get(InternalArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME),
                    creationConfig.variantDependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME
                    )
            )
            task.symbolTableBuildService.setDisallowChanges(getBuildService(creationConfig.services.buildServiceRegistry))
        }
    }
}
