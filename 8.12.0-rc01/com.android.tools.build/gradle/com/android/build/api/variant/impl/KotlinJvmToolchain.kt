/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.api.variant.impl

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions

object KotlinJvmToolchain {
    internal fun wireJvmTargetToToolchain(
        compilerOptions: KotlinJvmCompilerOptions,
        project: Project,
    ) {
        // Configure kotlin compiler with the jvm toolchain, similar to the JavaBasePlugin
        // (https://github.com/gradle/gradle/blob/66010b2/subprojects/plugins/src/main/java/org/gradle/api/plugins/JavaBasePlugin.java#L204)
        val toolchain = project.extensions.getByType(JavaPluginExtension::class.java).toolchain
        val service = project.extensions.getByType(JavaToolchainService::class.java)
        val javaLauncher = service.launcherFor(toolchain)
        wireJvmTargetToJvm(compilerOptions, javaLauncher.map(::mapToJvm))
    }

    private fun wireJvmTargetToJvm(
        jvmCompilerOptions: KotlinJvmCompilerOptions,
        toolchainJvm: Provider<Jvm>,
    ) {
        jvmCompilerOptions.jvmTarget.convention(
            toolchainJvm.map { jvm ->
                convertJavaVersionToJvmTarget(requireNotNull(jvm.javaVersion))
            }.orElse(JvmTarget.DEFAULT)
        )
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun convertJavaVersionToJvmTarget(
        javaVersion: JavaVersion,
    ): JvmTarget = try {
            JvmTarget.fromTarget(javaVersion.toString())
        } catch (_: IllegalArgumentException) {
            val fallbackTarget = JvmTarget.entries.last()
            fallbackTarget
        }

    private fun mapToJvm(javaLauncher: JavaLauncher): Jvm {
        val metadata = javaLauncher.metadata
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        return Jvm.discovered(
            metadata.installationPath.asFile,
            null,
            metadata.languageVersion.asInt()
        )
    }
}
