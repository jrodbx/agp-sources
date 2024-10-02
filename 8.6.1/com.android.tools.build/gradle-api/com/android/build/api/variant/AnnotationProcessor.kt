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

package com.android.build.api.variant

import org.gradle.api.Incubating
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.process.CommandLineArgumentProvider

/**
 * Build-time properties for Java annotation processors inside a [Component]
 *
 * This is accessed via [JavaCompilation.annotationProcessor]
 */
@Incubating
interface AnnotationProcessor {

    /**
     * Annotation processors to run.
     *
     * If empty, processors will be automatically discovered.
     */
    val classNames: ListProperty<String>

    /**
     * Options for the annotation processors provided via key-value pairs.
     *
     * @see [argumentProviders]
     */
    val arguments: MapProperty<String, String>

    /**
     * Options for the annotation processors provided via [CommandLineArgumentProvider].
     *
     * @see [arguments]
     */
    val argumentProviders: MutableList<CommandLineArgumentProvider>
}
