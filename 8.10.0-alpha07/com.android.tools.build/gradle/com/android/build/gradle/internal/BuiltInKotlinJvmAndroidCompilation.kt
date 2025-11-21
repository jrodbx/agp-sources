/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.internal

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import org.jetbrains.kotlin.gradle.plugin.HasCompilerOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilationOutput
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.tooling.core.MutableExtras


// TODO(b/341765853) - implement the rest of this to support more Kotlin compiler plugins
@Suppress("DEPRECATION")
class BuiltInKotlinJvmAndroidCompilation(
    override val compilationName: String,
    override val target: KotlinTarget,
    override val compileTaskProvider: TaskProvider<out KotlinCompilationTask<*>>
) : KotlinCompilation<KotlinJvmOptions> {
    override val kotlinSourceSets: Set<KotlinSourceSet>
        get() = throw RuntimeException("Not yet implemented")
    override val allKotlinSourceSets: Set<KotlinSourceSet>
        get() = throw RuntimeException("Not yet implemented")

    override fun defaultSourceSet(configure: KotlinSourceSet.() -> Unit) {
        throw RuntimeException("Not yet implemented")
    }

    override val defaultSourceSet: KotlinSourceSet
        get() = throw RuntimeException("Not yet implemented")
    override val compileDependencyConfigurationName: String
        get() = throw RuntimeException("Not yet implemented")
    override var compileDependencyFiles: FileCollection
        get() = throw RuntimeException("Not yet implemented")
        set(value) = throw RuntimeException("Not yet implemented")
    override val runtimeDependencyConfigurationName: String?
        get() = throw RuntimeException("Not yet implemented")
    override val runtimeDependencyFiles: FileCollection?
        get() = throw RuntimeException("Not yet implemented")
    override val output: KotlinCompilationOutput
        get() = throw RuntimeException("Not yet implemented")
    override val compileKotlinTaskName: String
        get() = throw RuntimeException("Not yet implemented")
    @Deprecated("To configure compilation compiler options use 'compileTaskProvider':\ncompilation.compileTaskProvider.configure{\n    compilerOptions {}\n}")
    override val compilerOptions: HasCompilerOptions<*>
        get() = throw RuntimeException("Not yet implemented")
    @Deprecated(
        "Accessing task instance directly is deprecated",
        replaceWith = ReplaceWith("compileTaskProvider")
    )
    override val compileKotlinTask: KotlinCompile<KotlinJvmOptions>
        get() = throw RuntimeException("Not yet implemented")
    @Deprecated(
        "Replaced with compileTaskProvider",
        replaceWith = ReplaceWith("compileTaskProvider")
    )
    override val compileKotlinTaskProvider: TaskProvider<out KotlinCompile<KotlinJvmOptions>>
        get() = throw RuntimeException("Not yet implemented")
    @Deprecated("Please migrate to the compilerOptions DSL. More details are here: https://kotl.in/u1r8ln")
    override val kotlinOptions: KotlinJvmOptions
        get() = throw RuntimeException("Not yet implemented")
    override val compileAllTaskName: String
        get() = throw RuntimeException("Not yet implemented")

    @Deprecated("scheduled for removal with Kotlin 2.1")
    override fun source(sourceSet: KotlinSourceSet) {
        throw RuntimeException("Not yet implemented")
    }

    override fun associateWith(other: KotlinCompilation<*>) {
        throw RuntimeException("Not yet implemented")
    }

    override val associatedCompilations: Set<KotlinCompilation<*>>
        get() = throw RuntimeException("Not yet implemented")

    @ExperimentalKotlinGradlePluginApi
    override val allAssociatedCompilations: Set<KotlinCompilation<*>>
        get() = throw RuntimeException("Not yet implemented")
    override val project: Project
        get() = throw RuntimeException("Not yet implemented")
    override val extras: MutableExtras
        get() = throw RuntimeException("Not yet implemented")

    override fun getAttributes(): AttributeContainer {
        throw RuntimeException("Not yet implemented")
    }

    override fun dependencies(configure: KotlinDependencyHandler.() -> Unit) {
        throw RuntimeException("Not yet implemented")
    }

    override fun dependencies(configure: Action<KotlinDependencyHandler>) {
        throw RuntimeException("Not yet implemented")
    }

    override val apiConfigurationName: String
        get() = throw RuntimeException("Not yet implemented")
    override val implementationConfigurationName: String
        get() = throw RuntimeException("Not yet implemented")
    override val compileOnlyConfigurationName: String
        get() = throw RuntimeException("Not yet implemented")
    override val runtimeOnlyConfigurationName: String
        get() = throw RuntimeException("Not yet implemented")
}
