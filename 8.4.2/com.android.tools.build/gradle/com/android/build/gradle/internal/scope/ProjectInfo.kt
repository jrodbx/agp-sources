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

package com.android.build.gradle.internal.scope

import com.android.SdkConstants
import com.android.builder.core.BuilderConstants
import org.gradle.api.Project
import org.gradle.api.capabilities.Capability
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.resources.TextResource
import org.gradle.internal.component.external.model.ImmutableCapability
import java.io.File

/**
 * A class that provides data about the Gradle project object without exposing the Project itself
 *
 * FIXME remove getProject() and old File-based APIs.
 */
class ProjectInfo(private val project: Project) {

    companion object {
        @JvmStatic
        fun Project.getBaseName(): Provider<String> =
            this.extensions.getByType(BasePluginExtension::class.java).archivesName
    }

    fun getProjectBaseName(): Provider<String> = project.getBaseName()

    val path: String
        get() = project.path

    val name: String
        get() = project.name

    val group: String
        get() = project.group.toString()

    val version: String
        get() = project.version.toString()

    val defaultProjectCapability: Capability
        get() = ImmutableCapability(project.group.toString(), project.name, "unspecified")

    val projectDirectory: Directory
        get() = project.layout.projectDirectory

    val buildFile: File
        get() = project.buildFile

    val buildDirectory: DirectoryProperty
        get() = project.layout.buildDirectory

    val rootDir: File
        get() = project.rootDir

    @Deprecated("Use rootBuildDirectory")
    val rootBuildDir: File
        get() = project.rootProject.buildDir

    val rootBuildDirectory: DirectoryProperty
        get() = project.rootProject.layout.buildDirectory

    val gradleUserHomeDir: File
            get() = project.gradle.gradleUserHomeDir

    val intermediatesDirectory: Provider<Directory>
        get() = project.layout.buildDirectory.dir(SdkConstants.FD_INTERMEDIATES)

    fun intermediatesDirectory(path: String): Provider<Directory> =
        project.layout.buildDirectory.dir(SdkConstants.FD_INTERMEDIATES).map {
            it.dir(path)
        }

    fun intermediatesFile(path: String): Provider<RegularFile> =
        project.layout.buildDirectory.dir(SdkConstants.FD_INTERMEDIATES).map {
            it.file(path)
        }

    @Deprecated("DO NOT USE - Only use the new Gradle Property objects")
    fun createTestResources(value: String): TextResource = project.resources.text.fromString(value)

    fun hasPlugin(plugin: String): Boolean = project.plugins.hasPlugin(plugin)

    @Deprecated("Use buildDirectory instead")
    fun getBuildDir(): File {
        return project.buildDir
    }

    fun getTestResultsFolder(): File? {
        return File(getBuildDir(), "test-results")
    }

    fun getReportsDir(): File {
        return File(getBuildDir(), BuilderConstants.FD_REPORTS)
    }

    fun getTestReportFolder(): File? {
        return File(getBuildDir(), "reports/tests")
    }

    @Deprecated("Use the version that returns a provider")
    fun getIntermediatesDir(): File {
        return File(getBuildDir(), SdkConstants.FD_INTERMEDIATES)
    }

    fun getTmpFolder(): File {
        return File(getIntermediatesDir(), "tmp")
    }

    fun getOutputsDir(): File {
        return File(getBuildDir(), SdkConstants.FD_OUTPUTS)
    }

    fun getJacocoAgentOutputDirectory(): File {
        return File(getIntermediatesDir(), "jacoco")
    }

    fun getJacocoAgent(): File {
        return File(getJacocoAgentOutputDirectory(), "jacocoagent.jar")
    }
}
