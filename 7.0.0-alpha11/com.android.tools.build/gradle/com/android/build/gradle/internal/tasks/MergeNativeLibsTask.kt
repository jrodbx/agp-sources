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
import com.android.build.api.variant.Renderscript
import com.android.build.gradle.internal.BuildToolsExecutableInput
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.initialize
import com.android.build.gradle.internal.packaging.ParsedPackagingOptions.Companion.compileGlob
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_NATIVE_LIBS
import com.android.build.gradle.internal.scope.InternalArtifactType.RENDERSCRIPT_LIB
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.builder.merge.DuplicateRelativeFileException
import com.android.utils.FileUtils
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.ReproducibleFileVisitor
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.util.PatternSet
import java.io.File
import java.io.File.separatorChar
import java.io.Serializable
import java.nio.file.PathMatcher
import java.nio.file.Paths
import java.util.function.Predicate

/**
 * Task to merge native libs from a project and possibly its dependencies
 */
@CacheableTask
abstract class MergeNativeLibsTask : NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:SkipWhenEmpty
    @get:IgnoreEmptyDirectories
    abstract val projectNativeLibs: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:SkipWhenEmpty
    @get:IgnoreEmptyDirectories
    abstract val subProjectNativeLibs: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:SkipWhenEmpty
    @get:IgnoreEmptyDirectories
    abstract val externalLibNativeLibs: ConfigurableFileCollection

    @get:Classpath
    @get:Optional
    @get:SkipWhenEmpty
    @get:IgnoreEmptyDirectories
    abstract val profilerNativeLibs: DirectoryProperty

    @get:Input
    abstract val excludes: SetProperty<String>

    @get:Input
    abstract val pickFirsts: SetProperty<String>

    @get:Nested
    abstract val buildTools: BuildToolsExecutableInput

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    // We use unfilteredProjectNativeLibs for the task action but projectNativeLibs as the task
    // input. We use unfilteredProjectNativeLibs for the task action because we need the relative
    // paths of the files, but that information is lost in projectNativeLibs. We use
    // projectNativeLibs as the task input to avoid snapshotting extra files. This is a workaround
    // for the lack of gradle custom snapshotting: https://github.com/gradle/gradle/issues/8503.
    @get:Internal
    abstract val unfilteredProjectNativeLibs: ConfigurableFileCollection

    override fun doTaskAction() {
        val inputFiles = mutableListOf<InputFile>()

        val fileVisitor = object : ReproducibleFileVisitor {
            override fun isReproducibleFileOrder() = true
            override fun visitFile(details: FileVisitDetails) {
                if (predicate.test(details.name)) {
                    inputFiles.add(
                        InputFile(details.file, "lib/${details.relativePath.pathString}")
                    )
                }
            }
            override fun visitDir(fileVisitDetails: FileVisitDetails) {
                // Do nothing.
            }
        }

        unfilteredProjectNativeLibs.asFileTree.visit(fileVisitor)
        subProjectNativeLibs.asFileTree.visit(fileVisitor)
        externalLibNativeLibs.asFileTree.visit(fileVisitor)
        if (profilerNativeLibs.isPresent) {
            profilerNativeLibs.asFileTree.visit(fileVisitor)
        }

        workerExecutor.noIsolation().submit(MergeNativeLibsTaskWorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.inputFiles.set(inputFiles.toList())
            it.projectNativeLibs.set(projectNativeLibs.files)
            it.outputDirectory.set(outputDir)
            it.excludes.set(excludes)
            it.pickFirsts.set(pickFirsts)
        }
    }

    abstract class MergeNativeLibsTaskWorkAction :
        ProfileAwareWorkAction<MergeNativeLibsTaskWorkAction.Parameters>() {

        override fun run() {

            // A map of pickFirst pattern strings to their compiled globs.
            val pickFirstsPathMatchers: Map<String, PathMatcher> =
                parameters.pickFirsts.get().associateWith { compileGlob(it) }
            // Keep track of which pickFirst patterns have already been "picked"
            val usedPickFirsts: MutableSet<String> = mutableSetOf()

            val excludesPathMatchers: List<PathMatcher> =
                parameters.excludes.get().map { compileGlob(it) }

            // Keep track of the files matching each relative path so we can create a useful error
            // message if necessary
            val usedRelativePaths =
                mutableMapOf<String, MutableList<File>>().withDefault { mutableListOf() }

            val outputDir = parameters.outputDirectory.get().asFile

            for (inputFile in parameters.inputFiles.get()) {
                val systemDependentPath =
                    Paths.get("$separatorChar${inputFile.relativePath.replace('/', separatorChar)}")
                val pickFirstMatches =
                    pickFirstsPathMatchers.filter { it.value.matches(systemDependentPath) }.keys
                if (pickFirstMatches.isNotEmpty()) {
                    // if the path matches a pickFirst pattern, we copy the file only if the path
                    // matches a pickFirst pattern which hasn't been "picked"
                    if (pickFirstMatches.any { !usedPickFirsts.contains(it) }) {
                        usedPickFirsts.addAll(pickFirstMatches)
                        copyInputFileToOutput(inputFile, outputDir, usedRelativePaths)
                    }
                } else if (excludesPathMatchers.none { it.matches(systemDependentPath) }) {
                    copyInputFileToOutput(inputFile, outputDir, usedRelativePaths)
                }
            }

            // Check usedRelativePaths and throw an exception or log warning(s) if necessary
            for (entry in usedRelativePaths) {
                if (entry.value.size > 1 && !usedPickFirsts.contains(entry.key)) {
                    val projectFiles =
                        entry.value.filter { parameters.projectNativeLibs.get().contains(it) }
                    if (projectFiles.size == 1) {
                        // TODO(b/141758241) enforce the use of pickFirst or pickFrom in this case
                        //  and throw an error instead of logging a warning.
                        val logger =
                            LoggerWrapper(Logging.getLogger(MergeNativeLibsTask::class.java))
                        val message =
                            StringBuilder().apply {
                                append(entry.value.size)
                                append(" files found for path '")
                                append(entry.key)
                                append(
                                    "'. This version of the Android Gradle Plugin chooses the file "
                                            + "from the app or dynamic-feature module, but this "
                                            + "can cause unexpected behavior or errors at runtime. "
                                            + "Future versions of the Android Gradle Plugin may "
                                            + "throw an error in this case.\n"
                                )
                                append("Inputs:\n")
                                for (file in entry.value) {
                                    append(" - ").append(file).append("\n")
                                }
                            }.toString()
                        logger.warning(message)
                    } else {
                        throw DuplicateRelativeFileException(
                            entry.key,
                            entry.value.size,
                            entry.value.map { it.absolutePath },
                            null
                        )
                    }
                }
            }
        }

        /**
         * Copy inputFile.file to the outputDirectory and update usedRelativePaths.
         */
        private fun copyInputFileToOutput(
            inputFile: InputFile,
            outputDir: File,
            usedRelativePaths: MutableMap<String, MutableList<File>>
        ) {
            // Update usedRelativePaths
            usedRelativePaths[inputFile.relativePath] =
                usedRelativePaths.getValue(inputFile.relativePath).also { it.add(inputFile.file) }

            val outputFile =
                FileUtils.join(outputDir, inputFile.relativePath.replace('/', separatorChar))
            if (!outputFile.exists()) {
                inputFile.file.copyTo(outputFile, overwrite = false)
            }
        }

        abstract class Parameters: ProfileAwareWorkAction.Parameters() {
            abstract val inputFiles: ListProperty<InputFile>
            // TODO(b/141758241) remove projectNativeLibs after we stop supporting different native
            //  libraries with the same name. The projectNativeLibs files are included in inputFiles
            //  above, but we include them again in the projectNativeLibs property as a means of
            //  tracking which files take precedence over others.
            abstract val projectNativeLibs: SetProperty<File>
            abstract val outputDirectory: DirectoryProperty
            abstract val excludes: SetProperty<String>
            abstract val pickFirsts: SetProperty<String>
        }
    }

    class CreationAction(creationConfig: ConsumableCreationConfig) :
            VariantTaskCreationAction<MergeNativeLibsTask, ConsumableCreationConfig>(creationConfig) {

        override val name: String
            get() = computeTaskName("merge", "NativeLibs")

        override val type: Class<MergeNativeLibsTask>
            get() = MergeNativeLibsTask::class.java

        override fun handleProvider(
                taskProvider: TaskProvider<MergeNativeLibsTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                MergeNativeLibsTask::outputDir
            ).withName("out").on(MERGED_NATIVE_LIBS)
        }

        override fun configure(
            task: MergeNativeLibsTask
        ) {
            super.configure(task)

            task.excludes.setDisallowChanges(creationConfig.packaging.jniLibs.excludes)
            task.pickFirsts.setDisallowChanges(creationConfig.packaging.jniLibs.pickFirsts)

            task.buildTools.initialize(creationConfig)

            task.projectNativeLibs
                .from(getProjectNativeLibs(
                    creationConfig,
                    task.buildTools,
                    creationConfig.renderscript
                ).asFileTree.matching(patternSet))
                .disallowChanges()

            if (creationConfig is ApkCreationConfig) {
                task.externalLibNativeLibs.from(getExternalNativeLibs(creationConfig))
                    .disallowChanges()
                task.subProjectNativeLibs.from(getSubProjectNativeLibs(creationConfig))
                    .disallowChanges()
                if (creationConfig.shouldPackageProfilerDependencies) {
                    task.profilerNativeLibs.setDisallowChanges(
                            creationConfig.artifacts.get(InternalArtifactType.PROFILERS_NATIVE_LIBS)
                    )
                }
            }

            task.unfilteredProjectNativeLibs
                .from(getProjectNativeLibs(
                    creationConfig,
                    task.buildTools,
                    creationConfig.renderscript)
                ).disallowChanges()
        }
    }

    companion object {

        private const val includedFileSuffix = SdkConstants.DOT_NATIVE_LIBS
        private val includedFileNames = listOf(SdkConstants.FN_GDBSERVER, SdkConstants.FN_GDB_SETUP)

        // predicate logic must match patternSet logic below
        val predicate = Predicate<String> { fileName ->
            fileName.endsWith(includedFileSuffix, ignoreCase = true)
                    || includedFileNames.any { it.equals(fileName, ignoreCase = true) }
        }

        // patternSet logic must match predicate logic above
        val patternSet: PatternSet
            get() {
                val patternSet = PatternSet().include("**/*$includedFileSuffix")
                includedFileNames.forEach { patternSet.include("**/$it") }
                return patternSet
            }
    }

    data class InputFile(val file: File, val relativePath: String) : Serializable
}

fun getProjectNativeLibs(
    creationConfig: ConsumableCreationConfig,
    buildTools: BuildToolsExecutableInput,
    renderscript: Renderscript?
): FileCollection {
    val artifacts = creationConfig.artifacts
    val taskContainer = creationConfig.taskContainer
    val nativeLibs = creationConfig.services.fileCollection()

    // add merged project native libs
    nativeLibs.from(
        artifacts.get(InternalArtifactType.MERGED_JNI_LIBS)
    )

    // add content of the local external native build if there is one
    taskContainer.cxxConfigurationModel?.variant?.soFolder?.let { objFolder ->
        nativeLibs.from(
            creationConfig.services.fileCollection(objFolder)
                    .builtBy(taskContainer.externalNativeBuildTask?.name)
        )
    }

    // add renderscript compilation output if support mode is enabled.
    if (renderscript != null) {
        nativeLibs.from(renderscript.renderscriptSupportModeEnabled.map {
            if (it) {
                val rsFileCollection: ConfigurableFileCollection =
                    creationConfig.services.fileCollection(artifacts.get(RENDERSCRIPT_LIB))
                rsFileCollection.from(buildTools::supportNativeLibFolderProvider)
                rsFileCollection
            } else {
                creationConfig.services.fileCollection()
            }
        })

        nativeLibs.from(renderscript.renderscriptSupportModeBlasEnabled.map {
            if (it) {
                buildTools.supportBlasLibFolderProvider().map { rsBlasLib ->
                    if (!rsBlasLib.isDirectory) {
                        throw GradleException(
                            "Renderscript BLAS support mode is not supported in BuildTools $rsBlasLib"
                        )
                    }
                    rsBlasLib
                }
            } else {
                creationConfig.services.fileCollection()
            }
        })
    }
    return nativeLibs
}

fun getSubProjectNativeLibs(creationConfig: VariantCreationConfig): FileCollection =
    creationConfig.variantDependencies.getArtifactFileCollection(
        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
        AndroidArtifacts.ArtifactScope.PROJECT,
        AndroidArtifacts.ArtifactType.JNI
    ).filter { file ->
        // Filter out directories without any file descendants so @SkipWhenEmpty works as desired.
        file.walk().any { it.isFile }
    }

fun getExternalNativeLibs(creationConfig: VariantCreationConfig): FileCollection =
    creationConfig.variantDependencies.getArtifactFileCollection(
        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
        AndroidArtifacts.ArtifactScope.EXTERNAL,
        AndroidArtifacts.ArtifactType.JNI
    ).filter { file ->
        // Filter out directories without any file descendants so @SkipWhenEmpty works as desired.
        file.walk().any { it.isFile }
    }
