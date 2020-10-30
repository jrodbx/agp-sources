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
package com.android.build.gradle.tasks

import com.android.build.gradle.external.cmake.CmakeUtils
import com.android.build.gradle.external.cmake.server.ComputeResult
import com.android.build.gradle.external.cmake.server.ConfigureCommandResult
import com.android.build.gradle.external.cmake.server.FileGroup
import com.android.build.gradle.external.cmake.server.HandshakeRequest
import com.android.build.gradle.external.cmake.server.ProtocolVersion
import com.android.build.gradle.external.cmake.server.Server
import com.android.build.gradle.external.cmake.server.ServerFactory
import com.android.build.gradle.external.cmake.server.ServerUtils
import com.android.build.gradle.external.cmake.server.Target
import com.android.build.gradle.external.cmake.server.receiver.InteractiveMessage
import com.android.build.gradle.external.cmake.server.receiver.ServerReceiver
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.cxx.cmake.makeCmakeMessagePathsAbsolute
import com.android.build.gradle.internal.cxx.cmake.parseLinkLibraries
import com.android.build.gradle.internal.cxx.configure.convertCmakeCommandLineArgumentsToStringList
import com.android.build.gradle.internal.cxx.configure.getBuildRootFolder
import com.android.build.gradle.internal.cxx.configure.getGenerator
import com.android.build.gradle.internal.cxx.configure.hasCmakeHeaderFileExtensions
import com.android.build.gradle.internal.cxx.configure.onlyKeepServerArguments
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValue
import com.android.build.gradle.internal.cxx.json.NativeHeaderFileValue
import com.android.build.gradle.internal.cxx.json.NativeLibraryValue
import com.android.build.gradle.internal.cxx.json.NativeSourceFileValue
import com.android.build.gradle.internal.cxx.json.NativeToolchainValue
import com.android.build.gradle.internal.cxx.json.StringTable
import com.android.build.gradle.internal.cxx.json.indexCompilationDatabase
import com.android.build.gradle.internal.cxx.json.populateCompilationDatabaseToolchains
import com.android.build.gradle.internal.cxx.logging.PassThroughPrintWriterLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.logging.warnln
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxBuildModel
import com.android.build.gradle.internal.cxx.model.CxxVariantModel
import com.android.build.gradle.internal.cxx.model.compileCommandsJsonFile
import com.android.build.gradle.internal.cxx.model.jsonFile
import com.android.build.gradle.internal.cxx.settings.getBuildCommandArguments
import com.android.build.gradle.internal.cxx.settings.getFinalCmakeCommandLineArguments
import com.android.ide.common.process.ProcessException
import com.android.utils.ILogger
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Strings
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.common.primitives.UnsignedInts
import com.google.gson.stream.JsonReader
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.Action
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.IOException
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Paths
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap

/**
 * This strategy uses the Vanilla-CMake that supports Cmake server version 1.0 to configure the
 * project and generate the android build JSON.
 */
internal class CmakeServerExternalNativeJsonGenerator(
    build: CxxBuildModel,
    variant: CxxVariantModel,
    abis: List<CxxAbiModel>,
    stats: GradleBuildVariant.Builder
) : CmakeExternalNativeJsonGenerator(build, variant, abis, stats) {
    @Throws(ProcessException::class, IOException::class)
    override fun executeProcessAndGetOutput(
        abi: CxxAbiModel,
        execOperations: (Action<in ExecSpec?>) -> ExecResult
    ): String {
        // Once a Cmake server object is created
        // - connect to the server
        // - perform a handshake
        // - configure and compute.
        // Create the NativeBuildConfigValue and write the required JSON file.
        val cmakeServerLogFile = abi.cmake!!.cmakeServerLogFile.absoluteFile
        cmakeServerLogFile.parentFile.mkdirs()
        PassThroughPrintWriterLoggingEnvironment(
            PrintWriter(cmakeServerLogFile, "UTF-8"),
            CMAKE_SERVER_LOG_PREFIX
        ).use {
            // Create a new cmake server for the given Cmake and configure the given project.
            val serverReceiver = ServerReceiver()
                .setMessageReceiver { message: InteractiveMessage ->
                    logInteractiveMessage(
                        message, makefile.parentFile
                    )
                }
                .setDiagnosticReceiver { message: String? ->
                    infoln(
                        message!!
                    )
                }
            val cmakeBinFolder = cmake.cmakeExe.parentFile
            val cmakeServer =
                ServerFactory.create(cmakeBinFolder, serverReceiver)
            if (cmakeServer == null) {
                val actual = CmakeUtils.getVersion(cmakeBinFolder)
                throw RuntimeException(
                    String.format(
                        "Actual CMake version '%s.%s.%s' did not satisfy requested minimum or default "
                                + "CMake minimum version '%s'. Possibly cmake.dir doesn't match "
                                + "android.externalNativeBuild.cmake.version.",
                        actual.major,
                        actual.minor,
                        actual.micro,
                        cmake.minimumCmakeVersion
                    )
                )
            }
            if (!cmakeServer.connect()) {
                throw RuntimeException(
                    "Unable to connect to Cmake server located at: "
                            + cmakeBinFolder.absolutePath
                )
            }
            return try {
                val arguments =
                    abi.getFinalCmakeCommandLineArguments()
                val cacheArgumentsList =
                    arguments.onlyKeepServerArguments()
                        .convertCmakeCommandLineArgumentsToStringList()
                val configureCommandResult: ConfigureCommandResult

                // Handshake
                doHandshake(
                    arguments.getGenerator()!!,
                    variant.module.makeFile.parentFile,
                    File(arguments.getBuildRootFolder()!!),
                    cmakeServer
                )

                // Configure
                val argsArray =
                    cacheArgumentsList.toTypedArray()
                configureCommandResult = cmakeServer.configure(*argsArray)
                if (!ServerUtils.isConfigureResultValid(configureCommandResult.configureResult)) {
                    throw ProcessException(
                        String.format(
                            "Error configuring CMake server (%s).\r\n%s",
                            cmakeServer.cmakePath,
                            configureCommandResult.interactiveMessages
                        )
                    )
                }
                val computeResult =
                    doCompute(cmakeServer)
                if (!ServerUtils.isComputedResultValid(computeResult)) {
                    throw ProcessException(
                        """
                    Error computing CMake server result.
                    ${configureCommandResult.interactiveMessages}
                    """.trimIndent()
                    )
                }
                generateAndroidGradleBuild(abi, cmakeServer)
                configureCommandResult.interactiveMessages
            } finally {
                cmakeServer.disconnect()
            }
        }
    }

    /**
     * Requests a handshake to a connected Cmake server.
     *
     * @throws IOException I/O failure. Note: The function throws RuntimeException if we receive an
     * invalid/erroneous handshake result.
     */
    @Throws(IOException::class)
    private fun doHandshake(
        generator: String,
        sourceDirectory: File,
        buildDirectory: File,
        cmakeServer: Server
    ) {
        val supportedProtocolVersions =
            cmakeServer.supportedVersion
        if (supportedProtocolVersions == null || supportedProtocolVersions.isEmpty()) {
            throw RuntimeException(
                String.format(
                    "Gradle does not support the Cmake server version. %s",
                    getCmakeInfoString(cmakeServer)
                )
            )
        }
        val handshakeResult = cmakeServer.handshake(
            getHandshakeRequest(
                generator,
                sourceDirectory,
                buildDirectory,
                supportedProtocolVersions[0]
            )
        )
        if (!ServerUtils.isHandshakeResultValid(handshakeResult)) {
            throw RuntimeException(
                String.format(
                    "Invalid handshake result from Cmake server: \n%s\n%s",
                    CmakeUtils.getObjectToString(handshakeResult),
                    getCmakeInfoString(cmakeServer)
                )
            )
        }
    }

    /**
     * Create a default handshake request for the given Cmake server-protocol version
     *
     * @return handshake request
     */
    private fun getHandshakeRequest(
        generator: String,
        sourceDirectory: File,
        buildDirectory: File,
        cmakeServerProtocolVersion: ProtocolVersion
    ): HandshakeRequest {
        if (!sourceDirectory.isDirectory) {
            errorln("Not a directory: %s", sourceDirectory)
        }
        val handshakeRequest = HandshakeRequest()
        handshakeRequest.cookie = "gradle-cmake-cookie"
        handshakeRequest.generator = generator
        handshakeRequest.protocolVersion = cmakeServerProtocolVersion
        handshakeRequest.buildDirectory =
            normalizeFilePath(buildDirectory)
        handshakeRequest.sourceDirectory =
            normalizeFilePath(sourceDirectory)
        return handshakeRequest
    }

    /**
     * Generates nativeBuildConfigValue by generating the code model from the cmake server and
     * writes the android_gradle_build.json.
     *
     * @throws IOException I/O failure
     */
    @Throws(IOException::class)
    private fun generateAndroidGradleBuild(
        config: CxxAbiModel, cmakeServer: Server
    ) {
        val nativeBuildConfigValue =
            getNativeBuildConfigValue(config, cmakeServer)
        AndroidBuildGradleJsons.writeNativeBuildConfigValueToJsonFile(
            config.jsonFile, nativeBuildConfigValue
        )
    }

    /**
     * Returns NativeBuildConfigValue for the given abi from the given Cmake server.
     *
     * @return returns NativeBuildConfigValue
     * @throws IOException I/O failure
     */
    @VisibleForTesting
    @Throws(IOException::class)
    private fun getNativeBuildConfigValue(
        abi: CxxAbiModel, cmakeServer: Server
    ): NativeBuildConfigValue {
        val nativeBuildConfigValue =
            createDefaultNativeBuildConfigValue()
        assert(nativeBuildConfigValue.stringTable != null)
        val strings =
            StringTable(nativeBuildConfigValue.stringTable!!)
        assert(nativeBuildConfigValue.buildFiles != null)
        nativeBuildConfigValue.buildFiles!!.addAll(getBuildFiles(abi, cmakeServer))
        assert(nativeBuildConfigValue.cleanCommands != null)
        nativeBuildConfigValue.cleanCommands!!.add(
            CmakeUtils.getCleanCommand(cmake.cmakeExe, abi.cxxBuildFolder)
        )
        assert(nativeBuildConfigValue.buildTargetsCommand != null)
        nativeBuildConfigValue.buildTargetsCommand = CmakeUtils.getBuildTargetsCommand(
            cmake.cmakeExe,
            abi.cxxBuildFolder,
            abi.getBuildCommandArguments()
        )
        val codeModel = cmakeServer.codemodel()
        if (!ServerUtils.isCodeModelValid(codeModel)) {
            throw RuntimeException(
                String.format(
                    "Invalid code model received from Cmake server: \n%s\n%s",
                    CmakeUtils.getObjectToString(codeModel),
                    getCmakeInfoString(cmakeServer)
                )
            )
        }
        assert(nativeBuildConfigValue.cFileExtensions != null)
        nativeBuildConfigValue.cFileExtensions!!.addAll(CmakeUtils.getCExtensionSet(codeModel))
        assert(nativeBuildConfigValue.cppFileExtensions != null)
        nativeBuildConfigValue.cppFileExtensions!!.addAll(CmakeUtils.getCppExtensionSet(codeModel))

        // toolchains
        nativeBuildConfigValue.toolchains =
            getNativeToolchains(
                abi,
                cmakeServer,
                nativeBuildConfigValue.cppFileExtensions!!,
                nativeBuildConfigValue.cFileExtensions!!
            )
        val toolchainHashString =
            getOnlyToolchainName(
                nativeBuildConfigValue.toolchains!!
            )

        // Fill in the required fields in NativeBuildConfigValue from the code model obtained from
        // Cmake server.
        for (config in codeModel.configurations) {
            for (project in config.projects) {
                for (target in project.targets) {
                    // Ignore targets that aren't valid.
                    if (!canAddTargetToNativeLibrary(
                            target
                        )
                    ) {
                        continue
                    }
                    val nativeLibraryValue =
                        getNativeLibraryValue(abi, abi.cxxBuildFolder, target, strings)
                    nativeLibraryValue.toolchain = toolchainHashString
                    val libraryName =
                        target.name + "-" + config.name + "-" + abi.abi.tag
                    assert(nativeBuildConfigValue.libraries != null)
                    nativeBuildConfigValue.libraries!![libraryName] = nativeLibraryValue
                } // target
            } // project
        }
        return nativeBuildConfigValue
    }

    @VisibleForTesting
    @Throws(FileNotFoundException::class)
    private fun getNativeLibraryValue(
        abi: CxxAbiModel,
        workingDirectory: File,
        target: Target,
        strings: StringTable
    ): NativeLibraryValue {
        return getNativeLibraryValue(
            cmake.cmakeExe,
            abi.cxxBuildFolder,
            isDebuggable,
            JsonReader(FileReader(abi.cmake!!.compileCommandsJsonFile)),
            abi.abi.tag,
            workingDirectory,
            target,
            strings
        )
    }

    /**
     * Returns the list of build files used by CMake as part of the build system. Temporary files
     * are currently ignored.
     */
    @Throws(IOException::class)
    private fun getBuildFiles(
        config: CxxAbiModel,
        cmakeServer: Server
    ): List<File> {
        val cmakeInputsResult = cmakeServer.cmakeInputs()
        if (!ServerUtils.isCmakeInputsResultValid(cmakeInputsResult)) {
            throw RuntimeException(
                String.format(
                    "Invalid cmakeInputs result received from Cmake server: \n%s\n%s",
                    CmakeUtils.getObjectToString(cmakeInputsResult),
                    getCmakeInfoString(cmakeServer)
                )
            )
        }

        // Ideally we should see the build files within cmakeInputs response, but in the weird case
        // that we don't, return the default make file.
        if (cmakeInputsResult.buildFiles == null) {
            val buildFiles: MutableList<File> =
                Lists.newArrayList()
            buildFiles.add(makefile)
            return buildFiles
        }

        // The sources listed might be duplicated, so remove the duplicates.
        val buildSources: MutableSet<String> = mutableSetOf()
        for (buildFile in cmakeInputsResult.buildFiles) {
            if (buildFile.isTemporary || buildFile.isCMake || buildFile.sources == null) {
                continue
            }
            Collections.addAll(buildSources, *buildFile.sources)
        }

        // The path to the build file source might be relative, so use the absolute path using
        // source directory information.
        var sourceDirectory: File? = null
        if (cmakeInputsResult.sourceDirectory != null) {
            sourceDirectory = File(cmakeInputsResult.sourceDirectory)
        }
        val buildFiles: MutableList<File> =
            Lists.newArrayList()
        for (source in buildSources) {
            // The source file can either be relative or absolute, if it's relative, use the source
            // directory to get the absolute path.
            var sourceFile = File(source)
            if (!sourceFile.isAbsolute) {
                if (sourceDirectory != null) {
                    sourceFile = File(sourceDirectory, source).canonicalFile
                }
            }
            if (!sourceFile.exists()) {
                val logger: ILogger =
                    LoggerWrapper.getLogger(
                        CmakeServerExternalNativeJsonGenerator::class.java
                    )
                logger.error(
                    null,
                    "Build file "
                            + sourceFile
                            + " provided by CMake "
                            + "does not exists. This might lead to incorrect Android Studio behavior."
                )
                continue
            }
            if (sourceFile
                    .path
                    .startsWith(config.cmake!!.cmakeWrappingBaseFolder.path)
            ) {
                // Skip files in .cxx/cmake/x86
                continue
            }
            buildFiles.add(sourceFile)
        }
        return buildFiles
    }

    companion object {
        private const val CMAKE_SERVER_LOG_PREFIX = "CMAKE SERVER: "

        /**
         * @param toolchains - toolchains map
         * @return the hash of the only entry in the map, ideally the toolchains map should have only
         * one entry.
         */
        private fun getOnlyToolchainName(
            toolchains: Map<String, NativeToolchainValue>
        ): String? {
            if (toolchains.size != 1) {
                throw RuntimeException(
                    String.format(
                        "Invalid number %d of toolchains. Only one toolchain should be present.",
                        toolchains.size
                    )
                )
            }
            return toolchains.keys.iterator().next()
        }

        @Throws(IOException::class)
        private fun getCmakeInfoString(cmakeServer: Server): String {
            return String.format(
                "Cmake path: %s, version: %s",
                cmakeServer.cmakePath,
                CmakeUtils.getVersion(File(cmakeServer.cmakePath)).toString()
            )
        }

        /**
         * Logs info/warning/error for the given interactive message. Throws a RunTimeException in case
         * of an 'error' message type.
         */
        private fun logInteractiveMessage(
            message: InteractiveMessage, makeFileDirectory: File
        ) {
            // CMake error/warning prefix strings. The CMake errors and warnings are part of the
            // message type "message" even though CMake is reporting errors/warnings (Note: They could
            // have a title that says if it's an error or warning, we check that first before checking
            // the prefix of the message string). Hence we would need to parse the output message to
            // figure out if we need to log them as error or warning.
            val cmakeErrorPrefix = "CMake Error"
            val cmakeWarningPrefix = "CMake Warning"

            // If the final message received is of type error, log and error and throw an exception.
            // Note: This is not the same as a message with type "message" with error information, that
            // case is handled below.
            if (message.type != null && message.type == "error") {
                errorln(makeCmakeMessagePathsAbsolute(message.errorMessage, makeFileDirectory))
                return
            }
            val correctedMessage =
                makeCmakeMessagePathsAbsolute(message.message, makeFileDirectory)
            if (message.title != null && message.title == "Error"
                || message.message.startsWith(cmakeErrorPrefix)
            ) {
                errorln(correctedMessage)
                return
            }
            if (message.title != null && message.title == "Warning"
                || message.message.startsWith(cmakeWarningPrefix)
            ) {
                warnln(correctedMessage)
                return
            }
            infoln(correctedMessage)
        }

        /**
         * Generate build system files in the build directly, or compute the given project and returns
         * the computed result.
         *
         * @param cmakeServer Connected cmake server.
         * @throws IOException I/O failure. Note: The function throws RuntimeException if we receive an
         * invalid/erroneous ComputeResult.
         */
        @Throws(IOException::class)
        private fun doCompute(cmakeServer: Server): ComputeResult {
            return cmakeServer.compute()
        }

        private fun findRuntimeFiles(target: Target): List<File>? {
            if (target.linkLibraries == null || target.type == "OBJECT_LIBRARY") {
                return null
            }

            val sysroot = Paths.get(target.sysroot)
            val runtimeFiles = mutableListOf<File>()
            for (library in parseLinkLibraries(target.linkLibraries)) {
                // Each element here is just an argument to the linker. It might be a full path to a
                // library to be linked or a trivial -l flag. If it's a full path that exists within
                // the prefab directory, it's a library that's needed at runtime.
                if (library.startsWith("-")) {
                    continue
                }

                // We don't actually care about the normalization here except that it makes it
                // possible to write a test for https://issuetracker.google.com/158317988. Without
                // it, the runtimeFile is sometimes a path that includes .. that resolves to the
                // same place as the destination, but sometimes isn't (within bazel's sandbox it is,
                // outside it isn't, could be related to the path lengths since CMake tries to keep
                // those short when possible). If the paths passed to Files.copy are equal the
                // operation will throw IllegalArgumentException, but only if they are exactly equal
                // (without normalization). Users were encountering this but it was being hidden
                // from tests because of the lack of normalization.
                val libraryPath = Paths.get(library).let {
                    if (!it.isAbsolute) {
                        Paths.get(target.buildDirectory).resolve(it)
                    } else {
                        it
                    }
                }.normalize()

                // Note: This used to contain a check for libraryPath.exists() to defend against any
                // items in the linkLibraries that were neither files nor - prefixed arguments. This
                // hasn't been observed and I'm not sure there are any valid inputs to
                // target_link_libraries that would have that problem.
                //
                // Ignoring files that didn't exist was causing different results depending on
                // whether this function was being run before or after a build. If run before a
                // build, any libraries the user is building will not be present yet and would not
                // be added to runtimeFiles. After a build they would. We no longer skip non-present
                // files for the sake of consistency.

                // Anything under the sysroot shouldn't be included in the APK. This isn't strictly
                // true since the STLs live here, but those are handled separately by
                // ExternalNativeBuildTask::buildImpl.
                if (libraryPath.startsWith(sysroot)) {
                    continue
                }

                // We could alternatively filter for .so files rather than filtering out .a files,
                // but it's possible that the user has things like libfoo.so.1. It's not common for
                // Android, but possible.
                val pathMatcher = libraryPath.fileSystem.getPathMatcher("glob:*.a")
                if (pathMatcher.matches(libraryPath.fileName)) {
                    continue
                }

                runtimeFiles.add(libraryPath.toFile())
            }
            return runtimeFiles
        }

        @VisibleForTesting
        fun getNativeLibraryValue(
            cmakeExecutable: File,
            outputFolder: File,
            isDebuggable: Boolean,
            compileCommandsJson: JsonReader,
            abi: String,
            workingDirectory: File,
            target: Target,
            strings: StringTable
        ): NativeLibraryValue {
            val nativeLibraryValue = NativeLibraryValue()
            nativeLibraryValue.abi = abi
            nativeLibraryValue.buildCommand =
                CmakeUtils.getBuildCommand(cmakeExecutable, outputFolder, target.name)
            nativeLibraryValue.artifactName = target.name
            nativeLibraryValue.buildType = if (isDebuggable) "debug" else "release"
            // If the target type is an OBJECT_LIBRARY, it does not build any output that we care.
            if ("OBJECT_LIBRARY" != target.type // artifacts of an object library could be null. See example in b/144938511
                && target.artifacts != null && target.artifacts.isNotEmpty()
            ) {
                // We'll have only one output, so get the first one.
                nativeLibraryValue.output = File(target.artifacts[0])
            }
            nativeLibraryValue.runtimeFiles = findRuntimeFiles(target)

            // Maps each source file to the index of the corresponding strings table entry, which
            // contains the build flags for that source file.
            // It is important to not use a File or Path as the key to the dictionary, but instead
            // use the corresponding normalized path. Two File/Path objects with the same normalized
            // string representation may not be equivalent due to "../" or "./" substrings in them
            // (b/123123307).
            var compilationDatabaseFlags: Map<String, Int> = Maps.newHashMap()
            val workingDirectoryOrdinal = strings.intern(
                normalizeFilePath(workingDirectory)
            )
            val files = mutableListOf<NativeSourceFileValue>()
            val headers = mutableListOf<NativeHeaderFileValue>()
            for (fileGroup in target.fileGroups) {
                for (source in fileGroup.sources) {
                    // Skip object files in sources
                    if (source.endsWith(".o")) continue
                    // CMake returns an absolute path or a path relative to the source directory,
                    // whichever one is shorter.
                    var sourceFilePath = Paths.get(source)
                    if (!sourceFilePath.isAbsolute) {
                        sourceFilePath = Paths.get(target.sourceDirectory, source)
                    }

                    // Even if CMake returns an absolute path, we still call normalize() to be symmetric
                    // with indexCompilationDatabase() which always uses normalized paths.
                    val normalizedSourceFilePath = sourceFilePath.normalize()
                    if (normalizedSourceFilePath.toString().isNotEmpty()) {
                        sourceFilePath = normalizedSourceFilePath
                    }
                    // else {
                    // Normalized path should not be empty, unless CMake sends us really bogus data
                    // such as such as sourceDirectory="a/b", source="../../". This is not supposed
                    // to happen because (1) sourceDirectory should not be relative, and (2) source
                    // should contain at least a file name.
                    //
                    // Although it is very unlikely, this branch protects against that case by using
                    // the non-normalized path, which also makes the case more debuggable.
                    //
                    // Fall through intended.
                    // }
                    val sourceFile = sourceFilePath.toFile()
                    if (hasCmakeHeaderFileExtensions(sourceFile)) {
                        headers.add(
                            NativeHeaderFileValue(sourceFile, workingDirectoryOrdinal)
                        )
                    } else {
                        val nativeSourceFileValue = NativeSourceFileValue()
                        nativeSourceFileValue.workingDirectoryOrdinal = workingDirectoryOrdinal
                        nativeSourceFileValue.src = sourceFile

                        // We use flags from compile_commands.json if present. Otherwise, fall back
                        // to server model compile flags (which is known to not always return a
                        // complete set).
                        // Reference b/116237485
                        if (compilationDatabaseFlags.isEmpty()) {
                            compilationDatabaseFlags =
                                indexCompilationDatabase(compileCommandsJson, strings)
                        }
                        if (compilationDatabaseFlags.containsKey(sourceFilePath.toString())) {
                            nativeSourceFileValue.flagsOrdinal =
                                compilationDatabaseFlags[sourceFilePath.toString()]
                        } else {
                            // TODO I think this path is always wrong because it won't have --targets
                            // I don't want to make it an exception this late in 3.3 cycle so I'm
                            // leaving it as-is for now.
                            val compileFlags =
                                compileFlagsFromFileGroup(
                                    fileGroup
                                )
                            if (!Strings.isNullOrEmpty(compileFlags)) {
                                nativeSourceFileValue.flagsOrdinal = strings.intern(compileFlags)
                            }
                        }
                        files.add(nativeSourceFileValue)
                    }
                }
            }

            nativeLibraryValue.files = files
            nativeLibraryValue.headers = headers
            return nativeLibraryValue
        }

        private fun compileFlagsFromFileGroup(fileGroup: FileGroup): String {
            val flags = StringBuilder()
            flags.append(fileGroup.compileFlags)
            if (fileGroup.defines != null) {
                for (define in fileGroup.defines) {
                    flags.append(" -D").append(define)
                }
            }
            if (fileGroup.includePath != null) {
                for (includePath in fileGroup.includePath) {
                    if (includePath?.path == null) {
                        continue
                    }
                    if (includePath.isSystem != null && includePath.isSystem) {
                        flags.append(" -system ")
                    } else {
                        flags.append(" -I ")
                    }
                    flags.append(includePath.path)
                }
            }
            return flags.toString()
        }

        /**
         * Helper function that returns true if the Target object is valid to be added to native
         * library.
         */
        private fun canAddTargetToNativeLibrary(target: Target): Boolean {
            // If the target has artifact, then we need to build it.
            // If the target has files, then we need it for intellisense.
            return target.artifacts != null || target.fileGroups != null
        }

        /**
         * Creates a default NativeBuildConfigValue.
         *
         * @return a default NativeBuildConfigValue.
         */
        private fun createDefaultNativeBuildConfigValue(): NativeBuildConfigValue {
            val nativeBuildConfigValue = NativeBuildConfigValue()
            nativeBuildConfigValue.buildFiles = ArrayList()
            nativeBuildConfigValue.cleanCommands = ArrayList()
            nativeBuildConfigValue.libraries =
                HashMap()
            nativeBuildConfigValue.toolchains =
                HashMap()
            nativeBuildConfigValue.cFileExtensions = ArrayList()
            nativeBuildConfigValue.cppFileExtensions = ArrayList()
            nativeBuildConfigValue.stringTable =
                Maps.newHashMap()
            return nativeBuildConfigValue
        }

        /**
         * Returns the native toolchain for the given abi from the provided Cmake server. We ideally
         * should get the toolchain information compile commands JSON file. If it's unavailable, we
         * fallback to figuring this information out from the messages produced by Cmake server when
         * configuring the project (though hacky, it works!).
         *
         * @param abi - ABI for which NativeToolchainValue needs to be created
         * @param cmakeServer - Cmake server
         * @param cppExtensionSet - CXX extensions
         * @param cExtensionSet - C extensions
         * @return a map of toolchain hash to toolchain value. The map will have only one entry.
         */
        private fun getNativeToolchains(
            abi: CxxAbiModel,
            cmakeServer: Server,
            cppExtensionSet: Collection<String>,
            cExtensionSet: Collection<String>
        ): Map<String, NativeToolchainValue> {
            val toolchainValue = NativeToolchainValue()
            var cCompilerExecutable: File? = null
            var cppCompilerExecutable: File? = null
            val compilationDatabase = abi.cmake!!.compileCommandsJsonFile
            if (compilationDatabase.exists()) {
                val (cppCompilerExecutable1, cCompilerExecutable1) = populateCompilationDatabaseToolchains(
                    compilationDatabase, cppExtensionSet, cExtensionSet
                )
                cppCompilerExecutable = cppCompilerExecutable1
                cCompilerExecutable = cCompilerExecutable1
            } else {
                if (cmakeServer.cCompilerExecutable.isNotEmpty()) {
                    cCompilerExecutable = File(cmakeServer.cCompilerExecutable)
                }
                if (cmakeServer.cppCompilerExecutable.isNotEmpty()) {
                    cppCompilerExecutable = File(cmakeServer.cppCompilerExecutable)
                }
            }
            if (cCompilerExecutable != null) {
                toolchainValue.cCompilerExecutable = cCompilerExecutable
            }
            if (cppCompilerExecutable != null) {
                toolchainValue.cppCompilerExecutable = cppCompilerExecutable
            }
            val toolchainHash = CmakeUtils.getToolchainHash(toolchainValue)
            val toolchainHashString =
                UnsignedInts.toString(toolchainHash)
            val toolchains: MutableMap<String, NativeToolchainValue> =
                HashMap()
            toolchains[toolchainHashString] = toolchainValue
            return toolchains
        }

        /**
         * Returns the normalized path for the given file. The normalized path for Unix is the default
         * string returned by getPath. For Microsoft Windows, getPath returns a path with "\\" (example:
         * "C:\\Android\\Sdk") while Vanilla-CMake prefers a forward slash (example "C:/Android/Sdk"),
         * without the forward slash, CMake would mix backward slash and forward slash causing compiler
         * issues. This function replaces the backward slashes with forward slashes for Microsoft
         * Windows.
         */
        private fun normalizeFilePath(file: File): String {
            return if (isWindows) {
                file.path.replace("\\", "/")
            } else file.path
        }
    }
}