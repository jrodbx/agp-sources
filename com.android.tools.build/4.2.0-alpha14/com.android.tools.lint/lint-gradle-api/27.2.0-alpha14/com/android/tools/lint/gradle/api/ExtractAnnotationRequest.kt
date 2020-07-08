/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.lint.gradle.api

import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger
import java.io.File

class ExtractAnnotationRequest(
    /**
     * The output .txt file to write the typedef recipe file to. A "recipe" file
     * is a file which describes typedef classes, typically ones that should
     * be deleted.
     */
    val typedefFile: File,

    /**
     * A logger to write warnings to
     */
    val logger: Logger,

    /**
     * Location of class files. If set, any non-public typedef source retention annotations
     * will be removed prior to .jar packaging.
     */
    val classDir: FileCollection,

    /** The output .zip file to write the annotations database to, if any */
    val output: File? = null,

    /** Source files to extract annotations from */
    val sourceFiles: List<File>,

    /** The roots from the source files */
    val sourceRoots: List<File>,

    /** The roots from the classpath */
    val classpathRoots: List<File>
)
