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

@file:Suppress("DEPRECATION")

package com.android.build.gradle.internal

import com.android.build.gradle.internal.services.BuiltInKotlinServices
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import org.jetbrains.kotlin.gradle.plugin.HasCompilerOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilationOutput
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jetbrains.kotlin.tooling.core.MutableExtras
import org.jetbrains.kotlin.tooling.core.mutableExtrasOf

/** Implementation of [KotlinCompilation] for built-in Kotlin support. */
class BuiltInKotlinJvmAndroidCompilation(
    override val project: Project,
    override val compilationName: String,
    override val compileTaskProvider: TaskProvider<out KotlinJvmCompile>,
    kotlinServices: BuiltInKotlinServices,
    kotlinSourceDirectories: ListProperty<Directory>,
) : KotlinCompilation<KotlinJvmOptions> {

    /*
     * 1. Supported APIs
     */

    override val target: KotlinAndroidTarget = kotlinServices.kotlinAndroidProjectExtension.target
    override val platformType: KotlinPlatformType = target.platformType

    override fun getName(): String = compilationName
    override val disambiguatedName: String = compilationName

    override val defaultSourceSet: KotlinSourceSet =
        kotlinServices.kotlinAndroidProjectExtension.sourceSets.maybeCreate(compilationName).also {
            // Set kotlinSourceDirectories. Note that:
            //   1. kotlinSourceDirectories is a Provider<List<*>>, but it is itself not a List<*>,
            //      so we need to convert it to a List for APIs that require a List.
            //   2. We use setSrcDirs instead of srcDir because we want to overwrite any directories
            //      that were previously set. For example, for compilation `debugAndroidTest`, KGP
            //      adds a source directory named `src/debugAndroidTest/kotlin`, but this is not
            //      correct. The directories should be `/src/androidTest/kotlin`,
            //      `src/androidTest/java`, `src/androidTestDebug/kotlin`,
            //      and `src/androidTestDebug/java`.
            it.kotlin.setSrcDirs(listOf(kotlinSourceDirectories))
        }
    override val kotlinSourceSets: Set<KotlinSourceSet> = setOf(defaultSourceSet)
    override val allKotlinSourceSets: Set<KotlinSourceSet> = kotlinSourceSets
    override fun defaultSourceSet(configure: KotlinSourceSet.() -> Unit) = defaultSourceSet.configure()

    override val compileKotlinTaskName: String = compileTaskProvider.name

    override val extras: MutableExtras = mutableExtrasOf()

    /*
     * 2. The following APIs that are currently not supported. We may or may not support them in the
     *    long run (tracked by b/409528883).
     */

    override val compileAllTaskName: String
        get() = notSupported("compileAllTaskName")

    override val associatedCompilations: Set<KotlinCompilation<*>>
        get() = notSupported("associatedCompilations")

    @ExperimentalKotlinGradlePluginApi
    override val allAssociatedCompilations: Set<KotlinCompilation<*>>
        get() = notSupported("allAssociatedCompilations")

    override fun associateWith(other: KotlinCompilation<*>) =
        notSupported("associateWith")

    override val compileDependencyConfigurationName: String
        get() = notSupported("compileDependencyConfigurationName")

    override var compileDependencyFiles: FileCollection
        get() = notSupported("getCompileDependencyFiles")
        set(_) = notSupported("setCompileDependencyFiles")

    override val runtimeDependencyConfigurationName: String
        get() = notSupported("runtimeDependencyConfigurationName")

    override val runtimeDependencyFiles: FileCollection
        get() = notSupported("runtimeDependencyFiles")

    override val apiConfigurationName: String
        get() = notSupported("apiConfigurationName")

    override val implementationConfigurationName: String
        get() = notSupported("implementationConfigurationName")

    override val compileOnlyConfigurationName: String
        get() = notSupported("compileOnlyConfigurationName")

    override val runtimeOnlyConfigurationName: String
        get() = notSupported("runtimeOnlyConfigurationName")

    override fun dependencies(configure: KotlinDependencyHandler.() -> Unit) =
        notSupported("dependencies")

    override fun dependencies(configure: Action<KotlinDependencyHandler>) =
        notSupported("dependencies")

    override fun getAttributes(): AttributeContainer =
        notSupported("getAttributes")

    override val output: KotlinCompilationOutput
        get() = notSupported("output")

    /*
     * 3. Deprecated APIs
     */

    @Deprecated(
        message = "Scheduled for removal with Kotlin 2.3. Please see the migration guide: https://kotl.in/compilation-source-deprecation",
        level = DeprecationLevel.ERROR,
    )
    override fun source(sourceSet: KotlinSourceSet) =
        deprecated("source", "Scheduled for removal with Kotlin 2.3. Please see the migration guide: https://kotl.in/compilation-source-deprecation")

    @Deprecated(
        message = "Please migrate to the compilerOptions DSL. More details are here: https://kotl.in/u1r8ln",
        level = DeprecationLevel.ERROR,
    )
    override val kotlinOptions: KotlinJvmOptions
        get() = deprecated("kotlinOptions", "Please migrate to the compilerOptions DSL. More details are here: https://kotl.in/u1r8ln")

    @Deprecated("To configure compilation compiler options use 'compileTaskProvider':\ncompilation.compileTaskProvider.configure{\n    compilerOptions {}\n}")
    override val compilerOptions: HasCompilerOptions<*>
        get() = deprecated("compilerOptions", "To configure compilation compiler options use 'compileTaskProvider':\ncompilation.compileTaskProvider.configure{\n    compilerOptions {}\n}")

    @Deprecated(
        message = "Accessing task instance directly is deprecated. Scheduled for removal in Kotlin 2.3.",
        replaceWith = ReplaceWith("compileTaskProvider"),
        level = DeprecationLevel.ERROR,
    )
    override val compileKotlinTask: KotlinCompile<KotlinJvmOptions>
        get() = deprecated("compileKotlinTask", "Accessing task instance directly is deprecated. Scheduled for removal in Kotlin 2.3.")

    @Deprecated(
        message = "Replaced with compileTaskProvider. Scheduled for removal in Kotlin 2.3.",
        replaceWith = ReplaceWith("compileTaskProvider"),
        level = DeprecationLevel.ERROR,
    )
    override val compileKotlinTaskProvider: TaskProvider<out KotlinCompile<KotlinJvmOptions>>
        get() = deprecated("compileKotlinTaskProvider", "Replaced with compileTaskProvider. Scheduled for removal in Kotlin 2.3.")

    /*
     * Error reporting
     */

    class BuiltInKotlinUnsupportedApiException(message: String) : Exception(message)

    private fun notSupported(api: String): Nothing =
        throw BuiltInKotlinUnsupportedApiException(
            """
            The '${BuiltInKotlinJvmAndroidCompilation::class.java.name}.$api' API is currently not supported.
            Please report this issue at https://issuetracker.google.com/409528883.
            """.trimIndent()
        )

    private fun deprecated(api: String, message: String): Nothing =
        throw BuiltInKotlinUnsupportedApiException(
            """
            The '${BuiltInKotlinJvmAndroidCompilation::class.java.name}.$api' API is not supported.
            Please report this issue at https://issuetracker.google.com/409528883.
            Deprecation message from the Kotlin Gradle plugin: $message
            """.trimIndent()
        )

}
