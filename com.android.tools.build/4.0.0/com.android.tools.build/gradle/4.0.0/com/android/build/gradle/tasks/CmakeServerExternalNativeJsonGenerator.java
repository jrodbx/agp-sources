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

package com.android.build.gradle.tasks;

import static com.android.build.gradle.external.cmake.CmakeUtils.getObjectToString;
import static com.android.build.gradle.internal.cxx.cmake.LinkLibrariesParserKt.parseLinkLibraries;
import static com.android.build.gradle.internal.cxx.cmake.MakeCmakeMessagePathsAbsoluteKt.makeCmakeMessagePathsAbsolute;
import static com.android.build.gradle.internal.cxx.configure.CmakeCommandLineKt.convertCmakeCommandLineArgumentsToStringList;
import static com.android.build.gradle.internal.cxx.configure.CmakeCommandLineKt.getBuildRootFolder;
import static com.android.build.gradle.internal.cxx.configure.CmakeCommandLineKt.getGenerator;
import static com.android.build.gradle.internal.cxx.configure.CmakeCommandLineKt.onlyKeepServerArguments;
import static com.android.build.gradle.internal.cxx.configure.CmakeSourceFileNamingKt.hasCmakeHeaderFileExtensions;
import static com.android.build.gradle.internal.cxx.json.CompilationDatabaseIndexingVisitorKt.indexCompilationDatabase;
import static com.android.build.gradle.internal.cxx.json.CompilationDatabaseToolchainVisitorKt.populateCompilationDatabaseToolchains;
import static com.android.build.gradle.internal.cxx.logging.LoggingEnvironmentKt.errorln;
import static com.android.build.gradle.internal.cxx.logging.LoggingEnvironmentKt.infoln;
import static com.android.build.gradle.internal.cxx.logging.LoggingEnvironmentKt.warnln;
import static com.android.build.gradle.internal.cxx.model.CxxAbiModelKt.getJsonFile;
import static com.android.build.gradle.internal.cxx.model.CxxCmakeAbiModelKt.getCompileCommandsJsonFile;
import static com.android.build.gradle.internal.cxx.settings.CxxAbiModelCMakeSettingsRewriterKt.getBuildCommandArguments;
import static com.android.build.gradle.internal.cxx.settings.CxxAbiModelCMakeSettingsRewriterKt.getFinalCmakeCommandLineArguments;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.external.cmake.CmakeUtils;
import com.android.build.gradle.external.cmake.server.BuildFiles;
import com.android.build.gradle.external.cmake.server.CmakeInputsResult;
import com.android.build.gradle.external.cmake.server.CodeModel;
import com.android.build.gradle.external.cmake.server.ComputeResult;
import com.android.build.gradle.external.cmake.server.Configuration;
import com.android.build.gradle.external.cmake.server.ConfigureCommandResult;
import com.android.build.gradle.external.cmake.server.FileGroup;
import com.android.build.gradle.external.cmake.server.HandshakeRequest;
import com.android.build.gradle.external.cmake.server.HandshakeResult;
import com.android.build.gradle.external.cmake.server.IncludePath;
import com.android.build.gradle.external.cmake.server.Project;
import com.android.build.gradle.external.cmake.server.ProtocolVersion;
import com.android.build.gradle.external.cmake.server.Server;
import com.android.build.gradle.external.cmake.server.ServerFactory;
import com.android.build.gradle.external.cmake.server.ServerUtils;
import com.android.build.gradle.external.cmake.server.Target;
import com.android.build.gradle.external.cmake.server.receiver.InteractiveMessage;
import com.android.build.gradle.external.cmake.server.receiver.ServerReceiver;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument;
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons;
import com.android.build.gradle.internal.cxx.json.CompilationDatabaseToolchain;
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValue;
import com.android.build.gradle.internal.cxx.json.NativeHeaderFileValue;
import com.android.build.gradle.internal.cxx.json.NativeLibraryValue;
import com.android.build.gradle.internal.cxx.json.NativeSourceFileValue;
import com.android.build.gradle.internal.cxx.json.NativeToolchainValue;
import com.android.build.gradle.internal.cxx.json.StringTable;
import com.android.build.gradle.internal.cxx.logging.PassThroughPrintWriterLoggingEnvironment;
import com.android.build.gradle.internal.cxx.logging.ThreadLoggingEnvironment;
import com.android.build.gradle.internal.cxx.model.CxxAbiModel;
import com.android.build.gradle.internal.cxx.model.CxxBuildModel;
import com.android.build.gradle.internal.cxx.model.CxxVariantModel;
import com.android.ide.common.process.ProcessException;
import com.android.repository.Revision;
import com.android.utils.ILogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.UnsignedInts;
import com.google.gson.stream.JsonReader;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.gradle.api.Action;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;

/**
 * This strategy uses the Vanilla-CMake that supports Cmake server version 1.0 to configure the
 * project and generate the android build JSON.
 */
class CmakeServerExternalNativeJsonGenerator extends CmakeExternalNativeJsonGenerator {

    private static final String CMAKE_SERVER_LOG_PREFIX = "CMAKE SERVER: ";

    public CmakeServerExternalNativeJsonGenerator(
            @NonNull CxxBuildModel build,
            @NonNull CxxVariantModel variant,
            @NonNull List<CxxAbiModel> abis,
            @NonNull GradleBuildVariant.Builder stats) {
        super(build, variant, abis, stats);
    }

    /**
     * @param toolchains - toolchains map
     * @return the hash of the only entry in the map, ideally the toolchains map should have only
     *     one entry.
     */
    @Nullable
    private static String getOnlyToolchainName(
            @NonNull Map<String, NativeToolchainValue> toolchains) {
        if (toolchains.size() != 1) {
            throw new RuntimeException(
                    String.format(
                            "Invalid number %d of toolchains. Only one toolchain should be present.",
                            toolchains.size()));
        }
        return toolchains.keySet().iterator().next();
    }

    @NonNull
    private static String getCmakeInfoString(@NonNull Server cmakeServer) throws IOException {
        return String.format(
                "Cmake path: %s, version: %s",
                cmakeServer.getCmakePath(),
                CmakeUtils.getVersion(new File(cmakeServer.getCmakePath())).toString());
    }

    @NonNull
    @Override
    public String executeProcessAndGetOutput(
            @NonNull CxxAbiModel abi,
            @NonNull Function<Action<? super ExecSpec>, ExecResult> execOperation)
            throws ProcessException, IOException {
        // Once a Cmake server object is created
        // - connect to the server
        // - perform a handshake
        // - configure and compute.
        // Create the NativeBuildConfigValue and write the required JSON file.
        File cmakeServerLogFile = abi.getCmake().getCmakeServerLogFile().getAbsoluteFile();
        cmakeServerLogFile.getParentFile().mkdirs();
        try (ThreadLoggingEnvironment ignore =
                new PassThroughPrintWriterLoggingEnvironment(
                        new PrintWriter(cmakeServerLogFile, "UTF-8"), CMAKE_SERVER_LOG_PREFIX)) {
            // Create a new cmake server for the given Cmake and configure the given project.
            ServerReceiver serverReceiver =
                    new ServerReceiver()
                            .setMessageReceiver(
                                    message ->
                                            logInteractiveMessage(
                                                    message, getMakefile().getParentFile()))
                            .setDiagnosticReceiver(message -> infoln(message));
            File cmakeBinFolder = cmake.getCmakeExe().getParentFile();
            Server cmakeServer = ServerFactory.create(cmakeBinFolder, serverReceiver);
            if (cmakeServer == null) {
                Revision actual = CmakeUtils.getVersion(cmakeBinFolder);
                throw new RuntimeException(
                        String.format(
                                "Actual CMake version '%s.%s.%s' did not satisfy requested minimum or default "
                                        + "CMake minimum version '%s'. Possibly cmake.dir doesn't match "
                                        + "android.externalNativeBuild.cmake.version.",
                                actual.getMajor(),
                                actual.getMinor(),
                                actual.getMicro(),
                                cmake.getMinimumCmakeVersion()));
            }

            if (!cmakeServer.connect()) {
                throw new RuntimeException(
                        "Unable to connect to Cmake server located at: "
                                + cmakeBinFolder.getAbsolutePath());
            }

            try {
                List<CommandLineArgument> arguments = getFinalCmakeCommandLineArguments(abi);
                List<String> cacheArgumentsList =
                        convertCmakeCommandLineArgumentsToStringList(
                                onlyKeepServerArguments(arguments));
                ConfigureCommandResult configureCommandResult;

                // Handshake
                doHandshake(
                        getGenerator(arguments),
                        variant.getModule().getMakeFile().getParentFile(),
                        new File(getBuildRootFolder(arguments)),
                        cmakeServer);

                // Configure
                String[] argsArray = cacheArgumentsList.toArray(new String[0]);
                configureCommandResult = cmakeServer.configure(argsArray);

                if (!ServerUtils.isConfigureResultValid(configureCommandResult.configureResult)) {
                    throw new ProcessException(
                            String.format(
                                    "Error configuring CMake server (%s).\r\n%s",
                                    cmakeServer.getCmakePath(),
                                    configureCommandResult.interactiveMessages));
                }

                ComputeResult computeResult = doCompute(cmakeServer);
                if (!ServerUtils.isComputedResultValid(computeResult)) {
                    throw new ProcessException(
                            "Error computing CMake server result.\r\n"
                                    + configureCommandResult.interactiveMessages);
                }

                generateAndroidGradleBuild(abi, cmakeServer);
                return configureCommandResult.interactiveMessages;
            } finally {
                cmakeServer.disconnect();
            }
        }
    }

    /**
     * Logs info/warning/error for the given interactive message. Throws a RunTimeException in case
     * of an 'error' message type.
     */
    private static void logInteractiveMessage(
            @NonNull InteractiveMessage message, @NonNull File makeFileDirectory) {
        // CMake error/warning prefix strings. The CMake errors and warnings are part of the
        // message type "message" even though CMake is reporting errors/warnings (Note: They could
        // have a title that says if it's an error or warning, we check that first before checking
        // the prefix of the message string). Hence we would need to parse the output message to
        // figure out if we need to log them as error or warning.
        final String CMAKE_ERROR_PREFIX = "CMake Error";
        final String CMAKE_WARNING_PREFIX = "CMake Warning";

        // If the final message received is of type error, log and error and throw an exception.
        // Note: This is not the same as a message with type "message" with error information, that
        // case is handled below.
        if (message.type != null && message.type.equals("error")) {
            errorln(makeCmakeMessagePathsAbsolute(message.errorMessage, makeFileDirectory));
            return;
        }

        String correctedMessage = makeCmakeMessagePathsAbsolute(message.message, makeFileDirectory);

        if ((message.title != null && message.title.equals("Error"))
                || message.message.startsWith(CMAKE_ERROR_PREFIX)) {
            errorln(correctedMessage);
            return;
        }

        if ((message.title != null && message.title.equals("Warning"))
                || message.message.startsWith(CMAKE_WARNING_PREFIX)) {
            warnln(correctedMessage);
            return;
        }

        infoln(correctedMessage);
    }

    /**
     * Requests a handshake to a connected Cmake server.
     *
     * @throws IOException I/O failure. Note: The function throws RuntimeException if we receive an
     *     invalid/erroneous handshake result.
     */
    private void doHandshake(
            @NonNull String generator,
            @NonNull File sourceDirectory,
            @NonNull File buildDirectory,
            @NonNull Server cmakeServer)
            throws IOException {
        List<ProtocolVersion> supportedProtocolVersions = cmakeServer.getSupportedVersion();
        if (supportedProtocolVersions == null || supportedProtocolVersions.isEmpty()) {
            throw new RuntimeException(
                    String.format(
                            "Gradle does not support the Cmake server version. %s",
                            getCmakeInfoString(cmakeServer)));
        }

        HandshakeResult handshakeResult =
                cmakeServer.handshake(
                        getHandshakeRequest(
                                generator,
                                sourceDirectory,
                                buildDirectory,
                                supportedProtocolVersions.get(0)));
        if (!ServerUtils.isHandshakeResultValid(handshakeResult)) {
            throw new RuntimeException(
                    String.format(
                            "Invalid handshake result from Cmake server: \n%s\n%s",
                            getObjectToString(handshakeResult), getCmakeInfoString(cmakeServer)));
        }
    }

    /**
     * Create a default handshake request for the given Cmake server-protocol version
     *
     * @return handshake request
     */
    private HandshakeRequest getHandshakeRequest(
            @NonNull String generator,
            @NonNull File sourceDirectory,
            @NonNull File buildDirectory,
            @NonNull ProtocolVersion cmakeServerProtocolVersion) {
        if (!sourceDirectory.isDirectory()) {
            errorln("Not a directory: %s", sourceDirectory);
        }
        HandshakeRequest handshakeRequest = new HandshakeRequest();
        handshakeRequest.cookie = "gradle-cmake-cookie";
        handshakeRequest.generator = generator;
        handshakeRequest.protocolVersion = cmakeServerProtocolVersion;
        handshakeRequest.buildDirectory = normalizeFilePath(buildDirectory);
        handshakeRequest.sourceDirectory = normalizeFilePath(sourceDirectory);
        return handshakeRequest;
    }

    /**
     * Generate build system files in the build directly, or compute the given project and returns
     * the computed result.
     *
     * @param cmakeServer Connected cmake server.
     * @throws IOException I/O failure. Note: The function throws RuntimeException if we receive an
     *     invalid/erroneous ComputeResult.
     */
    private static ComputeResult doCompute(@NonNull Server cmakeServer) throws IOException {
        return cmakeServer.compute();
    }

    /**
     * Generates nativeBuildConfigValue by generating the code model from the cmake server and
     * writes the android_gradle_build.json.
     *
     * @throws IOException I/O failure
     */
    private void generateAndroidGradleBuild(
            @NonNull CxxAbiModel config, @NonNull Server cmakeServer) throws IOException {
        NativeBuildConfigValue nativeBuildConfigValue =
                getNativeBuildConfigValue(config, cmakeServer);
        AndroidBuildGradleJsons.writeNativeBuildConfigValueToJsonFile(
                getJsonFile(config), nativeBuildConfigValue);
    }

    /**
     * Returns NativeBuildConfigValue for the given abi from the given Cmake server.
     *
     * @return returns NativeBuildConfigValue
     * @throws IOException I/O failure
     */
    @VisibleForTesting
    protected NativeBuildConfigValue getNativeBuildConfigValue(
            @NonNull CxxAbiModel abi, @NonNull Server cmakeServer) throws IOException {
        NativeBuildConfigValue nativeBuildConfigValue = createDefaultNativeBuildConfigValue();

        assert nativeBuildConfigValue.stringTable != null;
        StringTable strings = new StringTable(nativeBuildConfigValue.stringTable);

        // Build file
        assert nativeBuildConfigValue.buildFiles != null;
        nativeBuildConfigValue.buildFiles.addAll(getBuildFiles(abi, cmakeServer));

        // Clean commands
        assert nativeBuildConfigValue.cleanCommands != null;
        nativeBuildConfigValue.cleanCommands.add(
                CmakeUtils.getCleanCommand(cmake.getCmakeExe(), abi.getCxxBuildFolder()));

        // Build targets command.
        assert nativeBuildConfigValue.buildTargetsCommand != null;

        nativeBuildConfigValue.buildTargetsCommand =
                CmakeUtils.getBuildTargetsCommand(
                        cmake.getCmakeExe(),
                        abi.getCxxBuildFolder(),
                        getBuildCommandArguments(abi));

        CodeModel codeModel = cmakeServer.codemodel();
        if (!ServerUtils.isCodeModelValid(codeModel)) {
            throw new RuntimeException(
                    String.format(
                            "Invalid code model received from Cmake server: \n%s\n%s",
                            getObjectToString(codeModel), getCmakeInfoString(cmakeServer)));
        }

        // C and Cpp extensions
        assert nativeBuildConfigValue.cFileExtensions != null;
        nativeBuildConfigValue.cFileExtensions.addAll(CmakeUtils.getCExtensionSet(codeModel));
        assert nativeBuildConfigValue.cppFileExtensions != null;
        nativeBuildConfigValue.cppFileExtensions.addAll(CmakeUtils.getCppExtensionSet(codeModel));

        // toolchains
        nativeBuildConfigValue.toolchains =
                getNativeToolchains(
                        abi,
                        cmakeServer,
                        nativeBuildConfigValue.cppFileExtensions,
                        nativeBuildConfigValue.cFileExtensions);

        String toolchainHashString = getOnlyToolchainName(nativeBuildConfigValue.toolchains);

        // Fill in the required fields in NativeBuildConfigValue from the code model obtained from
        // Cmake server.
        for (Configuration config : codeModel.configurations) {
            for (Project project : config.projects) {
                for (Target target : project.targets) {
                    // Ignore targets that aren't valid.
                    if (!canAddTargetToNativeLibrary(target)) {
                        continue;
                    }

                    NativeLibraryValue nativeLibraryValue =
                            getNativeLibraryValue(abi, abi.getCxxBuildFolder(), target, strings);
                    nativeLibraryValue.toolchain = toolchainHashString;
                    String libraryName =
                            target.name + "-" + config.name + "-" + abi.getAbi().getTag();
                    assert nativeBuildConfigValue.libraries != null;
                    nativeBuildConfigValue.libraries.put(libraryName, nativeLibraryValue);
                } // target
            } // project
        }
        return nativeBuildConfigValue;
    }

    @VisibleForTesting
    protected NativeLibraryValue getNativeLibraryValue(
            @NonNull CxxAbiModel abi,
            @NonNull File workingDirectory,
            @NonNull Target target,
            StringTable strings)
            throws FileNotFoundException {
        return getNativeLibraryValue(
                cmake.getCmakeExe(),
                abi.getCxxBuildFolder(),
                isDebuggable(),
                new JsonReader(new FileReader(getCompileCommandsJsonFile(abi.getCmake()))),
                abi.getAbi().getTag(),
                workingDirectory,
                target,
                strings);
    }

    @VisibleForTesting
    static NativeLibraryValue getNativeLibraryValue(
            @NonNull File cmakeExecutable,
            @NonNull File outputFolder,
            boolean isDebuggable,
            @NonNull JsonReader compileCommandsJson,
            @NonNull String abi,
            @NonNull File workingDirectory,
            @NonNull Target target,
            @NonNull StringTable strings) {
        NativeLibraryValue nativeLibraryValue = new NativeLibraryValue();
        nativeLibraryValue.abi = abi;
        nativeLibraryValue.buildCommand =
                CmakeUtils.getBuildCommand(cmakeExecutable, outputFolder, target.name);
        nativeLibraryValue.artifactName = target.name;
        nativeLibraryValue.buildType = isDebuggable ? "debug" : "release";
        // We'll have only one output, so get the first one.
        if (target.artifacts.length > 0) {
            nativeLibraryValue.output = new File(target.artifacts[0]);
        }

        nativeLibraryValue.files = new ArrayList<>();
        nativeLibraryValue.headers = new ArrayList<>();
        nativeLibraryValue.runtimeFiles = new ArrayList<>();

        Path sysroot = Paths.get(target.sysroot);
        if (target.linkLibraries != null) {
            for (String library : parseLinkLibraries(target.linkLibraries)) {
                // Each element here is just an argument to the linker. It might be a full path to a
                // library to be linked or a trivial -l flag. If it's a full path that exists within
                // the prefab directory, it's a library that's needed at runtime.
                if (library.startsWith("-")) {
                    continue;
                }

                // Filter out any other arguments that aren't files.
                Path libraryPath = Paths.get(library);
                if (!Files.exists(libraryPath)) {
                    continue;
                }

                // Anything under the sysroot shouldn't be included in the APK. This isn't strictly
                // true since the STLs live here, but those are handled separately by
                // ExternalNativeBuildTask::buildImpl.
                if (!libraryPath.startsWith(sysroot)) {
                    nativeLibraryValue.runtimeFiles.add(libraryPath.toFile());
                }
            }
        }

        // Maps each source file to the index of the corresponding strings table entry, which
        // contains the build flags for that source file.
        // It is important to not use a File or Path as the key to the dictionary, but instead
        // use the corresponding normalized path. Two File/Path objects with the same normalized
        // string representation may not be equivalent due to "../" or "./" substrings in them
        // (b/123123307).
        Map<String, Integer> compilationDatabaseFlags = Maps.newHashMap();

        int workingDirectoryOrdinal = strings.intern(normalizeFilePath(workingDirectory));
        for (FileGroup fileGroup : target.fileGroups) {
            for (String source : fileGroup.sources) {
                // CMake returns an absolute path or a path relative to the source directory,
                // whichever one is shorter.
                Path sourceFilePath = Paths.get(source);
                if (!sourceFilePath.isAbsolute()) {
                    sourceFilePath = Paths.get(target.sourceDirectory, source);
                }

                // Even if CMake returns an absolute path, we still call normalize() to be symmetric
                // with indexCompilationDatabase() which always uses normalized paths.
                Path normalizedSourceFilePath = sourceFilePath.normalize();
                if (!normalizedSourceFilePath.toString().isEmpty()) {
                    sourceFilePath = normalizedSourceFilePath;
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

                File sourceFile = sourceFilePath.toFile();

                if (hasCmakeHeaderFileExtensions(sourceFile)) {
                    nativeLibraryValue.headers.add(
                            new NativeHeaderFileValue(sourceFile, workingDirectoryOrdinal));
                } else {
                    NativeSourceFileValue nativeSourceFileValue = new NativeSourceFileValue();
                    nativeSourceFileValue.workingDirectoryOrdinal = workingDirectoryOrdinal;
                    nativeSourceFileValue.src = sourceFile;

                    // We use flags from compile_commands.json if present. Otherwise, fall back
                    // to server model compile flags (which is known to not always return a
                    // complete set).
                    // Reference b/116237485
                    if (compilationDatabaseFlags.isEmpty()) {
                        compilationDatabaseFlags =
                                indexCompilationDatabase(compileCommandsJson, strings);
                    }
                    if (compilationDatabaseFlags.containsKey(sourceFilePath.toString())) {
                        nativeSourceFileValue.flagsOrdinal =
                                compilationDatabaseFlags.get(sourceFilePath.toString());
                    } else {
                        // TODO I think this path is always wrong because it won't have --targets
                        // I don't want to make it an exception this late in 3.3 cycle so I'm
                        // leaving it as-is for now.
                        String compileFlags = compileFlagsFromFileGroup(fileGroup);
                        if (!Strings.isNullOrEmpty(compileFlags)) {
                            nativeSourceFileValue.flagsOrdinal = strings.intern(compileFlags);
                        }
                    }
                    nativeLibraryValue.files.add(nativeSourceFileValue);
                }
            }
        }

        return nativeLibraryValue;
    }

    private static String compileFlagsFromFileGroup(FileGroup fileGroup) {
        StringBuilder flags = new StringBuilder();
        flags.append(fileGroup.compileFlags);
        if (fileGroup.defines != null) {
            for (String define : fileGroup.defines) {
                flags.append(" -D").append(define);
            }
        }
        if (fileGroup.includePath != null) {
            for (IncludePath includePath : fileGroup.includePath) {
                if (includePath == null || includePath.path == null) {
                    continue;
                }
                if (includePath.isSystem != null && includePath.isSystem) {
                    flags.append(" -system ");
                } else {
                    flags.append(" -I ");
                }
                flags.append(includePath.path);
            }
        }

        return flags.toString();
    }

    /**
     * Helper function that returns true if the Target object is valid to be added to native
     * library.
     */
    private static boolean canAddTargetToNativeLibrary(@NonNull Target target) {
        // If the target has no artifacts or file groups, the target will be get ignored, so mark
        // it valid.
        return (target.artifacts != null)
                && (target.fileGroups != null)
                && !target.type.equals("OBJECT_LIBRARY");
    }

    /**
     * Returns the list of build files used by CMake as part of the build system. Temporary files
     * are currently ignored.
     */
    @NonNull
    private List<File> getBuildFiles(@NonNull CxxAbiModel config, @NonNull Server cmakeServer)
            throws IOException {
        CmakeInputsResult cmakeInputsResult = cmakeServer.cmakeInputs();
        if (!ServerUtils.isCmakeInputsResultValid(cmakeInputsResult)) {
            throw new RuntimeException(
                    String.format(
                            "Invalid cmakeInputs result received from Cmake server: \n%s\n%s",
                            getObjectToString(cmakeInputsResult), getCmakeInfoString(cmakeServer)));
        }

        // Ideally we should see the build files within cmakeInputs response, but in the weird case
        // that we don't, return the default make file.
        if (cmakeInputsResult.buildFiles == null) {
            List<File> buildFiles = Lists.newArrayList();
            buildFiles.add(getMakefile());
            return buildFiles;
        }

        // The sources listed might be duplicated, so remove the duplicates.
        Set<String> buildSources = Sets.newHashSet();
        for (BuildFiles buildFile : cmakeInputsResult.buildFiles) {
            if (buildFile.isTemporary || buildFile.isCMake || buildFile.sources == null) {
                continue;
            }
            Collections.addAll(buildSources, buildFile.sources);
        }

        // The path to the build file source might be relative, so use the absolute path using
        // source directory information.
        File sourceDirectory = null;
        if (cmakeInputsResult.sourceDirectory != null) {
            sourceDirectory = new File(cmakeInputsResult.sourceDirectory);
        }

        List<File> buildFiles = Lists.newArrayList();

        for (String source : buildSources) {
            // The source file can either be relative or absolute, if it's relative, use the source
            // directory to get the absolute path.
            File sourceFile = new File(source);
            if (!sourceFile.isAbsolute()) {
                if (sourceDirectory != null) {
                    sourceFile = new File(sourceDirectory, source).getCanonicalFile();
                }
            }

            if (!sourceFile.exists()) {
                ILogger logger =
                        LoggerWrapper.getLogger(CmakeServerExternalNativeJsonGenerator.class);
                logger.error(
                        null,
                        "Build file "
                                + sourceFile
                                + " provided by CMake "
                                + "does not exists. This might lead to incorrect Android Studio behavior.");
                continue;
            }

            if (sourceFile
                    .getPath()
                    .startsWith(config.getCmake().getCmakeWrappingBaseFolder().getPath())) {
                // Skip files in .cxx/cmake/x86
                continue;
            }

            buildFiles.add(sourceFile);
        }

        return buildFiles;
    }

    /**
     * Creates a default NativeBuildConfigValue.
     *
     * @return a default NativeBuildConfigValue.
     */
    @NonNull
    private static NativeBuildConfigValue createDefaultNativeBuildConfigValue() {
        NativeBuildConfigValue nativeBuildConfigValue = new NativeBuildConfigValue();
        nativeBuildConfigValue.buildFiles = new ArrayList<>();
        nativeBuildConfigValue.cleanCommands = new ArrayList<>();
        nativeBuildConfigValue.libraries = new HashMap<>();
        nativeBuildConfigValue.toolchains = new HashMap<>();
        nativeBuildConfigValue.cFileExtensions = new ArrayList<>();
        nativeBuildConfigValue.cppFileExtensions = new ArrayList<>();
        nativeBuildConfigValue.stringTable = Maps.newHashMap();
        return nativeBuildConfigValue;
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
    @NonNull
    private static Map<String, NativeToolchainValue> getNativeToolchains(
            @NonNull CxxAbiModel abi,
            @NonNull Server cmakeServer,
            @NonNull Collection<String> cppExtensionSet,
            @NonNull Collection<String> cExtensionSet) {
        NativeToolchainValue toolchainValue = new NativeToolchainValue();
        File cCompilerExecutable = null;
        File cppCompilerExecutable = null;

        File compilationDatabase = getCompileCommandsJsonFile(abi.getCmake());
        if (compilationDatabase.exists()) {
            CompilationDatabaseToolchain toolchain =
                    populateCompilationDatabaseToolchains(
                            compilationDatabase, cppExtensionSet, cExtensionSet);
            cppCompilerExecutable = toolchain.getCppCompilerExecutable();
            cCompilerExecutable = toolchain.getCCompilerExecutable();
        } else {
            if (!cmakeServer.getCCompilerExecutable().isEmpty()) {
                cCompilerExecutable = new File(cmakeServer.getCCompilerExecutable());
            }
            if (!cmakeServer.getCppCompilerExecutable().isEmpty()) {
                cppCompilerExecutable = new File(cmakeServer.getCppCompilerExecutable());
            }
        }

        if (cCompilerExecutable != null) {
            toolchainValue.cCompilerExecutable = cCompilerExecutable;
        }
        if (cppCompilerExecutable != null) {
            toolchainValue.cppCompilerExecutable = cppCompilerExecutable;
        }

        int toolchainHash = CmakeUtils.getToolchainHash(toolchainValue);
        String toolchainHashString = UnsignedInts.toString(toolchainHash);

        Map<String, NativeToolchainValue> toolchains = new HashMap<>();
        toolchains.put(toolchainHashString, toolchainValue);

        return toolchains;
    }

    /**
     * Returns the normalized path for the given file. The normalized path for Unix is the default
     * string returned by getPath. For Microsoft Windows, getPath returns a path with "\\" (example:
     * "C:\\Android\\Sdk") while Vanilla-CMake prefers a forward slash (example "C:/Android/Sdk"),
     * without the forward slash, CMake would mix backward slash and forward slash causing compiler
     * issues. This function replaces the backward slashes with forward slashes for Microsoft
     * Windows.
     */
    @NonNull
    private static String normalizeFilePath(@NonNull File file) {
        if (isWindows()) {
            return (file.getPath().replace("\\", "/"));
        }
        return file.getPath();
    }
}
