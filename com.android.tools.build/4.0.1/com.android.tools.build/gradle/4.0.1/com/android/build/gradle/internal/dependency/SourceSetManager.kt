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
import com.android.build.gradle.internal.api.dsl.DslScope
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
        private val dslScope : DslScope,
        private val buildArtifactActions: DelayedActionsExecutor) {
    val sourceSetsContainer: NamedDomainObjectContainer<AndroidSourceSet> = project.container(
            AndroidSourceSet::class.java,
            AndroidSourceSetFactory(project, publishPackage, dslScope))
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

        // deprecated configurations first.
        val compileName = sourceSet.compileConfigurationName
        // due to compatibility with other plugins and with Gradle sync,
        // we have to keep 'compile' as resolvable.
        // TODO Fix this in gradle sync.
        val compile = createConfiguration(
                compileName,
                getDeprecatedConfigDesc("Compile", sourceSet.name, implementationName),
                "compile" == compileName || "testCompile" == compileName /*canBeResolved*/)
        compile.dependencies
                .whenObjectAdded(
                        RenamedConfigurationAction(
                                if (isTestComponent) implementationName
                                else "$implementationName' and '$apiName",
                                compileName,
                                dslScope.deprecationReporter,
                                DeprecationReporter.DeprecationTarget.CONFIG_NAME))

        val packageConfigDescription: String
        packageConfigDescription = if (publishPackage) {
            getDeprecatedConfigDesc("Publish",
                sourceSet.name,
                runtimeOnlyName)
        } else {
            getDeprecatedConfigDesc("Apk",
                sourceSet.name,
                runtimeOnlyName)
        }

        val apkName = sourceSet.packageConfigurationName
        val apk = createConfiguration(apkName, packageConfigDescription)
        apk.dependencies
                .whenObjectAdded(
                        RenamedConfigurationAction(
                                runtimeOnlyName,
                                apkName,
                                dslScope.deprecationReporter,
                                DeprecationReporter.DeprecationTarget.CONFIG_NAME))

        val providedName = sourceSet.providedConfigurationName
        val provided = createConfiguration(
                providedName,
                getDeprecatedConfigDesc("Provided", sourceSet.name, compileOnlyName))
        provided.dependencies
                .whenObjectAdded(
                        RenamedConfigurationAction(
                                compileOnlyName,
                                providedName,
                                dslScope.deprecationReporter,
                                DeprecationReporter.DeprecationTarget.CONFIG_NAME))

        // then the new configurations.
        val api = createConfiguration(apiName, getConfigDesc("API", sourceSet.name))
        api.extendsFrom(compile)
        if (isTestComponent) {
            api.dependencies
                    .whenObjectAdded(
                            RenamedConfigurationAction(
                                    implementationName,
                                    apiName,
                                    dslScope.deprecationReporter,
                                    DeprecationReporter.DeprecationTarget.CONFIG_NAME))
        }

        val implementation = createConfiguration(
                implementationName,
                getConfigDesc("Implementation only", sourceSet.name))
        implementation.extendsFrom(api)

        val runtimeOnly = createConfiguration(
                runtimeOnlyName, getConfigDesc("Runtime only", sourceSet.name))
        runtimeOnly.extendsFrom(apk)

        val compileOnly = createConfiguration(
                compileOnlyName, getConfigDesc("Compile only", sourceSet.name))
        compileOnly.extendsFrom(provided)

        // then the secondary configurations.
        val wearConfig = createConfiguration(
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

    private fun getDeprecatedConfigDesc(
            name: String, sourceSetName: String, replacement: String): String {
        return ("$name dependencies for '$sourceSetName' sources " +
                "(deprecated: use '$replacement' instead).")
    }

    // Check that all sourceSets in the container have been set up with configurations.
    // This will alert users who accidentally mistype the name of a sourceSet in their buildscript
    fun checkForUnconfiguredSourceSets() {
        sourceSetsContainer.forEach { sourceSet ->
            if (!configuredSourceSets.contains(sourceSet.name)) {
                val message = ("The SourceSet '${sourceSet.name}' is not recognized " +
                        "by the Android Gradle Plugin. Perhaps you misspelled something?")
                dslScope.issueReporter.reportError(IssueReporter.Type.GENERIC, message)
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


class RenamedConfigurationAction(
    private val replacement: String,
    private val oldName: String,
    private val deprecationReporter: DeprecationReporter,
    private val deprecationTarget: DeprecationReporter.DeprecationTarget = DeprecationReporter.DeprecationTarget.CONFIG_NAME) : Action<Dependency> {
    private var warningPrintedAlready = false

    override fun execute(dependency: Dependency) {
        if (!warningPrintedAlready) {
            warningPrintedAlready = true
            deprecationReporter.reportRenamedConfiguration(
                replacement, oldName, deprecationTarget)
        }
    }
}
