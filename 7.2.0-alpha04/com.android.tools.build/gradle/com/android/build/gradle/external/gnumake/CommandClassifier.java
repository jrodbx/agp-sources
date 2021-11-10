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

import static com.android.build.gradle.external.gnumake.CommandClassifierUtilsKt.endsWithExecutableName;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

/**
 * Find compiler commands (g++, gcc, clang) and extract inputs and outputs according to the command
 * line rules of that tool.
 */
class CommandClassifier {
    // Native tool is static here to share with CCacheBuildTool
    @NonNull
    private static final NativeCompilerBuildTool sNativeCompilerBuildTool =
            new NativeCompilerBuildTool();

    @NonNull
    @VisibleForTesting
    static final ImmutableList<BuildTool> DEFAULT_CLASSIFIERS = ImmutableList.of(
            sNativeCompilerBuildTool,
            new ArBuildTool(),
            new CCacheBuildTool());

    @NonNull
    @VisibleForTesting
    static List<BuildStepInfo> classify(
            @NonNull String commands,
            @NonNull OsFileConventions policy,
            @NonNull List<BuildTool> classifiers) {
        List<CommandLine> commandLines = CommandLineParser.parse(commands, policy);

        List<BuildStepInfo> commandSummaries = new ArrayList<>();

        for (CommandLine expr : commandLines) {
            for (BuildTool classifier : classifiers) {
                if (classifier.isMatch(expr)) {
                    BuildStepInfo buildStepInfo = classifier.createCommand(expr);
                    if (buildStepInfo != null) {
                        commandSummaries.add(buildStepInfo);
                    }
                }
            }
        }
        return commandSummaries;
    }

    /**
     * Give a string that contains a list of commands recognize the interesting calls and record
     * information about inputs and outputs.
     */
    @NonNull
    static List<BuildStepInfo> classify(
            @NonNull String commands, @NonNull OsFileConventions policy) {
        return classify(commands, policy, DEFAULT_CLASSIFIERS);
    }

    interface BuildTool {
        @Nullable
        BuildStepInfo createCommand(@NonNull CommandLine command);

        boolean isMatch(@NonNull CommandLine command);
    }

    /**
     * This build tool matches gcc-ar (the android NDK archiver). We care about the cases where the
     * command specifies 'c' for create. In this case, we pull out the inputs (typically .o) and
     * output (.a).
     */
    static class ArBuildTool implements BuildTool {
        @NonNull
        private static final OptionParser PARSER = new OptionParser("cSE");

        static {
            PARSER.accepts("plugin").withRequiredArg();
            PARSER.accepts("target").withRequiredArg();
            PARSER.accepts("X32_64");
            PARSER.accepts("p").withOptionalArg();
        }


        private static void checkValidInput(@NonNull String arg) {
            if (!arg.endsWith(".o")) {
                throw new RuntimeException(arg);
            }
        }

        private static void checkValidOutput(@NonNull String arg) {
            if (!arg.endsWith(".a")) {
                throw new RuntimeException(arg);
            }
        }

        @Nullable
        @Override
        public BuildStepInfo createCommand(@NonNull CommandLine command) {
            String[] arr = new String[command.getEscapedFlags().size()];
            for (int i = 0; i < arr.length; ++i) {
                arr[i] = command.getEscapedFlags().get(i);
            }
            @SuppressWarnings("unchecked")
            List<String> options = (List<String>) PARSER.parse(arr).nonOptionArguments();

            if (!options.get(0).contains("c") || options.size() < 2) { // Only match about 'create'
                return null;
            }

            if (options.size() == 2) {
                // There is a real-world case where archive has zero inputs (see native_codec in
                // NdkSamplesTest). Since there are no inputs the archive is not useful so don't
                // declare an output in the final JSON.
                return new BuildStepInfo(command, Lists.newArrayList(), Lists.newArrayList());
            }

            List<String> inputs = new ArrayList<>();
            List<String> outputs = new ArrayList<>();

            String output = options.get(1);
            checkValidOutput(output);
            outputs.add(output);

            for (int i = 2; i < options.size(); ++i) {
                String arg = options.get(i);
                checkValidInput(arg);
                inputs.add(arg);
            }
            return new BuildStepInfo(command, inputs, outputs);
        }

        @Override
        public boolean isMatch(@NonNull CommandLine command) {
            return endsWithExecutableName(command, "gcc-ar")
                    || endsWithExecutableName(command, "android-ar")
                    || endsWithExecutableName(command, "llvm-ar")
                    || endsWithExecutableName(command, "androideabi-ar");
        }
    }

    /**
     * A CCache command line is like:
     *
     * <p>/usr/bin/ccache gcc [gcc-flags]
     *
     * <p>This build tool first looks for ccache executable and then translates it into a compiler
     * BuildStepInfo.
     */
    static class CCacheBuildTool implements BuildTool {
        @Nullable
        @Override
        public BuildStepInfo createCommand(@NonNull CommandLine command) {
            CommandLine translated = translateToCompilerCommandLine(command);
            return sNativeCompilerBuildTool.createCommand(translated);
        }

        @Override
        public boolean isMatch(@NonNull CommandLine command) {
            if (endsWithExecutableName(command, "ccache")) {
                CommandLine translated = translateToCompilerCommandLine(command);
                return sNativeCompilerBuildTool.isMatch(translated);
            }
            return false;
        }

        @NonNull
        private static CommandLine translateToCompilerCommandLine(@NonNull CommandLine command) {
            List<String> escaped = Lists.newArrayList(command.getEscapedFlags());
            List<String> raw = Lists.newArrayList(command.getRawFlags());
            String baseCommand = escaped.get(0);
            escaped.remove(0);
            raw.remove(0);
            return new CommandLine(baseCommand, escaped, raw);
        }
    }

    /**
     * This build tool matches gcc, g++ and clang. Inputs may be like .c, .cpp, etc in the case that
     * the tool is used as a compiler. Inputs may also be like .o in the case that the tool is used
     * as a linker. Output may be like .o or .so respectively.
     */
    static class NativeCompilerBuildTool implements BuildTool {
        @NonNull
        @Override
        public BuildStepInfo createCommand(@NonNull CommandLine command) {
            String[] arr = new String[command.getEscapedFlags().size()];
            for (int i = 0; i < arr.length; ++i) {
                arr[i] = command.getEscapedFlags().get(i);
            }
            OptionSet options = CompilerParser.get().parse(arr);

            List<String> outputs = new ArrayList<>();
            @SuppressWarnings("unchecked")
            List<String> nonOptions = (List<String>) options.nonOptionArguments();

            // Inputs are whatever is left over that doesn't look like a flag.
            List<String> inputs =
                    nonOptions
                            .stream()
                            // gcc and clang don't allow source files that start with "-", so we don't need
                            // to either.
                            .filter(nonOption -> !nonOption.startsWith("-"))
                            // Do a weak heuristic check about whether this might really be a file. If
                            // there's no dot then it probably isn't a file.
                            .filter(nonOption -> nonOption.contains("."))
                            .collect(Collectors.toList());

            // Check -o
            if (options.has("o") && options.hasArgument("o")) {
                //noinspection unchecked
                List<String> outputValues = (List<String>) options.valuesOf("o");
                if (options.valuesOf("o").size() > 1) {
                    throw new RuntimeException(
                            String.format(
                                    "GNUMAKE: Expected exactly one -o file in compile step:"
                                            + " %s\nbut received: \n%s\nin command:\n%s\n",
                                    this,
                                    Joiner.on("\n").join(outputValues),
                                    Joiner.on("\n").join(command.getRawFlags())));
                }
                String output = (String) options.valueOf("o");
                outputs.add(output);
            }

            // Figure out whether this command can supply terminal source file (.cpp, .c).
            // The -c, -S and -E flags indicate this case.
            boolean inputsAreSourceFiles = options.has("c") || options.has("S") || options.has("E");

            if (inputsAreSourceFiles && inputs.size() != 1) {
                throw new RuntimeException(
                        String.format(
                                "GNUMAKE: Expected exactly one source file in compile step:"
                                        + " %s\nbut received: \n%s\nin command:\n%s\n",
                                this,
                                Joiner.on("\n").join(inputs),
                                Joiner.on("\n").join(command.getRawFlags())));
            }

            return new BuildStepInfo(command, inputs, outputs, inputsAreSourceFiles);
        }

        @Override
        public boolean isMatch(@NonNull CommandLine command) {
            String executable = new File(command.getExecutable()).getName();
            return endsWithExecutableName(command, "gcc")
                    || endsWithExecutableName(command, "g++")
                    || endsWithExecutableName(command, "clang")
                    || endsWithExecutableName(command, "clang++")
                    || (executable.contains("-gcc-") && !endsWithExecutableName(command, "-ar"))
                    || (executable.contains("-g++-") && !endsWithExecutableName(command, "-ar"));
        }
    }
}
