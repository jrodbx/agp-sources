/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks.structureplugin

import com.android.build.gradle.internal.plugins.ARTIFACT_TYPE_MODULE_INFO
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

open class CombineModuleInfoTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var subModules: FileCollection
        private set

    // optional module info if root project is also a module with plugins applied
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    var localModuleInfo: Provider<RegularFile>? = null
        internal set

    // output
    @get:OutputFile
    lateinit var outputProvider: Provider<RegularFile>
        private set

    @TaskAction
    fun action() {
        // load all the modules
        var moduleList = subModules.map { ModuleInfo.readAsJsonFrom(it) }

        // if we have a local module add it to the list.
        localModuleInfo?.get()?.asFile?.let {
            val localModule = ModuleInfo.readAsJsonFrom(it)

            moduleList = mutableListOf(localModule).apply {
                addAll(moduleList)
            }
        }

        val poetInfo = ASPoetInfo()
        poetInfo.gradleVersion = project.gradle.gradleVersion
        poetInfo.agpVersion = findAgpVersion()
        poetInfo.modules = moduleList.toMutableList()

        // Remove PII.
        poetInfo.anonymize()

        poetInfo.saveAsJsonTo(outputProvider.get().asFile)
    }

    class ConfigAction(private val project: Project,
            private val structureConfig: Configuration) : Action<CombineModuleInfoTask> {
        override fun execute(task: CombineModuleInfoTask) {
            task.outputProvider = project.layout.buildDirectory.file("project-structure.json")

            task.subModules = structureConfig
                    .incoming
                    .artifactView(
                            { config ->
                                config.attributes({ container ->
                                    container.attribute<String>(
                                            AndroidArtifacts.ARTIFACT_TYPE,
                                            ARTIFACT_TYPE_MODULE_INFO)
                                })
                            })
                    .artifacts
                    .artifactFiles
        }
    }

    private fun findAgpVersion(): String {
        for (config in project.buildscript.configurations) {
            for (dep in config.allDependencies) {
                if (dep.group == "com.android.tools.build" && dep.name == "gradle")
                    return dep.version!!
            }
        }
        throw IllegalStateException(
            "Unable to find Android Gradle Plugin Version on ${project.name}")
    }
}

