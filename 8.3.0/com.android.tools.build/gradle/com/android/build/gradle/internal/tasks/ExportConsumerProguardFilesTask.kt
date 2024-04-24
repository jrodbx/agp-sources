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
import com.android.build.gradle.ProguardFiles
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.features.OptimizationTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.features.OptimizationTaskCreationActionImpl
import com.android.build.gradle.internal.utils.LibraryArtifactType
import com.android.build.gradle.internal.utils.getFilteredFiles
import com.android.build.gradle.internal.utils.immutableMapBuilder
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.errors.EvalIssueException
import com.android.utils.FileUtils
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.function.Consumer

/**
 * Consolidates Proguard files into a single location for use in dependent modules
 *
 *
 * Caching disabled by default for this task because the task does very little work.
 * Some verification logic is executed and some files are copied to new locations.
 * Calculating cache hit/miss and fetching results is likely more expensive than
 * simply executing the task.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.OPTIMIZATION)
abstract class ExportConsumerProguardFilesTask : NonIncrementalTask() {

    @get:Input
    var isBaseModule: Boolean = false
        private set

    @get:Input
    var isDynamicFeature: Boolean = false
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val defaultProguardFilesDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val consumerProguardFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputFiles: ConfigurableFileCollection

    @get:Input
    @get:Optional
    abstract val defaultProguardFileNames: SetProperty<String>

    @get:Input
    @get:Optional
    abstract val ignoredKeepRules: SetProperty<String>

    @get:Input
    @get:Optional
    abstract val ignoreAllKeepRules: Property<Boolean>

    @get:Internal
    lateinit var libraryKeepRules: ArtifactCollection
        private set

    @get:Internal("only for task execution")
    abstract val buildDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    public override fun doTaskAction() {
        // combine the default proguard files with the consumer ones into a single collection.
        val finalListOfProguardFiles = mutableListOf<File>().also {
            it.addAll(consumerProguardFiles.files)
        }
        if (defaultProguardFilesDirectory.isPresent) {
            defaultProguardFileNames.get().forEach {fileName ->
                finalListOfProguardFiles.add(
                    defaultProguardFilesDirectory.get().file(fileName).asFile
                )
            }
        }
        // We check for default files unless it's a base feature, which can include default files.
        if (!isBaseModule) {
            checkProguardFiles(
                buildDirectory,
                isDynamicFeature,
                finalListOfProguardFiles
            ) { exception -> throw EvalIssueException(exception) }
        }

        val filteredProguardFiles = if (isDynamicFeature) {
            getFilteredFiles(
                ignoredKeepRules.get(),
                ignoreAllKeepRules.get(),
                libraryKeepRules,
                inputFiles,
                LoggerWrapper.getLogger(ExportConsumerProguardFilesTask::class.java),
                LibraryArtifactType.KEEP_RULES
            )
        } else {
            inputFiles
        }

        workerExecutor.noIsolation().submit(ExportConsumerProguardRunnable::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.input.from(filteredProguardFiles)
            it.outputDir.set(outputDir)
        }
    }

    class CreationAction(creationConfig: VariantCreationConfig) :
        VariantTaskCreationAction<ExportConsumerProguardFilesTask, VariantCreationConfig>(
            creationConfig
        ), OptimizationTaskCreationAction by OptimizationTaskCreationActionImpl(
            creationConfig
        ) {

        override val name: String
            get() = computeTaskName("export", "ConsumerProguardFiles")

        override val type: Class<ExportConsumerProguardFilesTask>
            get() = ExportConsumerProguardFilesTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<ExportConsumerProguardFilesTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                ExportConsumerProguardFilesTask::outputDir
            ).on(InternalArtifactType.CONSUMER_PROGUARD_DIR)
        }

        override fun configure(
            task: ExportConsumerProguardFilesTask
        ) {
            super.configure(task)

            // For compatibility reasons, the `consumerProguardFiles` file collection contains
            // the default proguard files that can be specified in the DSL using :
            //          proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // This is problematic because such default proguard files are actually extracted
            // from the AGP jars resources. The extraction is done by `ExtractProguardFiles` task
            // and stored in the InternalArtifactType.DEFAULT_PROGUARD_FILES.
            // Since `consumerProguardFiles` is a List<File>, we cannot represent the task
            // dependency.
            // Therefore even though the file reference is contained in `consumerProguardFiles`,
            // it represents an implicit binding that is flagged by Gradle as an Error. To avoid
            // the problem, we should remove all the default proguard files from the
            // `consumerProguardFiles` collection and use a normal @InputFiles annotated field
            // to store the DEFAULT_PROGUARD_FILES artifact.

            // first, identify all the default proguard files contained in the List<File>
            // collection.
            val defaultProguardFiles = optimizationCreationConfig.consumerProguardFiles.filter {
                it.absolutePath.startsWith(ProguardFiles
                    .getDefaultProguardFileDirectory(creationConfig.services.projectInfo.buildDirectory).get().asFile.absolutePath)
            }
            // keep the file names, as the DEFAULT_PROGUARD_FILES is a directory.
            defaultProguardFiles.forEach {
                task.defaultProguardFileNames.add(it.name)
            }

            // calculate the final list, potentially removing all the default proguard files we
            // identified.
            val finalListOfProguardFiles = if (defaultProguardFiles.isNotEmpty()) {
                optimizationCreationConfig.consumerProguardFiles - defaultProguardFiles
            } else optimizationCreationConfig.consumerProguardFiles

            task.consumerProguardFiles.from(
                finalListOfProguardFiles
            )
            // that's how the default proguard files will be consumed by the Task.
            if (defaultProguardFiles.isNotEmpty()) {
                val defaultRulesFiles = creationConfig.global.globalArtifacts.get(
                    InternalArtifactType.DEFAULT_PROGUARD_FILES
                )
                task.defaultProguardFilesDirectory.set(defaultRulesFiles)
                task.inputFiles.from(defaultRulesFiles)
            }
            task.isBaseModule = creationConfig.componentType.isBaseModule
            task.isDynamicFeature = creationConfig.componentType.isDynamicFeature

            task.inputFiles.from(
                task.consumerProguardFiles,
                creationConfig
                    .artifacts
                    .get(InternalArtifactType.GENERATED_PROGUARD_FILE)
            )
            if (creationConfig.componentType.isDynamicFeature) {
                task.libraryKeepRules = creationConfig.variantDependencies.getArtifactCollection(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.UNFILTERED_PROGUARD_RULES
                )
                task.inputFiles.from(task.libraryKeepRules.artifactFiles)

                task.ignoredKeepRules.setDisallowChanges(
                    optimizationCreationConfig.ignoredLibraryKeepRules
                )
                task.ignoreAllKeepRules.setDisallowChanges(
                    optimizationCreationConfig.ignoreAllLibraryKeepRules
                )
            }
            task.buildDirectory.setDisallowChanges(task.project.layout.buildDirectory)
        }
    }

    companion object {
        @JvmStatic
        fun checkProguardFiles(
            buildDirectory: DirectoryProperty,
            isDynamicFeature: Boolean,
            consumerProguardFiles: Collection<File>,
            exceptionHandler: Consumer<String>
        ) {
            val defaultFiles = immutableMapBuilder<File, String> {
                for (knownFileName in ProguardFiles.KNOWN_FILE_NAMES) {
                    this.put(
                        ProguardFiles.getDefaultProguardFile(knownFileName, buildDirectory),
                        knownFileName
                    )
                }
            }

            for (consumerProguardFile in consumerProguardFiles) {
                if (defaultFiles.containsKey(consumerProguardFile)) {
                    val errorMessage = if (isDynamicFeature) {
                        ("Default file "
                                + defaultFiles[consumerProguardFile]
                                + " should not be specified in this module."
                                + " It can be specified in the base module instead.")

                    } else {
                        ("Default file "
                                + defaultFiles[consumerProguardFile]
                                + " should not be used as a consumer configuration file.")
                    }

                    exceptionHandler.accept(errorMessage)
                }
            }
        }
    }
}

abstract class ExportConsumerProguardRunnable :
    ProfileAwareWorkAction<ExportConsumerProguardRunnable.Params>() {
    override fun run() {
        FileUtils.deleteRecursivelyIfExists(parameters.outputDir.asFile.get())
        var counter = 0
        parameters.input.forEach { input ->
            if (input.isFile) {
                val libSubDir = getLibSubDir(counter++)
                input.copyTo(File(libSubDir, SdkConstants.FN_PROGUARD_TXT))
            } else if (input.isDirectory) {
                input.listFiles { it -> it.isDirectory }?.forEach {
                    val libSubDir = getLibSubDir(counter++)
                    it.copyRecursively(libSubDir)
                }
            }
        }
    }

    private fun getLibSubDir(count: Int) =
        File(parameters.outputDir.asFile.get(), "lib$count").also { it.mkdirs() }

    abstract class Params : ProfileAwareWorkAction.Parameters() {
        abstract val input: ConfigurableFileCollection
        abstract val outputDir: DirectoryProperty
    }
}
