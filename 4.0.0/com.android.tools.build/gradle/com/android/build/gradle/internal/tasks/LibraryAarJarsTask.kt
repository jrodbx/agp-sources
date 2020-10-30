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
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.QualifiedContent.Scope
import com.android.build.gradle.internal.packaging.JarCreatorFactory
import com.android.build.gradle.internal.packaging.JarCreatorType
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.builder.packaging.JarCreator
import com.android.builder.packaging.JarMerger
import com.android.builder.packaging.TypedefRemover
import com.android.utils.FileUtils
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.util.function.Predicate
import java.util.function.Supplier
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
    @get:Input
    abstract val excludeList: ListProperty<String>

    @get:Input
    abstract val packageName: Property<String>

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
            Predicate { archivePath: String -> excludePatterns.any {
                it.matcher(archivePath).matches() }.not() },
            if (typedefRecipe.isPresent) {
                TypedefRemover()
                    .setTypedefFile(typedefRecipe.get().asFile)
            } else {
                null
            },
            jarCreatorType.get(),
            if (debugBuild.get()) Deflater.BEST_SPEED else null
        )
    }

    private fun computeExcludeList(): List<Pattern> {
        val excludes = getDefaultExcludes(
            packageName.get().replace(".", "/"))

        excludes.addAll(excludeList.get())

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

            JarCreatorFactory.make(toFile.toPath(), jarCreatorType).use { jarCreator ->
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
        variantScope: VariantScope,
        private val excludeListProvider: Supplier<List<String>> =  Supplier { listOf<String>() }
    ) : VariantTaskCreationAction<LibraryAarJarsTask>(variantScope) {
        override val type = LibraryAarJarsTask::class.java
        override val name =  variantScope.getTaskName("sync", "LibJars")

        override fun handleProvider(taskProvider: TaskProvider<out LibraryAarJarsTask>) {
            super.handleProvider(taskProvider)

            variantScope.artifacts.producesFile(
                artifactType = InternalArtifactType.AAR_MAIN_JAR,
                taskProvider = taskProvider,
                productProvider = LibraryAarJarsTask::mainClassLocation,
                fileName = SdkConstants.FN_CLASSES_JAR
            )

            variantScope.artifacts.producesDir(
                artifactType = InternalArtifactType.AAR_LIBS_DIRECTORY,
                taskProvider = taskProvider,
                productProvider = LibraryAarJarsTask::localJarsLocation,
                fileName = SdkConstants.LIBS_FOLDER
            )
        }

        override fun configure(task: LibraryAarJarsTask) {
            super.configure(task)

            task.excludeList.set(
                variantScope.globalScope.project.provider {
                    excludeListProvider.get()
                }
            )
            task.excludeList.disallowChanges()

            val artifacts = variantScope.artifacts

            if (artifacts.hasFinalProduct(InternalArtifactType.ANNOTATIONS_TYPEDEF_FILE)) {
                artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.ANNOTATIONS_TYPEDEF_FILE,
                    task.typedefRecipe
                )
            }

            task.packageName.set(
                variantScope.globalScope.project.provider {
                    variantScope.variantDslInfo.packageFromManifest
                }
            )
            task.packageName.disallowChanges()

            task.jarCreatorType.setDisallowChanges(variantScope.jarCreatorType)

            task.debugBuild
                .setDisallowChanges(variantScope.variantDslInfo.isDebuggable)

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
                if (artifacts.hasFinalProduct(InternalArtifactType.SHRUNK_CLASSES)) {
                    artifacts
                        .getFinalProductAsFileCollection(InternalArtifactType.SHRUNK_CLASSES)
                        .get()
                } else {
                    variantScope.transformManager
                        .getPipelineOutputAsFileCollection(
                            { contentTypes, scopes ->
                                contentTypes.contains(QualifiedContent.DefaultContentType.CLASSES)
                                        && scopes.contains(Scope.PROJECT)
                            },
                            { contentTypes, scopes ->
                                (contentTypes.contains(QualifiedContent.DefaultContentType.CLASSES)
                                        && !contentTypes.contains(
                                    QualifiedContent.DefaultContentType.RESOURCES))
                                        && scopes.contains(Scope.PROJECT)
                            })
                }
            )
            task.mainScopeClassFiles.disallowChanges()

            task.mainScopeResourceFiles.from(
                if (artifacts.hasFinalProduct(InternalArtifactType.SHRUNK_JAVA_RES)) {
                    artifacts
                        .getFinalProductAsFileCollection(InternalArtifactType.SHRUNK_JAVA_RES)
                        .get()
                } else {
                    variantScope.transformManager
                        .getPipelineOutputAsFileCollection { contentTypes, scopes ->
                            contentTypes.contains(QualifiedContent.DefaultContentType.RESOURCES)
                                    && scopes.contains(Scope.PROJECT)
                        }
                }
            )
            task.mainScopeResourceFiles.disallowChanges()

            task.localScopeInputFiles.from(
                variantScope.transformManager
                    .getPipelineOutputAsFileCollection { contentTypes, scopes ->
                        (contentTypes.contains(QualifiedContent.DefaultContentType.CLASSES)
                                || contentTypes.contains(QualifiedContent.DefaultContentType.RESOURCES))
                                && scopes.intersect(
                            TransformManager.SCOPE_FULL_LIBRARY_WITH_LOCAL_JARS).isNotEmpty()
                                && !scopes.contains(Scope.PROJECT)
                    }
            )
            task.localScopeInputFiles.disallowChanges()
        }
    }

}