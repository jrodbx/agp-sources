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
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.databinding.DataBindingExcludeDelegate
import com.android.build.gradle.internal.databinding.configureFrom
import com.android.build.gradle.internal.dependency.getClassesDirFormat
import com.android.build.gradle.internal.packaging.JarCreatorFactory
import com.android.build.gradle.internal.packaging.JarCreatorType
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
import com.android.builder.dexing.isJarFile
import com.android.builder.files.SerializableChange
import com.android.builder.files.SerializableFileChanges
import com.android.utils.FileUtils
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File
import java.lang.IllegalStateException
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

    @get:Input
    val jarCreatorType: Property<JarCreatorType>
}

private fun BundleLibraryClassesInputs.configure(
    creationConfig: ComponentCreationConfig,
    inputs: FileCollection,
    packageRClass: Boolean
) {
    namespace.setDisallowChanges(creationConfig.namespace)
    classes.from(inputs)
    if (packageRClass) {
        classes.from(
                creationConfig.artifacts.get(InternalArtifactType.COMPILE_R_CLASS_JAR)
        )
    }
    this.packageRClass.setDisallowChanges(packageRClass)
    jarCreatorType.setDisallowChanges(creationConfig.variantScope.jarCreatorType)

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
    params.jarCreatorType.set(jarCreatorType)
}

/** Bundles all library classes to a directory. */
@CacheableTask
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
            it.initializeFromAndroidVariantTask(this)
            configureWorkerActionParams(it, inputChanges, output.asFile)
        }
    }

    class CreationAction(
        creationConfig: ComponentCreationConfig
    ) : VariantTaskCreationAction<BundleLibraryClassesDir, ComponentCreationConfig>(
        creationConfig
    ) {

        private val inputs: FileCollection

        init {
            // Because ordering matters for TransformAPI, we need to fetch classes from the
            // transform pipeline as soon as this creation action is instantiated.
            @Suppress("DEPRECATION") // Legacy support (b/195153220)
            inputs =
                creationConfig.transformManager.getPipelineOutputAsFileCollection { types, scopes ->
                    types.contains(com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES)
                            && scopes.size == 1 && scopes.contains(com.android.build.api.transform.QualifiedContent.Scope.PROJECT)
                }
        }

        override val name: String = creationConfig.computeTaskName("bundleLibRuntimeToDir")

        override val type: Class<BundleLibraryClassesDir> = BundleLibraryClassesDir::class.java

        override fun handleProvider(taskProvider: TaskProvider<BundleLibraryClassesDir>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts
                .setInitialProvider(taskProvider, BundleLibraryClassesDir::output)
                .on(InternalArtifactType.RUNTIME_LIBRARY_CLASSES_DIR)
        }

        override fun configure(task: BundleLibraryClassesDir) {
            super.configure(task)
            task.configure(creationConfig, inputs, false)
        }
    }
}

/** Bundles all library classes to a jar. */
@CacheableTask
abstract class BundleLibraryClassesJar : NonIncrementalTask(), BundleLibraryClassesInputs {

    @get:OutputFile
    abstract val output: RegularFileProperty

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(
            BundleLibraryClassesWorkAction::class.java
        ) {
            it.initializeFromAndroidVariantTask(this)
            configureWorkerActionParams(it, null, output.asFile)
        }
    }

    class CreationAction(
        creationConfig: ComponentCreationConfig,
        private val publishedType: PublishedConfigType
    ) : VariantTaskCreationAction<BundleLibraryClassesJar, ComponentCreationConfig>(
        creationConfig
    ) {

        private val inputs: FileCollection

        init {
            check(
                publishedType == PublishedConfigType.API_ELEMENTS
                        || publishedType == PublishedConfigType.RUNTIME_ELEMENTS
            ) { "Library classes bundling is supported only for api and runtime." }
            // Because ordering matters for TransformAPI, we need to fetch classes from the
            // transform pipeline as soon as this creation action is instantiated.
            @Suppress("DEPRECATION") // Legacy support (b/195153220)
            inputs = if (publishedType == PublishedConfigType.RUNTIME_ELEMENTS) {
                creationConfig.transformManager.getPipelineOutputAsFileCollection { types, scopes ->
                    types.contains(com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES)
                            && scopes.size == 1 && scopes.contains(com.android.build.api.transform.QualifiedContent.Scope.PROJECT)
                }
            } else {
                creationConfig.artifacts.getAllClasses()
            }
        }

        override val name: String = creationConfig.computeTaskName(
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
                creationConfig.services.projectOptions[BooleanOption.COMPILE_CLASSPATH_LIBRARY_R_CLASSES] &&
                        publishedType == PublishedConfigType.API_ELEMENTS &&
                        creationConfig.androidResourcesEnabled
            task.configure(creationConfig, inputs, packageRClass)
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
        abstract val jarCreatorType: Property<JarCreatorType>
    }

    override fun run() {
        val ignorePatterns =
            (LibraryAarJarsTask.getDefaultExcludes(
                packagePath = parameters.namespace.get().replace('.', '/'),
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
            zipFilesNonIncrementally(parameters.input.files, parameters.output.get(), predicate, parameters.jarCreatorType.get())
        } else {
            when (getClassesDirFormat(parameters.input.files)) {
                CONTAINS_SINGLE_JAR -> {
                    FileUtils.deleteRecursivelyIfExists(parameters.output.get())
                    FileUtils.mkdirs(parameters.output.get())
                    zipFilesNonIncrementally(
                        parameters.input.files,
                        parameters.output.get().resolve(FN_CLASSES_JAR),
                        predicate,
                        parameters.jarCreatorType.get()
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
        jarCreatorType: JarCreatorType
    ) {
        FileUtils.deleteIfExists(outputJar)
        FileUtils.mkdirs(outputJar.parentFile)
        JarCreatorFactory.make(outputJar.toPath(), filter, jarCreatorType).use { jarCreator ->
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
            classRoot.walk().forEach {
                val relativePath = it.relativeTo(classRoot).path
                if (it.isFile && relativePath.isNotEmpty() && filter.test(relativePath)) {
                    val outputFile = outputDir.resolve(relativePath)
                    FileUtils.mkdirs(outputFile.parentFile)
                    if (outputFile.isFile) {
                        throw IllegalStateException("File $outputFile already exists, it cannot be overwritten by $it.")
                    }
                    FileUtils.copyFile(it, outputFile)
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
            if (it.file.isFile && relativePath.isNotEmpty() && filter.test(relativePath)) {
                val outputFile = outputDir.resolve(relativePath)
                FileUtils.mkdirs(outputFile.parentFile)
                if (outputFile.isFile) {
                    throw IllegalStateException("File $outputFile already exists, it cannot be overwritten by $it.")
                }
                FileUtils.copyFile(it.file, outputFile)
            }
        }
    }
}
