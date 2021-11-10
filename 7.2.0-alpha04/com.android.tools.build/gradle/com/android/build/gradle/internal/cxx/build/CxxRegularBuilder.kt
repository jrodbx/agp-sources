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

import com.android.build.gradle.internal.cxx.attribution.encode
import com.android.build.gradle.internal.cxx.attribution.generateChromeTrace
import com.android.build.gradle.internal.cxx.attribution.generateNinjaSourceFileAttribution
import com.android.build.gradle.internal.cxx.io.synchronizeFile
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValueMini
import com.android.build.gradle.internal.cxx.json.NativeLibraryValueMini
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.logging.logStructured
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.ifLogNativeBuildToLifecycle
import com.android.build.gradle.internal.cxx.model.jsonFile
import com.android.build.gradle.internal.cxx.model.ninjaLogFile
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
import java.time.Clock
import kotlin.streams.toList

/**
 * Build a C/C++ project.
 */
class CxxRegularBuilder(val abi: CxxAbiModel) : CxxBuilder {
    private val variant get() = abi.variant

    /**
     * Get native build config minis. Also gather stats if they haven't already been gathered for
     * this variant
     *
     * @return the mini configs
     */
    private val nativeBuildConfigValueMini: NativeBuildConfigValueMini
        get() = AndroidBuildGradleJsons.getNativeBuildMiniConfig(abi, null)

    /** Represents a single build step that, when executed, builds one or more libraries.  */
    private class BuildStep(
        val abi: CxxAbiModel,
        val buildCommandComponents: List<String>,
        val libraries: List<NativeLibraryValueMini>,
        val targetsFromDsl : Set<String>,
        val outputFolder: File
    )

    override fun build(ops: ExecOperations) {
        infoln("starting build")
        infoln("reading expected JSONs")
        val config = nativeBuildConfigValueMini
        infoln("done reading expected JSONs")

        val targets = variant.buildTargetSet

        if (targets.isEmpty()) {
            infoln("executing build commands for targets that produce .so files or executables")
        } else {
            verifyTargetExists(config)
        }

        val buildSteps = Lists.newArrayList<BuildStep>()

        infoln("evaluate miniconfig")
        if (config.libraries.isEmpty()) {
            infoln("no libraries")
            return
        }

        val librariesToBuild = findLibrariesToBuild(config)
        if (librariesToBuild.isEmpty()) {
            infoln("no libraries to build")
            return
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
                    abi,
                    buildTargetsCommand,
                    librariesToBuild,
                    variant.buildTargetSet,
                    abi.jsonFile.parentFile
                )
            )
            infoln("about to build targets " + artifactNames.joinToString(", "))
        } else {
            // Build each library separately using multiple steps.
            for (libraryValue in librariesToBuild) {
                buildSteps.add(
                    BuildStep(
                        abi,
                        libraryValue.buildCommandComponents!!,
                        listOf(libraryValue),
                        variant.buildTargetSet,
                        abi.jsonFile.parentFile
                    )
                )
                infoln("about to build ${libraryValue.buildCommandComponents!!.joinToString(" ")}")
            }
        }

        executeProcessBatch(
            ops,
            buildSteps)

        infoln("check expected build outputs")
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
            val expectedOutputFile = FileUtils.join(
                abi.soFolder,
                output.name
            )

            if (!FileUtils.isSameFile(output, expectedOutputFile)) {
                infoln("external build set its own library output location for " +
                        "'${output.name}', hard link or copy to expected location")
                // Use synchronizeFile so that libs removed from the build will
                // also be deleted.
                synchronizeFile(
                    output,
                    expectedOutputFile)
            }

            for (runtimeFile in library.runtimeFiles) {
                val dest = FileUtils.join(abi.soFolder, runtimeFile.name)
                synchronizeFile(
                    runtimeFile,
                    dest)
            }
        }

        if (!abi.soFolder.isDirectory) {
            // A build failure can leave the obj/abi folder missing. Just note that case
            // and continue without copying STL.
            infoln("didn't copy STL file to ${abi.soFolder} because that folder wasn't created by the build ")
        } else {
            if (abi.stlLibraryFile != null && abi.stlLibraryFile.isFile) {
                val objAbi = abi.soFolder.resolve(abi.stlLibraryFile.name)
                synchronizeFile(
                    abi.stlLibraryFile,
                    objAbi)
            }
        }

        infoln("build complete")
    }

    /**
     * Verifies that all targets provided by the user will be built. Throws GradleException if it
     * detects an unexpected target.
     */
    private fun verifyTargetExists(config: NativeBuildConfigValueMini) {
        // Check the resulting JSON targets against the targets specified in ndkBuild.targets or
        // cmake.targets. If a target name specified by the user isn't present then provide an
        // error to the user that lists the valid target names.
        val targets = variant.buildTargetSet
        infoln("executing build commands for targets: '${Joiner.on(", ").join(targets)}'")

        // Search libraries for matching targets.
        val matchingTargets = Sets.newHashSet<String>()
        val unmatchedTargets = Sets.newHashSet<String>()
        for (libraryValue in config.libraries.values) {
            if (targets.contains(libraryValue.artifactName)) {
                matchingTargets.add(libraryValue.artifactName)
            } else {
                unmatchedTargets.add(libraryValue.artifactName)
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
    private fun executeProcessBatch(
        ops: ExecOperations,
        buildSteps: List<BuildStep>) {
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
            val abi = buildStep.abi
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

            applyBuildSettings(abi.buildSettings, processBuilder)

            val buildStartTime = Clock.systemUTC().millis()

            // Count of .ninja_log lines off core
            // (as opposed to reading the file entirely into memory)
            val linesToSkip = if (abi.ninjaLogFile.isFile) {
                abi.ninjaLogFile.useLines { it.count() }
            } else 0

            createProcessOutputJunction(
                buildStep.outputFolder.resolve("android_gradle_build_command_$logFileSuffix.txt"),
                buildStep.outputFolder.resolve("android_gradle_build_stdout_$logFileSuffix.txt"),
                buildStep.outputFolder.resolve("android_gradle_build_stderr_$logFileSuffix.txt"),
                processBuilder,
                "")
                .logStderr()
                .logStdout()
                .logFullStdout(abi.ifLogNativeBuildToLifecycle { true } ?: false)
                .execute(ops::exec)

            // Build attribution reporting based on .ninja_log
            // This is best-effort because it appears that ninja does not guarantee
            // that the log file is written.
            //
            // There's no existing way to track C++ build time for ndk-build.
            if (variant.module.buildSystem == NativeBuildSystem.CMAKE) {
                // Lazy because we only need to generate if the user has requested
                // chrome tracing or structured logging.
                val attributions by lazy {
                    generateNinjaSourceFileAttribution(
                        abi,
                        linesToSkip,
                        buildStartTime
                    ).toBuilder()
                        .addAllLibrary(buildStep.libraries.map { it.artifactName })
                        .build()
                }

                // If the user has requested chrome tracing, then generate it
                abi.variant.module.project.chromeTraceJsonFolder?.let { traceFolder ->
                    generateChromeTrace(
                        attributions,
                        abi,
                        buildStartTime,
                        traceFolder
                    )
                }

                // If the user has requested structured logging, then log it
                logStructured { encoder -> attributions.encode(encoder) }
            }
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

