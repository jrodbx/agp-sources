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

package com.android.build.gradle.internal

import com.android.build.api.variant.impl.KotlinMultiplatformAndroidCompilation
import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidExtension
import org.gradle.api.JavaVersion

/**
 * Implementation of [CompileOptions] that is based on kotlin multiplatform APIs for internal use.
 */
internal class KotlinMultiplatformCompileOptionsImpl(
    private val extension: KotlinMultiplatformAndroidExtension
): CompileOptions() {

    override var isCoreLibraryDesugaringEnabled
        get() = extension.isCoreLibraryDesugaringEnabled
        set(_) {
            throw IllegalAccessException("Compile options for kmp variants are read only.")
        }

    override var targetCompatibility: JavaVersion
        get() = super.targetCompatibility
        set(_) {
            throw IllegalAccessException("Compile options for kmp variants are read only.")
        }

    override var sourceCompatibility: JavaVersion
        get() = super.sourceCompatibility
        set(_) {
            throw IllegalAccessException("Compile options for kmp variants are read only.")
        }

    override var encoding: String
        get() = super.encoding
        set(_) {
            throw IllegalAccessException("Compile options for kmp variants are read only.")
        }

    override fun sourceCompatibility(sourceCompatibility: Any) {
        throw IllegalAccessException("Compile options for kmp variants are read only.")
    }

    override fun targetCompatibility(targetCompatibility: Any) {
        throw IllegalAccessException("Compile options for kmp variants are read only.")
    }

    fun initFromCompilation(
        compilation: KotlinMultiplatformAndroidCompilation
    ) {
        compilation.compilerOptions.options.jvmTarget.orNull?.let {
            _targetCompatibility = JavaVersion.toVersion(it.target)
            _sourceCompatibility = JavaVersion.toVersion(it.target)
        }
    }
}
