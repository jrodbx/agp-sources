/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.api.dsl

import org.gradle.api.Incubating
import org.gradle.process.CommandLineArgumentProvider

/** Options for configuring Java annotation processor. */
@Incubating
interface AnnotationProcessorOptions {
    /**
     * Specifies the annotation processor classes to run.
     *
     * By default, this property is empty and the plugin automatically discovers and runs annotation
     * processors that you add to the annotation processor classpath. To learn more about adding
     * annotation processor dependencies to your project, read
     * [Add annotation processors](https://d.android.com/studio/build/dependencies#annotation_processor).
     */
    val classNames: MutableList<String>

    /**
     * Adds an annotation processor class to run.
     *
     * By default, this property is empty and the plugin automatically discovers and runs annotation
     * processors that you add to the annotation processor classpath. To learn more about adding
     * annotation processor dependencies to your project, read
     * [Add annotation processors](https://d.android.com/studio/build/dependencies#annotation_processor).
     */
    fun className(className: String)

    /**
     * Adds annotation processor classes to run.
     *
     * By default, this property is empty and the plugin automatically discovers and runs annotation
     * processors that you add to the annotation processor classpath. To learn more about adding
     * annotation processor dependencies to your project, read
     * [Add annotation processors](https://d.android.com/studio/build/dependencies#annotation_processor).
     */
    fun classNames(vararg classNames: String)
    /**
     * Specifies arguments that represent primitive types for annotation processors.
     *
     * If one or more arguments represent files or directories, you must instead use
     * [compilerArgumentProviders].
     *
     * @see [compilerArgumentProviders]
     */
    val arguments: MutableMap<String, String>

    /**
     * Adds an argument that represent primitive types for annotation processors.
     *
     * If one or more arguments represent files or directories, you must instead use
     * [compilerArgumentProviders].
     *
     * @see [compilerArgumentProviders]
     */
    fun argument(key: String, value: String)

    /**
     * Adds arguments that represent primitive types for annotation processors.
     *
     * If one or more arguments represent files or directories, you must instead use
     * [compilerArgumentProviders].
     *
     * @see [compilerArgumentProviders]
     */
    fun arguments(arguments: Map<String, String>)

    /**
     * Specifies arguments for annotation processors that you want to pass to the Android plugin
     * using the [CommandLineArgumentProvider] class.
     *
     * The benefit of using this class is that it allows you or the annotation processor author to
     * improve the correctness and performance of incremental and cached clean builds by applying
     * [incremental build property type annotations](https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:up_to_date_checks).
     *
     * To learn more about how to use this class to annotate arguments for annotation processors and
     * pass them to the Android plugin, read
     * [Pass arguments to annotation processors](https://developer.android.com/studio/build/dependencies#processor-arguments).
     */
    val compilerArgumentProviders: MutableList<CommandLineArgumentProvider>

    /**
     * Adds an argument for annotation processors that you want to pass to the Android plugin using
     * the [CommandLineArgumentProvider] class.
     *
     * The benefit of using this class is that it allows you or the annotation processor author to
     * improve the correctness and performance of incremental and cached clean builds by applying
     * [incremental build property type annotations](https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:up_to_date_checks).
     *
     * To learn more about how to use this class to annotate arguments for annotation processors and
     * pass them to the Android plugin, read
     * [Pass arguments to annotation processors](https://developer.android.com/studio/build/dependencies#processor-arguments).
     */
    fun compilerArgumentProvider(compilerArgumentProvider: CommandLineArgumentProvider)

    /**
     * Adds arguments for annotation processors that you want to pass to the Android plugin using
     * the [CommandLineArgumentProvider] class.
     *
     * The benefit of using this class is that it allows you or the annotation processor author to
     * improve the correctness and performance of incremental and cached clean builds by applying
     * [incremental build property type annotations](https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:up_to_date_checks).
     *
     * To learn more about how to use this class to annotate arguments for annotation processors and
     * pass them to the Android plugin, read
     * [Pass arguments to annotation processors](https://developer.android.com/studio/build/dependencies#processor-arguments).
     */
    fun compilerArgumentProviders(vararg compilerArgumentProviders: CommandLineArgumentProvider)
}