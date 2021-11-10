/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.SdkConstants.DOT_CLASS
import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.DOT_JSON
import com.android.build.api.artifact.MultipleArtifact
import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.instrumentation.AsmInstrumentationManager
import com.android.build.gradle.internal.instrumentation.ClassesHierarchyResolver
import com.android.build.gradle.internal.instrumentation.loadClassData
import com.android.build.gradle.internal.instrumentation.saveClassData
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.services.ClassesHierarchyBuildService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.JarsClasspathInputsWithIdentity
import com.android.build.gradle.internal.tasks.NewIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.builder.utils.isValidZipEntryName
import com.android.utils.FileUtils
import com.google.common.io.ByteStreams
import com.google.common.io.Files
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * A task that instruments the project classes with the asm visitors registered via the DSL.
 */
// TODO: Add parallelism
@CacheableTask
abstract class TransformClassesWithAsmTask : NewIncrementalTask() {

    @get:Input
    abstract val asmApiVersion: Property<Int>

    @get:Input
    abstract val framesComputationMode: Property<FramesComputationMode>

    @get:Nested
    abstract val visitorsList: ListProperty<AsmClassVisitorFactory<*>>

    @get:Incremental
    @get:Classpath
    abstract val inputClassesDir: ConfigurableFileCollection

    // This is used when jacoco instrumented jars are used as inputs
    @get:Incremental
    @get:Classpath
    @get:Optional
    abstract val inputJarsDir: DirectoryProperty

    @get:Nested
    abstract val inputJarsWithIdentity: JarsClasspathInputsWithIdentity

    @get:CompileClasspath
    abstract val runtimeClasspath: ConfigurableFileCollection

    @get:CompileClasspath
    abstract val bootClasspath: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val classesOutputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val jarsOutputDir: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val profilingTransforms: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val shouldPackageProfilerDependencies: Property<Boolean>

    @get:Internal
    abstract val classesHierarchyBuildService: Property<ClassesHierarchyBuildService>

    /**
     * A folder to save [com.android.build.api.instrumentation.ClassData] objects for classes that
     * were queried by a visitor using
     * [com.android.build.api.instrumentation.ClassContext.loadClassData] so if any of these classes
     * changed in a way that the ClassData is also changed, we will need to run non incrementally.
     */
    @get:OutputDirectory
    lateinit var incrementalFolder: File
        private set

    override fun doTaskAction(inputChanges: InputChanges) {
        val classesHierarchyResolver = classesHierarchyBuildService.get()
            .getClassesHierarchyResolverBuilder()
            .addProjectSources(inputClassesDir.files)
            .addProjectSources(inputJarsWithIdentity.inputJars.files)
            .addDependenciesSources(runtimeClasspath.files)
            .addDependenciesSources(bootClasspath.files)
            .build()
        if (inputChanges.isIncremental) {
            doIncrementalTaskAction(inputChanges, classesHierarchyResolver)
        } else {
            doFullTaskAction(inputChanges, classesHierarchyResolver)
        }
    }

    private fun doFullTaskAction(
        inputChanges: InputChanges,
        classesHierarchyResolver: ClassesHierarchyResolver
    ) {
        incrementalFolder.mkdirs()
        FileUtils.deleteDirectoryContents(classesOutputDir.get().asFile)
        FileUtils.deleteDirectoryContents(incrementalFolder)

        getInstrumentationManager(classesHierarchyResolver).use { instrumentationManager ->
            inputClassesDir.files.filter(File::exists).forEach {
                instrumentationManager.instrumentClassesFromDirectoryToDirectory(
                    it,
                    classesOutputDir.get().asFile
                )
            }

            processJars(instrumentationManager, inputChanges, false)
        }

        updateIncrementalState(emptySet(), classesHierarchyResolver)
    }

    private fun doIncrementalTaskAction(
        inputChanges: InputChanges,
        classesHierarchyResolver: ClassesHierarchyResolver
    ) {
        val previouslyQueriedClasses = incrementalFolder.listFiles()!!.map {
            it.name.removeSuffix(DOT_JSON)
        }.toSet()

        inputChanges.getFileChanges(inputClassesDir).filter { it.changeType == ChangeType.MODIFIED}
            .forEach {
                val className = it.normalizedPath.removeSuffix(DOT_CLASS)
                    .replace('/', '.')
                // check if this class that changed was queried in a previous build
                if (previouslyQueriedClasses.contains(className)) {
                    val classDataFromLastBuild =
                        loadClassData(File(incrementalFolder, className + DOT_JSON))!!
                    val currentClassData =
                        classesHierarchyResolver.maybeLoadClassDataForClass(className)

                    // the class data changed so we need to run the full task action
                    if (classDataFromLastBuild != currentClassData) {
                        doFullTaskAction(inputChanges, classesHierarchyResolver)
                        return
                    }
                }
        }

        getInstrumentationManager(classesHierarchyResolver).use { instrumentationManager ->
            val classesChanges = inputChanges.getFileChanges(inputClassesDir).toSerializable()

            classesChanges.removedFiles.plus(classesChanges.modifiedFiles).forEach {
                val outputFile = classesOutputDir.get().asFile.resolve(it.normalizedPath)
                FileUtils.deleteIfExists(outputFile)
            }

            classesChanges.addedFiles.plus(classesChanges.modifiedFiles).forEach {
                val outputFile = classesOutputDir.get().asFile.resolve(it.normalizedPath)
                instrumentationManager.instrumentModifiedFile(
                        inputFile = it.file,
                        outputFile = outputFile,
                        packageName = it.normalizedPath.removeSuffix("/${it.file.name}")
                                .replace('/', '.')
                )
            }

            processJars(instrumentationManager, inputChanges, true)
        }

        updateIncrementalState(previouslyQueriedClasses, classesHierarchyResolver)
    }

    private fun updateIncrementalState(
        previouslyQueriedClasses: Set<String>,
        classesHierarchyResolver: ClassesHierarchyResolver
    ) {
        classesHierarchyResolver.queriedProjectClasses.forEach { classData ->
            // we know that the data of the classes in previouslyQueriedClasses didn't change so
            // just save the classes that weren't queried before
            if (!previouslyQueriedClasses.contains(classData.className)) {
                saveClassData(
                    File(incrementalFolder, classData.className + DOT_JSON),
                    classData
                )
            }
        }
    }

    private fun processJars(
            instrumentationManager: AsmInstrumentationManager,
            inputChanges: InputChanges,
            isIncremental: Boolean
    ) {
        if (inputJarsDir.isPresent) {
            if (isIncremental) {
                inputChanges.getFileChanges(inputJarsDir).forEach { inputJar ->
                    val instrumentedJar = File(jarsOutputDir.get().asFile, inputJar.file.name)
                    FileUtils.deleteIfExists(instrumentedJar)
                    if (inputJar.changeType == ChangeType.ADDED ||
                            inputJar.changeType == ChangeType.MODIFIED) {
                        instrumentationManager.instrumentClassesFromJarToJar(inputJar.file,
                                instrumentedJar)
                    }
                }
            } else {
                FileUtils.deleteDirectoryContents(jarsOutputDir.get().asFile)
                extractProfilerDependencyJars()
                inputJarsDir.get().asFile.listFiles()?.forEach { inputJar ->
                    val instrumentedJar = File(jarsOutputDir.get().asFile, inputJar.name)
                    instrumentationManager.instrumentClassesFromJarToJar(inputJar, instrumentedJar)
                }
            }
        } else {
            val mappingState = inputJarsWithIdentity.getMappingState(inputChanges)
            if (mappingState.reprocessAll) {
                FileUtils.deleteDirectoryContents(jarsOutputDir.get().asFile)
                extractProfilerDependencyJars()
            }
            mappingState.jarsInfo.forEach { (file, info) ->
                if (info.hasChanged) {
                    val instrumentedJar = File(jarsOutputDir.get().asFile, info.identity + DOT_JAR)
                    FileUtils.deleteIfExists(instrumentedJar)
                    instrumentationManager.instrumentClassesFromJarToJar(file, instrumentedJar)
                }
            }
        }
    }

    /**
     * Extract profiler dependency jars and add them to the project jars as they need to be packaged
     * with the rest of the classes.
     */
    private fun extractProfilerDependencyJars() {
        if (shouldPackageProfilerDependencies.getOrElse(false)) {
            profilingTransforms.get().forEach { path ->
                val profilingTransformFile = File(path)
                extractDependencyJars(profilingTransformFile) { name: String ->
                    FileUtils.join(
                            jarsOutputDir.get().asFile,
                            "profiler-deps",
                            profilingTransformFile.nameWithoutExtension,
                            name + DOT_JAR
                    )
                }
            }
        }
    }

    private fun extractDependencyJars(inputJar: File, outputLocation: (String) -> File) {
        // To avoid https://bugs.openjdk.java.net/browse/JDK-7183373
        // we extract the resources directly as a zip file.
        ZipInputStream(FileInputStream(inputJar)).use { zis ->
            val pattern = Pattern.compile("dependencies/(.*)\\.jar")
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null && isValidZipEntryName(entry)) {
                val matcher = pattern.matcher(entry.name)
                if (matcher.matches()) {
                    val name = matcher.group(1)
                    val outputJar: File = outputLocation.invoke(name)
                    Files.createParentDirs(outputJar)
                    FileOutputStream(outputJar).use { fos -> ByteStreams.copy(zis, fos) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun getInstrumentationManager(
        classesHierarchyResolver: ClassesHierarchyResolver
    ): AsmInstrumentationManager {
        return AsmInstrumentationManager(
                visitors = visitorsList.get(),
                apiVersion = asmApiVersion.get(),
                classesHierarchyResolver = classesHierarchyResolver,
                framesComputationMode = framesComputationMode.get(),
                profilingTransforms = profilingTransforms.getOrElse(emptyList())
        )
    }

    class CreationAction(
            creationConfig: ComponentCreationConfig,
            val isTestCoverageEnabled: Boolean
    ) : VariantTaskCreationAction<TransformClassesWithAsmTask, ComponentCreationConfig>(
            creationConfig
    ) {

        override val name: String = computeTaskName("transform", "ClassesWithAsm")
        override val type: Class<TransformClassesWithAsmTask> =
                TransformClassesWithAsmTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<TransformClassesWithAsmTask>) {
            super.handleProvider(taskProvider)
            creationConfig
                    .artifacts
                    .setInitialProvider(taskProvider) { it.classesOutputDir }
                    .on(InternalArtifactType.ASM_INSTRUMENTED_PROJECT_CLASSES)

            creationConfig
                    .artifacts
                    .setInitialProvider(taskProvider) { it.jarsOutputDir }
                    .on(InternalArtifactType.ASM_INSTRUMENTED_PROJECT_JARS)
        }

        override fun configure(task: TransformClassesWithAsmTask) {
            super.configure(task)
            task.incrementalFolder = creationConfig.paths.getIncrementalDir(task.name)

            task.visitorsList.setDisallowChanges(creationConfig.registeredProjectClassesVisitors)

            task.framesComputationMode.setDisallowChanges(creationConfig.asmFramesComputationMode)

            task.asmApiVersion.setDisallowChanges(creationConfig.asmApiVersion)

            if (isTestCoverageEnabled &&
                !creationConfig.services.projectOptions[BooleanOption.ENABLE_JACOCO_TRANSFORM_INSTRUMENTATION]) {
                task.inputClassesDir.from(
                        creationConfig.artifacts.get(
                                InternalArtifactType.JACOCO_INSTRUMENTED_CLASSES
                        )
                )
                creationConfig.artifacts.setTaskInputToFinalProduct(
                        InternalArtifactType.JACOCO_INSTRUMENTED_JARS,
                        task.inputJarsDir
                )
            } else {
                task.inputClassesDir.from(
                    creationConfig.artifacts.getAll(MultipleArtifact.ALL_CLASSES_DIRS)
                )

                task.inputJarsWithIdentity.inputJars.from(
                    creationConfig.artifacts.getAll(MultipleArtifact.ALL_CLASSES_JARS)
                )
            }

            task.bootClasspath.from(creationConfig.variantScope.bootClasspath)

            task.runtimeClasspath.from(creationConfig.variantScope.providedOnlyClasspath)


            task.runtimeClasspath.from(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                        if (creationConfig.services.projectOptions[BooleanOption.ENABLE_JACOCO_TRANSFORM_INSTRUMENTATION]
                            && creationConfig.variantDslInfo.isTestCoverageEnabled
                        ) {
                            AndroidArtifacts.ArtifactType.JACOCO_CLASSES_JAR
                        } else {
                            AndroidArtifacts.ArtifactType.CLASSES_JAR
                        }
                    )
            )

            task.classesHierarchyBuildService.setDisallowChanges(
                    getBuildService(creationConfig.services.buildServiceRegistry)
            )

            if (creationConfig is ApkCreationConfig) {
                task.profilingTransforms.setDisallowChanges(
                        creationConfig.advancedProfilingTransforms
                )
                task.shouldPackageProfilerDependencies.setDisallowChanges(
                        creationConfig.shouldPackageProfilerDependencies
                )
            }
        }
    }
}
