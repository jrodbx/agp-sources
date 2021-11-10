/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl

import com.android.build.gradle.internal.services.DslServices
import com.google.common.base.MoreObjects
import javax.inject.Inject
import org.gradle.api.Action

/** DSL object for javaCompileOptions. */
open class JavaCompileOptions @Inject constructor(
    dslServices: DslServices
) : com.android.build.gradle.api.JavaCompileOptions,
    com.android.build.api.dsl.JavaCompileOptions {
    /** Options for configuration the annotation processor. */
    final override val annotationProcessorOptions: AnnotationProcessorOptions =
        dslServices.newInstance(AnnotationProcessorOptions::class.java, dslServices)

    /** Configures annotation processor options. */
    fun annotationProcessorOptions(configAction: Action<AnnotationProcessorOptions>) {
        configAction.execute(annotationProcessorOptions)
    }

    override fun annotationProcessorOptions(action: com.android.build.api.dsl.AnnotationProcessorOptions.() -> Unit) {
        action.invoke(annotationProcessorOptions)
    }

    override fun toString(): String {
        return MoreObjects.toStringHelper(this)
            .add("annotationProcessorOptions", annotationProcessorOptions)
            .toString()
    }
}
