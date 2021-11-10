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

package com.android.build.gradle.internal.tasks

import com.android.SdkConstants
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.databinding.DataBindingExcludeDelegate
import com.android.build.gradle.internal.databinding.configureFrom
import com.android.build.gradle.internal.packaging.JarCreatorFactory
import com.android.build.gradle.internal.packaging.JarCreatorType
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.builder.packaging.JarCreator
import com.android.builder.packaging.JarMerger
import com.android.tools.lint.typedefs.TypedefRemover
import com.android.utils.FileUtils
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.util.function.Predicate
import java.util.regex.Pattern
import java.util.zip.Deflater

/**
 * A Task that takes the project/project local jars for CLASSES and RESOURCES, and
 * processes and combines them, and put them in the bundle folder.
 *
 *
 * This creates a main jar with the classes from the main scope, and all the java resources, and
 * a set of local jars (which are mostly the same as before except without the resources). This is
 * used to package the AAR.
 */


// TODO(b/132975663): add workers
@CacheableTask
abstract class LibraryAarJarsTask : NonIncrementalTask() {
    @get:Nested
    @get:Optional
    abstract val dataBindingExcludeDelegate: Property<DataBindingExcludeDelegate>

    @get:Input
    abstract val namespace: Property<String>

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val typedefRecipe: RegularFileProperty

    @get:Classpath
    abstract val mainScopeClassFiles: ConfigurableFileCollection

    // We can't use @Classpath as it ignores empty directories which may still be used as resources.
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mainScopeResourceFiles: ConfigurableFileCollection

    @get:Classpath
    abstract val localScopeInputFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val mainClassLocation: RegularFileProperty

    @get:OutputDirectory
    abstract val localJarsLocation: DirectoryProperty

    @get:Input
    abstract val jarCreatorType: Property<JarCreatorType>

    @get:Input
    abstract val debugBuild: Property<Boolean>

    override fun doTaskAction() {
        // non incremental task, need to clear out outputs.
        // main class jar will get rewritten, just delete local jar folder content.
        FileUtils.deleteDirectoryContents(localJarsLocation.asFile.get())

        if (typedefRecipe.isPresent && !typedefRecipe.get().asFile.exists()) {
            throw IllegalStateException("Type def recipe not found: $typedefRecipe")
        }

        val excludePatterns = computeExcludeList()

        if (mainScopeClassFiles.isEmpty && mainScopeResourceFiles.isEmpty) {
            throw RuntimeException("Empty Main scope for $name")
        }

        mergeInputs(
            localScopeInputFiles.files,
            localJarsLocation.asFile.get(),
            mainScopeClassFiles.files,
            mainScopeResourceFiles.files,
            mainClassLocation.asFile.get(),
            { archivePath: String -> excludePatterns.any {
                it.matcher(archivePath).matches() }.not() },
            if (typedefRecipe.isPresent) {
                TypedefRemover().setTypedefFile(typedefRecipe.get().asFile)::filter
            } else {
                null
            },
            jarCreatorType.get(),
            if (debugBuild.get()) Deflater.BEST_SPEED else null
        )
    }

    private fun computeExcludeList(): List<Pattern> {
        val namespaceValue = namespace.get()

        val excludes = getDefaultExcludes(namespaceValue.replace(".", "/"))

        dataBindingExcludeDelegate.orNull?.let {
            excludes.addAll(it.getExcludedClassList(namespaceValue))
        }

        // create Pattern Objects.
        return excludes.map { Pattern.compile(it) }
    }

    companion object {

        fun mergeInputs(
            localJars: MutableSet<File>,
            localJarsLocation: File,
            classFiles: MutableSet<File>,
            resourceFiles: MutableSet<File>,
            toFile: File,
            filter: Predicate<String>,
            typedefRemover: JarCreator.Transformer?,
            jarCreatorType: JarCreatorType,
            compressionLevel: Int?) {

            // process main scope.
            mergeInputsToLocation(
                classFiles,
                resourceFiles,
                toFile,
                filter,
                typedefRemover,
                jarCreatorType,
                compressionLevel
            )

            // process local scope
            processLocalJars(localJars, localJarsLocation, jarCreatorType, compressionLevel)
        }


        private fun processLocalJars(
            inputs: MutableSet<File>,
            localJarsLocation: File,
            jarCreatorType: JarCreatorType,
            compressionLevel: Int?
        ) {

            /*
             * Separate jar and dir inputs, then copy the jars (almost) as is
             * then we'll make a single jars that contains all the folders
             * (though it's unlikely to happen)
             * Note that we do need to remove the resources
             * from the jars since they have been merged somewhere else.
             */

            val jarInputs = inputs.filter { it.name.endsWith(SdkConstants.DOT_JAR) }
            val dirInputs = inputs.filter { !it.name.endsWith(SdkConstants.DOT_JAR) }

            for (jar in jarInputs) {
                // we need to copy the jars but only take the class files as the resources have
                // been merged into the main jar.
                JarCreatorFactory.make(
                    File(localJarsLocation, jar.name).toPath(),
                    JarMerger.CLASSES_ONLY,
                    jarCreatorType
                ).use { jarCreator ->
                    compressionLevel?.let { jarCreator.setCompressionLevel(it) }
                    jarCreator.addJar(jar.toPath()) }
            }

            // now handle the folders.
            if (dirInputs.isNotEmpty()) {
                JarCreatorFactory.make(
                    File(localJarsLocation, "otherclasses.jar").toPath(),
                    JarMerger.CLASSES_ONLY,
                    jarCreatorType
                ).use {jarCreator ->
                    for (dir in dirInputs) {
                        jarCreator.addDirectory(dir.toPath())
                    }
                }
            }
        }

        private fun mergeInputsToLocation(
            classFiles: MutableSet<File>,
            resourceFiles: MutableSet<File>,
            toFile: File,
            filter: Predicate<String>,
            typedefRemover: JarCreator.Transformer?,
            jarCreatorType: JarCreatorType,
            compressionLevel: Int?
        ) {
            val filterAndOnlyClasses = JarMerger.CLASSES_ONLY.and(filter)

            JarCreatorFactory.make(
                jarFile = toFile.toPath(),
                type = jarCreatorType
            ).use { jarCreator ->
                compressionLevel?.let { jarCreator.setCompressionLevel(it) }
                // Merge only class files on CLASS type inputs
                for (input in classFiles) {
                    // Skip if file doesn't exist
                    if (!input.exists()) {
                        continue
                    }

                    if (input.name.endsWith(SdkConstants.DOT_JAR)) {
                        jarCreator.addJar(input.toPath(), filterAndOnlyClasses, null)
                    } else {
                        jarCreator.addDirectory(
                            input.toPath(), filterAndOnlyClasses, typedefRemover, null)
                    }
                }

                for (input in resourceFiles) {
                    // Skip if file doesn't exist
                    if (!input.exists()) {
                        continue
                    }

                    if (input.name.endsWith(SdkConstants.DOT_JAR)) {
                        jarCreator.addJar(input.toPath(), filter, null)
                    } else {
                        jarCreator.addDirectory(
                            input.toPath(), filter, typedefRemover, null)
                    }
                }
            }
        }


        fun getDefaultExcludes(
            packagePath: String, packageR: Boolean = false
        ): MutableList<String> {
            val excludes = ArrayList<String>(5)
            if (!packageR) {
                // these must be regexp to match the zip entries
                excludes.add(".*/R.class$")
                excludes.add(".*/R\\$(.*).class$")
            }
            excludes.add("$packagePath/Manifest.class$")
            excludes.add("$packagePath/Manifest\\$(.*).class$")
            return excludes
        }
    }

    class CreationAction(
        creationConfig: ComponentCreationConfig,
        private val minifyEnabled: Boolean
    ) : VariantTaskCreationAction<LibraryAarJarsTask, ComponentCreationConfig>(
        creationConfig
    ) {
        override val type = LibraryAarJarsTask::class.java
        override val name =  computeTaskName("sync", "LibJars")

        override fun handleProvider(
            taskProvider: TaskProvider<LibraryAarJarsTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                LibraryAarJarsTask::mainClassLocation
            ).withName(SdkConstants.FN_CLASSES_JAR).on(InternalArtifactType.AAR_MAIN_JAR)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                LibraryAarJarsTask::localJarsLocation
            ).withName(SdkConstants.LIBS_FOLDER)
                .on(InternalArtifactType.AAR_LIBS_DIRECTORY)
        }

        override fun configure(
            task: LibraryAarJarsTask
        ) {
            super.configure(task)

            task.dataBindingExcludeDelegate.configureFrom(creationConfig)

            val artifacts = creationConfig.artifacts

            artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.ANNOTATIONS_TYPEDEF_FILE,
                task.typedefRecipe
            )

            task.namespace.setDisallowChanges(creationConfig.namespace)
            task.jarCreatorType.setDisallowChanges(creationConfig.variantScope.jarCreatorType)
            task.debugBuild.setDisallowChanges(creationConfig.debuggable)

            /*
             * Only get files that are CLASS, and exclude files that are both CLASS and RESOURCES
             * The first filter gets streams that CONTAIN classes, the second one gets
             * ONLY class content
             *
             * Files coming from the transform streams might not exist.
             * Need to check if they exist during the task action [mergeInputsToLocation],
             * which means gradle will have to deal with possibly non-existent files in the cache
             */
            task.mainScopeClassFiles.from(
                if (minifyEnabled) {
                    creationConfig.artifacts
                        .get(InternalArtifactType.SHRUNK_CLASSES)
                } else {
                    @Suppress("DEPRECATION") // Legacy support (b/195153220)
                    creationConfig.transformManager
                        .getPipelineOutputAsFileCollection(
                            { contentTypes, scopes ->
                                contentTypes.contains(com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES)
                                        && scopes.contains(com.android.build.api.transform.QualifiedContent.Scope.PROJECT)
                            },
                            { contentTypes, scopes ->
                                (contentTypes.contains(com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES)
                                        && !contentTypes.contains(
                                    com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES))
                                        && scopes.contains(com.android.build.api.transform.QualifiedContent.Scope.PROJECT)
                            })
                }
            )
            task.mainScopeClassFiles.disallowChanges()

            task.mainScopeResourceFiles.from(
                if (minifyEnabled) {
                    creationConfig.artifacts
                        .get(InternalArtifactType.SHRUNK_JAVA_RES)
                } else {
                    @Suppress("DEPRECATION") // Legacy support (b/195153220)
                    creationConfig.transformManager
                        .getPipelineOutputAsFileCollection { contentTypes, scopes ->
                            contentTypes.contains(com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES)
                                    && scopes.contains(com.android.build.api.transform.QualifiedContent.Scope.PROJECT)
                        }
                }
            )
            task.mainScopeResourceFiles.disallowChanges()

            @Suppress("DEPRECATION") // Legacy support (b/195153220)
            task.localScopeInputFiles.from(
                creationConfig.transformManager
                    .getPipelineOutputAsFileCollection { contentTypes, scopes ->
                        (contentTypes.contains(com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES)
                                || contentTypes.contains(com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES))
                                && scopes.intersect(
                            TransformManager.SCOPE_FULL_LIBRARY_WITH_LOCAL_JARS).isNotEmpty()
                                && !scopes.contains(com.android.build.api.transform.QualifiedContent.Scope.PROJECT)
                    }
            )
            task.localScopeInputFiles.disallowChanges()
        }
    }

}
