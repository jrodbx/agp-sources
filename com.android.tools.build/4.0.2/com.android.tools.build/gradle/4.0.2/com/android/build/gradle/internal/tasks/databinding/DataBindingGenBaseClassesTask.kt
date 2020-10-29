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

package com.android.build.gradle.internal.tasks.databinding

import android.databinding.tool.BaseDataBinder
import android.databinding.tool.DataBindingBuilder
import android.databinding.tool.processing.ScopedException
import android.databinding.tool.store.LayoutInfoInput
import android.databinding.tool.util.L
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.options.BooleanOption
import com.android.utils.FileUtils
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import java.io.File
import java.io.Serializable
import java.util.ArrayList
import javax.inject.Inject
import javax.tools.Diagnostic

/**
 * Generates base classes from data binding info files.
 *
 * This class takes the output of XML processor which generates binding info files (binding
 * information in layout files). Then it generates base classes which are the classes accessed
 * by the user code.
 *
 * Generating these classes in gradle instead of annotation processor avoids showing too many
 * errors to the user if the compilation fails before annotation processor output classes are
 * compiled.
 */
@CacheableTask
abstract class DataBindingGenBaseClassesTask : AndroidVariantTask() {
    // where xml info files are
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val layoutInfoDirectory: DirectoryProperty
    // the package name for the module / app
    private lateinit var packageNameSupplier: () -> String
    @get:Input val packageName: String
        get() = packageNameSupplier()
    // list of artifacts from dependencies
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mergedArtifactsFromDependencies: DirectoryProperty
    // list of v1 artifacts from dependencies
    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val v1Artifacts: DirectoryProperty

    // where to keep the log of the task
    @get:OutputDirectory lateinit var logOutFolder: File
        private set
    // where to write the new files
    @get:OutputDirectory abstract val sourceOutFolder: DirectoryProperty
    @get:OutputDirectory abstract val classInfoBundleDir: DirectoryProperty

    @get:Input
    var useAndroidX: Boolean = false
        private set
    @get:Internal
    var encodeErrors: Boolean = false
        private set
    @get:Input
    var enableViewBinding: Boolean = false
        private set

    @TaskAction
    fun writeBaseClasses(inputs: IncrementalTaskInputs) {
        // TODO extend NewIncrementalTask when moved to new API so that we can remove the manual call to recordTaskAction

        recordTaskAction {
            // TODO figure out why worker execution makes the task flake.
            // Some files cannot be accessed even though they show up when directory listing is
            // invoked.
            // b/69652332
            val args = buildInputArgs(inputs)
            CodeGenerator(args, sourceOutFolder.get().asFile, project.logger, encodeErrors).run()
        }
    }

    private fun buildInputArgs(inputs: IncrementalTaskInputs): LayoutInfoInput.Args {
        val outOfDate = ArrayList<File>()
        val removed = ArrayList<File>()
        val layoutInfoDir = layoutInfoDirectory.get().asFile

        // if dependency added/removed a file, it is handled by the LayoutInfoInput class
        if (inputs.isIncremental) {
            inputs.outOfDate { inputFileDetails ->
                if (FileUtils.isFileInDirectory(
                        inputFileDetails.file,
                        layoutInfoDir
                    ) && inputFileDetails.file.name.endsWith(".xml")
                ) {
                    outOfDate.add(inputFileDetails.file)
                }
            }
            inputs.removed { inputFileDetails ->
                if (FileUtils.isFileInDirectory(
                        inputFileDetails.file,
                        layoutInfoDir
                    ) && inputFileDetails.file.name.endsWith(".xml")
                ) {
                    removed.add(inputFileDetails.file)
                }
            }
        } else {
            FileUtils.cleanOutputDir(logOutFolder)
            FileUtils.cleanOutputDir(sourceOutFolder.get().asFile)
        }
        return LayoutInfoInput.Args(
                outOfDate = outOfDate,
                removed = removed,
                infoFolder = layoutInfoDir,
                dependencyClassesFolder = mergedArtifactsFromDependencies.get().asFile,
                logFolder = logOutFolder,
                incremental = inputs.isIncremental,
                packageName = packageName,
                artifactFolder = classInfoBundleDir.get().asFile,
                v1ArtifactsFolder = v1Artifacts.orNull?.asFile,
                useAndroidX = useAndroidX,
                enableViewBinding = enableViewBinding
        )
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<DataBindingGenBaseClassesTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("dataBindingGenBaseClasses")
        override val type: Class<DataBindingGenBaseClassesTask>
            get() = DataBindingGenBaseClassesTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out DataBindingGenBaseClassesTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesDir(
                InternalArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT,
                taskProvider,
                DataBindingGenBaseClassesTask::classInfoBundleDir
            )
            variantScope.artifacts.producesDir(
                InternalArtifactType.DATA_BINDING_BASE_CLASS_SOURCE_OUT,
                taskProvider,
                DataBindingGenBaseClassesTask::sourceOutFolder
            )
        }

        override fun configure(task: DataBindingGenBaseClassesTask) {
            super.configure(task)

            variantScope.artifacts.setTaskInputToFinalProduct(
                DataBindingCompilerArguments.getLayoutInfoArtifactType(variantScope),
                task.layoutInfoDirectory)
            val variantData = variantScope.variantData
            val artifacts = variantScope.artifacts
            task.packageNameSupplier = { variantData.variantDslInfo.originalApplicationId }
            artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.DATA_BINDING_BASE_CLASS_LOGS_DEPENDENCY_ARTIFACTS,
                task.mergedArtifactsFromDependencies
            )
            artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.DATA_BINDING_DEPENDENCY_ARTIFACTS, task.v1Artifacts)
            task.logOutFolder = variantScope.getIncrementalDir(task.name)
            task.useAndroidX = variantScope.globalScope.projectOptions[BooleanOption.USE_ANDROID_X]
            // needed to decide whether data binding should encode errors or not
            task.encodeErrors = variantScope.globalScope
                .projectOptions[BooleanOption.IDE_INVOKED_FROM_IDE]
            task.enableViewBinding = variantScope.globalScope.buildFeatures.viewBinding
        }
    }

    class CodeGenerator @Inject constructor(
        val args: LayoutInfoInput.Args,
        private val sourceOutFolder: File,
        private val logger: Logger,
        private val encodeErrors: Boolean
    ) : Runnable, Serializable {
        override fun run() {
            try {
                initLogger()
                BaseDataBinder(LayoutInfoInput(args))
                    .generateAll(DataBindingBuilder.GradleFileWriter(sourceOutFolder.absolutePath))
            } finally {
                clearLogger()
            }
        }

        private fun initLogger() {
            ScopedException.encodeOutput(encodeErrors)
            L.setClient { kind, message, _ ->
                logger.log(
                    kind.toLevel(),
                    message
                )
            }
        }

        private fun Diagnostic.Kind.toLevel() = when (this) {
            Diagnostic.Kind.ERROR -> LogLevel.ERROR
            Diagnostic.Kind.WARNING -> LogLevel.WARN
            else -> LogLevel.INFO
        }

        private fun clearLogger() {
            L.setClient(null)
        }
    }
}
