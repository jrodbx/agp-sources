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

package com.android.build.gradle.internal.ide.v2

import com.android.builder.model.v2.ide.BasicVariant
import com.android.builder.model.v2.ide.ProjectType
import com.android.builder.model.v2.ide.SourceSetContainer
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.BasicAndroidProject
import java.io.File
import java.io.Serializable

/**
 * Implementation of [AndroidProject] for serialization via the Tooling API.
 */
data class BasicAndroidProjectImpl(
    override val path: String,
    override val projectType: ProjectType,
    override val mainSourceSet: SourceSetContainer?,
    override val buildTypeSourceSets: Collection<SourceSetContainer>,
    override val productFlavorSourceSets: Collection<SourceSetContainer>,
    override val variants: Collection<BasicVariant>,
    override val bootClasspath: Collection<File>,
    override val buildFolder: File,
) : BasicAndroidProject, Serializable {

    // Not used by the IDE (since H Canaries); kept only because of binary compatibility.
    @Deprecated("Since AGP 8.2 this is not set; the IDE uses the Gradle build file system path for dependency resolution.")
    override val buildName: String = "n/a"

    companion object {
        @JvmStatic
        private val serialVersionUID: Long = 1L
    }
}
