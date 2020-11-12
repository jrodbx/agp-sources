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
import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.cxx.configure.JsonGenerationInvalidationState
import com.android.build.gradle.internal.cxx.configure.isCmakeForkVersion
import com.android.build.gradle.internal.cxx.configure.registerWriteModelAfterJsonGeneration
import com.android.build.gradle.internal.cxx.gradle.generator.ExternalNativeJsonGenerator
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValueMini
import com.android.build.gradle.internal.cxx.logging.IssueReporterLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.PassThroughPrefixingLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.logging.toJsonString
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxBuildModel
import com.android.build.gradle.internal.cxx.model.CxxModuleModel
import com.android.build.gradle.internal.cxx.model.CxxVariantModel
import com.android.build.gradle.internal.cxx.model.PrefabConfigurationState
import com.android.build.gradle.internal.cxx.model.PrefabConfigurationState.Companion.fromJson
import com.android.build.gradle.internal.cxx.model.buildCommandFile
import com.android.build.gradle.internal.cxx.model.buildOutputFile
import com.android.build.gradle.internal.cxx.model.createCxxAbiModel
import com.android.build.gradle.internal.cxx.model.createCxxVariantModel
import com.android.build.gradle.internal.cxx.model.getCxxBuildModel
import com.android.build.gradle.internal.cxx.model.jsonFile
import com.android.build.gradle.internal.cxx.model.jsonGenerationLoggingRecordFile
import com.android.build.gradle.internal.cxx.model.prefabConfigFile
import com.android.build.gradle.internal.cxx.model.shouldGeneratePrefabPackages
import com.android.build.gradle.internal.cxx.model.soFolder
import com.android.build.gradle.internal.cxx.services.executeListenersOnceAfterJsonGeneration
import com.android.build.gradle.internal.cxx.services.executeListenersOnceBeforeJsonGeneration
import com.android.build.gradle.internal.cxx.services.issueReporter
import com.android.build.gradle.internal.cxx.services.jsonGenerationInputDependencyFileCollection
import com.android.build.gradle.internal.cxx.settings.getBuildCommandArguments
import com.android.build.gradle.internal.cxx.settings.rewriteCxxAbiModelWithCMakeSettings
import com.android.build.gradle.internal.profile.AnalyticsUtil
import com.android.builder.profile.ProcessProfileWriter
import com.android.ide.common.process.ProcessException
import com.android.ide.common.process.ProcessInfoBuilder
import com.android.utils.FileUtils
import com.google.common.base.Charsets
import com.google.common.collect.Lists
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import com.google.wireless.android.sdk.stats.GradleBuildVariant.NativeBuildConfigInfo
import com.google.wireless.android.sdk.stats.GradleBuildVariant.NativeBuildConfigInfo.GenerationOutcome
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.gradle.process.JavaExecSpec
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayList
import java.util.Objects
import java.util.concurrent.Callable

/**
 * Base class for generation of native JSON.
 */
abstract class ExternalNativeJsonGeneratorBase internal constructor(
    @get:Internal protected val build: CxxBuildModel,
    @get:Internal("Temporary to suppress Gradle warnings (bug 135900510), may need more investigation")
    override val variant: CxxVariantModel,
    @get:Internal override val abis: List<CxxAbiModel>,
    @get:Internal override val stats: GradleBuildVariant.Builder
) : ExternalNativeJsonGenerator {
    @Throws(IOException::class)
    private fun getDependentBuildFiles(json: File): List<File> {
        val result: MutableList<File> =
            Lists.newArrayList()
        if (!json.exists()) {
            return result
        }

        // Now check whether the JSON is out-of-date with respect to the build files it declares.
        val config =
            AndroidBuildGradleJsons.getNativeBuildMiniConfig(json, stats)

        // If anything in the prefab package changes, re-run. Note that this also depends on the
        // directories, so added/removed files will also trigger a re-run.
        for (pkgDir in variant.prefabPackageDirectoryList) {
            Files.walk(pkgDir.toPath())
                .forEach {
                    result.add(it.toFile())
                }
        }
        result.addAll(config.buildFiles)
        return result
    }

    override fun build(
        forceJsonGeneration: Boolean,
        execOperation: (Action<in ExecSpec?>) -> ExecResult,
        javaExecOperation: (Action<in JavaExecSpec?>) -> ExecResult
    ) {
        try {
            infoln("building json with force flag %s", forceJsonGeneration)
            buildAndPropagateException(forceJsonGeneration, execOperation, javaExecOperation)
        } catch (e: IOException) {
            errorln("exception while building Json $%s", e.message!!)
        } catch (e: GradleException) {
            errorln("exception while building Json $%s", e.message!!)
        } catch (e: ProcessException) {
            errorln(
                "executing external native build for %s %s",
                nativeBuildSystem.tag, variant.module.makeFile
            )
        }
    }

    override fun parallelBuild(
        forceJsonGeneration: Boolean,
        execOperation: (Action<in ExecSpec?>) -> ExecResult,
        javaExecOperation: (Action<in JavaExecSpec?>) -> ExecResult
    ): List<Callable<Void?>> {
        val buildSteps: MutableList<Callable<Void?>> =
            ArrayList(abis.size)
        // These are lazily initialized values that can only be computed from a Gradle managed
        // thread. Compute now so that we don't in the worker threads that we'll be running as.
        variant.prefabPackageDirectoryList
        variant.prefabClassPath
        for (abi in abis) {
            buildSteps.add(
                Callable {
                    buildForOneConfigurationConvertExceptions(
                        forceJsonGeneration, abi, execOperation, javaExecOperation
                    )
                }
            )
        }
        return buildSteps
    }

    private fun buildForOneConfigurationConvertExceptions(
        forceJsonGeneration: Boolean,
        abi: CxxAbiModel,
        execOperation: (Action<in ExecSpec?>) -> ExecResult,
        javaExecOperation: (Action<in JavaExecSpec?>) -> ExecResult
    ): Void? {
        IssueReporterLoggingEnvironment(abi.variant.module.issueReporter()).use {
            try {
                buildForOneConfiguration(
                    forceJsonGeneration, abi, execOperation, javaExecOperation
                )
            } catch (e: IOException) {
                errorln("exception while building Json %s", e.message!!)
            } catch (e: GradleException) {
                errorln("exception while building Json %s", e.message!!)
            } catch (e: ProcessException) {
                errorln(
                    "executing external native build for %s %s",
                    nativeBuildSystem.tag, variant.module.makeFile
                )
            }
            return null
        }
    }

    protected open fun checkPrefabConfig() {}

    @Throws(IOException::class, ProcessException::class)
    private fun buildAndPropagateException(
        forceJsonGeneration: Boolean,
        execOperation: (Action<in ExecSpec?>) -> ExecResult,
        javaExecOperation: (Action<in JavaExecSpec?>) -> ExecResult
    ) {
        var firstException: Exception? = null
        for (abi in abis) {
            try {
                buildForOneConfiguration(
                    forceJsonGeneration, abi, execOperation, javaExecOperation
                )
            } catch (e: GradleException) {
                if (firstException == null) {
                    firstException = e
                }
            } catch (e: IOException) {
                if (firstException == null) {
                    firstException = e
                }
            } catch (e: ProcessException) {
                if (firstException == null) {
                    firstException = e
                }
            }
        }
        if (firstException != null) {
            if (firstException is GradleException) {
                throw (firstException as GradleException?)!!
            }
            if (firstException is IOException) {
                throw (firstException as IOException?)!!
            }
            throw (firstException as ProcessException?)!!
        }
    }

    override fun buildForOneAbiName(
        forceJsonGeneration: Boolean,
        abiName: String,
        execOperation: (Action<in ExecSpec?>) -> ExecResult,
        javaExecOperation: (Action<in JavaExecSpec?>) -> ExecResult
    ) {
        var built = 0
        for (abi in abis) {
            if (abi.abi.tag != abiName) {
                continue
            }
            built++
            buildForOneConfigurationConvertExceptions(
                forceJsonGeneration, abi, execOperation, javaExecOperation
            )
        }
        assert(built == 1)
    }

    @Throws(GradleException::class, IOException::class, ProcessException::class)
    private fun buildForOneConfiguration(
        forceJsonGeneration: Boolean,
        abi: CxxAbiModel,
        execOperation: (Action<in ExecSpec?>) -> ExecResult,
        javaExecOperation: (Action<in JavaExecSpec?>) -> ExecResult
    ) {
        PassThroughPrefixingLoggingEnvironment(
            abi.variant.module.makeFile,
            abi.variant.variantName + "|" + abi.abi.tag
        ).use { recorder ->
            val variantStats =
                NativeBuildConfigInfo.newBuilder()
            variantStats.abi = AnalyticsUtil.getAbi(abi.abi.tag)
            variantStats.debuggable = variant.isDebuggableEnabled
            val startTime = System.currentTimeMillis()
            variantStats.generationStartMs = startTime
            try {
                infoln(
                    "Start JSON generation. Platform version: %s min SDK version: %s",
                    abi.abiPlatformVersion,
                    abi.abi.tag,
                    abi.abiPlatformVersion
                )
                if (!build.executeListenersOnceBeforeJsonGeneration()) {
                    infoln("Errors seen in validation before JSON generation started")
                    return
                }
                val processBuilder = getProcessBuilder(abi)

                // See whether the current build command matches a previously written build command.
                val currentBuildCommand = """
                ${processBuilder}Build command args:${abi.getBuildCommandArguments()}

                """.trimIndent()
                val prefabState = PrefabConfigurationState(
                    abi.variant.module.project.isPrefabEnabled,
                    abi.variant.prefabClassPath,
                    variant.prefabPackageDirectoryList
                )
                val previousPrefabState =
                    getPreviousPrefabConfigurationState(abi.prefabConfigFile)
                val invalidationState =
                    JsonGenerationInvalidationState(
                        forceJsonGeneration,
                        abi.jsonFile,
                        abi.buildCommandFile,
                        currentBuildCommand,
                        getPreviousBuildCommand(abi.buildCommandFile),
                        getDependentBuildFiles(abi.jsonFile),
                        prefabState,
                        previousPrefabState
                    )
                if (invalidationState.rebuild) {
                    infoln("rebuilding JSON %s due to:", abi.jsonFile)
                    for (reason in invalidationState.rebuildReasons) {
                        infoln(reason)
                    }
                    if (abi.shouldGeneratePrefabPackages()) {
                        checkPrefabConfig()
                        generatePrefabPackages(
                            variant.module,
                            abi,
                            variant.prefabPackageDirectoryList,
                            javaExecOperation
                        )
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
                    infoln("executing %s %s", nativeBuildSystem.tag, processBuilder)
                    val buildOutput = executeProcess(abi, execOperation)
                    infoln("done executing %s", nativeBuildSystem.tag)

                    // Write the captured process output to a file for diagnostic purposes.
                    infoln("write build output %s", abi.buildOutputFile.absolutePath)
                    Files.write(
                        abi.buildOutputFile.toPath(),
                        buildOutput.toByteArray(Charsets.UTF_8)
                    )
                    processBuildOutput(buildOutput, abi)
                    if (!abi.jsonFile.exists()) {
                        throw GradleException(
                            String.format(
                                "Expected json generation to create '%s' but it didn't",
                                abi.jsonFile
                            )
                        )
                    }
                    synchronized(stats) {
                        // Related to https://issuetracker.google.com/69408798
                        // Targets may have been removed or there could be other orphaned extra .so
                        // files. Remove these and rely on the build step to replace them if they are
                        // legitimate. This is to prevent unexpected .so files from being packaged in
                        // the APK.
                        removeUnexpectedSoFiles(
                            abi.soFolder,
                            AndroidBuildGradleJsons.getNativeBuildMiniConfig(
                                abi.jsonFile, stats
                            )
                        )
                    }

                    // Write the ProcessInfo to a file, this has all the flags used to generate the
                    // JSON. If any of these change later the JSON will be regenerated.
                    infoln("write command file %s", abi.buildCommandFile.absolutePath)
                    Files.write(
                        abi.buildCommandFile.toPath(),
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
                synchronized(stats) { stats.addNativeBuildConfig(variantStats) }
                abi.jsonGenerationLoggingRecordFile.parentFile.mkdirs()
                Files.write(
                    abi.jsonGenerationLoggingRecordFile.toPath(),
                    recorder.record.toJsonString()
                        .toByteArray(Charsets.UTF_8)
                )
                abi.executeListenersOnceAfterJsonGeneration()
            }
        }
    }

    /**
     * Derived class implements this method to post-process build output. NdkPlatform-build uses
     * this to capture and analyze the compile and link commands that were written to stdout.
     */
    @Throws(IOException::class)
    abstract fun processBuildOutput(buildOutput: String, abiConfig: CxxAbiModel)
    abstract fun getProcessBuilder(abi: CxxAbiModel): ProcessInfoBuilder

    /**
     * Executes the JSON generation process. Return the combination of STDIO and STDERR from running
     * the process.
     *
     * @return Returns the combination of STDIO and STDERR from running the process.
     */
    @Throws(ProcessException::class, IOException::class)
    abstract fun executeProcess(
        abi: CxxAbiModel,
        execOperation: (Action<in ExecSpec?>) -> ExecResult
    ): String

    @Throws(IOException::class)
    override fun forEachNativeBuildConfiguration(callback: (JsonReader) -> Unit) {
        IssueReporterLoggingEnvironment(variant.module.issueReporter()).use {
            val files = nativeBuildConfigurationsJsons
            infoln("streaming %s JSON files", files.size)
            for (file in nativeBuildConfigurationsJsons) {
                if (file.exists()) {
                    infoln("string JSON file %s", file.absolutePath)
                    try {
                        JsonReader(FileReader(file))
                            .use { reader -> callback(reader) }
                    } catch (e: Throwable) {
                        infoln(
                            "Error parsing: %s",
                            java.lang.String.join(
                                "\r\n",
                                Files.readAllLines(file.toPath())
                            )
                        )
                        throw e
                    }
                } else {
                    // If the tool didn't create the JSON file then create fallback with the
                    // information we have so the user can see partial information in the UI.
                    infoln("streaming fallback JSON for %s", file.absolutePath)
                    val fallback = NativeBuildConfigValueMini()
                    fallback.buildFiles =
                        Lists.newArrayList(variant.module.makeFile)
                    JsonReader(
                        StringReader(
                            Gson().toJson(fallback)
                        )
                    ).use { reader -> callback(reader) }
                }
            }
        }
    }

    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    @get:InputFile
    val makefile: File
        get() = variant.module.makeFile

    // We don't need contents of the files in the generated JSON, just the path.
    @get:Input
    override val objFolder: String
        get() = variant.objFolder.path

    // We don't need contents of the files in the generated JSON, just the path.
    @get:Input
    val ndkFolder: String
        get() = variant.module.ndkFolder.path

    @get:Input
    val isDebuggable: Boolean
        get() = variant.isDebuggableEnabled

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    val jsonGenerationDependencyFiles: FileCollection
        get() = variant.module.jsonGenerationInputDependencyFileCollection(abis)

    @get:Input
    @get:Optional
    val buildArguments: List<String>
        get() = variant.buildSystemArgumentList

    @Optional
    @Input
    fun getcFlags(): List<String> {
        return variant.cFlagsList
    }

    @get:Input
    @get:Optional
    val cppFlags: List<String>
        get() = variant.cppFlagsList

    @get:OutputFiles
    override val nativeBuildConfigurationsJsons: List<File>
        get() {
            val generatedJsonFiles: MutableList<File> =
                ArrayList()
            for (abi in abis) {
                generatedJsonFiles.add(abi.jsonFile)
            }
            return generatedJsonFiles
        }

    // We don't need contents of the files in the generated JSON, just the path.
    @get:Input
    override val soFolder: String
        get() = variant.soFolder.path

    // We don't need contents of the files in the generated JSON, just the path.
    @get:Input
    val sdkFolder: String
        get() = variant.module.project.sdkFolder.path

    companion object {
        /**
         * Returns true if platform is windows
         */
        @JvmStatic
        protected val isWindows: Boolean
            get() = SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS

        @Throws(IOException::class)
        private fun getPreviousBuildCommand(commandFile: File): String {
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

        @JvmStatic
        fun create(
            module: CxxModuleModel, componentProperties: ComponentPropertiesImpl
        ): ExternalNativeJsonGenerator {
            IssueReporterLoggingEnvironment(module.issueReporter()).use { ignore ->
                return createImpl(
                    module,
                    componentProperties
                )
            }
        }

        private fun createImpl(
            module: CxxModuleModel, componentProperties: ComponentPropertiesImpl
        ): ExternalNativeJsonGenerator {
            val variant = createCxxVariantModel(module, componentProperties)
            val abis: MutableList<CxxAbiModel> =
                Lists.newArrayList()
            val cxxBuildModel =
                getCxxBuildModel(componentProperties.globalScope.project.gradle)
            for (abi in variant.validAbiList) {
                val model = createCxxAbiModel(
                    variant,
                    abi,
                    componentProperties.globalScope,
                    componentProperties
                ).rewriteCxxAbiModelWithCMakeSettings()
                abis.add(model)

                // Register callback to write Json after generation finishes.
                // We don't write it now because sync configuration is executing. We want to defer
                // until model building.
                registerWriteModelAfterJsonGeneration(model)
            }
            val stats = ProcessProfileWriter.getOrCreateVariant(
                module.gradleModulePathName, componentProperties.name
            )
            return when (module.buildSystem) {
                NativeBuildSystem.NDK_BUILD -> NdkBuildExternalNativeJsonGenerator(
                    cxxBuildModel,
                    variant,
                    abis,
                    stats
                )
                NativeBuildSystem.CMAKE -> {
                    val cmake =
                        Objects.requireNonNull(variant.module.cmake)!!
                    val cmakeRevision = cmake.minimumCmakeVersion
                    stats.nativeCmakeVersion = cmakeRevision.toString()
                    if (cmakeRevision.isCmakeForkVersion()) {
                        return CmakeAndroidNinjaExternalNativeJsonGenerator(
                            cxxBuildModel, variant, abis, stats
                        )
                    }
                    if (cmakeRevision.major < 3
                        || cmakeRevision.major == 3 && cmakeRevision.minor <= 6
                    ) {
                        throw RuntimeException(
                            "Unexpected/unsupported CMake version "
                                    + cmakeRevision.toString()
                                    + ". Try 3.7.0 or later."
                        )
                    }
                    CmakeServerExternalNativeJsonGenerator(
                        cxxBuildModel, variant, abis, stats
                    )
                }
            }
        }
    }

}