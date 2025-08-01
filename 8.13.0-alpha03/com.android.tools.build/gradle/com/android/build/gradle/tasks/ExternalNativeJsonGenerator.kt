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

import com.android.SdkConstants
import com.android.build.gradle.internal.cxx.configure.createConfigurationInvalidationState
import com.android.build.gradle.internal.cxx.configure.encode
import com.android.build.gradle.internal.cxx.configure.shouldConfigure
import com.android.build.gradle.internal.cxx.configure.shouldConfigureReasonMessages
import com.android.build.gradle.internal.cxx.configure.recordConfigurationFingerPrint
import com.android.build.gradle.internal.cxx.configure.softConfigureOkay
import com.android.build.gradle.internal.cxx.configure.trySymlinkNdk
import com.android.build.gradle.internal.cxx.gradle.generator.CxxMetadataGenerator
import com.android.build.gradle.internal.cxx.io.writeTextIfDifferent
import com.android.build.gradle.internal.cxx.io.synchronizeFile
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValueMini
import com.android.build.gradle.internal.cxx.logging.PassThroughPrefixingLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.ThreadLoggingEnvironment.Companion.requireExplicitLogger
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.logging.logStructured
import com.android.build.gradle.internal.cxx.logging.toJsonString
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.PrefabConfigurationState
import com.android.build.gradle.internal.cxx.model.additionalProjectFilesIndexFile
import com.android.build.gradle.internal.cxx.model.buildFileIndexFile
import com.android.build.gradle.internal.cxx.model.buildSystemTag
import com.android.build.gradle.internal.cxx.model.compileCommandsJsonBinFile
import com.android.build.gradle.internal.cxx.model.compileCommandsJsonFile
import com.android.build.gradle.internal.cxx.model.getBuildCommandArguments
import com.android.build.gradle.internal.cxx.model.jsonFile
import com.android.build.gradle.internal.cxx.model.jsonGenerationLoggingRecordFile
import com.android.build.gradle.internal.cxx.model.lastConfigureFingerPrintFile
import com.android.build.gradle.internal.cxx.model.metadataGenerationCommandFile
import com.android.build.gradle.internal.cxx.model.metadataGenerationTimingFolder
import com.android.build.gradle.internal.cxx.model.miniConfigFile
import com.android.build.gradle.internal.cxx.model.modelOutputFile
import com.android.build.gradle.internal.cxx.model.name
import com.android.build.gradle.internal.cxx.model.ninjaBuildFile
import com.android.build.gradle.internal.cxx.model.ninjaBuildLocationFile
import com.android.build.gradle.internal.cxx.model.predictableRepublishFolder
import com.android.build.gradle.internal.cxx.model.prefabClassPath
import com.android.build.gradle.internal.cxx.model.prefabConfigFile
import com.android.build.gradle.internal.cxx.model.prefabPackageConfigurationList
import com.android.build.gradle.internal.cxx.model.prefabPackageDirectoryList
import com.android.build.gradle.internal.cxx.model.shouldGeneratePrefabPackages
import com.android.build.gradle.internal.cxx.model.symbolFolderIndexFile
import com.android.build.gradle.internal.cxx.model.writeJsonToFile
import com.android.utils.cxx.os.which
import com.android.build.gradle.internal.cxx.process.ExecuteProcessCommand
import com.android.build.gradle.internal.cxx.timing.TimingEnvironment
import com.android.build.gradle.internal.cxx.timing.time
import com.android.build.gradle.internal.profile.AnalyticsUtil
import com.android.ide.common.process.ProcessException
import com.android.utils.FileUtils
import com.android.utils.cxx.CxxDiagnosticCode.METADATA_GENERATION_GRADLE_EXCEPTION
import com.android.utils.cxx.CxxDiagnosticCode.METADATA_GENERATION_PROCESS_FAILURE
import com.google.common.base.Charsets
import com.google.common.collect.Lists
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import com.google.wireless.android.sdk.stats.GradleBuildVariant.NativeBuildConfigInfo
import com.google.wireless.android.sdk.stats.GradleBuildVariant.NativeBuildConfigInfo.GenerationOutcome
import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.process.ExecOperations
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import com.android.build.gradle.internal.cxx.json.lint

const val ANDROID_GRADLE_BUILD_VERSION = "2"

/**
 * Base class for generation of native JSON.
 */
abstract class ExternalNativeJsonGenerator internal constructor(
    @get:Internal("Temporary to suppress Gradle warnings (bug 135900510), may need more investigation")
    val abi: CxxAbiModel,
    @get:Internal override val variantBuilder: GradleBuildVariant.Builder?
) : CxxMetadataGenerator {
    override fun configure(ops: ExecOperations, forceConfigure: Boolean) {
        requireExplicitLogger()
        // Check whether NDK folder symlinking is required.
        if (abi.variant.module.ndkFolderAfterSymLinking != null
            && !abi.variant.module.ndkFolder.isDirectory) {
            if (!trySymlinkNdk(
                abi.variant.module.ndkFolderBeforeSymLinking,
                abi.variant.module.ndkFolderAfterSymLinking
            )) return
        }
        if (!abi.variant.module.ndkFolder.isDirectory) error("Expected NDK folder to exist")
        // These are lazily initialized values that can only be computed from a Gradle managed
        // thread. Compute now so that we don't in the worker threads that we'll be running as.
        abi.variant.prefabPackageConfigurationList
        abi.variant.prefabPackageDirectoryList
        abi.variant.prefabClassPath
        try {
            configureOneAbi(ops, forceConfigure, abi)
        } catch (e: GradleException) {
            errorln(
                METADATA_GENERATION_GRADLE_EXCEPTION,
                "exception while building Json %s",
                "${e.message} : ${e.stackTraceToString()}"
            )
        } catch (e: ProcessException) {
            errorln(
                METADATA_GENERATION_PROCESS_FAILURE,
                "error when building with %s using %s: %s",
                abi.variant.module.buildSystemTag,
                abi.variant.module.makeFile,
                "${e.message} : ${e.stackTraceToString()}"
            )
        }
    }

    protected open fun checkPrefabConfig() {}

    private fun configureOneAbi(
        ops: ExecOperations,
        forceConfigure: Boolean,
        abi: CxxAbiModel
    ) {
        PassThroughPrefixingLoggingEnvironment(
            abi.variant.module.makeFile,
            abi.variant.variantName + "|" + abi.name
        ).use { recorder ->
            TimingEnvironment(
                    abi.metadataGenerationTimingFolder,
                    "generate_cxx_metadata").use {
                val variantStats =
                        NativeBuildConfigInfo.newBuilder()
                variantStats.abi = AnalyticsUtil.getAbi(abi.name)
                variantStats.debuggable = abi.variant.isDebuggableEnabled
                val startTime = System.currentTimeMillis()
                variantStats.generationStartMs = startTime
                try {
                    infoln(
                            "Start JSON generation. Platform version: %s min SDK version: %s",
                            abi.abiPlatformVersion,
                            abi.name,
                            abi.abiPlatformVersion
                    )

                    val processBuilder = getProcessBuilder(abi)

                    // Write the command-file if it changed
                    val currentBuildCommand = """
                        ${processBuilder.argsText()}
                        Build command args: ${abi.getBuildCommandArguments()}
                        Version: $ANDROID_GRADLE_BUILD_VERSION
                        """.trimIndent()
                    abi.metadataGenerationCommandFile.writeTextIfDifferent(currentBuildCommand)

                    // Write the prefab configuration if it changed
                    val prefabConfigurationState = PrefabConfigurationState(
                            abi.variant.module.project.isPrefabEnabled,
                            abi.variant.prefabClassPath,
                            abi.variant.prefabPackageDirectoryList
                        ).toJsonString()
                    abi.prefabConfigFile.writeTextIfDifferent(prefabConfigurationState)

                    val invalidationState = time("create-invalidation-state") {
                        createConfigurationInvalidationState(
                            forceConfigure = forceConfigure,
                            lastConfigureFingerPrintFile = abi.lastConfigureFingerPrintFile,
                            configureInputFiles = getConfigureInputFiles(abi),
                            requiredOutputFiles = listOf(abi.jsonFile),
                            optionalOutputFiles = listOf(
                                abi.ninjaBuildFile,
                                abi.ninjaBuildLocationFile,
                                abi.compileCommandsJsonFile,
                                abi.prefabConfigFile,
                                abi.miniConfigFile,
                                abi.symbolFolderIndexFile,
                                abi.buildFileIndexFile,
                                abi.additionalProjectFilesIndexFile,
                                abi.compileCommandsJsonBinFile
                            ),
                            hardConfigureFiles = listOf(abi.metadataGenerationCommandFile)
                        )
                    }

                    // Log information about why configuration was executed or not
                    logStructured { encoder ->
                        invalidationState.encode(encoder)
                    }

                    // Remove the fingerprint file if it exists. This ensures that any fingerprint
                    // file is written as a result of a successful configure.
                    abi.lastConfigureFingerPrintFile.delete()

                    if (invalidationState.shouldConfigure) {
                        infoln("rebuilding JSON %s due to:", abi.jsonFile)
                        for (reason in invalidationState.shouldConfigureReasonMessages) {
                            infoln(reason)
                        }
                        if (abi.shouldGeneratePrefabPackages()) {
                            time("generate-prefab-packages") {
                                checkPrefabConfig()
                                createPrefabBuildSystemGlue(
                                    ops,
                                    abi
                                )
                            }
                        }

                        // Related to https://issuetracker.google.com/69408798
                        // Something has changed so we need to clean up some build intermediates and
                        // outputs.
                        // - If only a build file has changed then we try to keep .o files and,
                        // in the case of CMake, the generated Ninja project. In this case we must
                        // remove .so files because they are automatically packaged in the APK on a
                        // *.so basis.
                        // - If there is some other cause to recreate the JSon, such as command-line
                        // changed then wipe out the whole JSon folder.
                        if (abi.cxxBuildFolder.parentFile.exists()) {
                            if (invalidationState.softConfigureOkay) {
                                infoln(
                                    "keeping json folder '%s' but regenerating project",
                                    abi.cxxBuildFolder
                                )
                            } else {
                                infoln("removing stale contents from '%s'", abi.cxxBuildFolder)
                                FileUtils.deletePath(abi.cxxBuildFolder)
                            }
                        }
                        if (abi.cxxBuildFolder.mkdirs()) {
                            infoln("created folder '%s'", abi.cxxBuildFolder)
                        }

                        infoln("executing %s %s", abi.variant.module.buildSystemTag, processBuilder)
                        time("execute-generate-process") { executeProcess(ops, abi) }
                        infoln("done executing %s", abi.variant.module.buildSystemTag)

                        // Check that required outputs were produced
                        for(requiredOutput in invalidationState.requiredOutputFilesList) {
                            val file = File(requiredOutput)
                            if (file.isFile) continue

                            throw GradleException(
                                String.format(
                                    "Expected metadata generation to create '%s' but it didn't",
                                    file
                                )
                            )
                        }

                        val variantBuilder = variantBuilder
                        val miniconfig =
                            if (variantBuilder == null) AndroidBuildGradleJsons.getNativeBuildMiniConfig(abi, null)
                            else synchronized(variantBuilder) {
                                AndroidBuildGradleJsons.getNativeBuildMiniConfig(
                                    abi, variantBuilder
                                )
                            }

                        // Related to https://issuetracker.google.com/69408798
                        // Targets may have been removed or there could be other orphaned extra
                        // .so files. Remove these and rely on the build step to replace them
                        // if they are legitimate. This is to prevent unexpected .so files from
                        // being packaged in the APK.
                        time("remove-unexpected-so-files") {
                            removeUnexpectedSoFiles(
                                abi.soFolder,
                                miniconfig
                            )
                        }

                        // Write the command file
                        abi.metadataGenerationCommandFile.writeTextIfDifferent(currentBuildCommand)

                        // Write the prefab configuration
                        abi.prefabConfigFile.writeTextIfDifferent(prefabConfigurationState)

                        // Write extra metadata files
                        abi.symbolFolderIndexFile.writeTextIfDifferent(abi.soFolder.absolutePath)
                        abi.buildFileIndexFile.writeTextIfDifferent(miniconfig.buildFiles.joinToString(System.lineSeparator()))

                        // Republish compile_commands.json to a predictable location.
                        if (abi.compileCommandsJsonFile.isFile) {
                            synchronizeFile(
                                abi.compileCommandsJsonFile,
                                abi.predictableRepublishFolder.resolve(abi.compileCommandsJsonFile.name)
                            )
                        }

                        // Lint the final environment
                        miniconfig.lint(
                            abi.miniConfigFile,
                            abi.variant.module.ndkDefaultAbiList,
                            abi.variant.module.ndkSupportedAbiList);

                        // Record the outcome. JSON was built.
                        variantStats.outcome = GenerationOutcome.SUCCESS_BUILT
                    } else {
                        infoln("JSON '%s' was up-to-date", abi.jsonFile)
                        variantStats.outcome = GenerationOutcome.SUCCESS_UP_TO_DATE
                    }

                    // Record well-known files that were written
                    invalidationState.recordConfigurationFingerPrint()

                    infoln("JSON generation completed without problems")
                } catch (e: GradleException) {
                    variantStats.outcome = GenerationOutcome.FAILED
                    infoln("JSON generation completed with problem. Exception: $e")
                    throw e
                } catch (e: IOException) {
                    variantStats.outcome = GenerationOutcome.FAILED
                    infoln("JSON generation completed with problem. Exception: $e")
                    throw e
                } catch (e: ProcessException) {
                    variantStats.outcome = GenerationOutcome.FAILED
                    infoln("JSON generation completed with problem. Exception: $e")
                    throw e
                } finally {
                    variantStats.generationDurationMs = System.currentTimeMillis() - startTime
                    variantBuilder?.let {
                        synchronized(it) {
                            it.addNativeBuildConfig(variantStats)
                        }
                    }
                    abi.jsonGenerationLoggingRecordFile.parentFile.mkdirs()
                    Files.write(
                            abi.jsonGenerationLoggingRecordFile.toPath(),
                            recorder.record.toJsonString()
                                    .toByteArray(Charsets.UTF_8)
                    )
                    infoln("Writing build model to ${abi.modelOutputFile}")
                    time("write-metadata-json-to-file") { abi.writeJsonToFile() }
                }
            }
        }
    }

    abstract fun getProcessBuilder(abi: CxxAbiModel): ExecuteProcessCommand

    /**
     * Executes the JSON generation process. Return the combination of STDIO and STDERR from running
     * the process.
     */
    abstract fun executeProcess(ops: ExecOperations, abi: CxxAbiModel)

    private fun getConfigureInputFiles(abi: CxxAbiModel): List<File> {
        val result = mutableSetOf<File>()

        // Add the make file (like CMakeLists.txt)
        result.add(abi.variant.module.makeFile)

        // If there is a Ninja configure script then depend on it.
        if (abi.variant.module.configureScript != null) {
            // Find the tool on PATH. If not found, then just include the path as-is. In this
            // case the tool will be listed in the configuration fingerprint file as missing.
            result.add(
                which(abi.variant.module.configureScript)
                    ?: abi.variant.module.configureScript)
        }

        // Add prefab_publication.json files
        result.addAll(abi.variant.prefabPackageConfigurationList)

        // We're going to read the mini-config json file to get the list of input files. The
        // mini-config is derived from jsonFile so we check for that file's existing before
        // proceeding.
        if (!abi.jsonFile.exists()) {
            return result.toList()
        }

        // Now check whether the JSON is out-of-date with respect to the build files it declares.
        // TODO : Populating variantBuilder should be decoupled from getNativeBuildMiniConfig.
        //        The concerns are separate and its hard to reason about when variantBuilder
        //        is actually updated (which should be once per C/C++ configure).
        val config = AndroidBuildGradleJsons.getNativeBuildMiniConfig(abi, variantBuilder)
        result.addAll(config.buildFiles)
        return result.toList()
    }

    companion object {
        /**
         * Returns true if platform is windows
         */
        @JvmStatic
        protected val isWindows: Boolean
            get() = SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS

        /**
         * This function removes unexpected so files from disk. Unexpected means they exist on disk but
         * are not present as a build output from the json.
         *
         *
         * It is generally valid for there to be extra .so files because the build system may copy
         * libraries to the output folder. This function is meant to be used in cases where we suspect
         * the .so may have been orphaned by the build system due to a change in build files.
         *
         * @param expectedOutputFolder the expected location of output .so files
         * @param config the existing miniconfig
         * @throws IOException in the case that json is missing or can't be read or some other IO
         * problem.
         */
        @Throws(IOException::class)
        private fun removeUnexpectedSoFiles(
            expectedOutputFolder: File, config: NativeBuildConfigValueMini
        ) {
            if (!expectedOutputFolder.isDirectory) {
                // Nothing to clean
                return
            }

            // Gather all expected build outputs
            val expectedSoFiles: MutableList<Path> =
                Lists.newArrayList()
            for (library in config.libraries.values) {
                val output = library.output ?: continue
                expectedSoFiles.add(output.toPath())
            }
            Files.walk(expectedOutputFolder.toPath()).use { paths ->
                paths
                    .filter { Files.isRegularFile(it) }
                    .filter { it.toString().endsWith(".so") }
                    .filter { !expectedSoFiles.contains(it) }
                    .forEach {
                        if (it.toFile().delete()) {
                            infoln("deleted unexpected build output $it in incremental regenerate")
                        }
                    }
            }
        }
    }
}
