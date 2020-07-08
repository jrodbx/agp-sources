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
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.errors.DeprecationReporter;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.gradle.process.CommandLineArgumentProvider;

/** Options for configuring Java annotation processors. */
@SuppressWarnings("UnnecessaryInheritDoc")
public class AnnotationProcessorOptions
        implements com.android.build.gradle.api.AnnotationProcessorOptions {

    @NonNull private final List<String> classNames = Lists.newArrayList();

    @NonNull private final Map<String, String> arguments = Maps.newHashMap();

    @NonNull
    private final List<CommandLineArgumentProvider> compilerArgumentProviders = new ArrayList<>();

    private final Boolean includeCompileClasspath = false;

    @Nullable private final DeprecationReporter deprecationReporter;

    @Inject
    public AnnotationProcessorOptions(@Nullable DeprecationReporter deprecationReporter) {
        this.deprecationReporter = deprecationReporter;
    }

    /**
     * Specifies the annotation processor classes to run.
     *
     * <p>By default, this property is empty and the plugin automatically discovers and runs
     * annotation processors that you add to the annotation processor classpath. To learn more about
     * adding annotation processor dependencies to your project, read <a
     * href="https://d.android.com/studio/build/dependencies#annotation_processor">Add annotation
     * processors</a>.
     */
    @NonNull
    @Override
    public List<String> getClassNames() {
        return classNames;
    }

    public void setClassNames(List<String> classNames) {
        this.classNames.clear();
        this.classNames.addAll(classNames);
    }

    public void className(String className) {
        classNames.add(className);
    }

    public void classNames(Collection<String> className) {
        classNames.addAll(className);
    }

    /**
     * Specifies arguments that represent primitive types for annotation processors.
     *
     * <p>If one or more arguments represent files or directories, you must instead use {@link
     * #getCompilerArgumentProviders()}.
     *
     * @see #getCompilerArgumentProviders()
     */
    @NonNull
    @Override
    public Map<String, String> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, String> arguments) {
        this.arguments.clear();
        this.arguments.putAll(arguments);
    }

    public void argument(@NonNull String key, @NonNull String value) {
        arguments.put(key, value);
    }

    public void arguments(Map<String, String> arguments) {
        this.arguments.putAll(arguments);
    }

    /**
     * Specifies arguments for annotation processors that you want to pass to the Android plugin
     * using the {@link CommandLineArgumentProvider} class.
     *
     * <p>The benefit of using this class is that it allows you or the annotation processor author
     * to improve the correctness and performance of incremental and cached clean builds by applying
     * <a
     * href="https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:up_to_date_checks">
     * incremental build property type annotations</a>.
     *
     * <p>To learn more about how to use this class to annotate arguments for annotation processors
     * and pass them to the Android plugin, read <a
     * href="https://developer.android.com/studio/build/dependencies#processor-arguments">Pass
     * arguments to annotation processors</a>.
     */
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

    public void compilerArgumentProvider(
            @NonNull CommandLineArgumentProvider compilerArgumentProvider) {
        this.compilerArgumentProviders.add(compilerArgumentProvider);
    }

    public void compilerArgumentProviders(
            @NonNull List<CommandLineArgumentProvider> compilerArgumentProviders) {
        this.compilerArgumentProviders.addAll(compilerArgumentProviders);
    }

    /**
     * Whether to include compile classpath in the processor path.
     *
     * <p>This option is removed from Android Gradle plugin 4.0 and always returns false. We won't
     * include annotation processors on your project's compile classpath and will throw warnings if
     * you use this option.
     */
    @Override
    public Boolean getIncludeCompileClasspath() {
        deprecationReporter.reportObsoleteUsage(
                "annotationProcessorOptions.includeCompileClasspath",
                DeprecationReporter.DeprecationTarget.INCLUDE_COMPILE_CLASSPATH);
        return includeCompileClasspath;
    }

    public void setIncludeCompileClasspath(@Nullable Boolean includeCompileClasspath) {
        if (deprecationReporter != null) {
            deprecationReporter.reportObsoleteUsage(
                    "annotationProcessorOptions.includeCompileClasspath",
                    DeprecationReporter.DeprecationTarget.INCLUDE_COMPILE_CLASSPATH);
        }
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
                .add("includeCompileClasspath", includeCompileClasspath)
                .toString();
    }
}
