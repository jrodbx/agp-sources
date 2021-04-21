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

package com.android.build.gradle.internal.dependency

import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.dsl.AndroidSourceSetFactory
import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.build.gradle.internal.scope.DelayedActionsExecutor
import com.android.builder.errors.IssueReporter
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class SourceSetManager(
        project: Project,
        private val publishPackage: Boolean,
        private val dslServices : DslServices,
        private val buildArtifactActions: DelayedActionsExecutor) {
    val sourceSetsContainer: NamedDomainObjectContainer<AndroidSourceSet> = project.container(
            AndroidSourceSet::class.java,
            AndroidSourceSetFactory(project, publishPackage, dslServices))
    private val configurations: ConfigurationContainer = project.configurations
    private val logger: Logger = Logging.getLogger(this.javaClass)

    private val configuredSourceSets = mutableSetOf<String>()

    fun setUpTestSourceSet(name: String): AndroidSourceSet {
        return setUpSourceSet(name, true)
    }

    @JvmOverloads
    fun setUpSourceSet(name: String, isTestComponent: Boolean = false): AndroidSourceSet {
        val sourceSet = sourceSetsContainer.maybeCreate(name)
        if (!configuredSourceSets.contains(name)) {
            createConfigurationsForSourceSet(sourceSet, isTestComponent)
            configuredSourceSets.add(name)
        }
        return sourceSet
    }

    private fun createConfigurationsForSourceSet(
            sourceSet: AndroidSourceSet, isTestComponent: Boolean) {
        val apiName = sourceSet.apiConfigurationName
        val implementationName = sourceSet.implementationConfigurationName
        val runtimeOnlyName = sourceSet.runtimeOnlyConfigurationName
        val compileOnlyName = sourceSet.compileOnlyConfigurationName

        val api = if (!isTestComponent) {
            createConfiguration(apiName, getConfigDesc("API", sourceSet.name))
        } else {
            null
        }

        val implementation = createConfiguration(
                implementationName,
                getConfigDesc("Implementation only", sourceSet.name))
        api?.let {
            implementation.extendsFrom(it)
        }

        createConfiguration(runtimeOnlyName, getConfigDesc("Runtime only", sourceSet.name))
        createConfiguration(compileOnlyName, getConfigDesc("Compile only", sourceSet.name))

        // then the secondary configurations.
        createConfiguration(
                sourceSet.wearAppConfigurationName,
                "Link to a wear app to embed for object '" + sourceSet.name + "'.")

        createConfiguration(
                sourceSet.annotationProcessorConfigurationName,
                "Classpath for the annotation processor for '" + sourceSet.name + "'.")
    }

    /**
     * Creates a Configuration for a given source set.
     *
     * @param name the name of the configuration to create.
     * @param description the configuration description.
     * @param canBeResolved Whether the configuration can be resolved directly.
     * @return the configuration
     * @see Configuration.isCanBeResolved
     */
    private fun createConfiguration(
            name: String, description: String, canBeResolved: Boolean = false): Configuration {
        logger.debug("Creating configuration {}", name)

        val configuration = configurations.maybeCreate(name)

        configuration.isVisible = false
        configuration.description = description
        configuration.isCanBeConsumed = false
        configuration.isCanBeResolved = canBeResolved

        return configuration
    }

    private fun getConfigDesc(name: String, sourceSetName: String): String {
        return "$name dependencies for '$sourceSetName' sources."
    }

    // Check that all sourceSets in the container have been set up with configurations.
    // This will alert users who accidentally mistype the name of a sourceSet in their buildscript
    fun checkForUnconfiguredSourceSets() {
        sourceSetsContainer.forEach { sourceSet ->
            if (!configuredSourceSets.contains(sourceSet.name)) {
                val message = ("The SourceSet '${sourceSet.name}' is not recognized " +
                        "by the Android Gradle Plugin. Perhaps you misspelled something?")
                dslServices.issueReporter.reportError(IssueReporter.Type.GENERIC, message)
            }
        }
    }

    fun executeAction(action: Action<NamedDomainObjectContainer<AndroidSourceSet>>) {
        action.execute(sourceSetsContainer)
    }

    fun executeAction(action: NamedDomainObjectContainer<AndroidSourceSet>.() -> Unit) {
        action.invoke(sourceSetsContainer)
    }

    fun runBuildableArtifactsActions() {
        buildArtifactActions.runAll()
    }
}
