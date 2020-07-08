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
import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.api.transform.QualifiedContent
import com.android.build.gradle.internal.databinding.DataBindingExcludeDelegate
import com.android.build.gradle.internal.databinding.configureFrom
import com.android.build.gradle.internal.dependency.getClassesDirFormat
import com.android.build.gradle.internal.packaging.JarCreatorFactory
import com.android.build.gradle.internal.packaging.JarCreatorType
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
import com.android.builder.files.SerializableFileChanges
import com.android.utils.FileUtils
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
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
import java.io.Serializable
import java.util.function.Predicate
import java.util.regex.Pattern
import java.util.zip.Deflater
import javax.inject.Inject

private val CLASS_PATTERN = Pattern.compile(".*\\.class$")
private val META_INF_PATTERN = Pattern.compile("^META-INF/.*$")

/** Common interface for bundle classes tasks to make configuring them easier. */
interface BundleLibraryClassesInputs {
    @get:Input
    val packageName: Property<String>

    @get:Classpath
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
    component: ComponentPropertiesImpl,
    inputs: FileCollection,
    packageRClass: Boolean
) {
    packageName.setDisallowChanges(component.packageName)
    classes.from(inputs)
    this.packageRClass.set(packageRClass)
    if (packageRClass) {
        classes.from(component.artifacts.get(InternalArtifactType.COMPILE_R_CLASS_JAR))
    }
    jarCreatorType.set(component.variantScope.jarCreatorType)

    dataBindingExcludeDelegate.configureFrom(component)
}

private fun BundleLibraryClassesInputs.getWorkerActionParams(
    inputChanges: InputChanges?, output: File
): BundleLibraryClassesRunnable.Params {
    val incrementalChanges = if (inputChanges?.isIncremental == true) {
        inputChanges.getFileChanges(classes)
    } else {
        emptyList()
    }
    val packageNameValue = packageName.get()
    return BundleLibraryClassesRunnable.Params(
        packageName = packageNameValue,
        toIgnore = dataBindingExcludeDelegate.orNull?.getExcludedClassList(packageNameValue) ?: listOf(),
        output = output,
        // Ignore non-existent files (without this, ResourceNamespaceTest would fail).
        input = classes.files.filter { file -> file.exists() }.toSet(),
        incremental = inputChanges?.isIncremental ?: false,
        inputChanges = incrementalChanges.toSerializable(),
        packageRClass = packageRClass.get(),
        jarCreatorType = jarCreatorType.get()
    )
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
        getWorkerFacadeWithWorkers().use {
            it.submit(
                BundleLibraryClassesRunnable::class.java,
                getWorkerActionParams(inputChanges, output.asFile.get())
            )
        }
    }

    class CreationAction(
        componentProperties: ComponentPropertiesImpl
    ) : VariantTaskCreationAction<BundleLibraryClassesDir, ComponentPropertiesImpl>(
        componentProperties
    ) {

        private val inputs: FileCollection

        init {
            // Because ordering matters for TransformAPI, we need to fetch classes from the
            // transform pipeline as soon as this creation action is instantiated.
            inputs =
                componentProperties.transformManager.getPipelineOutputAsFileCollection { types, scopes ->
                    types.contains(QualifiedContent.DefaultContentType.CLASSES)
                            && scopes.size == 1 && scopes.contains(QualifiedContent.Scope.PROJECT)
                }
        }

        override val name: String = componentProperties.computeTaskName("bundleLibRuntimeToDir")

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
        getWorkerFacadeWithWorkers().use {
            it.submit(
                BundleLibraryClassesRunnable::class.java,
                getWorkerActionParams(null, output.asFile.get())
            )
        }
    }

    class CreationAction(
        componentProperties: ComponentPropertiesImpl,
        private val publishedType: PublishedConfigType
    ) : VariantTaskCreationAction<BundleLibraryClassesJar, ComponentPropertiesImpl>(
        componentProperties
    ) {

        private val inputs: FileCollection

        init {
            check(
                publishedType == PublishedConfigType.API_ELEMENTS
                        || publishedType == PublishedConfigType.RUNTIME_ELEMENTS
            ) { "Library classes bundling is supported only for api and runtime." }
            // Because ordering matters for TransformAPI, we need to fetch classes from the
            // transform pipeline as soon as this creation action is instantiated.
            inputs = if (publishedType == PublishedConfigType.RUNTIME_ELEMENTS) {
                componentProperties.transformManager.getPipelineOutputAsFileCollection { types, scopes ->
                    types.contains(QualifiedContent.DefaultContentType.CLASSES)
                            && scopes.size == 1 && scopes.contains(QualifiedContent.Scope.PROJECT)
                }
            } else {
                componentProperties.artifacts.getAllClasses()
            }
        }

        override val name: String = componentProperties.computeTaskName(
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
                        creationConfig.buildFeatures.androidResources
            task.configure(creationConfig, inputs, packageRClass)
        }
    }
}

/** Packages files to jar using the provided filter. */
class BundleLibraryClassesRunnable @Inject constructor(private val params: Params) : Runnable {
    data class Params(
        val packageName: String,
        val toIgnore: List<String>,
        val output: File,
        val input: Set<File>,
        val incremental: Boolean,
        val inputChanges: SerializableFileChanges,
        val packageRClass: Boolean,
        val jarCreatorType: JarCreatorType
    ) : Serializable {

        companion object {

            private const val serialVersionUID = 1L
        }
    }

    override fun run() {
        val ignorePatterns =
            (LibraryAarJarsTask.getDefaultExcludes(
                packagePath = params.packageName.replace('.', '/'),
                packageR = params.packageRClass
            ) + params.toIgnore)
                .map { Pattern.compile(it) }

        val predicate = Predicate<String> { relativePath ->
            val normalizedPath = FileUtils.toSystemIndependentPath(relativePath)
            (CLASS_PATTERN.matcher(normalizedPath).matches()
                    || META_INF_PATTERN.matcher(normalizedPath).matches())
                    && !ignorePatterns.any { it.matcher(normalizedPath).matches() }
        }


        if (isJarFile(params.output)) {
            zipFilesNonIncrementally(params.input, params.output, predicate, params.jarCreatorType)
        } else {
            when (getClassesDirFormat(params.input)) {
                CONTAINS_SINGLE_JAR -> {
                    FileUtils.deleteRecursivelyIfExists(params.output)
                    FileUtils.mkdirs(params.output)
                    zipFilesNonIncrementally(
                        params.input,
                        params.output.resolve(FN_CLASSES_JAR),
                        predicate,
                        params.jarCreatorType
                    )
                }
                CONTAINS_CLASS_FILES_ONLY -> {
                    if (params.incremental) {
                        if (getClassesDirFormat(params.output) == CONTAINS_SINGLE_JAR) {
                            // It's not trivial to update the output directory incrementally from
                            // directory format CONTAINS_SINGLE_JAR to CONTAINS_CLASS_FILES_ONLY, so
                            // let's create it non-incrementally.
                            copyFilesNonIncrementally(params.input, params.output, predicate)
                        } else {
                            copyFilesIncrementally(params.inputChanges, params.output, predicate)
                        }
                    } else {
                        copyFilesNonIncrementally(params.input, params.output, predicate)
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
                if (relativePath.isNotEmpty() && filter.test(relativePath)) {
                    val outputFile = outputDir.resolve(relativePath)
                    FileUtils.mkdirs(outputFile.parentFile)
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
        inputChanges.removedFiles.forEach {
            val staleOutputFile = outputDir.resolve(it.normalizedPath)
            FileUtils.deleteRecursivelyIfExists(staleOutputFile)
        }
        (inputChanges.modifiedFiles + inputChanges.addedFiles).forEach {
            // If an added file is one of the roots of the FileCollection, normalizedPath will be
            // the file name, not an empty string, but we can probably ignore this edge case.
            val relativePath = it.normalizedPath
            if (relativePath.isNotEmpty() && filter.test(relativePath)) {
                val outputFile = outputDir.resolve(relativePath)
                FileUtils.mkdirs(outputFile.parentFile)
                FileUtils.copyFile(it.file, outputFile)
            }
        }
    }
}