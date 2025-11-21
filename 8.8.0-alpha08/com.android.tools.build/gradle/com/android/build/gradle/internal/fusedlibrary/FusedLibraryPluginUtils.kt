/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.fusedlibrary

import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.gradle.internal.DependencyConfigurator
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.dependency.FilterShrinkerRulesTransform
import com.android.build.gradle.internal.dependency.ShrinkerVersion
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.getAarOrJarTypeToConsume
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.DslServicesImpl
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.tasks.factory.TaskFactoryImpl
import com.android.build.gradle.options.BooleanOption
import com.android.builder.errors.IssueReporter
import com.android.builder.model.v2.ide.ProjectType
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.transform.TransformSpec
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Usage
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

const val NAMESPACED_ANDROID_RESOURCES_FOR_PRIVACY_SANDBOX_ENABLED = false

internal fun createTasks(
        project: Project,
        artifacts: ArtifactsImpl,
        artifactForPublication: Artifact.Single<RegularFile>,
        tasksCreationActions: List<TaskCreationAction<out DefaultTask>>
) {
    val taskProviders = TaskFactoryImpl(project.tasks).let { taskFactory ->
        tasksCreationActions.map { creationAction ->
            taskFactory.register(creationAction)
        }
    }

    // create anchor tasks
    project.tasks.register("assemble") { assembleTask ->
        artifactForPublication?.let { artifactTypeForPublication ->
            assembleTask.dependsOn(artifacts.get(artifactTypeForPublication))
        } ?: taskProviders.forEach { assembleTask.dependsOn(it) }
    }
}

internal fun configureTransformsForFusedLibrary(
    project: Project,
    projectServices: ProjectServices
): DependencyConfigurator {
    if (projectServices.projectOptions[BooleanOption.ENABLE_PROGUARD_RULES_EXTRACTION]) {
        project.dependencies.registerTransform(
            FilterShrinkerRulesTransform::class.java
        ) { reg: TransformSpec<FilterShrinkerRulesTransform.Parameters> ->
            reg.from
                .attribute(
                    ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
                    AndroidArtifacts.ArtifactType.UNFILTERED_PROGUARD_RULES.type
                )
            reg.to
                .attribute(
                    ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
                    AndroidArtifacts.ArtifactType.FILTERED_PROGUARD_RULES.type
                )
            reg.parameters { params: FilterShrinkerRulesTransform.Parameters ->
                params.shrinker.set(ShrinkerVersion.R8)
                params.projectName.set(project.name)
            }
        }
    }
    return DependencyConfigurator(project, projectServices).configureGeneralTransforms(
        NAMESPACED_ANDROID_RESOURCES_FOR_PRIVACY_SANDBOX_ENABLED,
        getAarOrJarTypeToConsume(
            projectServices.projectOptions,
            NAMESPACED_ANDROID_RESOURCES_FOR_PRIVACY_SANDBOX_ENABLED
        )
    )
}

internal fun getDslServices(project: Project, projectServices: ProjectServices): DslServices {
    val sdkComponentsBuildService: Provider<SdkComponentsBuildService> =
            SdkComponentsBuildService.RegistrationAction(
                    project,
                    projectServices.projectOptions
            ).execute()

    return DslServicesImpl(projectServices, sdkComponentsBuildService, ProjectType.FUSED_LIBRARIES)
}

fun configureElements(
        project: Project,
        elements: Configuration,
        usage: String,
        artifacts: ArtifactsImpl,
        publications: Map<Artifact.Single<RegularFile>, AndroidArtifacts.ArtifactType>,
) {
    elements.attributes.attribute(
            Usage.USAGE_ATTRIBUTE,
            project.objects.named(Usage::class.java, usage)
    )
    elements.isCanBeResolved = false
    elements.isCanBeConsumed = true
    elements.isTransitive = true

    elements.outgoing.variants { variants ->
        for (publication in publications) {
            // we are only interested in the last provider in the chain of transformers for this bundle.
            // Obviously, this is theoretical at this point since there is no variant API to replace
            // artifacts, there is always only one.
            val bundleTaskProvider = publication.key.let {
                artifacts
                        .getArtifactContainer(it)
                        .getTaskProviders()
                        .last()
            }
            variants.create(publication.value.type) { variant ->
                variant.artifact(bundleTaskProvider) { artifact ->
                    artifact.type = publication.value.type
                }
            }
        }
    }
}

/**
 * Intended to provide a list of Dependency from a list of ModuleVersionIdentifier.
 *
 * @param project The Gradle project used to create the dependencies.
 * @return A provider of a List<Dependency>.
 *
 */
internal fun Provider<Set<ModuleVersionIdentifier>>.toDependenciesProvider(
    project: Project,
): Provider<List<Dependency>> {
    return map { moduleVersionIdentifierSet ->
        moduleVersionIdentifierSet.map { id: ModuleVersionIdentifier ->
            project.dependencies.create(id.toString())
        }
    }
}

/**
 * For determining dependencies that are not included in the Fused Library AAR.
 *
 * As transitive dependencies are used for fused library publication and consumption, they must be
 * added to these dependencies to each directly to each configuration that requires them.
 *
 * @param sourceConfiguration
 *        configuration containing all dependencies (included in artifacts and dependencies).
 *        Direct/first level dependencies will be assumed to be packaged in the aar artifact.
 * @param issueReporter The issue reporter used to report any validation errors.
 * @return A provider of a Set<ModuleVersionIdentifier>.
 */
internal fun getFusedLibraryDependencyModuleVersionIdentifiers(
    sourceConfiguration: Configuration,
    issueReporter: IssueReporter
) : Provider<Set<ModuleVersionIdentifier>> {
    return sourceConfiguration.incoming.resolutionResult.rootComponent.map { sourceRootComponent ->
        val dependenciesIncludedInFusedAar: Set<ModuleVersionIdentifier> =
            sourceRootComponent.dependencies
                .map { (it as ResolvedDependencyResult).selected }
                .mapNotNull(ResolvedComponentResult::getModuleVersion)
                .toSet()

        val moduleVersionVersionIds = sourceConfiguration.incoming.resolutionResult.allComponents
            // ResolvedComponentResult's subclasses don't define `equals()` methods so we need to
            // compare `ResolvedComponentResult`s through `ComponentIdentifier`s (which has `equals()`
            // defined in their subclasses).
            .asSequence()
            .mapNotNull(ResolvedComponentResult::getModuleVersion)
            .minus(dependenciesIncludedInFusedAar)
            .minus(sourceRootComponent.moduleVersion)
            .toSet() as Set<ModuleVersionIdentifier>

        moduleVersionVersionIds.also {
            it.forEach { it.validate(issueReporter) }
        }
    }
}

private fun ModuleVersionIdentifier.validate(issueReporter: IssueReporter) {
    if (group in setOf("androidx.databinding", "com.android.databinding")) {
        issueReporter.reportError(
            IssueReporter.Type.GENERIC,
                "Fused Library plugin does not allow dependencies with databinding."
        )
    }
}
