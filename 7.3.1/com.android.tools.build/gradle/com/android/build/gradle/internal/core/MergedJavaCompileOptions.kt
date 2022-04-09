/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.gradle.api.JavaCompileOptions
import com.android.build.gradle.internal.dsl.AnnotationProcessorOptions

/** Implementation of CoreJavaCompileOptions used to merge multiple configs together.  */
abstract class MergedJavaCompileOptions : JavaCompileOptions,
    com.android.build.api.dsl.JavaCompileOptions,
    MergedOptions<JavaCompileOptions> {

    abstract override val annotationProcessorOptions: AnnotationProcessorOptions

    override fun reset() {
        annotationProcessorOptions.classNames.clear()
        annotationProcessorOptions.arguments.clear()
        annotationProcessorOptions.compilerArgumentProviders.clear()
    }

    override fun append(option: JavaCompileOptions) {
        annotationProcessorOptions.classNames.addAll(
            option.annotationProcessorOptions.classNames
        )
        annotationProcessorOptions.arguments.putAll(
            option.annotationProcessorOptions.arguments
        )
        annotationProcessorOptions.compilerArgumentProviders.addAll(
            option.annotationProcessorOptions.compilerArgumentProviders
        )
    }
}
