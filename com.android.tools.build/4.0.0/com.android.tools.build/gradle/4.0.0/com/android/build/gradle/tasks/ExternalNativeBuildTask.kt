/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.attribution.generateChromeTrace
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValueMini
import com.android.build.gradle.internal.cxx.json.NativeLibraryValueMini
import com.android.build.gradle.internal.cxx.logging.IssueReporterLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.model.ninjaLogFile
import com.android.build.gradle.internal.cxx.process.createProcessOutputJunction
import com.android.build.gradle.internal.cxx.settings.BuildSettingsConfiguration
import com.android.build.gradle.internal.cxx.settings.getEnvironmentVariableMap
import com.android.build.gradle.internal.process.GradleProcessExecutor
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.JNI
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.UnsafeOutputsTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.errors.DefaultIssueReporter
import com.android.ide.common.process.BuildCommandException
import com.android.ide.common.process.ProcessInfoBuilder
import com.android.utils.FileUtils
import com.android.utils.tokenizeCommandLineToEscaped
import com.google.common.base.Joiner
import com.google.common.base.Preconditions.checkElementIndex
import com.google.common.base.Preconditions.checkNotNull
import com.google.common.base.Preconditions.checkState
import com.google.common.base.Strings
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import com.google.common.io.Files
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskProvider
import org.gradle.process.ExecOperations
import java.io.File
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import kotlin.streams.toList

/**
 * Task that takes set of JSON files of type NativeBuildConfigValueMini and does build steps with
 * them.
 *
 *
 * It declares no inputs or outputs, as it's supposed to always run when invoked. Incrementality
 * is left to the underlying build system.
 */
abstract class ExternalNativeBuildTask : UnsafeOutputsTask() {

    private lateinit var generator: Provider<ExternalNativeJsonGenerator>

    /**
     * Get native build config minis. Also gather stats if they haven't already been gathered for
     * this variant
     *
     * @return the mini configs
     */
    private// Gather stats only if they haven't been gathered during model build
    val nativeBuildConfigValueMinis: List<NativeBuildConfigValueMini>
        @Throws(IOException::class)
        get() = if (stats.nativeBuildConfigCount == 0) {
            AndroidBuildGradleJsons.getNativeBuildMiniConfigs(
                generator.get().nativeBuildConfigurationsJsons, stats
            )
        } else AndroidBuildGradleJsons.getNativeBuildMiniConfigs(
            generator.get().nativeBuildConfigurationsJsons, null/* kotlin.Unit */
        )

    // Exposed in Variants API
    @get:Internal("Temporary to suppress warnings (bug 135900510), may need more investigation")
    val objFolder: File
        get() = File(generator.get().objFolder)

    // Exposed in Variants API
    @get:Internal("Temporary to suppress warnings (bug 135900510), may need more investigation")
    val soFolder: File
        get() = File(generator.get().soFolder)

    @get:Inject
    abstract val execOperations: ExecOperations

    private val stlSharedObjectFiles: Map<Abi, File>
        get() = generator.get().stlSharedObjectFiles

    private val stats: GradleBuildVariant.Builder
        get() = generator.get().stats

    /** Represents a single build step that, when executed, builds one or more libraries.  */
    private class BuildStep(
        val buildCommand: String,
        val libraries: List<NativeLibraryValueMini>,
        val outputFolder: File
    )

    override fun doTaskAction() {
        IssueReporterLoggingEnvironment(DefaultIssueReporter(LoggerWrapper(logger))).use { buildImpl() }
    }

    @Throws(BuildCommandException::class, IOException::class)
    private fun buildImpl() {
        infoln("starting build")
        checkNotNull(variantName)
        infoln("reading expected JSONs")
        val miniConfigs = nativeBuildConfigValueMinis
        infoln("done reading expected JSONs")

        val targets = generator.get().variant.buildTargetSet

        if (targets.isEmpty()) {
            infoln("executing build commands for targets that produce .so files or executables")
        } else {
            verifyTargetsExist(miniConfigs)
        }

        val buildSteps = Lists.newArrayList<BuildStep>()

        for (miniConfigIndex in miniConfigs.indices) {
            val config = miniConfigs[miniConfigIndex]
            infoln("evaluate miniconfig")
            if (config.libraries.isEmpty()) {
                infoln("no libraries")
                continue
            }

            val librariesToBuild = findLibrariesToBuild(config)
            if (librariesToBuild.isEmpty()) {
                infoln("no libraries to build")
                continue
            }

            if (!Strings.isNullOrEmpty(config.buildTargetsCommand)) {
                // Build all libraries together in one step, using the names of the artifacts.
                val artifactNames = librariesToBuild
                    .stream()
                    .filter { library -> library.artifactName != null }
                    .map<String> { library -> library.artifactName }
                    .sorted()
                    .distinct()
                    .toList()
                val buildTargetsCommand =
                    substituteBuildTargetsCommand(config.buildTargetsCommand!!, artifactNames)
                buildSteps.add(
                    BuildStep(
                        buildTargetsCommand,
                        librariesToBuild,
                        generator
                            .get()
                            .nativeBuildConfigurationsJsons[miniConfigIndex]
                            .parentFile
                    )
                )
                infoln("about to build targets " + artifactNames.joinToString(", "))
            } else {
                // Build each library separately using multiple steps.
                for (libraryValue in librariesToBuild) {
                    buildSteps.add(
                        BuildStep(
                            libraryValue.buildCommand!!,
                            listOf(libraryValue),
                            generator
                                .get()
                                .nativeBuildConfigurationsJsons[miniConfigIndex]
                                .parentFile
                        )
                    )
                    infoln("about to build ${libraryValue.buildCommand!!}")
                }
            }
        }

        executeProcessBatch(buildSteps)

        infoln("check expected build outputs")
        for (config in miniConfigs) {
            for (library in config.libraries.values) {
                checkState(!Strings.isNullOrEmpty(library.artifactName))
                if (targets.isNotEmpty() && !targets.contains(library.artifactName)) {
                    continue
                }
                if (buildSteps.stream().noneMatch { step -> step.libraries.contains(library) }) {
                    // Only need to check existence of output files we expect to create
                    continue
                }
                val output = library.output!!
                if (!output.exists()) {
                    throw GradleException(
                        "Expected output file at $output for target ${library.artifactName} but there was none")
                }
                if (library.abi == null) {
                    throw GradleException("Expected NativeLibraryValue to have non-null abi")
                }

                // If the build chose to write the library output somewhere besides objFolder
                // then copy to objFolder (reference b.android.com/256515)
                //
                // Since there is now a .so file outside of the standard build/ folder we have to
                // consider clean. Here's how the two files are covered.
                // (1) Gradle plugin deletes the build/ folder. This covers the destination of the
                //     copy.
                // (2) ExternalNativeCleanTask calls the individual clean targets for everything
                //     that was built. This should cover the source of the copy but it is up to the
                //     CMakeLists.txt or Android.mk author to ensure this.
                val abi = Abi.getByName(library.abi!!) ?: throw RuntimeException(
                    "Unknown ABI seen $(ibraryValue.abi}"
                )
                val expectedOutputFile = FileUtils.join(
                    generator.get().variant.objFolder,
                    abi.tag,
                    output.name
                )
                if (!FileUtils.isSameFile(output, expectedOutputFile)) {
                    infoln("external build set its own library output location for " +
                            "'${output.name}', copy to expected location")

                    if (expectedOutputFile.parentFile.mkdirs()) {
                        infoln("created folder ${expectedOutputFile.parentFile}")
                    }
                    infoln("copy file ${library.output} to $expectedOutputFile")
                    Files.copy(output, expectedOutputFile)
                }

                for (runtimeFile in library.runtimeFiles) {
                    Files.copy(
                        runtimeFile,
                        FileUtils.join(generator.get().variant.objFolder, abi.tag, runtimeFile.name)
                    )
                }
            }
        }

        if (stlSharedObjectFiles.isNotEmpty()) {
            infoln("copy STL shared object files")
            for (abi in stlSharedObjectFiles.keys) {
                val stlSharedObjectFile = stlSharedObjectFiles.getValue(abi)
                val objAbi = FileUtils.join(
                    generator.get().variant.objFolder,
                    abi.tag,
                    stlSharedObjectFile.name
                )
                if (!objAbi.parentFile.isDirectory) {
                    // A build failure can leave the obj/abi folder missing. Just note that case
                    // and continue without copying STL.
                    infoln("didn't copy STL file to ${objAbi.parentFile} because that folder wasn't created by the build ")
                } else {
                    infoln("copy file $stlSharedObjectFile to $objAbi")
                    Files.copy(stlSharedObjectFile, objAbi)
                }
            }
        }

        infoln("build complete")
    }

    /**
     * Verifies that all targets provided by the user will be built. Throws GradleException if it
     * detects an unexpected target.
     */
    private fun verifyTargetsExist(miniConfigs: List<NativeBuildConfigValueMini>) {
        // Check the resulting JSON targets against the targets specified in ndkBuild.targets or
        // cmake.targets. If a target name specified by the user isn't present then provide an
        // error to the user that lists the valid target names.
        val targets = generator.get().variant.buildTargetSet
        infoln("executing build commands for targets: '${Joiner.on(", ").join(targets)}'")

        // Search libraries for matching targets.
        val matchingTargets = Sets.newHashSet<String>()
        val unmatchedTargets = Sets.newHashSet<String>()
        for (config in miniConfigs) {
            for (libraryValue in config.libraries.values) {
                if (targets.contains(libraryValue.artifactName)) {
                    matchingTargets.add(libraryValue.artifactName)
                } else {
                    unmatchedTargets.add(libraryValue.artifactName)
                }
            }
        }

        // All targets must be found or it's a build error
        for (target in targets) {
            if (!matchingTargets.contains(target)) {
                throw GradleException("Unexpected native build target $target. " +
                        "Valid values are: ${Joiner.on(", ").join(unmatchedTargets)}")
            }
        }
    }

    /**
     * @return List of libraries defined in the input config file, filtered based on the targets
     * field optionally provided by the user, and other criteria.
     */
    private fun findLibrariesToBuild(
        config: NativeBuildConfigValueMini
    ): List<NativeLibraryValueMini> {
        val librariesToBuild = Lists.newArrayList<NativeLibraryValueMini>()
        val targets = generator.get().variant.buildTargetSet
        loop@for (libraryValue in config.libraries.values) {
            infoln("evaluate library ${libraryValue.artifactName} (${libraryValue.abi})")
            if (targets.isNotEmpty() && !targets.contains(libraryValue.artifactName)) {
                infoln("not building target ${libraryValue.artifactName!!} because it isn't in targets set")
                continue
            }

            if (Strings.isNullOrEmpty(config.buildTargetsCommand) && Strings.isNullOrEmpty(
                    libraryValue.buildCommand
                )
            ) {
                // This can happen when there's an externally referenced library.
                infoln(
                    "not building target ${libraryValue.artifactName!!} because there was no " +
                            "buildCommand for the target, nor a buildTargetsCommand for the config")
                continue
            }

            if (targets.isEmpty()) {
                if (libraryValue.output == null) {
                    infoln(
                        "not building target ${libraryValue.artifactName!!} because no targets " +
                                "are specified and library build output file is null")
                    continue
                }

                when (Files.getFileExtension(libraryValue.output!!.name)) {
                    "so" -> infoln("building target library ${libraryValue.artifactName!!} because no targets are specified.")
                    "" -> infoln("building target executable ${libraryValue.artifactName!!} because no targets are specified.")
                    else -> {
                        infoln("not building target ${libraryValue.artifactName!!} because the type cannot be determined.")
                        continue@loop
                    }
                }
            }

            librariesToBuild.add(libraryValue)
        }

        return librariesToBuild
    }

    /**
     * Given a list of build steps, execute each. If there is a failure, processing is stopped at
     * that point.
     */
    @Throws(BuildCommandException::class, IOException::class)
    private fun executeProcessBatch(buildSteps: List<BuildStep>) {
        val logger = logger
        val processExecutor = GradleProcessExecutor(execOperations::exec)

        for (buildStep in buildSteps) {
            val tokens = buildStep.buildCommand.tokenizeCommandLineToEscaped()
            val processBuilder = ProcessInfoBuilder()
            processBuilder.setExecutable(tokens[0])
            for (i in 1 until tokens.size) {
                processBuilder.addArgs(tokens[i])
            }
            infoln("$processBuilder")

            val logFileSuffix: String
            val abiName = buildStep.libraries[0].abi
            if (buildStep.libraries.size > 1) {
                logFileSuffix = "targets"
                val targetNames = buildStep
                    .libraries
                    .stream()
                    .map { library -> library.artifactName + "_" + library.abi }
                    .toList()
                logger.lifecycle(
                    String.format("Build multiple targets ${targetNames.joinToString(" ")}"))
            } else {
                checkElementIndex(0, buildStep.libraries.size)
                logFileSuffix = buildStep.libraries[0].artifactName + "_" + abiName
                logger.lifecycle(
                    String.format("Build $logFileSuffix"))
            }

            generator.get().abis
                .firstOrNull { abiModel -> abiModel.abi.tag == abiName }
                ?.let {
                    applyBuildSettings(it.buildSettings, processBuilder)
                }

            val generateChromeTraces =
                generator.get().takeIf { it.nativeBuildSystem == NativeBuildSystem.CMAKE }
                    ?.abis
                    ?.firstOrNull { it.abi.tag == abiName }
                    ?.let { abiModel ->
                        abiModel.variant.module.project.chromeTraceJsonFolder?.let { traceFolder ->
                            val ninjaFile = abiModel.ninjaLogFile
                            val lineToSkip =
                                if (ninjaFile.canRead()) ninjaFile.readLines().size else 0
                            val buildStartTime = Clock.systemUTC().millis()
                            val m = fun() {
                                generateChromeTrace(
                                    abiModel,
                                    ninjaFile,
                                    lineToSkip,
                                    buildStartTime,
                                    traceFolder
                                )
                            }
                            m
                        }
                    }

            createProcessOutputJunction(
                buildStep.outputFolder,
                "android_gradle_build_$logFileSuffix",
                processBuilder,
                logger,
                processExecutor,
                ""
            )
                .logStderrToInfo()
                .logStdoutToInfo()
                .execute(execOperations::exec)

            generateChromeTraces?.invoke()
        }
    }

    private fun applyBuildSettings(buildSettings: BuildSettingsConfiguration, processBuilder: ProcessInfoBuilder){
        processBuilder.addEnvironments(buildSettings.getEnvironmentVariableMap())
    }

    class CreationAction(
        private val generator: Provider<ExternalNativeJsonGenerator>,
        private val generateTask: TaskProvider<out Task>,
        scope: VariantScope
    ) : VariantTaskCreationAction<ExternalNativeBuildTask>(scope) {

        override val name: String
            get() = variantScope.getTaskName("externalNativeBuild")

        override val type: Class<ExternalNativeBuildTask>
            get() = ExternalNativeBuildTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<out ExternalNativeBuildTask>
        ) {
            super.handleProvider(taskProvider)
            assert(variantScope.taskContainer.externalNativeBuildTask == null)
            variantScope.taskContainer.externalNativeBuildTask = taskProvider
        }

        override fun configure(task: ExternalNativeBuildTask) {
            super.configure(task)

            val scope = variantScope

            task.dependsOn(
                generateTask, scope.getArtifactFileCollection(RUNTIME_CLASSPATH, ALL, JNI)
            )

            task.generator = generator
        }
    }

    companion object {

        // This placeholder is inserted into the buildTargetsCommand, and then later replaced by the
        // list of libraries that shall be built with a single build tool invocation.
        const val BUILD_TARGETS_PLACEHOLDER = "{LIST_OF_TARGETS_TO_BUILD}"

        /**
         * @param buildTargetsCommand The build command that can build multiple targets in parallel.
         * @param artifactNames The names of artifacts the build command will build in parallel.
         * @return Replaces the placeholder in the input command with the given artifacts and returns a
         * command that can be executed directly.
         */
        private fun substituteBuildTargetsCommand(
            buildTargetsCommand: String, artifactNames: List<String>
        ): String {
            return buildTargetsCommand.replace(
                BUILD_TARGETS_PLACEHOLDER, artifactNames.joinToString(" ")
            )
        }
    }
}
