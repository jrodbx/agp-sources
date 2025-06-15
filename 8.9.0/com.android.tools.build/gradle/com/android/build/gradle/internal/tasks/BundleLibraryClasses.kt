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

import com.android.SdkConstants.FN_CLASSES_JAR
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.internal.caching.DisabledCachingReason.SIMPLE_MERGING_TASK
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.KmpCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.databinding.DataBindingExcludeDelegate
import com.android.build.gradle.internal.databinding.configureFrom
import com.android.build.gradle.internal.dependency.getClassesDirFormat
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES_DIR
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ClassesDirFormat.CONTAINS_CLASS_FILES_ONLY
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ClassesDirFormat.CONTAINS_SINGLE_JAR
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.toSerializable
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.dexing.isJarFile
import com.android.builder.files.SerializableFileChanges
import com.android.builder.packaging.JarFlinger
import com.android.utils.FileUtils
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File
import java.util.function.Predicate
import java.util.regex.Pattern
import java.util.zip.Deflater

private val CLASS_PATTERN = Pattern.compile(".*\\.class$")
private val META_INF_PATTERN = Pattern.compile("^META-INF/.*$")

/** Common interface for bundle classes tasks to make configuring them easier. */
interface BundleLibraryClassesInputs {
    @get:Input
    val namespace: Property<String>

    @get:Classpath
    @get:Optional
    val classes: ConfigurableFileCollection

    @get:Input
    val packageRClass: Property<Boolean>

    @get:Nested
    @get:Optional
    val dataBindingExcludeDelegate: Property<DataBindingExcludeDelegate>
}

private fun BundleLibraryClassesInputs.configure(
    creationConfig: ComponentCreationConfig,
    packageRClass: Boolean,
    publishRuntimeElement: Boolean,
) {
    namespace.setDisallowChanges(creationConfig.namespace)

    // For inter-project publishing, we cannot minify the library program classes before publishing
    // if the local file dependencies are minified which would cause local file deps to be included
    // in AAR. However, there isn't a good way to avoid the consumer(e.g. application) getting
    // ”duplicate” local file dependencies, one from the AAR, the other by getting EXTERNAL
    // dependencies .
    val disableMinifyLocalDeps = creationConfig.services.projectOptions.get(
        BooleanOption.DISABLE_MINIFY_LOCAL_DEPENDENCIES_FOR_LIBRARIES)

    if (creationConfig is ConsumableCreationConfig
            && creationConfig.optimizationCreationConfig.minifiedEnabled
            && publishRuntimeElement
            && disableMinifyLocalDeps
    ) {
        classes.from(creationConfig.artifacts.get(InternalArtifactType.SHRUNK_CLASSES))
    } else {
        classes.from(creationConfig.artifacts
                .forScope(ScopedArtifacts.Scope.PROJECT)
                .getFinalArtifacts(ScopedArtifact.CLASSES))
    }

    if (packageRClass) {
        classes.from(
                creationConfig.artifacts.get(InternalArtifactType.COMPILE_R_CLASS_JAR)
        )
    }
    this.packageRClass.setDisallowChanges(packageRClass)

    dataBindingExcludeDelegate.configureFrom(creationConfig)
}

private fun BundleLibraryClassesInputs.configureWorkerActionParams(
    params: BundleLibraryClassesWorkAction.Params,
    inputChanges: InputChanges?,
    output: Provider<File>
) {

    val incrementalChanges = if (inputChanges?.isIncremental == true) {
        inputChanges.getFileChanges(classes)
    } else {
        emptyList()
    }
    params.namespace.set(namespace)
    params.toIgnore.set(
        dataBindingExcludeDelegate.orNull?.getExcludedClassList(namespace.get()) ?: listOf()
    )
    params.output.set(output)
    // Ignore non-existent files (without this, ResourceNamespaceTest would fail).
    params.input.from(classes.files.filter { file -> file.exists() }.toSet())
    params.incremental.set(inputChanges?.isIncremental ?: false)
    params.inputChanges.set(incrementalChanges.toSerializable())
    params.packageRClass.set(packageRClass)
}

/**
 * Bundles all library classes to a directory.
 */
@DisableCachingByDefault(because = SIMPLE_MERGING_TASK)
@BuildAnalyzer(primaryTaskCategory = TaskCategory.COMPILED_CLASSES, secondaryTaskCategories = [TaskCategory.ZIPPING])
abstract class BundleLibraryClassesDir: NewIncrementalTask(), BundleLibraryClassesInputs {

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @get:Classpath
    @get:Incremental
    abstract override val classes: ConfigurableFileCollection

    override fun doTaskAction(inputChanges: InputChanges) {
        workerExecutor.noIsolation().submit(
            BundleLibraryClassesWorkAction::class.java
        ) {
            it.initializeFromBaseTask(this)
            configureWorkerActionParams(it, inputChanges, output.asFile)
        }
    }

    class CreationAction(
        creationConfig: ComponentCreationConfig
    ) : VariantTaskCreationAction<BundleLibraryClassesDir, ComponentCreationConfig>(
        creationConfig
    ) {
        override val name: String = creationConfig.computeTaskNameInternal("bundleLibRuntimeToDir")

        override val type: Class<BundleLibraryClassesDir> = BundleLibraryClassesDir::class.java

        override fun handleProvider(taskProvider: TaskProvider<BundleLibraryClassesDir>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts
                .setInitialProvider(taskProvider, BundleLibraryClassesDir::output)
                .on(InternalArtifactType.RUNTIME_LIBRARY_CLASSES_DIR)
        }

        override fun configure(task: BundleLibraryClassesDir) {
            super.configure(task)

            task.configure(
                creationConfig,
                packageRClass = false,
                publishRuntimeElement = true)
        }
    }

    class KotlinMultiplatformCreationAction(
        creationConfig: KmpCreationConfig
    ) : VariantTaskCreationAction<BundleLibraryClassesDir, KmpCreationConfig>(
        creationConfig
    ) {

        override val name: String = creationConfig.computeTaskNameInternal("bundleLibRuntimeToDir")

        override val type: Class<BundleLibraryClassesDir> = BundleLibraryClassesDir::class.java

        override fun handleProvider(taskProvider: TaskProvider<BundleLibraryClassesDir>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts
                .setInitialProvider(taskProvider, BundleLibraryClassesDir::output)
                .on(InternalArtifactType.RUNTIME_LIBRARY_CLASSES_DIR)
        }

        override fun configure(task: BundleLibraryClassesDir) {
            super.configure(task)
            task.configure(
                creationConfig,
                packageRClass = false,
                publishRuntimeElement = false
            )
        }
    }
}

/**
 * Bundles all library classes to a jar.
 */
@DisableCachingByDefault(because = SIMPLE_MERGING_TASK)
@BuildAnalyzer(primaryTaskCategory = TaskCategory.COMPILED_CLASSES, secondaryTaskCategories = [TaskCategory.ZIPPING])
abstract class BundleLibraryClassesJar : NonIncrementalTask(), BundleLibraryClassesInputs {

    @get:OutputFile
    abstract val output: RegularFileProperty

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(
            BundleLibraryClassesWorkAction::class.java
        ) {
            it.initializeFromBaseTask(this)
            configureWorkerActionParams(it, null, output.asFile)
        }
    }

    class CreationAction(
        creationConfig: ComponentCreationConfig,
        private val publishedType: PublishedConfigType
    ) : VariantTaskCreationAction<BundleLibraryClassesJar, ComponentCreationConfig>(
        creationConfig
    ) {
        override val name: String = creationConfig.computeTaskNameInternal(
            if (publishedType == PublishedConfigType.API_ELEMENTS) {
                "bundleLibCompileToJar"
            } else {
                "bundleLibRuntimeToJar"
            }
        )

        override val type: Class<BundleLibraryClassesJar> = BundleLibraryClassesJar::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<BundleLibraryClassesJar>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts
                .setInitialProvider(
                    taskProvider,
                    BundleLibraryClassesJar::output
                ).withName(creationConfig.getArtifactName(FN_CLASSES_JAR)).let {
                    if (publishedType == PublishedConfigType.API_ELEMENTS) {
                        it.on(InternalArtifactType.COMPILE_LIBRARY_CLASSES_JAR)
                    } else {
                        it.on(InternalArtifactType.RUNTIME_LIBRARY_CLASSES_JAR)
                    }
                }
        }

        override fun configure(task: BundleLibraryClassesJar) {
            super.configure(task)
            val packageRClass =
                    publishedType == PublishedConfigType.API_ELEMENTS && creationConfig.buildFeatures.androidResources

            task.configure(
                    creationConfig,
                    packageRClass,
                    publishedType != PublishedConfigType.API_ELEMENTS
            )
        }
    }

    class KotlinMultiplatformCreationAction(
        component: KmpCreationConfig,
        private val publishedType: PublishedConfigType
    ) : VariantTaskCreationAction<BundleLibraryClassesJar, KmpCreationConfig>(
        component
    ) {

        init {
            check(
                publishedType == PublishedConfigType.API_ELEMENTS
                        || publishedType == PublishedConfigType.RUNTIME_ELEMENTS
            ) { "Kotlin multiplatform classes bundling is supported only for api and runtime." }
        }

        override val name: String = computeTaskName(
            "bundle",
            if (publishedType == PublishedConfigType.API_ELEMENTS) {
                "ClassesToCompileJar"
            } else {
                "ClassesToRuntimeJar"
            }
        )

        override val type: Class<BundleLibraryClassesJar> = BundleLibraryClassesJar::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<BundleLibraryClassesJar>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts
                .setInitialProvider(
                    taskProvider,
                    BundleLibraryClassesJar::output
                ).withName(FN_CLASSES_JAR).let {
                    if (publishedType == PublishedConfigType.API_ELEMENTS) {
                        it.on(InternalArtifactType.COMPILE_LIBRARY_CLASSES_JAR)
                    } else {
                        it.on(InternalArtifactType.RUNTIME_LIBRARY_CLASSES_JAR)
                    }
                }
        }

        override fun configure(task: BundleLibraryClassesJar) {
            super.configure(task)
            val packageRClass =
                publishedType == PublishedConfigType.API_ELEMENTS && creationConfig.buildFeatures.androidResources

            task.namespace.setDisallowChanges(creationConfig.namespace)
            task.classes.from(
                creationConfig
                .artifacts
                .forScope(ScopedArtifacts.Scope.PROJECT)
                .getFinalArtifacts(ScopedArtifact.CLASSES)
            )
            if (packageRClass) {
                task.classes.from(
                    creationConfig.artifacts.get(InternalArtifactType.COMPILE_R_CLASS_JAR)
                )
            }
            task.classes.disallowChanges()
            task.packageRClass.setDisallowChanges(packageRClass)
        }
    }
}

/** Packages files to jar using the provided filter. */
abstract class BundleLibraryClassesWorkAction : ProfileAwareWorkAction<BundleLibraryClassesWorkAction.Params>() {
    abstract class Params: ProfileAwareWorkAction.Parameters() {
        abstract val namespace: Property<String>
        abstract val toIgnore: ListProperty<String>
        abstract val output: Property<File>
        abstract val input: ConfigurableFileCollection
        abstract val incremental: Property<Boolean>
        abstract val inputChanges: Property<SerializableFileChanges>
        abstract val packageRClass: Property<Boolean>
    }

    override fun run() {
        val ignorePatterns =
            (LibraryAarJarsTask.getDefaultExcludes(
                packageR = parameters.packageRClass.get()
            ) + parameters.toIgnore.get())
                .map { Pattern.compile(it) }

        val predicate = Predicate<String> { relativePath ->
            val normalizedPath = FileUtils.toSystemIndependentPath(relativePath)
            (CLASS_PATTERN.matcher(normalizedPath).matches()
                    || META_INF_PATTERN.matcher(normalizedPath).matches())
                    && !ignorePatterns.any { it.matcher(normalizedPath).matches() }
        }


        if (isJarFile(parameters.output.get())) {
            zipFilesNonIncrementally(parameters.input.files, parameters.output.get(), predicate)
        } else {
            when (getClassesDirFormat(parameters.input.files)) {
                CONTAINS_SINGLE_JAR -> {
                    FileUtils.deleteRecursivelyIfExists(parameters.output.get())
                    FileUtils.mkdirs(parameters.output.get())
                    zipFilesNonIncrementally(
                        parameters.input.files,
                        parameters.output.get().resolve(FN_CLASSES_JAR),
                        predicate,
                    )
                }
                CONTAINS_CLASS_FILES_ONLY -> {
                    if (parameters.incremental.get()) {
                        if (getClassesDirFormat(parameters.output.get()) == CONTAINS_SINGLE_JAR) {
                            // It's not trivial to update the output directory incrementally from
                            // directory format CONTAINS_SINGLE_JAR to CONTAINS_CLASS_FILES_ONLY, so
                            // let's create it non-incrementally.
                            copyFilesNonIncrementally(parameters.input.files, parameters.output.get(), predicate)
                        } else {
                            copyFilesIncrementally(parameters.inputChanges.get(), parameters.output.get(), predicate)
                        }
                    } else {
                        copyFilesNonIncrementally(parameters.input.files, parameters.output.get(), predicate)
                    }
                }
            }
        }
    }

    /**
     * Returns the format of the directory with artifact type [CLASSES_DIR]. The format
     * depends on whether the input classes contain jars nor not. See
     * [AndroidArtifacts.ClassesDirFormat] for more details.
     */
    private fun getClassesDirFormat(inputClassRoots: Set<File>): AndroidArtifacts.ClassesDirFormat {
        return if (inputClassRoots.any { isJarFile(it) }) {
            CONTAINS_SINGLE_JAR
        } else {
            CONTAINS_CLASS_FILES_ONLY
        }
    }

    private fun zipFilesNonIncrementally(
        input: Set<File>,
        outputJar: File,
        filter: Predicate<String>,
    ) {
        FileUtils.deleteIfExists(outputJar)
        FileUtils.mkdirs(outputJar.parentFile)
        JarFlinger(outputJar.toPath(), filter).use { jarCreator ->
            // Don't compress because compressing takes extra time, and this jar doesn't go into any
            // APKs or AARs
            jarCreator.setCompressionLevel(Deflater.NO_COMPRESSION)
            input.forEach { base ->
                if (base.isDirectory) {
                    jarCreator.addDirectory(base.toPath())
                } else {
                    check(isJarFile(base)) { "Expected jar file but found ${base.path}." }
                    jarCreator.addJar(base.toPath())
                }
            }
        }
    }

    private fun copyFilesNonIncrementally(
        input: Set<File>,
        outputDir: File,
        filter: Predicate<String>
    ) {
        FileUtils.deleteRecursivelyIfExists(outputDir)
        FileUtils.mkdirs(outputDir)
        input.forEach { classRoot ->
            check(classRoot.isDirectory) { "Expected directory but found ${classRoot.path}." }
            for (it in classRoot.walk()) {
                val relativePath = it.relativeTo(classRoot).path
                if (relativePath.isEmpty() || !filter.test(relativePath)) continue

                val outputLocation = outputDir.resolve(relativePath)
                if (it.isFile) {
                    FileUtils.mkdirs(outputLocation.parentFile)
                    if (outputLocation.isFile) {
                        throw IllegalStateException("File $outputLocation already exists, it cannot be overwritten by $it.")
                    }
                    FileUtils.copyFile(it, outputLocation)
                } else if (it.isDirectory && !outputLocation.isDirectory) {
                    FileUtils.mkdirs(outputLocation)
                }
            }
        }
    }

    private fun copyFilesIncrementally(
        inputChanges: SerializableFileChanges,
        outputDir: File,
        filter: Predicate<String>
    ) {
        (inputChanges.removedFiles + inputChanges.modifiedFiles).forEach {
            val staleOutputFile = outputDir.resolve(it.normalizedPath)
            FileUtils.deleteRecursivelyIfExists(staleOutputFile)
        }
        (inputChanges.modifiedFiles + inputChanges.addedFiles).forEach {
            // If an added file is one of the roots of the FileCollection, normalizedPath will be
            // the file name, not an empty string, but we can probably ignore this edge case.
            val relativePath = it.normalizedPath
            if (relativePath.isEmpty() || !filter.test(relativePath)) return@forEach

            val outputLocation = outputDir.resolve(relativePath)
            if (it.file.isFile) {
                FileUtils.mkdirs(outputLocation.parentFile)
                if (outputLocation.isFile) {
                    throw IllegalStateException("File $outputLocation already exists, it cannot be overwritten by $it.")
                }
                FileUtils.copyFile(it.file, outputLocation)
            } else if (it.file.isDirectory && !outputLocation.isDirectory) {
                FileUtils.mkdirs(outputLocation)
            }
        }
    }
}
