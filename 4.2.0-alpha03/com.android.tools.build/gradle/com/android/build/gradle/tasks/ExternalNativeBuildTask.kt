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

import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.attribution.generateChromeTrace
import com.android.build.gradle.internal.cxx.gradle.generator.CxxMetadataGenerator
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons.getNativeBuildMiniConfigs
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValueMini
import com.android.build.gradle.internal.cxx.json.NativeLibraryValueMini
import com.android.build.gradle.internal.cxx.logging.IssueReporterLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel
import com.android.build.gradle.internal.cxx.gradle.generator.createCxxMetadataGenerator
import com.android.build.gradle.internal.cxx.model.jsonFile
import com.android.build.gradle.internal.cxx.model.ninjaLogFile
import com.android.build.gradle.internal.cxx.model.soFolder
import com.android.build.gradle.internal.cxx.process.createProcessOutputJunction
import com.android.build.gradle.internal.cxx.settings.BuildSettingsConfiguration
import com.android.build.gradle.internal.cxx.settings.getEnvironmentVariableMap
import com.android.build.gradle.internal.ndk.Stl
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.JNI
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.UnsafeOutputsTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
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
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.provider.Property
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
abstract class ExternalNativeBuildTask @Inject constructor(private val ops: ExecOperations) :
    UnsafeOutputsTask("External Native Build task is always run as incrementality is left to the external build system.") {
    @get:Internal
    abstract val sdkComponents: Property<SdkComponentsBuildService>
    private lateinit var configurationModel: CxxConfigurationModel

    @Transient
    private var generator : CxxMetadataGenerator? = null
    private val variant get() = generator().variant
    private val abis get() = generator().abis

    private fun generator() : CxxMetadataGenerator {
        if (generator != null) return generator!!
        IssueReporterLoggingEnvironment(
            DefaultIssueReporter(LoggerWrapper(logger))
        ).use {
            generator =
                createCxxMetadataGenerator(
                    sdkComponents.get(),
                    configurationModel
                )
        }
        return generator!!
    }

    /**
     * Get native build config minis. Also gather stats if they haven't already been gathered for
     * this variant
     *
     * @return the mini configs
     */
    private val nativeBuildConfigValueMinis: List<NativeBuildConfigValueMini>
        get() = getNativeBuildMiniConfigs(abis.map { it.jsonFile }, null)

    // Exposed in Variants API
    @get:Internal("Temporary to suppress warnings (bug 135900510), may need more investigation")
    val objFolder: File
        get() = variant.objFolder

    // Exposed in Variants API
    @get:Internal("Temporary to suppress warnings (bug 135900510), may need more investigation")
    val soFolder: File
        get() = variant.soFolder

    /** Represents a single build step that, when executed, builds one or more libraries.  */
    private class BuildStep(
        val buildCommand: String,
        val libraries: List<NativeLibraryValueMini>,
        val outputFolder: File
    )

    override fun doTaskAction() {
        IssueReporterLoggingEnvironment(DefaultIssueReporter(LoggerWrapper(logger))).use { buildImpl() }
    }

    private fun getStlSharedObjectFiles(): Map<Abi, File> {
        if (variant.module.buildSystem != NativeBuildSystem.CMAKE) return mapOf()
        // Search for ANDROID_STL build argument. Process in order / later flags take precedent.
        var stl: Stl? = null
        for (argument in variant.buildSystemArgumentList.map { it.replace(" ", "") }) {
            if (argument.startsWith("-DANDROID_STL=")) {
                val stlName = argument.split("=".toRegex(), 2).toTypedArray()[1]
                stl = Stl.fromArgumentName(stlName)
                if (stl == null) {
                    errorln("Unrecognized STL in arguments: %s", stlName)
                }
            }
        }

        // TODO: Query the default from the NDK.
        // We currently assume the default to not require packaging for the default STL. This is
        // currently safe because the default for ndk-build has always been system (which doesn't
        // require packaging because it's a system library) and gnustl_static or c++_static for
        // CMake (which also doesn't require packaging).
        //
        // https://github.com/android-ndk/ndk/issues/744 wants to change the default for both to
        // c++_shared, but that can't happen until we stop assuming the default does not need to be
        // packaged.
        return if (stl == null) {
            mapOf()
        } else {
            variant.module.stlSharedObjectMap.getValue(stl)
                .filter { e -> abis.map { it.abi }.contains(e.key) }
        }
    }

    @Throws(BuildCommandException::class, IOException::class)
    private fun buildImpl() {
        infoln("starting build")
        checkNotNull(variantName)
        infoln("reading expected JSONs")
        val miniConfigs = nativeBuildConfigValueMinis
        infoln("done reading expected JSONs")

        val targets = variant.buildTargetSet

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
                        abis[miniConfigIndex].jsonFile
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
                            abis[miniConfigIndex].jsonFile.parentFile
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
                val output = library.output ?: continue
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
                    "Unknown ABI seen ${library.abi}"
                )
                val expectedOutputFile = FileUtils.join(
                    variant.objFolder,
                    abi.tag,
                    output.name
                )
                if (!FileUtils.isSameFile(output, expectedOutputFile)) {
                    infoln("external build set its own library output location for " +
                            "'${output.name}', copy to expected location")

                    if (expectedOutputFile.parentFile.mkdirs()) {
                        infoln("created folder ${expectedOutputFile.parentFile}")
                    }
                    infoln("copy file $output to $expectedOutputFile")
                    Files.copy(output, expectedOutputFile)
                }

                for (runtimeFile in library.runtimeFiles) {
                    val dest =
                        FileUtils.join(variant.objFolder, abi.tag, runtimeFile.name)
                    // Dependencies within the same project will also show up as runtimeFiles, and
                    // will have the same source and destination. Can skip those.
                    if (!FileUtils.isSameFile(runtimeFile, dest)) {
                        Files.copy(runtimeFile, dest)
                    }
                }
            }
        }

        val stlSharedObjectFiles = getStlSharedObjectFiles()
        if (stlSharedObjectFiles.isNotEmpty()) {
            infoln("copy STL shared object files")
            for (abi in stlSharedObjectFiles.keys) {
                val stlSharedObjectFile = stlSharedObjectFiles.getValue(abi)
                val objAbi = FileUtils.join(
                    variant.objFolder,
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
        val targets = variant.buildTargetSet
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
        val targets = variant.buildTargetSet
        val implicitTargets = variant.implicitBuildTargetSet.toMutableSet()
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
                val output = libraryValue.output
                if (output == null) {
                    infoln(
                        "not building target ${libraryValue.artifactName!!} because no targets " +
                                "are specified and library build output file is null")
                    continue
                }

                if (variant.implicitBuildTargetSet.contains(libraryValue.artifactName)) {
                    infoln("building target ${libraryValue.artifactName} because it is required by the build")
                } else {
                    when (Files.getFileExtension(output.name)) {
                        "a" -> {
                            infoln("not building target library ${libraryValue.artifactName!!} because static libraries are not build by default.")
                            continue@loop
                        }
                        "so" -> infoln("building target library ${libraryValue.artifactName!!} because no targets are specified.")
                        "" -> infoln("building target executable ${libraryValue.artifactName!!} because no targets are specified.")
                        else -> {
                            infoln("not building target ${libraryValue.artifactName!!} because the type cannot be determined.")
                            continue@loop
                        }
                    }
                }
            }

            librariesToBuild.add(libraryValue)
        }

        implicitTargets.removeAll(librariesToBuild.map { it.artifactName })
        if (implicitTargets.isNotEmpty()) {
            errorln("did not find implicitly required targets: ${implicitTargets.joinToString(", ")}")
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

            abis
                .firstOrNull { abiModel -> abiModel.abi.tag == abiName }
                ?.let {
                    applyBuildSettings(it.buildSettings, processBuilder)
                }

            val generateChromeTraces =
                generator().takeIf { it.variant.module.buildSystem == NativeBuildSystem.CMAKE }
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
                "")
                .logStderrToInfo()
                .logStdoutToInfo()
                .execute(ops::exec)

            generateChromeTraces?.invoke()
        }
    }

    private fun applyBuildSettings(buildSettings: BuildSettingsConfiguration, processBuilder: ProcessInfoBuilder){
        processBuilder.addEnvironments(buildSettings.getEnvironmentVariableMap())
    }

    class CreationAction(
        private val configurationModel : CxxConfigurationModel,
        componentProperties: ComponentPropertiesImpl,
        private val generateTask: TaskProvider<out Task>
    ) : VariantTaskCreationAction<ExternalNativeBuildTask, ComponentPropertiesImpl>(
        componentProperties
    ) {

        override val name: String
            get() = computeTaskName("externalNativeBuild")

        override val type: Class<ExternalNativeBuildTask>
            get() = ExternalNativeBuildTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<ExternalNativeBuildTask>
        ) {
            super.handleProvider(taskProvider)
            assert(creationConfig.taskContainer.externalNativeBuildTask == null)
            creationConfig.taskContainer.externalNativeBuildTask = taskProvider
        }

        override fun configure(
            task: ExternalNativeBuildTask
        ) {
            super.configure(task)

            task.dependsOn(
                generateTask,
                creationConfig.variantDependencies.getArtifactFileCollection(RUNTIME_CLASSPATH, ALL, JNI)
            )

            task.configurationModel = configurationModel
            task.sdkComponents.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry)
            )
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
