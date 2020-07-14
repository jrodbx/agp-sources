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

package com.android.build.gradle.internal.plugins

import com.android.build.gradle.internal.tasks.structureplugin.CombineModuleInfoTask
import com.android.build.gradle.internal.tasks.structureplugin.GatherModuleInfoTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationVariant
import org.gradle.api.attributes.Usage
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin

const val USAGE_STRUCTURE = "android-debug-structure"
const val ARTIFACT_TYPE_MODULE_INFO = "android-debug-module-info"

const val ELEMENT_CONFIG_NAME = "structureElements"
const val CLASSPATH_CONFIG_NAME = "structureClasspath"

/**
 * Plugin to gather the project structure of modules
 */
class StructurePlugin: Plugin<Project> {

    private var combineModuleTask: CombineModuleInfoTask? = null
    private var gatherModuleTask: GatherModuleInfoTask? = null

    override fun apply(project: Project) {
        if (project == project.rootProject) {
            // create a configuration to consume the files
            val structureConfig = project.configurations.create(CLASSPATH_CONFIG_NAME).apply {
                isCanBeConsumed = false
                attributes.attribute(
                        Usage.USAGE_ATTRIBUTE,
                        project.objects.named(Usage::class.java, USAGE_STRUCTURE))
            }

            // go through all the sub modules add add as a dependencies
            val depHandler = project.dependencies
            project.subprojects.forEach {
                depHandler.add(CLASSPATH_CONFIG_NAME, it)
            }

            // create the task
            combineModuleTask = project.tasks.create(
                    "getStructure",
                    CombineModuleInfoTask::class.java,
                    CombineModuleInfoTask.ConfigAction(project, structureConfig))
        }

        // we have to do the following in afterEvaluate because we want to see all the plugins
        // in one go. Using [PluginContainer.withType] doesn't really work because we have
        // to do some conditional work depending on which plugins are applied, or not applied.
        project.afterEvaluate {
            if (it.plugins.count {
                    it is BasePlugin || it is JavaPlugin || it is JavaLibraryPlugin
                } > 0) createTask(it)

            if (gatherModuleTask == null) {
                // no plugin? still create the configuration.
                createPublishingConfiguration(it)
            }
        }
    }

    private fun createTask(project: Project) {
        // create a configuration to publish the file
        val structureConfig = createPublishingConfiguration(project)
        gatherModuleTask = project.tasks.create(
            "gatherModuleInfo",
            GatherModuleInfoTask::class.java,
            GatherModuleInfoTask.ConfigAction(project))

        // publish the json file as an artifact
        structureConfig.outgoing.variants(
                { variants: NamedDomainObjectContainer<ConfigurationVariant> ->
                    variants.create(ARTIFACT_TYPE_MODULE_INFO) { variant ->
                        variant.artifact(gatherModuleTask!!.outputProvider) { artifact ->
                            artifact.type = ARTIFACT_TYPE_MODULE_INFO
                            artifact.builtBy(gatherModuleTask)
                        }
                    }
                })

        // if this the root project, register this output as an input to the combine task
        combineModuleTask?.let {
            it.localModuleInfo = gatherModuleTask!!.outputProvider
            it.dependsOn(gatherModuleTask)
        }
    }

    private fun createPublishingConfiguration(project: Project): Configuration {
        return project.configurations.create(ELEMENT_CONFIG_NAME).apply {
            isCanBeResolved = false
            attributes.attribute(
                    Usage.USAGE_ATTRIBUTE,
                    project.objects.named(Usage::class.java, USAGE_STRUCTURE))
        }
    }
}

