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

package com.android.build.gradle.options;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.model.OptionalCompilationStep;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;
import org.gradle.api.Project;

/** Determines if various options, triggered from the command line or environment, are set. */
@Immutable
public final class ProjectOptions {

    public static final String PROPERTY_TEST_RUNNER_ARGS =
            "android.testInstrumentationRunnerArguments.";

    private final ImmutableMap<ReplacedOption, String> replacedOptions;
    private final ImmutableMap<BooleanOption, Boolean> booleanOptions;
    private final ImmutableMap<OptionalBooleanOption, Boolean> optionalBooleanOptions;
    private final ImmutableMap<IntegerOption, Integer> integerOptions;
    private final ImmutableMap<StringOption, String> stringOptions;
    private final ImmutableMap<String, String> testRunnerArgs;

    public ProjectOptions(@NonNull ImmutableMap<String, Object> properties) {
        replacedOptions = readOptions(ReplacedOption.values(), properties);
        booleanOptions = readOptions(BooleanOption.values(), properties);
        optionalBooleanOptions = readOptions(OptionalBooleanOption.values(), properties);
        integerOptions = readOptions(IntegerOption.values(), properties);
        stringOptions = readOptions(StringOption.values(), properties);
        testRunnerArgs = readTestRunnerArgs(properties);
    }

    /**
     * Constructor used to obtain Project Options from the project's properties.
     *
     * @param project the project containing the properties
     */
    public ProjectOptions(@NonNull Project project) {
        this(copyProperties(project));
    }

    /**
     * Constructor used to obtain Project Options from the project's properties and modify them by
     * applying all the flags from the given map.
     *
     * @param project the project containing the properties
     * @param overwrites a map of flags overwriting project properties' values
     */
    public ProjectOptions(
            @NonNull Project project, @NonNull ImmutableMap<String, Object> overwrites) {
        this(copyAndModifyProperties(project, overwrites));
    }

    @NonNull
    private static ImmutableMap<String, Object> copyProperties(@NonNull Project project) {
        return copyAndModifyProperties(project, ImmutableMap.of());
    }

    @NonNull
    private static ImmutableMap<String, Object> copyAndModifyProperties(
            @NonNull Project project, @NonNull ImmutableMap<String, Object> overwrites) {
        ImmutableMap.Builder<String, Object> optionsBuilder = ImmutableMap.builder();
        for (Map.Entry<String, ?> entry :
                project.getExtensions().getExtraProperties().getProperties().entrySet()) {
            Object value = entry.getValue();
            if (value != null && !overwrites.containsKey(entry.getKey())) {
                optionsBuilder.put(entry.getKey(), value);
            }
        }
        for (Map.Entry<String, ?> overwrite : overwrites.entrySet()) {
            optionsBuilder.put(overwrite.getKey(), overwrite.getValue());
        }
        return optionsBuilder.build();
    }

    @NonNull
    private static <OptionT extends Option<ValueT>, ValueT>
            ImmutableMap<OptionT, ValueT> readOptions(
                    @NonNull OptionT[] values, @NonNull Map<String, ?> properties) {
        Map<String, OptionT> optionLookup =
                Arrays.stream(values).collect(Collectors.toMap(Option::getPropertyName, v -> v));
        ImmutableMap.Builder<OptionT, ValueT> valuesBuilder = ImmutableMap.builder();
        for (Map.Entry<String, ?> property : properties.entrySet()) {
            OptionT option = optionLookup.get(property.getKey());
            if (option != null) {
                ValueT value = option.parse(property.getValue());
                valuesBuilder.put(option, value);
            }
        }
        return valuesBuilder.build();
    }

    @NonNull
    private static ImmutableMap<String, String> readTestRunnerArgs(
            @NonNull Map<String, ?> properties) {
        ImmutableMap.Builder<String, String> testRunnerArgsBuilder = ImmutableMap.builder();
        for (Map.Entry<String, ?> entry : properties.entrySet()) {
            String name = entry.getKey();
            if (name.startsWith(PROPERTY_TEST_RUNNER_ARGS)) {
                String argName = name.substring(PROPERTY_TEST_RUNNER_ARGS.length());
                String argValue = entry.getValue().toString();
                testRunnerArgsBuilder.put(argName, argValue);
            }
        }
        return testRunnerArgsBuilder.build();
    }

    public boolean get(BooleanOption option) {
        return booleanOptions.getOrDefault(option, option.getDefaultValue());
    }

    @Nullable
    public Boolean get(OptionalBooleanOption option) {
        return optionalBooleanOptions.get(option);
    }

    @Nullable
    public Integer get(IntegerOption option) {
        return integerOptions.getOrDefault(option, option.getDefaultValue());
    }

    @Nullable
    public String get(StringOption option) {
        return stringOptions.getOrDefault(option, option.getDefaultValue());
    }

    @NonNull
    public Map<String, String> getExtraInstrumentationTestRunnerArgs() {
        return testRunnerArgs;
    }

    @NonNull
    public Set<OptionalCompilationStep> getOptionalCompilationSteps() {
        String values = get(StringOption.IDE_OPTIONAL_COMPILATION_STEPS);
        if (values != null) {
            List<OptionalCompilationStep> optionalCompilationSteps = new ArrayList<>();
            StringTokenizer st = new StringTokenizer(values, ",");
            while (st.hasMoreElements()) {
                optionalCompilationSteps.add(OptionalCompilationStep.valueOf(st.nextToken()));
            }
            return EnumSet.copyOf(optionalCompilationSteps);
        }
        return EnumSet.noneOf(OptionalCompilationStep.class);
    }

    public ImmutableMap<BooleanOption, Boolean> getExplicitlySetBooleanOptions() {
        return booleanOptions;
    }

    public ImmutableMap<OptionalBooleanOption, Boolean> getExplicitlySetOptionalBooleanOptions() {
        return optionalBooleanOptions;
    }

    public ImmutableMap<IntegerOption, Integer> getExplicitlySetIntegerOptions() {
        return integerOptions;
    }

    public ImmutableMap<StringOption, String> getExplicitlySetStringOptions() {
        return stringOptions;
    }

    public ImmutableMap<Option<?>, Object> getAllOptions() {
        return new ImmutableMap.Builder()
                .putAll(replacedOptions)
                .putAll(booleanOptions)
                .putAll(optionalBooleanOptions)
                .putAll(integerOptions)
                .putAll(stringOptions)
                .build();
    }
}
