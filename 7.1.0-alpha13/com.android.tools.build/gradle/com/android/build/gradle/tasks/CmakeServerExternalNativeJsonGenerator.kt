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
import com.android.build.gradle.external.cmake.server.HandshakeRequest
import com.android.build.gradle.external.cmake.server.ProtocolVersion
import com.android.build.gradle.external.cmake.server.Server
import com.android.build.gradle.external.cmake.server.ServerFactory
import com.android.build.gradle.external.cmake.server.ServerUtils
import com.android.build.gradle.external.cmake.server.Target
import com.android.build.gradle.external.cmake.server.findRuntimeFiles
import com.android.build.gradle.external.cmake.server.receiver.InteractiveMessage
import com.android.build.gradle.external.cmake.server.receiver.ServerReceiver
import com.android.build.gradle.internal.cxx.cmake.makeCmakeMessagePathsAbsolute
import com.android.build.gradle.internal.cxx.cmake.parseLinkLibraries
import com.android.build.gradle.internal.cxx.configure.convertCMakeToCompileCommandsBin
import com.android.build.gradle.internal.cxx.configure.getCmakeBinaryOutputPath
import com.android.build.gradle.internal.cxx.configure.getCmakeGenerator
import com.android.build.gradle.internal.cxx.configure.onlyKeepCmakeServerArguments
import com.android.build.gradle.internal.cxx.configure.toCmakeArguments
import com.android.build.gradle.internal.cxx.configure.toStringList
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValue
import com.android.build.gradle.internal.cxx.json.NativeLibraryValue
import com.android.build.gradle.internal.cxx.logging.PassThroughPrintWriterLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.logging.warnln
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxVariantModel
import com.android.build.gradle.internal.cxx.model.additionalProjectFilesIndexFile
import com.android.build.gradle.internal.cxx.model.cmakeServerLogFile
import com.android.build.gradle.internal.cxx.model.compileCommandsJsonBinFile
import com.android.build.gradle.internal.cxx.model.compileCommandsJsonFile
import com.android.build.gradle.internal.cxx.model.getBuildCommandArguments
import com.android.build.gradle.internal.cxx.model.jsonFile
import com.android.ide.common.process.ProcessException
import com.android.ide.common.process.ProcessInfoBuilder
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.Maps
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import com.google.wireless.android.sdk.stats.GradleNativeAndroidModule.NativeBuildSystemType.CMAKE
import org.gradle.process.ExecOperations
import java.io.BufferedWriter
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.*

/**
 * This strategy uses the Vanilla-CMake that supports Cmake server version 1.0 to configure the
 * project and generate the android build JSON.
 */
internal class CmakeServerExternalNativeJsonGenerator(
    abi: CxxAbiModel,
    variantBuilder: GradleBuildVariant.Builder?
) : ExternalNativeJsonGenerator(abi, variantBuilder) {
    init {
        variantBuilder?.nativeBuildSystemType = CMAKE
        cmakeMakefileChecks(abi.variant)
    }

    private val cmake get() = abi.variant.module.cmake!!

    override fun executeProcess(ops: ExecOperations, abi: CxxAbiModel) {
        executeProcessAndGetOutput(abi)
    }

    override fun getProcessBuilder(abi: CxxAbiModel): ProcessInfoBuilder {
        val builder = ProcessInfoBuilder()
        builder.setExecutable(cmake.cmakeExe!!)
        builder.addArgs(abi.configurationArguments)
        return builder
    }

    /**
     * Executes the JSON generation process. Return the combination of STDIO and STDERR from running
     * the process.
     *
     * @return Returns the combination of STDIO and STDERR from running the process.
     */
    private fun executeProcessAndGetOutput(abi: CxxAbiModel) {
        // Once a Cmake server object is created
        // - connect to the server
        // - perform a handshake
        // - configure and compute.
        // Create the NativeBuildConfigValue and write the required JSON file.
        val cmakeServerLogFile = abi.cmakeServerLogFile.absoluteFile
        cmakeServerLogFile.parentFile.mkdirs()
        PassThroughPrintWriterLoggingEnvironment(
            PrintWriter(cmakeServerLogFile, "UTF-8"),
            CMAKE_SERVER_LOG_PREFIX
        ).use {
            // Create a new cmake server for the given Cmake and configure the given project.
            val serverReceiver = ServerReceiver()
                .setMessageReceiver { message: InteractiveMessage ->
                    logInteractiveMessage(
                        message, abi.variant.module.makeFile.parentFile
                    )
                }
                .setDiagnosticReceiver { message: String? ->
                    infoln(
                        message!!
                    )
                }
            val cmakeBinFolder = cmake.cmakeExe!!.parentFile
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
                val arguments = abi.configurationArguments.toCmakeArguments()
                val cacheArgumentsList =
                    arguments.onlyKeepCmakeServerArguments()
                        .toStringList()
                val configureCommandResult: ConfigureCommandResult

                // Handshake
                doHandshake(
                    arguments.getCmakeGenerator()!!,
                    abi.variant.module.makeFile.parentFile,
                    File(arguments.getCmakeBinaryOutputPath()!!),
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
        abi: CxxAbiModel, cmakeServer: Server
    ) {
        val nativeBuildConfigValue =
            getNativeBuildConfigValue(abi, cmakeServer)
        AndroidBuildGradleJsons.writeNativeBuildConfigValueToJsonFile(
            abi.jsonFile, nativeBuildConfigValue
        )
        convertCMakeToCompileCommandsBin(
            abi.compileCommandsJsonFile,
            abi.compileCommandsJsonBinFile)
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
        assert(nativeBuildConfigValue.buildFiles != null)
        nativeBuildConfigValue.buildFiles!!.addAll(getBuildFiles(cmakeServer))
        assert(nativeBuildConfigValue.cleanCommandsComponents != null)
        nativeBuildConfigValue.cleanCommandsComponents!!.add(
            CmakeUtils.getCleanCommand(cmake.cmakeExe!!, abi.cxxBuildFolder)
        )
        assert(nativeBuildConfigValue.buildTargetsCommandComponents != null)
        nativeBuildConfigValue.buildTargetsCommandComponents = CmakeUtils.getBuildTargetsCommand(
            cmake.cmakeExe!!,
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

        abi.additionalProjectFilesIndexFile.bufferedWriter(StandardCharsets.UTF_8).use { additionalProjectFilesIndexWriter ->
            // Fill in the required fields in NativeBuildConfigValue from the code model obtained from
            // Cmake server.
            for (config in codeModel.configurations) {
                for (project in config.projects) {
                    for (target in project.targets) {
                        // Ignore targets that aren't valid.
                        if (!canAddTargetToNativeLibrary(target)) {
                            continue
                        }
                        val nativeLibraryValue = getNativeLibraryValue(abi, target, additionalProjectFilesIndexWriter)
                        val libraryName = target.name + "-" + config.name + "-" + abi.abi.tag
                        assert(nativeBuildConfigValue.libraries != null)
                        nativeBuildConfigValue.libraries!![libraryName] = nativeLibraryValue
                    } // target
                } // project
            }
        }
        return nativeBuildConfigValue
    }

    @VisibleForTesting
    @Throws(FileNotFoundException::class)
    private fun getNativeLibraryValue(
      abi: CxxAbiModel,
      target: Target,
      additionalProjectFilesIndexWriter: BufferedWriter,
    ): NativeLibraryValue {
        return getNativeLibraryValue(
          cmake.cmakeExe!!,
          abi.cxxBuildFolder,
          abi.variant.isDebuggableEnabled,
          abi.abi.tag,
          target,
          additionalProjectFilesIndexWriter
        )
    }

    /**
     * Returns the list of build files used by CMake as part of the build system. Temporary files
     * are currently ignored.
     */
    private fun getBuildFiles(cmakeServer: Server): List<File> {
        val cmakeInputsResult = cmakeServer.cmakeInputs()
        if (!ServerUtils.isCmakeInputsResultValid(cmakeInputsResult)
            || cmakeInputsResult.buildFiles == null) {
            // When CMake server doesn't return a result just use the CMakeLists.txt we know about.
            return listOf(abi.variant.module.makeFile.absoluteFile)
        }

        // The path to the build file source might be relative, so use the absolute path using
        // source directory information.
        val sourceDirectory= File(cmakeInputsResult.sourceDirectory
            ?: abi.variant.module.makeFile.absoluteFile.parent)

        val files = listOf(abi.variant.module.makeFile) +
            cmakeInputsResult.buildFiles
                // Combine multiple results from CMake server
                .flatMap { buildFiles -> buildFiles.sources?.filterNotNull() ?: listOf() }
                .map { File(it) }

        //  remove duplicates, filter down to only CMakeLists.txt
        return files
                // Throw away everything except CMakeLists.txt
                .filter { it.name.compareTo("CMakeLists.txt", ignoreCase = true) == 0 }
                // Make canonical
                .map { file -> sourceDirectory.resolve(file).canonicalFile }
                // Remove duplicates
                .distinct()
                // Sort by length of path to put more important ones higher for readability
                .sortedBy { it.path.length }
    }

    companion object {
        private const val CMAKE_SERVER_LOG_PREFIX = "CMAKE SERVER: "

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

        @VisibleForTesting
        fun getNativeLibraryValue(
          cmakeExecutable: File,
          outputFolder: File,
          isDebuggable: Boolean,
          abi: String,
          target: Target,
          additionalProjectFilesIndexWriter: BufferedWriter,
        ): NativeLibraryValue {
            val nativeLibraryValue = NativeLibraryValue()
            nativeLibraryValue.abi = abi
            nativeLibraryValue.buildCommandComponents =
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
            nativeLibraryValue.runtimeFiles = target.findRuntimeFiles()

            val sourceDirectory = File(target.sourceDirectory)
            for (fileGroup in target.fileGroups.orEmpty()) {
                if (fileGroup.language == "C" || fileGroup.language == "CXX") {
                    // Skip C and CXX since these are contained in compile_commands.json already.
                    continue
                }
                for (source in fileGroup.sources) {
                    additionalProjectFilesIndexWriter.appendln(sourceDirectory.resolve(source).absolutePath)
                }
            }
            return nativeLibraryValue
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
            nativeBuildConfigValue.cleanCommandsComponents = ArrayList()
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
