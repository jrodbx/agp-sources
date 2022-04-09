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
import com.android.build.gradle.internal.services.DslServices;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.gradle.process.CommandLineArgumentProvider;

/** Options for configuring Java annotation processors. */
public class AnnotationProcessorOptions
        implements com.android.build.gradle.api.AnnotationProcessorOptions,
                com.android.build.api.dsl.AnnotationProcessorOptions {

    @NonNull private final List<String> classNames = Lists.newArrayList();

    @NonNull private final Map<String, String> arguments = Maps.newHashMap();

    @NonNull
    private final List<CommandLineArgumentProvider> compilerArgumentProviders = new ArrayList<>();

    @NonNull private final DslServices dslServices;

    @Inject
    public AnnotationProcessorOptions(@NonNull DslServices dslServices) {
        this.dslServices = dslServices;
    }

    @NonNull
    @Override
    public List<String> getClassNames() {
        return classNames;
    }

    public void setClassNames(@NonNull List<String> classNames) {
        this.classNames.clear();
        this.classNames.addAll(classNames);
    }

    @Override
    public void className(@NonNull String className) {
        classNames.add(className);
    }

    public void classNames(Collection<String> className) {
        classNames.addAll(className);
    }

    @Override
    public void classNames(@NonNull String... classNames) {
        this.classNames.addAll(Arrays.asList(classNames));
    }

    @NonNull
    @Override
    public Map<String, String> getArguments() {
        return arguments;
    }

    public void setArguments(@NonNull Map<String, String> arguments) {
        this.arguments.clear();
        this.arguments.putAll(arguments);
    }

    @Override
    public void argument(@NonNull String key, @NonNull String value) {
        arguments.put(key, value);
    }

    @Override
    public void arguments(@NonNull Map<String, String> arguments) {
        this.arguments.putAll(arguments);
    }

    @NonNull
    @Override
    public List<CommandLineArgumentProvider> getCompilerArgumentProviders() {
        return compilerArgumentProviders;
    }

    public void setCompilerArgumentProviders(
            @NonNull List<CommandLineArgumentProvider> compilerArgumentProviders) {
        this.compilerArgumentProviders.clear();
        this.compilerArgumentProviders.addAll(compilerArgumentProviders);
    }

    @Override
    public void compilerArgumentProvider(
            @NonNull CommandLineArgumentProvider compilerArgumentProvider) {
        this.compilerArgumentProviders.add(compilerArgumentProvider);
    }

    public void compilerArgumentProviders(
            @NonNull List<CommandLineArgumentProvider> compilerArgumentProviders) {
        this.compilerArgumentProviders.addAll(compilerArgumentProviders);
    }

    @Override
    public void compilerArgumentProviders(
            @NonNull CommandLineArgumentProvider... compilerArgumentProviders) {
        this.compilerArgumentProviders.addAll(Arrays.asList(compilerArgumentProviders));
    }

    public void _initWith(com.android.build.gradle.api.AnnotationProcessorOptions aptOptions) {
        setClassNames(aptOptions.getClassNames());
        setArguments(aptOptions.getArguments());
        setCompilerArgumentProviders(aptOptions.getCompilerArgumentProviders());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("classNames", classNames)
                .add("arguments", arguments)
                .add("compilerArgumentProviders", compilerArgumentProviders)
                .toString();
    }
}
