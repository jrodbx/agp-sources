/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.build

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.attribution.generateChromeTrace
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel
import com.android.build.gradle.internal.cxx.gradle.generator.NativeBuildOutputLevel
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValueMini
import com.android.build.gradle.internal.cxx.json.NativeLibraryValueMini
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.logging.lifecycleln
import com.android.build.gradle.internal.cxx.model.jsonFile
import com.android.build.gradle.internal.cxx.model.ninjaLogFile
import com.android.build.gradle.internal.cxx.model.objFolder
import com.android.build.gradle.internal.cxx.process.createProcessOutputJunction
import com.android.build.gradle.internal.cxx.settings.BuildSettingsConfiguration
import com.android.build.gradle.internal.cxx.settings.getEnvironmentVariableMap
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.ide.common.process.ProcessInfoBuilder
import com.android.utils.FileUtils
import com.android.utils.cxx.CxxDiagnosticCode
import com.google.common.base.Joiner
import com.google.common.base.Preconditions
import com.google.common.base.Strings
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import org.gradle.api.GradleException
import org.gradle.process.ExecOperations
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Clock
import kotlin.streams.toList

/**
 * Build a C/C++ project.
 */
class CxxRegularBuilder(val configurationModel: CxxConfigurationModel) : CxxBuilder {
    override val objFolder: File get() = configurationModel.activeAbis.first().objFolder
    override val soFolder: File get() = configurationModel.activeAbis.first().soFolder

    private val variant get() = configurationModel.variant
    private val abis get() = configurationModel.activeAbis

    /**
     * Get native build config minis. Also gather stats if they haven't already been gathered for
     * this variant
     *
     * @return the mini configs
     */
    private val nativeBuildConfigValueMinis: List<NativeBuildConfigValueMini>
        get() = AndroidBuildGradleJsons.getNativeBuildMiniConfigs(abis, null)

    /** Represents a single build step that, when executed, builds one or more libraries.  */
    private class BuildStep(
            val buildCommandComponents: List<String>,
            val libraries: List<NativeLibraryValueMini>,
            val outputFolder: File
    )

    override fun build(ops: ExecOperations) {
        infoln("starting build")
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

            if (config.buildTargetsCommandComponents?.isNotEmpty() == true) {
                // Build all libraries together in one step, using the names of the artifacts.
                val artifactNames = librariesToBuild
                        .mapNotNull { library -> library.artifactName }
                        .distinct()
                        .sorted()
                val buildTargetsCommand =
                        substituteBuildTargetsCommand(
                                config.buildTargetsCommandComponents!!,
                                artifactNames
                        )
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
                                    libraryValue.buildCommandComponents!!,
                                    listOf(libraryValue),
                                    abis[miniConfigIndex].jsonFile.parentFile
                            )
                    )
                    infoln("about to build ${libraryValue.buildCommandComponents!!.joinToString(" ")}")
                }
            }
        }

        executeProcessBatch(ops, buildSteps)

        infoln("check expected build outputs")
        for (config in miniConfigs) {
            for (library in config.libraries.values) {
                Preconditions.checkState(!Strings.isNullOrEmpty(library.artifactName))
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
                // then link or copy to objFolder (reference b.android.com/256515)
                //
                // Since there is now a hard link outside of the standard build/ folder we have to
                // make sure `clean` deletes both the original .so file and the hard link to so
                // that the ref count on the inode goes down from two to zero. Here's how the two
                // files are covered.
                // (1) Gradle plugin deletes the build/ folder. This covers the destination of the
                //     copy.
                // (2) ExternalNativeCleanTask calls the individual clean targets for everything
                //     that was built. This is expected to delete the .so file but it is up to the
                //     CMakeLists.txt or Android.mk author to ensure this.
                val abi = Abi.getByName(library.abi!!) ?: throw RuntimeException(
                        "Unknown ABI seen ${library.abi}"
                )
                val expectedOutputFile = FileUtils.join(
                        variant.soFolder,
                        abi.tag,
                        output.name
                )
                if (!FileUtils.isSameFile(output, expectedOutputFile)) {
                    infoln("external build set its own library output location for " +
                            "'${output.name}', hard link or copy to expected location")

                    if (expectedOutputFile.parentFile.mkdirs()) {
                        infoln("created folder ${expectedOutputFile.parentFile}")
                    }
                    hardLinkOrCopy(output, expectedOutputFile)
                }

                for (runtimeFile in library.runtimeFiles) {
                    val dest = FileUtils.join(variant.soFolder, abi.tag, runtimeFile.name)
                    hardLinkOrCopy(runtimeFile, dest)
                }
            }
        }

        for(abi in abis) {
            if (abi.stlLibraryFile == null) continue
            if (!abi.stlLibraryFile.isFile) continue
            if (!abi.soFolder.isDirectory) {
                // A build failure can leave the obj/abi folder missing. Just note that case
                // and continue without copying STL.
                infoln("didn't copy STL file to ${abi.soFolder} because that folder wasn't created by the build ")
            } else {
                val objAbi = abi.soFolder.resolve(abi.stlLibraryFile.name)
                hardLinkOrCopy(abi.stlLibraryFile, objAbi)
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

            if ((config.buildTargetsCommandComponents?.isEmpty() != false) && (libraryValue.buildCommandComponents?.isEmpty() != false)
            ) {
                // This can happen when there's an externally referenced library.
                infoln(
                        "not building target ${libraryValue.artifactName!!} because there was no " +
                                "buildCommandComponents for the target, nor a buildTargetsCommandComponents for the config")
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
                    when (com.google.common.io.Files.getFileExtension(output.name)) {
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
            errorln(
                    CxxDiagnosticCode.REQUIRED_BUILD_TARGETS_MISSING,
                    "did not find implicitly required targets: ${implicitTargets.joinToString(", ")}"
            )
        }
        return librariesToBuild
    }

    /**
     * Given a list of build steps, execute each. If there is a failure, processing is stopped at
     * that point.
     */
    private fun executeProcessBatch(ops: ExecOperations, buildSteps: List<BuildStep>) {
        for (buildStep in buildSteps) {
            val tokens = buildStep.buildCommandComponents
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
                infoln("Build multiple targets ${targetNames.joinToString(" ")}")
            } else {
                Preconditions.checkElementIndex(0, buildStep.libraries.size)
                logFileSuffix = buildStep.libraries[0].artifactName + "_" + abiName
                infoln("Build $logFileSuffix")
            }

            abis
                    .firstOrNull { abiModel -> abiModel.abi.tag == abiName }
                    ?.let {
                        applyBuildSettings(it.buildSettings, processBuilder)
                    }

            val generateChromeTraces =
                    abis
                            .filter { it.variant.module.buildSystem == NativeBuildSystem.CMAKE}
                            .firstOrNull { it.abi.tag == abiName }
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
                    buildStep.outputFolder.resolve("android_gradle_build_command_$logFileSuffix.txt"),
                    buildStep.outputFolder.resolve("android_gradle_build_stdout_$logFileSuffix.txt"),
                    buildStep.outputFolder.resolve("android_gradle_build_stderr_$logFileSuffix.txt"),
                    processBuilder,
                    "")
                    .logStderr()
                    .logStdout()
                    .logFullStdout(abis.firstOrNull()?.variant?.module?.nativeBuildOutputLevel == NativeBuildOutputLevel.VERBOSE)
                    .execute(ops::exec)

            generateChromeTraces?.invoke()
        }
    }

    private fun applyBuildSettings(buildSettings: BuildSettingsConfiguration, processBuilder: ProcessInfoBuilder){
        processBuilder.addEnvironments(buildSettings.getEnvironmentVariableMap())
    }

    companion object {

        // This placeholder is inserted into the buildTargetsCommand, and then later replaced by the
        // list of libraries that shall be built with a single build tool invocation.
        const val BUILD_TARGETS_PLACEHOLDER = "{LIST_OF_TARGETS_TO_BUILD}"

        /**
         * @param buildTargetsCommandComponents The build command that can build multiple targets in parallel.
         * @param artifactNames The names of artifacts the build command will build in parallel.
         * @return Replaces the placeholder in the input command with the given artifacts and returns a
         * command that can be executed directly.
         */
        private fun substituteBuildTargetsCommand(
                buildTargetsCommandComponents: List<String>, artifactNames: List<String>
        ): List<String> {
            if (BUILD_TARGETS_PLACEHOLDER !in buildTargetsCommandComponents) {
                return buildTargetsCommandComponents
            }
            return buildTargetsCommandComponents.takeWhile { it != BUILD_TARGETS_PLACEHOLDER } +
                    artifactNames +
                    buildTargetsCommandComponents.takeLastWhile { it != BUILD_TARGETS_PLACEHOLDER }
        }
    }
}

/**
 * Hard link [source] to [destination].
 */
internal fun hardLinkOrCopy(source: File, destination: File) {
    // Dependencies within the same project will also show up as runtimeFiles, and
    // will have the same source and destination. Can skip those.
    if (FileUtils.isSameFile(source, destination)) {
        // This happens if source and destination are lexically the same
        // --or-- if one is a hard link to the other.
        // Either way, no work to do.
        return
    }

    if (destination.exists()) {
        destination.delete()
    }

    // CMake can report runtime files that it doesn't later produce.
    // Don't try to copy these. Also, don't warn because hard-link/copy
    // is not the correct location to diagnose why the *original*
    // runtime file was not created.
    if (!source.exists()) {
        return
    }

    try {
        Files.createLink(destination.toPath(), source.toPath().toRealPath())
        infoln("linked $source to $destination")
    } catch (e: IOException) {
        // This can happen when hard linking from one drive to another on Windows
        // In this case, copy the file instead.
        com.google.common.io.Files.copy(source, destination)
        infoln("copied $source to $destination")
    }
}
