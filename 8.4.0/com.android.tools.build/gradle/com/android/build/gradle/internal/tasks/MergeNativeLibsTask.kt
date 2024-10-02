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
import com.android.build.api.artifact.SingleArtifact.MERGED_NATIVE_LIBS
import com.android.build.api.variant.Renderscript
import com.android.build.api.variant.TestedComponentPackaging
import com.android.build.gradle.internal.BuildToolsExecutableInput
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.KmpComponentCreationConfig
import com.android.build.gradle.internal.component.NestedComponentCreationConfig
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.gradle.generator.externalNativeBuildIsActive
import com.android.build.gradle.internal.cxx.io.removeDuplicateFiles
import com.android.build.gradle.internal.initialize
import com.android.build.gradle.internal.packaging.ParsedPackagingOptions.Companion.compileGlob
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_TEST_ONLY_NATIVE_LIBS
import com.android.build.gradle.internal.scope.InternalArtifactType.RENDERSCRIPT_LIB
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.merge.DuplicateRelativeFileException
import com.android.utils.FileUtils
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.RelativePath
import org.gradle.api.file.ReproducibleFileVisitor
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.SetProperty
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
import org.gradle.work.DisableCachingByDefault

/**
 * Task to merge native libs from a project and possibly its dependencies
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.NATIVE, secondaryTaskCategories = [TaskCategory.MERGING])
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

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:SkipWhenEmpty
    @get:IgnoreEmptyDirectories
    abstract val testOnlyNativeLibs: ConfigurableFileCollection

    @get:Input
    abstract val excludes: SetProperty<String>

    @get:Input
    abstract val pickFirsts: SetProperty<String>

    @get:Input
    abstract val testOnly: SetProperty<String>

    @get:Nested
    abstract val buildTools: BuildToolsExecutableInput

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    // testOnlyDir is only set for main components. It will contain the native libraries that should
    // be packaged with the test components and excluded from the main components.
    @get:OutputDirectory
    @get:Optional
    abstract val testOnlyDir: DirectoryProperty

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
                        InputFile(details.file,
                            "lib/${toAbiRootedPath(details.file, details.relativePath)}")
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
        testOnlyNativeLibs.asFileTree.visit(fileVisitor)
        if (profilerNativeLibs.isPresent) {
            profilerNativeLibs.asFileTree.visit(fileVisitor)
        }

        workerExecutor.noIsolation().submit(MergeNativeLibsTaskWorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.inputFiles.set(inputFiles.toList())
            it.projectNativeLibs.set(projectNativeLibs.files)
            it.outputDirectory.set(outputDir)
            it.testOnlyDir.set(testOnlyDir)
            it.excludes.set(excludes)
            it.pickFirsts.set(pickFirsts)
            it.testOnly.set(testOnly)
        }
    }

    abstract class MergeNativeLibsTaskWorkAction :
        ProfileAwareWorkAction<MergeNativeLibsTaskWorkAction.Parameters>() {

        override fun run() {

            // A map of pickFirst pattern strings to their compiled globs.
            val pickFirstsPathMatchers: Map<String, PathMatcher> =
                parameters.pickFirsts.get().associateWith { compileGlob(it) }

            val excludesPathMatchers: List<PathMatcher> =
                parameters.excludes.get().map { compileGlob(it) }

            val testOnlyPathMatchers: List<PathMatcher> =
                parameters.testOnly.get().map { compileGlob(it) }

            // Keep track of the files matching each relative path so we can create a useful error
            // message if necessary
            val usedRelativePaths =
                mutableMapOf<String, MutableList<File>>().withDefault { mutableListOf() }

            val outputDir = parameters.outputDirectory.get().asFile
            val testOnlyDir = parameters.testOnlyDir.orNull?.asFile

            for (inputFile in parameters.inputFiles.get()) {
                val systemDependentPath =
                    Paths.get("$separatorChar${inputFile.relativePath.replace('/', separatorChar)}")
                val pickFirstMatches =
                    pickFirstsPathMatchers.filter { it.value.matches(systemDependentPath) }.keys
                val destinationDir =
                    if (testOnlyDir != null
                        && testOnlyPathMatchers.any { it.matches(systemDependentPath)}) {
                        testOnlyDir
                    } else {
                        outputDir
                    }
                if (pickFirstMatches.isNotEmpty()) {
                    // if the path matches a pickFirst pattern, we copy the file only if the
                    // relative path hasn't already been used.
                    if (!usedRelativePaths.containsKey(inputFile.relativePath)) {
                        copyInputFileToOutput(inputFile, destinationDir, usedRelativePaths)
                    }
                } else if (excludesPathMatchers.none { it.matches(systemDependentPath) }) {
                    copyInputFileToOutput(inputFile, destinationDir, usedRelativePaths)
                }
            }

            // Check usedRelativePaths and throw an exception or log warning(s) if necessary
            // Files that have the same content are considered to be the same (and no error or
            // warning is emitted).
            val deduplicatedUsedRelativePaths = usedRelativePaths
                .map { (k, v) -> k to removeDuplicateFiles(v) }
                .toMap()

            for (entry in deduplicatedUsedRelativePaths) {
                if (entry.value.size > 1) {
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
            abstract val testOnlyDir: DirectoryProperty
            abstract val excludes: SetProperty<String>
            abstract val pickFirsts: SetProperty<String>
            abstract val testOnly: SetProperty<String>
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
            if (creationConfig.writesTestOnlyDir()) {
                creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    MergeNativeLibsTask::testOnlyDir
                ).withName("out").on(MERGED_TEST_ONLY_NATIVE_LIBS)
            }
        }

        override fun configure(
            task: MergeNativeLibsTask
        ) {
            super.configure(task)

            val packaging = creationConfig.packaging
            task.excludes.setDisallowChanges(packaging.jniLibs.excludes)
            task.pickFirsts.setDisallowChanges(packaging.jniLibs.pickFirsts)
            if (creationConfig.writesTestOnlyDir() && packaging is TestedComponentPackaging) {
                task.testOnly.setDisallowChanges(packaging.jniLibs.testOnly)
            } else {
                task.testOnly.setDisallowChanges(listOf())
            }

            task.buildTools.initialize(creationConfig)

            task.projectNativeLibs
                .from(getProjectNativeLibs(
                    creationConfig,
                    task.buildTools,
                    creationConfig.renderscriptCreationConfig?.renderscript
                ).asFileTree.matching(patternSet))
                .disallowChanges()

            if (creationConfig is ApkCreationConfig) {
                task.externalLibNativeLibs.from(getExternalNativeLibs(creationConfig))
                task.subProjectNativeLibs.from(getSubProjectNativeLibs(creationConfig))
                if (creationConfig.shouldPackageProfilerDependencies) {
                    task.profilerNativeLibs.set(
                            creationConfig.artifacts.get(InternalArtifactType.PROFILERS_NATIVE_LIBS)
                    )
                }
            }
            task.externalLibNativeLibs.disallowChanges()
            task.subProjectNativeLibs.disallowChanges()
            task.profilerNativeLibs.disallowChanges()

            if (creationConfig.componentType.isForTesting && creationConfig.componentType.isApk) {
                task.testOnlyNativeLibs.from(getTestOnlyNativeLibs(creationConfig))
            }
            task.testOnlyNativeLibs.disallowChanges()

            task.unfilteredProjectNativeLibs
                .from(getProjectNativeLibs(
                    creationConfig,
                    task.buildTools,
                    creationConfig.renderscriptCreationConfig?.renderscript
                )).disallowChanges()
        }
    }

    companion object {

        private const val includedFileSuffix = SdkConstants.DOT_NATIVE_LIBS
        private val includedFileNames = listOf(SdkConstants.FN_GDBSERVER, SdkConstants.FN_GDB_SETUP)
        private val abiTags = Abi.values().map { it.tag }.toSet()

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

        /**
         * [file] is one of these three kinds:
         *    (1) /path/to/{x86/lib.so}
         *    (2) /path/to/{lib/x86/lib.so}
         *    (3) /path/to/x86/{lib.so}
         * Where the value in {braces} is the [relativePath] from the file visitor.
         * (1) and (2) are from tasks that process all ABIs in a single task.
         * (3) is from tasks the where each task processes one ABI.
         *
         * This function distinguishes the three cases and returns a relative path that always
         * starts with an ABI. So, for example, all the cases above would return:
         *
         *    x86/lib.so
         *
         */
        private fun toAbiRootedPath(file : File, relativePath: RelativePath) : String {
            return when {
                // Case (1) the relative path starts with an ABI name. Return it directly.
                abiTags.any { it == relativePath.segments[0] } -> {
                    relativePath.pathString
                }

                // Case (2) the relative path starts with "lib" followed by an ABI name.
                relativePath.segments.size > 1
                        && abiTags.any { it == relativePath.segments[1] } -> {
                    relativePath.segments.drop(1).joinToString("$separatorChar")
                }

                // Case (3) the relative path does not start with an ABI name. Prepend the
                // ABI name from the end of [file] after [relativePath] has been removed.
                else -> {
                    var root = file
                    repeat(relativePath.segments.size) { root = root.parentFile }
                    val abi = root.name
                    if (!abiTags.any { it == abi }) {
                        error("$abi extracted from path $file is not an ABI")
                    }
                    abi + separatorChar + relativePath.pathString
                }
            }
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
    val nativeLibs = creationConfig.services.fileCollection()

    // add merged project native libs
    nativeLibs.from(
        artifacts.get(InternalArtifactType.MERGED_JNI_LIBS)
    )

    // add content of the local external native build if there is one
    if (externalNativeBuildIsActive(creationConfig)) {
        nativeLibs.from(
            artifacts.getAll(InternalMultipleArtifactType.EXTERNAL_NATIVE_BUILD_LIBS)
        )
    }

    // add renderscript compilation output if support mode is enabled.
    if (renderscript != null) {
        nativeLibs.from(renderscript.supportModeEnabled.map {
            if (it) {
                val rsFileCollection: ConfigurableFileCollection =
                    creationConfig.services.fileCollection(artifacts.get(RENDERSCRIPT_LIB))
                rsFileCollection.from(buildTools::supportNativeLibFolderProvider)
                rsFileCollection
            } else {
                creationConfig.services.fileCollection()
            }
        })

        nativeLibs.from(renderscript.supportModeBlasEnabled.map {
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

fun getSubProjectNativeLibs(creationConfig: ComponentCreationConfig): FileCollection =
    creationConfig.variantDependencies.getArtifactFileCollection(
        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
        AndroidArtifacts.ArtifactScope.PROJECT,
        AndroidArtifacts.ArtifactType.JNI
    ).filter { file ->
        // Filter out directories without any file descendants so @SkipWhenEmpty works as desired.
        file.walk().any { it.isFile }
    }

fun getExternalNativeLibs(creationConfig: ComponentCreationConfig): FileCollection =
    creationConfig.variantDependencies.getArtifactFileCollection(
        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
        AndroidArtifacts.ArtifactScope.EXTERNAL,
        AndroidArtifacts.ArtifactType.JNI
    ).filter { file ->
        // Filter out directories without any file descendants so @SkipWhenEmpty works as desired.
        file.walk().any { it.isFile }
    }

fun getTestOnlyNativeLibs(creationConfig: ComponentCreationConfig): FileCollection {
    val nativeLibs = creationConfig.services.fileCollection()
    if (creationConfig.componentType.isSeparateTestProject) {
        nativeLibs.from(
            creationConfig.variantDependencies.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                AndroidArtifacts.ArtifactScope.PROJECT,
                AndroidArtifacts.ArtifactType.MERGED_TEST_ONLY_NATIVE_LIBS
            )
        )
    }
    if (creationConfig is NestedComponentCreationConfig
        && creationConfig.mainVariant.writesTestOnlyDir()
        && creationConfig.componentType.isForTesting
        && creationConfig.componentType.isApk) {
        nativeLibs.from(
            creationConfig.mainVariant.artifacts.get(MERGED_TEST_ONLY_NATIVE_LIBS)
        )
    }
    // Filter out directories without any file descendants so @SkipWhenEmpty works as desired.
    return nativeLibs.filter { file -> file.walk().any { it.isFile } }
}

/**
 * Whether a creation config will create [MergeNativeLibsTask.testOnlyDir]
 *
 * [KmpComponentCreationConfig]s are excluded because they don't merge native libs
 */
private fun ConsumableCreationConfig.writesTestOnlyDir() =
    this.packaging is TestedComponentPackaging && this !is KmpComponentCreationConfig
