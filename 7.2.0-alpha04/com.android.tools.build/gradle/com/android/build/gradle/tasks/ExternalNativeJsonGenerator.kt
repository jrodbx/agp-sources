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
import com.android.build.gradle.internal.cxx.configure.JsonGenerationInvalidationState
import com.android.build.gradle.internal.cxx.gradle.generator.CxxMetadataGenerator
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValueMini
import com.android.build.gradle.internal.cxx.logging.PassThroughPrefixingLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.ThreadLoggingEnvironment.Companion.requireExplicitLogger
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.logging.toJsonString
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.PrefabConfigurationState
import com.android.build.gradle.internal.cxx.model.PrefabConfigurationState.Companion.fromJson
import com.android.build.gradle.internal.cxx.model.buildFileIndexFile
import com.android.build.gradle.internal.cxx.model.buildSystemTag
import com.android.build.gradle.internal.cxx.model.getBuildCommandArguments
import com.android.build.gradle.internal.cxx.model.jsonFile
import com.android.build.gradle.internal.cxx.model.jsonGenerationLoggingRecordFile
import com.android.build.gradle.internal.cxx.model.metadataGenerationCommandFile
import com.android.build.gradle.internal.cxx.model.metadataGenerationTimingFolder
import com.android.build.gradle.internal.cxx.model.modelOutputFile
import com.android.build.gradle.internal.cxx.model.prefabClassPath
import com.android.build.gradle.internal.cxx.model.prefabConfigFile
import com.android.build.gradle.internal.cxx.model.prefabPackageDirectoryList
import com.android.build.gradle.internal.cxx.model.shouldGeneratePrefabPackages
import com.android.build.gradle.internal.cxx.model.symbolFolderIndexFile
import com.android.build.gradle.internal.cxx.model.writeJsonToFile
import com.android.build.gradle.internal.cxx.timing.TimingEnvironment
import com.android.build.gradle.internal.cxx.timing.time
import com.android.build.gradle.internal.profile.AnalyticsUtil
import com.android.ide.common.process.ProcessException
import com.android.ide.common.process.ProcessInfoBuilder
import com.android.utils.FileUtils
import com.android.utils.cxx.CxxDiagnosticCode.METADATA_GENERATION_FAILURE
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
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

const val ANDROID_GRADLE_BUILD_VERSION = "2"

/**
 * Base class for generation of native JSON.
 */
abstract class ExternalNativeJsonGenerator internal constructor(
    @get:Internal("Temporary to suppress Gradle warnings (bug 135900510), may need more investigation")
    val abi: CxxAbiModel,
    @get:Internal override val variantBuilder: GradleBuildVariant.Builder?
) : CxxMetadataGenerator {

    // TODO(153964094) Reconcile this with jsonGenerationDependencyFiles
    // They do the same work but one is for single abi and one is for all abis.
    @Throws(IOException::class)
    private fun getDependentBuildFiles(abi: CxxAbiModel): List<File> {
        val result: MutableList<File> =
            Lists.newArrayList()
        if (!abi.jsonFile.exists()) {
            return result
        }

        // Now check whether the JSON is out-of-date with respect to the build files it declares.
        val config = AndroidBuildGradleJsons
            .getNativeBuildMiniConfig(abi, variantBuilder)

        // If anything in the prefab package changes, re-run. Note that this also depends on the
        // directories, so added/removed files will also trigger a re-run.
        for (pkgDir in abi.variant.prefabPackageDirectoryList) {
            Files.walk(pkgDir.toPath())
                .forEach {
                    result.add(it.toFile())
                }
        }
        result.addAll(config.buildFiles)
        return result
    }

    override fun generate(ops: ExecOperations, forceGeneration: Boolean) {
        requireExplicitLogger()
        // These are lazily initialized values that can only be computed from a Gradle managed
        // thread. Compute now so that we don't in the worker threads that we'll be running as.
        abi.variant.prefabPackageDirectoryList
        abi.variant.prefabClassPath
        try {
            buildForOneConfiguration(ops, forceGeneration, abi)
        } catch (e: GradleException) {
            errorln(
                    METADATA_GENERATION_FAILURE,
                    "exception while building Json %s",
                    e.message!!
            )
        } catch (e: ProcessException) {
            errorln(
                    METADATA_GENERATION_FAILURE,
                    "error when building with %s using %s: %s",
                    abi.variant.module.buildSystemTag,
                    abi.variant.module.makeFile,
                    e.message!!
            )
        }
    }

    protected open fun checkPrefabConfig() {}

    @Throws(GradleException::class, IOException::class, ProcessException::class)
    private fun buildForOneConfiguration(
        ops: ExecOperations,
        forceJsonGeneration: Boolean,
        abi: CxxAbiModel
    ) {
        PassThroughPrefixingLoggingEnvironment(
            abi.variant.module.makeFile,
            abi.variant.variantName + "|" + abi.abi.tag
        ).use { recorder ->
            TimingEnvironment(
                    abi.metadataGenerationTimingFolder,
                    "generate_cxx_metadata").use {
                val variantStats =
                        NativeBuildConfigInfo.newBuilder()
                variantStats.abi = AnalyticsUtil.getAbi(abi.abi.tag)
                variantStats.debuggable = abi.variant.isDebuggableEnabled
                val startTime = System.currentTimeMillis()
                variantStats.generationStartMs = startTime
                try {
                    infoln(
                            "Start JSON generation. Platform version: %s min SDK version: %s",
                            abi.abiPlatformVersion,
                            abi.abi.tag,
                            abi.abiPlatformVersion
                    )

                    val processBuilder = getProcessBuilder(abi)

                    // See whether the current build command matches a previously written build command.
                    val currentBuildCommand = """
                    $processBuilder
                    Build command args: ${abi.getBuildCommandArguments()}
                    Version: $ANDROID_GRADLE_BUILD_VERSION
                    """.trimIndent()
                    val prefabState = PrefabConfigurationState(
                            abi.variant.module.project.isPrefabEnabled,
                            abi.variant.prefabClassPath,
                            abi.variant.prefabPackageDirectoryList
                    )
                    val previousPrefabState =
                            getPreviousPrefabConfigurationState(abi.prefabConfigFile)
                    val invalidationState = time("create-invalidation-state") {
                        JsonGenerationInvalidationState(
                                forceJsonGeneration,
                                abi.jsonFile,
                                abi.metadataGenerationCommandFile,
                                currentBuildCommand,
                                getFileContent(abi.metadataGenerationCommandFile),
                                getDependentBuildFiles(abi),
                                prefabState,
                                previousPrefabState
                        )
                    }
                    if (invalidationState.rebuild) {
                        infoln("rebuilding JSON %s due to:", abi.jsonFile)
                        for (reason in invalidationState.rebuildReasons) {
                            infoln(reason)
                        }
                        if (abi.shouldGeneratePrefabPackages()) {
                            time("generate-prefab-packages") {
                                checkPrefabConfig()
                                generatePrefabPackages(
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
                            if (invalidationState.softRegeneration) {
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

                        if (!abi.jsonFile.exists()) {
                            throw GradleException(
                                    String.format(
                                            "Expected json generation to create '%s' but it didn't",
                                            abi.jsonFile
                                    )
                            )
                        }

                        variantBuilder?.let {
                            synchronized(it) {
                                // Related to https://issuetracker.google.com/69408798
                                // Targets may have been removed or there could be other orphaned extra
                                // .so files. Remove these and rely on the build step to replace them
                                // if they are legitimate. This is to prevent unexpected .so files from
                                // being packaged in the APK.
                                time("remove-unexpected-so-files") {
                                    removeUnexpectedSoFiles(
                                            abi.soFolder,
                                            AndroidBuildGradleJsons.getNativeBuildMiniConfig(
                                                    abi, it
                                            )
                                    )
                                }
                            }
                        }

                        // Write the ProcessInfo to a file, this has all the flags used to generate the
                        // JSON. If any of these change later the JSON will be regenerated.
                        infoln("write command file %s", abi.metadataGenerationCommandFile.absolutePath)
                        abi.metadataGenerationCommandFile.parentFile.mkdirs()
                        Files.write(
                            abi.metadataGenerationCommandFile.toPath(),
                            currentBuildCommand.toByteArray(Charsets.UTF_8)
                        )

                        // Persist the prefab configuration as well.
                        Files.write(
                                abi.prefabConfigFile.toPath(),
                                prefabState.toJsonString()
                                        .toByteArray(Charsets.UTF_8)
                        )

                        // Record the outcome. JSON was built.
                        variantStats.outcome = GenerationOutcome.SUCCESS_BUILT
                    } else {
                        infoln("JSON '%s' was up-to-date", abi.jsonFile)
                        variantStats.outcome = GenerationOutcome.SUCCESS_UP_TO_DATE
                    }
                    time("generate-extra-metadata-files") {
                        generateSymbolFolderIndexFile(abi)
                        generateBuildFilesIndex(abi, variantBuilder)
                    }
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

    private fun generateSymbolFolderIndexFile(abi : CxxAbiModel) {
        abi.symbolFolderIndexFile.parentFile.mkdirs()
        abi.symbolFolderIndexFile.writeText(
            abi.soFolder.absolutePath,
            StandardCharsets.UTF_8
        )
    }

    private fun generateBuildFilesIndex(abi : CxxAbiModel, variantBuilder: GradleBuildVariant.Builder?) {
        abi.buildFileIndexFile.parentFile.mkdirs()
        abi.buildFileIndexFile.writeText(
            AndroidBuildGradleJsons.getNativeBuildMiniConfig(
                abi,
                variantBuilder
            ).buildFiles.joinToString(System.lineSeparator()),
            StandardCharsets.UTF_8
        )
    }

    abstract fun getProcessBuilder(abi: CxxAbiModel): ProcessInfoBuilder

    /**
     * Executes the JSON generation process. Return the combination of STDIO and STDERR from running
     * the process.
     */
    abstract fun executeProcess(ops: ExecOperations, abi: CxxAbiModel)

    companion object {
        /**
         * Returns true if platform is windows
         */
        @JvmStatic
        protected val isWindows: Boolean
            get() = SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS

        @Throws(IOException::class)
        private fun getFileContent(commandFile: File): String {
            return if (!commandFile.exists()) {
                ""
            } else String(
                Files.readAllBytes(commandFile.toPath()),
                Charsets.UTF_8
            )
        }

        @Throws(IOException::class)
        private fun getPreviousPrefabConfigurationState(
            prefabStateFile: File
        ): PrefabConfigurationState {
            return if (!prefabStateFile.exists()) {
                PrefabConfigurationState(false, null, emptyList())
            } else fromJson(
                String(
                    Files.readAllBytes(prefabStateFile.toPath()),
                    Charsets.UTF_8
                )
            )
        }

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
