/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.feature

import com.android.SdkConstants.FN_CLASSES_JAR
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.getDirectories
import com.android.build.gradle.internal.scope.getRegularFiles
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.packaging.JarFlinger
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.file.ReproducibleFileVisitor
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.zip.Deflater

/**
 * Task to jar all classes in a project. This includes pre/post java classes, and compiled
 * namespaced R class (if it exists).
 *
 * It is used for e.g.:
 * - dependent features to compile against these classes without bundling them.
 * - unit tests to compile and run them against these classes.
 *
 * Caching disabled by default for this task because the task does very little work.
 * The task moves files from Inputs, unchanged, into a Jar file.
 * Calculating cache hit/miss and fetching results is likely more expensive than
 * simply executing the task.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.COMPILED_CLASSES, secondaryTaskCategories = [TaskCategory.ZIPPING])
abstract class BundleAllClasses : NonIncrementalTask() {

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @get:Classpath
    abstract val inputDirs: ConfigurableFileCollection

    @get:Classpath
    abstract val inputJars: ConfigurableFileCollection

    @get:Input
    lateinit var modulePath: String
        private set

    public override fun doTaskAction() {
        workerExecutor.noIsolation().submit(BundleAllClassesWorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.inputDirs.from(inputDirs)
            it.inputJars.from(inputJars)
            it.outputJar.set(outputJar)
        }
    }

    abstract class BundleAllClassesWorkAction :
        ProfileAwareWorkAction<BundleAllClassesWorkAction.Parameters>() {
        abstract class Parameters : ProfileAwareWorkAction.Parameters() {
            abstract val inputDirs: ConfigurableFileCollection
            abstract val inputJars: ConfigurableFileCollection
            abstract val outputJar: RegularFileProperty
        }

        override fun run() {
            val files = HashMap<String, File>()
            val collector = object : ReproducibleFileVisitor {
                override fun isReproducibleFileOrder() = true
                override fun visitFile(fileVisitDetails: FileVisitDetails) {
                    addFile(fileVisitDetails.relativePath.pathString, fileVisitDetails.file)
                }

                override fun visitDir(fileVisitDetails: FileVisitDetails) {
                }

                fun addFile(path: String, file: File) {
                    files[path] = file
                }
            }
            parameters.inputDirs.asFileTree.visit(collector)

            JarFlinger(
                parameters.outputJar.asFile.get().toPath(),
                null
            ).use { out ->
                // Don't compress because compressing takes extra time, and this jar doesn't go
                // into any APKs or AARs.
                out.setCompressionLevel(Deflater.NO_COMPRESSION)
                files.forEach { (path, file) -> out.addFile(path, file.toPath()) }
                parameters.inputJars.forEach {
                    out.addJar(it.toPath())
                }
            }
        }
    }

    class CreationAction(
        creationConfig: ComponentCreationConfig,
        private val publishedType: AndroidArtifacts.PublishedConfigType
    ) :
        VariantTaskCreationAction<BundleAllClasses, ComponentCreationConfig>(
            creationConfig
        ) {

        init {
            check(
                publishedType == AndroidArtifacts.PublishedConfigType.API_ELEMENTS
                        || publishedType == AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS
            ) { "App classes bundling is supported only for api and runtime." }
        }

        override val name: String
            get() = computeTaskName("bundle",
                if (publishedType == AndroidArtifacts.PublishedConfigType.API_ELEMENTS) {
                    "ClassesToCompileJar"
                } else {
                    "ClassesToRuntimeJar"
                }
            )
        override val type: Class<BundleAllClasses>
            get() = BundleAllClasses::class.java

        override fun handleProvider(taskProvider: TaskProvider<BundleAllClasses>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                BundleAllClasses::outputJar
            ).withName(FN_CLASSES_JAR).let {
                if (publishedType == AndroidArtifacts.PublishedConfigType.API_ELEMENTS) {
                    it.on(InternalArtifactType.COMPILE_APP_CLASSES_JAR)
                } else {
                    it.on(InternalArtifactType.RUNTIME_APP_CLASSES_JAR)
                }
            }
        }

        override fun configure(task: BundleAllClasses) {
            super.configure(task)
            // Only add the instrumented classes to the runtime jar
            if (publishedType == AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS) {
                val projectClasses = creationConfig.artifacts
                    .forScope(ScopedArtifacts.Scope.PROJECT)
                    .getFinalArtifacts(ScopedArtifact.CLASSES)

                task.inputJars
                    .from(projectClasses
                        .getRegularFiles(creationConfig.services.projectInfo.projectDirectory)
                    )
                task.inputDirs
                    .from(projectClasses
                        .getDirectories(creationConfig.services.projectInfo.projectDirectory))
            } else {
                task.inputDirs.from(
                    listOfNotNull(
                        creationConfig.getBuiltInKotlincOutput(),
                        creationConfig.getBuiltInKaptArtifact(InternalArtifactType.BUILT_IN_KAPT_CLASSES_DIR),
                        creationConfig.artifacts.get(InternalArtifactType.JAVAC),
                        creationConfig.oldVariantApiLegacySupport?.variantData?.allPreJavacGeneratedBytecode,
                        creationConfig.oldVariantApiLegacySupport?.variantData?.allPostJavacGeneratedBytecode
                    )
                )
                if (creationConfig.global.namespacedAndroidResources) {
                    task.inputJars.fromDisallowChanges(
                        creationConfig.artifacts.get(
                            InternalArtifactType.COMPILE_R_CLASS_JAR
                        )
                    )
                } else {
                    task.inputJars.fromDisallowChanges(
                        creationConfig.artifacts.get(
                            InternalArtifactType.COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR
                        )
                    )
                }
            }
            task.inputDirs.disallowChanges()
            task.inputJars.disallowChanges()
            task.modulePath = task.project.path
        }
    }
}
