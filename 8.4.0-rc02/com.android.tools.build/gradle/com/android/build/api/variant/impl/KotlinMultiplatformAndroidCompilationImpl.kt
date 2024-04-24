/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.build.api.dsl.KotlinMultiplatformAndroidCompilation
import com.android.build.gradle.internal.plugins.KotlinMultiplatformAndroidPlugin.Companion.getNamePrefixedWithAndroidTarget
import org.gradle.api.Action
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.plugin.HasCompilerOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetHierarchy
import org.jetbrains.kotlin.gradle.plugin.mpp.external.DecoratedExternalKotlinCompilation

@OptIn(ExternalKotlinTargetApi::class)
open class KotlinMultiplatformAndroidCompilationImpl(
    delegate: Delegate
) : DecoratedExternalKotlinCompilation(delegate), KotlinMultiplatformAndroidCompilation {

    // This is a workaround for non-removable parametrization for compiler options, it should be
    // safe to cast as the type will always be KotlinJvmCompilerOptions
    @Suppress("UNCHECKED_CAST")
    override val compilerOptions
        get() = super.compilerOptions as HasCompilerOptions<KotlinJvmCompilerOptions>

    @Deprecated("Use compilerOptions instead of kotlinOptions to configure compilations")
    override val kotlinOptions: KotlinCommonOptions
        get() = super.kotlinOptions

    @Deprecated(
        "Use compilerOptions instead of kotlinOptions to configure compilations",
        ReplaceWith("compilerOptions.configure { }")
    )
    override fun kotlinOptions(configure: KotlinCommonOptions.() -> Unit) {
        super.kotlinOptions(configure)
    }

    @Deprecated(
        "Use compilerOptions instead of kotlinOptions to configure compilations",
        ReplaceWith("compilerOptions.configure { }")
    )
    override fun kotlinOptions(configure: Action<KotlinCommonOptions>) {
        super.kotlinOptions(configure)
    }
}

internal enum class KmpAndroidCompilationType(
    val defaultCompilationName: String,
    val defaultSourceSetName: String = defaultCompilationName.getNamePrefixedWithAndroidTarget(),
    val defaultSourceSetTreeName: String?
) {
    MAIN(
        defaultCompilationName = "main",
        defaultSourceSetTreeName = KotlinTargetHierarchy.SourceSetTree.main.name
    ), TEST_ON_JVM(
        defaultCompilationName = "testOnJvm",
        defaultSourceSetTreeName = KotlinTargetHierarchy.SourceSetTree.test.name
    ), TEST_ON_DEVICE(
        defaultCompilationName = "testOnDevice",
        defaultSourceSetTreeName = null
    )
}
