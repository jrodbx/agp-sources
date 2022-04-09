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

import com.android.build.gradle.internal.plugins.KotlinMultiplatformAndroidPlugin.Companion.androidTargetName
import com.android.utils.appendCapitalized
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.plugin.HasCompilerOptions
import org.jetbrains.kotlin.gradle.plugin.mpp.external.ExternalDecoratedKotlinCompilation

@OptIn(ExternalKotlinTargetApi::class)
class KotlinMultiplatformAndroidCompilationImpl(
    delegate: Delegate
) : ExternalDecoratedKotlinCompilation(delegate), KotlinMultiplatformAndroidCompilation {

    // This is a workaround for non-removable parametrization for compiler options, it should be
    // safe to cast as the type will always be KotlinJvmCompilerOptions
    @Suppress("UNCHECKED_CAST")
    override val compilerOptions
        get() = super.compilerOptions as HasCompilerOptions<KotlinJvmCompilerOptions>
}

internal enum class KmpPredefinedAndroidCompilation(val compilationName: String) {
    MAIN("main"),
    UNIT_TEST("unitTest"),
    INSTRUMENTED_TEST("instrumentedTest");

    fun getNamePrefixedWithTarget() = androidTargetName.appendCapitalized(compilationName)
}
