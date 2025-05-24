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
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.capabilities.Capability
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.resources.TextResource
import org.gradle.internal.component.external.model.DefaultImmutableCapability
import java.io.File

/**
 * A class that provides data about the Gradle project object without exposing the Project itself
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
        get() = DefaultImmutableCapability(project.group.toString(), project.name, "unspecified")

    val projectDirectory: Directory
        get() = project.layout.projectDirectory

    val buildFile: File
        get() = project.buildFile

    val buildDirectory: DirectoryProperty
        get() = project.layout.buildDirectory

    val rootDir: File
        get() = project.rootDir

    val rootBuildDirectory: DirectoryProperty
        get() = project.rootProject.layout.buildDirectory

    val gradleUserHomeDir: File
            get() = project.gradle.gradleUserHomeDir

    val intermediatesDirectory: Provider<Directory>
        get() = buildDirectory.dir(SdkConstants.FD_INTERMEDIATES)

    fun intermediatesDirectory(path: String): Provider<Directory> =
        buildDirectory.dir(SdkConstants.FD_INTERMEDIATES).map {
            it.dir(path)
        }

    fun intermediatesFile(path: String): Provider<RegularFile> =
        buildDirectory.dir(SdkConstants.FD_INTERMEDIATES).map {
            it.file(path)
        }

    @Deprecated("DO NOT USE - Only use the new Gradle Property objects")
    fun createTestResources(value: String): TextResource = project.resources.text.fromString(value)

    fun hasPlugin(plugin: String): Boolean = project.plugins.hasPlugin(plugin)

    fun <T : Plugin<*>> hasPlugin(pluginClass: Class<T>): Boolean =
        project.plugins.hasPlugin(pluginClass)

    fun <T : Plugin<*>> findPlugin(pluginClass: Class<T>): T? =
        project.plugins.findPlugin(pluginClass)

    fun getTestResultsFolder(): Provider<Directory> {
        return buildDirectory.dir("test-results")
    }

    fun getReportsDir(): Provider<Directory> {
        return buildDirectory.dir(BuilderConstants.FD_REPORTS)
    }

    fun getTestReportFolder(): Provider<Directory> {
        return buildDirectory.dir("reports/tests")
    }

    fun getOutputsDir(): Provider<Directory> {
        return buildDirectory.dir(SdkConstants.FD_OUTPUTS)
    }
}
