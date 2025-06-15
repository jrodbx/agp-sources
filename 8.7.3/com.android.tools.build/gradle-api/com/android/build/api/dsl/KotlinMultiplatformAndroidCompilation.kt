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

package com.android.build.api.dsl

import org.gradle.api.Action
import org.gradle.api.Incubating
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.plugin.HasCompilerOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation

@Incubating
interface KotlinMultiplatformAndroidCompilation: KotlinCompilation<KotlinCommonOptions> {
    override val compilerOptions: HasCompilerOptions<KotlinJvmCompilerOptions>

    @Deprecated("Use compilerOptions instead of kotlinOptions to configure compilations")
    override val kotlinOptions: KotlinCommonOptions

    @Deprecated(
        "Use compilerOptions instead of kotlinOptions to configure compilations",
        ReplaceWith("compilerOptions.configure { }")
    )
    override fun kotlinOptions(configure: KotlinCommonOptions.() -> Unit)

    @Deprecated(
        "Use compilerOptions instead of kotlinOptions to configure compilations",
        ReplaceWith("compilerOptions.configure { }")
    )
    override fun kotlinOptions(configure: Action<KotlinCommonOptions>)
}
