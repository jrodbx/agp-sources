/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.core

import com.android.build.gradle.internal.dsl.AnnotationProcessorOptions
import org.gradle.process.CommandLineArgumentProvider

class MergedAnnotationProcessorOptions:
    AnnotationProcessorOptions(),
    MergedOptions<com.android.build.gradle.api.AnnotationProcessorOptions> {

    override fun setClassNames(classNames: MutableList<String>) {
        this.classNames.clear()
        this.classNames.addAll(classNames)
    }

    override fun setArguments(arguments: MutableMap<String, String>) {
        this.arguments.clear()
        this.arguments.putAll(arguments)
    }

    override fun setCompilerArgumentProviders(compilerArgumentProviders: MutableList<CommandLineArgumentProvider>) {
        this.compilerArgumentProviders.clear()
        this.compilerArgumentProviders.addAll(compilerArgumentProviders)
    }

    override val classNames = mutableListOf<String>()
    override val arguments = mutableMapOf<String, String>()
    override val compilerArgumentProviders = mutableListOf<CommandLineArgumentProvider>()

    override fun reset() {
        classNames.clear()
        arguments.clear()
        compilerArgumentProviders.clear()
    }

    override fun append(option: com.android.build.gradle.api.AnnotationProcessorOptions) {
        classNames.addAll(option.classNames)
        arguments.putAll(option.arguments)
        compilerArgumentProviders.addAll(option.compilerArgumentProviders)
    }
}
