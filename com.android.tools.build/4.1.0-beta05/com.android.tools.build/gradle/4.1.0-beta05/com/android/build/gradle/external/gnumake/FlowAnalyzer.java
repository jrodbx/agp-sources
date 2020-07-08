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
import com.android.utils.SparseArray;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Analyze flow of inputs and outputs between commands (where ordering is important).
 * Result is a mapping from output file to input file(s).
 */
class FlowAnalyzer {
    /**
     * Build the flow analysis for the given set of classifications. This tracks library files back
     * through the {@link BuildStepInfo} call chain and attributes source input files (.c and .cpp)
     * to resulting library files (.so)
     *
     * @return ListMultimap where keys are the library names and values are a list of {@link
     *     BuildStepInfo} of the source files used to create the library.
     */
    @NonNull
    static ListMultimap<String, List<BuildStepInfo>> analyze(
            @NonNull String commands, @NonNull OsFileConventions policy) {
        List<BuildStepInfo> commandSummaries = CommandClassifier.classify(commands, policy);

        // For each filename, record the last command that created it.
        Map<String, Integer> outputToCommand = new HashMap<>();

        // For each command, the set of terminal inputs.
        ArrayList<Set<BuildStepInfo>> outputToTerminals = new ArrayList<>();

        // For each command, the set of outputs that was consumed.
        SparseArray<Set<String>> commandOutputsConsumed = new SparseArray<>();

        for (int i = 0; i < commandSummaries.size(); ++i) {
            BuildStepInfo current = commandSummaries.get(i);
            if (current.inputsAreSourceFiles()) {
                if (current.getInputs().size() != 1) {
                    throw new RuntimeException(
                            String.format(
                                    "GNUMAKE: Expected exactly one source file in compile step:"
                                            + " %s\nbut received: \n%s",
                                    current,
                                    Joiner.on("\n").join(current.getInputs())));
                }
            }
            commandOutputsConsumed.put(i, new HashSet<>());

            // For each input, find the line that created it or null if this is a terminal input.
            Set<BuildStepInfo> terminals = new HashSet<>();
            for (String input : current.getInputs()) {
                if (outputToCommand.containsKey(input)) {
                    int inputCommandIndex = outputToCommand.get(input);
                    terminals.addAll(outputToTerminals.get(inputCommandIndex));

                    // Record this a consumed output.
                    commandOutputsConsumed.get(inputCommandIndex).add(input);
                    continue;
                }
                if (current.inputsAreSourceFiles()) {
                    terminals.add(current);
                }
            }
            outputToTerminals.add(terminals);

            // Record the files output by this command
            for (String output : current.getOutputs()) {
                outputToCommand.put(output, i);
            }
        }

        // Emit the outputs that are never consumed.
        ListMultimap<String, List<BuildStepInfo>> result = ArrayListMultimap.create();
        for (int i = 0; i < commandSummaries.size(); ++i) {
            BuildStepInfo current = commandSummaries.get(i);
            Set<String> outputsConsumed = commandOutputsConsumed.get(i);
            for (String output : current.getOutputs()) {
                if (!outputsConsumed.contains(output) || !current.inputsAreSourceFiles()) {
                    // Sort the inputs
                    List<BuildStepInfo> ordered = new ArrayList<>();
                    ordered.addAll(outputToTerminals.get(i));
                    Collections.sort(ordered, Comparator.comparing(BuildStepInfo::getOnlyInput));
                    result.put(output, ordered);
                }
            }
        }
        return result;
    }
}
