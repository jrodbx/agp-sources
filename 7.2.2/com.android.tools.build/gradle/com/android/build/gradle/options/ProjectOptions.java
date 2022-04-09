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
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.profile.GradleAnalyticsEnvironment;
import com.android.build.gradle.internal.profile.GradleSystemEnvironment;
import com.android.tools.analytics.AnalyticsSettings;
import com.android.utils.Environment;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

/** Determines if various options, triggered from the command line or environment, are set. */
@Immutable
public final class ProjectOptions {

    private final ImmutableMap<String, String> testRunnerArgs;
    private final ProviderFactory providerFactory;
    private final ImmutableMap<BooleanOption, OptionValue<BooleanOption, Boolean>>
            booleanOptionValues;
    private final ImmutableMap<OptionalBooleanOption, OptionValue<OptionalBooleanOption, Boolean>>
            optionalBooleanOptionValues;
    private final ImmutableMap<IntegerOption, OptionValue<IntegerOption, Integer>>
            integerOptionValues;
    private final ImmutableMap<ReplacedOption, OptionValue<ReplacedOption, String>>
            replacedOptionValues;
    private final ImmutableMap<StringOption, OptionValue<StringOption, String>> stringOptionValues;

    public ProjectOptions(
            @NonNull ImmutableMap<String, String> customTestRunnerArgs,
            @NonNull ProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
        testRunnerArgs = readTestRunnerArgs(customTestRunnerArgs);
        booleanOptionValues = createOptionValues(BooleanOption.values());
        optionalBooleanOptionValues = createOptionValues(OptionalBooleanOption.values());
        integerOptionValues = createOptionValues(IntegerOption.values());
        replacedOptionValues = createOptionValues(ReplacedOption.values());
        stringOptionValues = createOptionValues(StringOption.values());
        // Initialize AnalyticsSettings before we access its properties in isAnalyticsEnabled
        // function
        AnalyticsSettings.initialize(
                LoggerWrapper.getLogger(ProjectOptions.class),
                null,
                new GradleAnalyticsEnvironment(providerFactory));
        Environment.initialize(new GradleSystemEnvironment(providerFactory));
    }

    @NonNull
    private <OptionT extends Option<ValueT>, ValueT>
            ImmutableMap<OptionT, OptionValue<OptionT, ValueT>> createOptionValues(
                    @NonNull OptionT[] options) {
        ImmutableMap.Builder<OptionT, OptionValue<OptionT, ValueT>> map = ImmutableMap.builder();
        for (OptionT option : options) {
            map.put(option, new OptionValue<>(option));
        }
        return map.build();
    }

    @NonNull
    private ImmutableMap<String, String> readTestRunnerArgs(Map<String, String> customArgs) {
        ImmutableMap.Builder<String, String> testRunnerArgsBuilder = ImmutableMap.builder();
        ImmutableSet.Builder<String> standardArgKeysBuilder = ImmutableSet.builder();

        // Standard test runner arguments are fully compatible with configuration caching
        for (TestRunnerArguments arg : TestRunnerArguments.values()) {
            standardArgKeysBuilder.add(arg.getShortKey());
            String argValue =
                    providerFactory
                            .gradleProperty(arg.getFullKey())
                            .forUseAtConfigurationTime()
                            .getOrNull();
            if (argValue != null) {
                testRunnerArgsBuilder.put(arg.getShortKey(), argValue);
            }
        }
        testRunnerArgsBuilder.putAll(customArgs);
        return testRunnerArgsBuilder.build();
    }

    /** Obtain the gradle property value immediately at configuration time. */
    public boolean get(@NonNull BooleanOption option) {
        Boolean value = booleanOptionValues.get(option).getValueForUseAtConfiguration();
        if (value != null) {
            return value;
        } else {
            return option.getDefaultValue();
        }
    }

    /** Returns a provider which has the gradle property value to be obtained at execution time. */
    @NonNull
    public Provider<Boolean> getProvider(@NonNull BooleanOption option) {
        return providerFactory.provider(
                () ->
                        booleanOptionValues
                                .get(option)
                                .getValueForUseAtExecution()
                                .getOrElse(option.getDefaultValue()));
    }

    /** Obtain the gradle property value immediately at configuration time. */
    @Nullable
    public Boolean get(@NonNull OptionalBooleanOption option) {
        return optionalBooleanOptionValues.get(option).getValueForUseAtConfiguration();
    }

    /** Returns a provider which has the gradle property value to be obtained at execution time. */
    @NonNull
    public Provider<Boolean> getProvider(@NonNull OptionalBooleanOption option) {
        return optionalBooleanOptionValues.get(option).getValueForUseAtExecution();
    }

    /** Obtain the gradle property value immediately at configuration time. */
    @Nullable
    public Integer get(@NonNull IntegerOption option) {
        Integer value = integerOptionValues.get(option).getValueForUseAtConfiguration();
        if (value != null) {
            return value;
        } else {
            return option.getDefaultValue();
        }
    }

    /** Returns a provider which has the gradle property value to be obtained at execution time. */
    @NonNull
    public Provider<Integer> getProvider(@NonNull IntegerOption option) {
        return providerFactory.provider(
                () -> {
                    Integer value =
                            integerOptionValues.get(option).getValueForUseAtExecution().getOrNull();
                    if (value != null) {
                        return value;
                    } else {
                        return option.getDefaultValue();
                    }
                });
    }

    /** Obtain the gradle property value immediately at configuration time. */
    @Nullable
    public String get(@NonNull StringOption option) {
        String value = stringOptionValues.get(option).getValueForUseAtConfiguration();
        if (value != null) {
            return value;
        } else {
            return option.getDefaultValue();
        }
    }

    /** Returns a provider which has the gradle property value to be obtained at execution time. */
    @NonNull
    public Provider<String> getProvider(@NonNull StringOption option) {
        return providerFactory.provider(
                () -> {
                    String value =
                            stringOptionValues.get(option).getValueForUseAtExecution().getOrNull();
                    if (value != null) {
                        return value;
                    } else {
                        return option.getDefaultValue();
                    }
                });
    }

    @NonNull
    public Map<String, String> getExtraInstrumentationTestRunnerArgs() {
        return testRunnerArgs;
    }

    public boolean isAnalyticsEnabled() {
        return AnalyticsSettings.getOptedIn()
                || get(BooleanOption.ENABLE_PROFILE_JSON)
                || get(StringOption.PROFILE_OUTPUT_DIR) != null;
    }

    public <OptionT extends Option<ValueT>, ValueT>
            ImmutableMap<OptionT, ValueT> getExplicitlySetOptions(
                    ImmutableMap<OptionT, OptionValue<OptionT, ValueT>> optionValues) {
        ImmutableMap.Builder<OptionT, ValueT> mapBuilder = ImmutableMap.builder();
        for (Map.Entry<OptionT, OptionValue<OptionT, ValueT>> entry : optionValues.entrySet()) {
            ValueT value = entry.getValue().getValueForUseAtConfiguration();
            if (value != null) {
                mapBuilder.put(entry.getKey(), value);
            }
        }
        return mapBuilder.build();
    }

    public ImmutableMap<BooleanOption, Boolean> getExplicitlySetBooleanOptions() {
        return getExplicitlySetOptions(booleanOptionValues);
    }

    public ImmutableMap<OptionalBooleanOption, Boolean> getExplicitlySetOptionalBooleanOptions() {
        return getExplicitlySetOptions(optionalBooleanOptionValues);
    }

    public ImmutableMap<IntegerOption, Integer> getExplicitlySetIntegerOptions() {
        return getExplicitlySetOptions(integerOptionValues);
    }

    public ImmutableMap<StringOption, String> getExplicitlySetStringOptions() {
        return getExplicitlySetOptions(stringOptionValues);
    }

    private ImmutableMap<ReplacedOption, String> getExplicitlySetReplacedOptions() {
        return getExplicitlySetOptions(replacedOptionValues);
    }

    public ImmutableMap<Option<?>, Object> getAllOptions() {
        return new ImmutableMap.Builder()
                .putAll(getExplicitlySetReplacedOptions())
                .putAll(getExplicitlySetBooleanOptions())
                .putAll(getExplicitlySetOptionalBooleanOptions())
                .putAll(getExplicitlySetIntegerOptions())
                .putAll(getExplicitlySetStringOptions())
                .build();
    }

    private class OptionValue<OptionT extends Option<ValueT>, ValueT> {
        @Nullable private Provider<ValueT> valueForUseAtConfiguration;
        @Nullable private Provider<ValueT> valueForUseAtExecution;
        @NonNull private OptionT option;

        OptionValue(@NonNull OptionT option) {
            this.option = option;
        }

        @Nullable
        private ValueT getValueForUseAtConfiguration() {
            if (valueForUseAtConfiguration == null) {
                valueForUseAtConfiguration = setValueForUseAtConfiguration();
            }
            return valueForUseAtConfiguration.getOrNull();
        }

        @NonNull
        private Provider<ValueT> getValueForUseAtExecution() {
            if (valueForUseAtExecution == null) {
                valueForUseAtExecution = setValueForUseAtExecution();
            }
            return valueForUseAtExecution;
        }

        @NonNull
        private Provider<ValueT> setValueForUseAtConfiguration() {
            Provider<String> rawValue = providerFactory.gradleProperty(option.getPropertyName());
            return providerFactory.provider(
                    () -> {
                        String str = rawValue.forUseAtConfigurationTime().getOrNull();
                        if (str == null) {
                            return null;
                        }
                        return option.parse(str);
                    });
        }

        @NonNull
        private Provider<ValueT> setValueForUseAtExecution() {
            Provider<String> rawValue = providerFactory.gradleProperty(option.getPropertyName());
            return providerFactory.provider(
                    () -> {
                        String str = rawValue.getOrNull();
                        if (str == null) {
                            return null;
                        }
                        return option.parse(str);
                    });
        }
    }
}
