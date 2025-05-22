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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.google.common.base.MoreObjects;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.gradle.process.CommandLineArgumentProvider;

/** Options for configuring Java annotation processors. */
public abstract class AnnotationProcessorOptions
        implements com.android.build.gradle.api.AnnotationProcessorOptions,
                com.android.build.api.dsl.AnnotationProcessorOptions {

    public abstract void setClassNames(@NonNull List<String> classNames);

    @Override
    public void className(@NonNull String className) {
        getClassNames().add(className);
    }

    public void classNames(Collection<String> className) {
        getClassNames().addAll(className);
    }

    @Override
    public void classNames(@NonNull String... classNames) {
        getClassNames().addAll(Arrays.asList(classNames));
    }

    public abstract void setArguments(@NonNull Map<String, String> arguments);

    @Override
    public void argument(@NonNull String key, @NonNull String value) {
        getArguments().put(key, value);
    }

    @Override
    public void arguments(@NonNull Map<String, String> arguments) {
        getArguments().putAll(arguments);
    }

    public abstract void setCompilerArgumentProviders(
            @NonNull List<CommandLineArgumentProvider> compilerArgumentProviders);

    @Override
    public void compilerArgumentProvider(
            @NonNull CommandLineArgumentProvider compilerArgumentProvider) {
        getCompilerArgumentProviders().add(compilerArgumentProvider);
    }

    public void compilerArgumentProviders(
            @NonNull List<CommandLineArgumentProvider> compilerArgumentProviders) {
        getCompilerArgumentProviders().addAll(compilerArgumentProviders);
    }

    @Override
    public void compilerArgumentProviders(
            @NonNull CommandLineArgumentProvider... compilerArgumentProviders) {
        getCompilerArgumentProviders().addAll(Arrays.asList(compilerArgumentProviders));
    }

    public void _initWith(com.android.build.gradle.api.AnnotationProcessorOptions aptOptions) {
        setClassNames(aptOptions.getClassNames());
        setArguments(aptOptions.getArguments());
        setCompilerArgumentProviders(aptOptions.getCompilerArgumentProviders());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("classNames", getClassNames())
                .add("arguments", getArguments())
                .add("compilerArgumentProviders", getCompilerArgumentProviders())
                .toString();
    }
}
