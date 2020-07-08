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
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import java.util.List;

/**
 * Classification of a given command-line. Includes tool-specific interpretation of input and output
 * files.
 */
class BuildStepInfo {
    @NonNull
    private final CommandLine command;
    @NonNull
    private final List<String> inputs;
    @NonNull
    private final List<String> outputs;
    // true only if this command can supply terminal input files.
    // For example, .c files specified by gcc with -c flag.
    private final boolean inputsAreSourceFiles;

    BuildStepInfo(@NonNull CommandLine command, @NonNull List<String> inputs, @NonNull List<String> outputs) {
        this(command, inputs, outputs, false);
    }

    BuildStepInfo(@NonNull CommandLine command, @NonNull List<String> inputs, @NonNull List<String> outputs,
            boolean inputsAreSourceFiles) {
        this.command = command;
        this.inputs = Lists.newArrayList(inputs);
        this.outputs = Lists.newArrayList(outputs);
        this.inputsAreSourceFiles = inputsAreSourceFiles;
        for (String input : inputs) {
            if (input == null) {
                throw new RuntimeException(String.format("GNUMAKE: Unexpected null input in %s", this));
            }
        }

        if (inputsAreSourceFiles()) {
            if (getInputs().size() != 1) {
                throw new RuntimeException(
                        String.format(
                                "GNUMAKE: Expected exactly one source file in compile step:"
                                        + " %s\nbut received: \n%s\nin command:\n%s\n",
                                this,
                                Joiner.on("\n").join(getInputs()),
                                command));
            }
        }
    }

    String getOnlyInput() {
        if (getInputs().size() != 1) {
            throw new RuntimeException("Did not expect multiple inputs");
        }
        return getInputs().iterator().next();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof  BuildStepInfo)) {
            return false;
        }
        BuildStepInfo that = (BuildStepInfo) obj;
        return this.command.executable.equals(that.command.executable)
                && this.inputs.equals(that.getInputs())
                && this.outputs.equals(that.getOutputs())
                && this.inputsAreSourceFiles == that.inputsAreSourceFiles;
    }

    @NonNull
    @Override
    public String toString() {
        return command.executable
                + " in:[" + Joiner.on(' ').join(inputs) + "]"
                + " out:[" + Joiner.on(' ').join(outputs) + "]"
                + (inputsAreSourceFiles ? "SOURCE" : "INTERMEDIATE");
    }

    @NonNull
    CommandLine getCommand() {
        return command;
    }

    @NonNull
    List<String> getInputs() {
        return inputs;
    }

    @NonNull
    List<String> getOutputs() {
        return outputs;
    }

    boolean inputsAreSourceFiles() {
        return inputsAreSourceFiles;
    }
}
