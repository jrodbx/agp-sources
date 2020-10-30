/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.build.gradle.external.gnumake;


import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValue;
import com.android.build.gradle.internal.cxx.json.NativeLibraryValue;
import com.android.build.gradle.internal.cxx.json.NativeSourceFileValue;
import com.android.build.gradle.internal.cxx.json.NativeToolchainValue;
import com.android.build.gradle.tasks.ExternalNativeBuildTask;
import com.android.utils.NativeSourceFileExtensions;
import com.android.utils.NdkUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 The purpose of this class is to take the raw output of an ndk-build -n call and to produce a
 NativeBuildConfigValue instance to pass upstream through gradle.

 This involves several stages of processing:

 (1) CommandLineParser.parse accepts a single string which is the ndk-build -n output. It tokenizes
 each command in the string according to shell parsing rules on Windows or bash (includes mac).
 The result is a list of CommandLine.

 (2) CommandClassifier.classify accepts the output of (1). It looks at each command for something it
 recognizes. This will typically be calls to clang, gcc or gcc-ar. Once a command is recognized,
 its file inputs and outputs are recorded. The result is a list of Classification.

 (3) FlowAnalyzer.analyze accepts the output of (2). It traces the flow of inputs and outputs. This
 flow tracing will involve intermediate steps through linking and possibly archiving (gcc-ar).
 Files involved are typically .c, .cpp, .o, .a and .so. The result of this step is a map from
 terminal outputs (.so) to original inputs (.c and .cpp).

 (4) NativeBuildConfigValueBuilder.build accepts the output of (3). It examines the terminal outputs
 and input information to build up an instance of NativeBuildConfigValue.
 */
@SuppressWarnings("SingleCharacterStringConcatenation")
public class NativeBuildConfigValueBuilder {

    // These are flags which have a following argument.
    @NonNull
    private static final List<String> STRIP_FLAGS_WITH_ARG =
            Arrays.asList(
                    "-c",
                    "-o",
                    // Skip -M* flags because these govern the creation of .d files in gcc. We don't want
                    // spurious files dropped by Cidr. See see b.android.com/215555 and
                    // b.android.com/213429.
                    // Also, removing these flags reduces the number of Settings groups that have to be
                    // passed to Android Studio.
                    "-MF",
                    "-MT",
                    "-MQ");

    // These are flags which don't have a following argument.
    @NonNull
    private static final List<String> STRIP_FLAGS_WITHOUT_ARG =
            Lists.newArrayList(
                    // Skip -M* flags because these govern the creation of .d files in gcc. We don't want
                    // spurious files dropped by Cidr. See see b.android.com/215555 and
                    // b.android.com/213429
                    "-M", "-MM", "-MD", "-MG", "-MP", "-MMD");

    @NonNull private final Map<String, String> toolChainToCCompiler = new HashMap<>();
    @NonNull private final Map<String, String> toolChainToCppCompiler = new HashMap<>();
    @NonNull private final Set<String> cFileExtensions = new HashSet<>();
    @NonNull private final Set<String> cppFileExtensions = new HashSet<>();
    @NonNull private final File androidMk;
    @NonNull private final File executionRootPath;
    @NonNull private final List<Output> outputs;
    @NonNull private final OsFileConventions fileConventions;

    @Nullable private String buildTargetsCommand;

    /**
     * Constructs a NativeBuildConfigValueBuilder which can be used to build a {@link
     * NativeBuildConfigValue}.
     *
     * <p>projectRootPath -- file path to the project that contains an ndk-build project (
     */
    public NativeBuildConfigValueBuilder(@NonNull File androidMk, @NonNull File executionRootPath) {
        this(androidMk, executionRootPath, AbstractOsFileConventions.createForCurrentHost());
    }

    NativeBuildConfigValueBuilder(
            @NonNull File androidMk,
            @NonNull File executionRootPath,
            @NonNull OsFileConventions fileConventions) {
        this.androidMk = androidMk;
        this.executionRootPath = executionRootPath;
        this.outputs = new ArrayList<>();
        this.fileConventions = fileConventions;
    }

    /** Set the commands and variantName for the NativeBuildConfigValue being built. */
    @NonNull
    public NativeBuildConfigValueBuilder setCommands(
            @NonNull String buildCommand,
            @NonNull String cleanCommand,
            @NonNull String variantName,
            @NonNull String commands) {
        if (!this.outputs.isEmpty()) {
            throw new RuntimeException("setCommands should be called once");
        }
        ListMultimap<String, List<BuildStepInfo>> outputs =
                FlowAnalyzer.analyze(commands, fileConventions);
        for (Map.Entry<String, List<BuildStepInfo>> entry : outputs.entries()) {
            this.outputs.add(
                    new Output(
                            entry.getKey(),
                            entry.getValue(),
                            buildCommand,
                            cleanCommand,
                            variantName));
        }
        this.buildTargetsCommand =
                buildCommand + " " + ExternalNativeBuildTask.BUILD_TARGETS_PLACEHOLDER;
        return this;
    }

    /**
     * Builds the {@link NativeBuildConfigValue} from the given information.
     */
    @NonNull
    public NativeBuildConfigValue build() {
        findLibraryNames();
        findToolchainNames();
        findToolChainCompilers();

        NativeBuildConfigValue config = new NativeBuildConfigValue();
        // Sort by library name so that output is stable
        Collections.sort(outputs, Comparator.comparing(o -> o.libraryName));
        config.cleanCommands = generateCleanCommands();
        config.buildTargetsCommand = buildTargetsCommand;
        config.buildFiles = Lists.newArrayList(androidMk);
        config.libraries = generateLibraries();
        config.toolchains = generateToolchains();
        config.cFileExtensions = generateExtensions(cFileExtensions);
        config.cppFileExtensions = generateExtensions(cppFileExtensions);
        return config;
    }

    private static Collection<String> generateExtensions(@NonNull Set<String> extensionSet) {
        List<String> extensionList = Lists.newArrayList(extensionSet);
        Collections.sort(extensionList);

        return extensionList;
    }

    private void findLibraryNames() {
        for (Output output : outputs) {
            // This pattern is for standard ndk-build and should give names like:
            //  mips64-test-libstl-release
            String parentFile = fileConventions.getFileParent(output.outputFileName);
            String abi = fileConventions.getFileName(parentFile);
            output.artifactName =
                    NdkUtils.getTargetNameFromBuildOutputFileName(
                            fileConventions.getFileName(output.outputFileName));
            output.libraryName = String.format("%s-%s-%s", output.artifactName,
                    output.variantName, abi);
        }
    }

    private void findToolChainCompilers() {
        for (Output output : outputs) {
            String toolchain = output.toolchain;
            Set<String> cCompilers = new HashSet<>();
            Set<String> cppCompilers = new HashSet<>();
            Map<String, Set<String>> compilerToWeirdExtensions = new HashMap<>();
            for (BuildStepInfo command : output.commandInputs) {
                String compilerCommand = command.getCommand().executable;
                String extension = Files.getFileExtension(command.getOnlyInput());

                if (NativeSourceFileExtensions.C_FILE_EXTENSIONS.contains(extension)) {
                    cFileExtensions.add(extension);
                    cCompilers.add(compilerCommand);
                } else if (NativeSourceFileExtensions.CPP_FILE_EXTENSIONS.contains(extension)) {
                    cppFileExtensions.add(extension);
                    cppCompilers.add(compilerCommand);
                } else {
                    // Unrecognized extensions are recorded and added to the relevant compiler
                    Set<String> extensions = compilerToWeirdExtensions.get(compilerCommand);
                    if (extensions == null) {
                        extensions = new HashSet<>();
                        compilerToWeirdExtensions.put(compilerCommand, extensions);
                    }
                    extensions.add(extension);
                }
            }

            if (cCompilers.size() > 1) {
                throw new RuntimeException("Too many c compilers in toolchain.");
            }

            if (cppCompilers.size() > 1) {
                throw new RuntimeException("Too many cpp compilers in toolchain.");
            }

            String cCompiler = null;
            String cppCompiler = null;

            if (cCompilers.size() == 1) {
                cCompiler = cCompilers.iterator().next();
                toolChainToCCompiler.put(toolchain, cCompiler);
            }

            if (cppCompilers.size() == 1) {
                cppCompiler = cppCompilers.iterator().next();
                toolChainToCppCompiler.put(toolchain, cppCompiler);
            }

            // Record the weird file extensions.
            for (String compiler : compilerToWeirdExtensions.keySet()) {
                if (compiler.equals(cCompiler)) {
                    cFileExtensions.addAll(compilerToWeirdExtensions.get(compiler));
                } else if (compiler.equals(cppCompiler)) {
                    cppFileExtensions.addAll(compilerToWeirdExtensions.get(compiler));
                }
            }
        }
    }

    @NonNull
    private String findToolChainName(@NonNull String outputFileName) {
        return "toolchain-"
                + fileConventions.getFileName(fileConventions.getFileParent(outputFileName));
    }

    private void findToolchainNames() {
        for (Output output : outputs) {
            output.toolchain = findToolChainName(output.outputFileName);
        }
    }

    @NonNull
    private List<String> generateCleanCommands() {
        Set<String> cleanCommands = Sets.newHashSet();
        for (Output output : outputs) {
            cleanCommands.add(output.cleanCommand);
        }

        return Lists.newArrayList(cleanCommands);
    }

    @NonNull
    private Map<String, NativeLibraryValue> generateLibraries() {

        Map<String, NativeLibraryValue> librariesMap = new HashMap<>();

        for (Output output : outputs) {
            NativeLibraryValue value = new NativeLibraryValue();
            librariesMap.put(output.libraryName, value);
            value.buildCommand = output.buildCommand + " " + output.outputFileName;
            value.abi =
                    fileConventions.getFileName(
                            fileConventions.getFileParent(output.outputFileName));
            value.artifactName = output.artifactName;
            value.toolchain = output.toolchain;
            value.output = fileConventions.toFile(output.outputFileName);
            value.files = new ArrayList<>();

            for (BuildStepInfo input : output.commandInputs) {
                NativeSourceFileValue file = new NativeSourceFileValue();
                value.files.add(file);
                file.src = fileConventions.toFile(input.getOnlyInput());
                if (!fileConventions.isPathAbsolute(input.getOnlyInput())) {
                    file.src = fileConventions.toFile(executionRootPath, input.getOnlyInput());
                }
                List<String> flags = new ArrayList<>();
                for (int i = 0; i < input.getCommand().escapedFlags.size(); ++i) {
                    String arg = input.getCommand().escapedFlags.get(i);
                    if (STRIP_FLAGS_WITH_ARG.contains(arg)) {
                        ++i; // skip the next argument.
                        continue;
                    }
                    if (startsWithStripFlag(arg)) {
                        continue;
                    }
                    if (STRIP_FLAGS_WITHOUT_ARG.contains(arg)) {
                        continue;
                    }
                    flags.add(input.getCommand().rawFlags.get(i));
                }
                file.flags = Joiner.on(" ").join(flags);
            }
        }

        return librariesMap;
    }

    private static boolean startsWithStripFlag(@NonNull String arg) {
        for (String flag : STRIP_FLAGS_WITH_ARG) {
            if (arg.startsWith(flag)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private Map<String, NativeToolchainValue> generateToolchains() {
        Set<String> toolchainSet = outputs.stream()
                .map(output -> output.toolchain)
                .collect(Collectors.toSet());
        List<String> toolchains = new ArrayList<>(toolchainSet);
        Collections.sort(toolchains);

        Map<String, NativeToolchainValue> toolchainsMap = new HashMap<>();

        for (String toolchain : toolchains) {
            NativeToolchainValue toolchainValue = new NativeToolchainValue();
            toolchainsMap.put(toolchain, toolchainValue);

            if (toolChainToCCompiler.containsKey(toolchain)) {
                toolchainValue.cCompilerExecutable =
                        fileConventions.toFile(toolChainToCCompiler.get(toolchain));
            }
            if (toolChainToCppCompiler.containsKey(toolchain)) {
                toolchainValue.cppCompilerExecutable =
                        fileConventions.toFile(toolChainToCppCompiler.get(toolchain));
            }
        }
        return toolchainsMap;
    }

    private static class Output {
        private final String outputFileName;
        private final List<BuildStepInfo> commandInputs;
        private final String buildCommand;
        private final String cleanCommand;
        private final String variantName;
        private String artifactName;
        private String libraryName;
        private String toolchain;

        private Output(
                String outputFileName,
                List<BuildStepInfo> commandInputs,
                String buildCommand,
                String cleanCommand,
                String variantName) {
            this.outputFileName = outputFileName;
            this.commandInputs = commandInputs;
            this.buildCommand = buildCommand;
            this.cleanCommand = cleanCommand;
            this.variantName = variantName;
        }
    }
}
