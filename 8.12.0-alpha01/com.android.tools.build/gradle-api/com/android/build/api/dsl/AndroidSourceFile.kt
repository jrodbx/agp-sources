/*
 * Copyright (C) 2020 The Android Open Source Project
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

import org.gradle.api.Named

/**
 * An AndroidSourceFile represents a single file input for an Android project.
 */
interface AndroidSourceFile: Named {

    /**
     * A concise name for the source directory (typically used to identify it in a collection).
     */
    override fun getName(): String

    /**
     * Sets the location of the file.
     *
     * @param srcPath The source directory. This is evaluated as [org.gradle.api.Project.file]
     *
     * This method has a return value for legacy reasons.
     */
    fun srcFile(srcPath: Any): Any
}
