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

@file:JvmName("ArtifactUtils")

package com.android.build.gradle.internal.ide.dependencies

import com.android.build.api.component.impl.ComponentImpl
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.ide.DependencyFailureHandler
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.services.getBuildService
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.Sets
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

/**
 * This holder class exists to allow lint to depend on the artifact collections.
 *
 * It is used as a [org.gradle.api.tasks.Nested] input to the lint model generation task.
 */
class ArtifactCollectionsInputs constructor(
    variantDependencies: VariantDependencies,
    @get:Input val projectPath: String,
    @get:Input val variantName: String,
    @get:Input val runtimeType: RuntimeType,
    @get:Internal internal val mavenCoordinatesCache: Provider<MavenCoordinatesCacheBuildService>,
    @get:Input val buildMapping: ImmutableMap<String, String>
) {
    enum class RuntimeType { FULL, PARTIAL }

    constructor(
        componentImpl: ComponentImpl,
        runtimeType: RuntimeType,
        buildMapping: ImmutableMap<String, String>
    ) : this(
        componentImpl.variantDependencies,
        componentImpl.services.projectInfo.getProject().path,
        componentImpl.name,
        runtimeType,
        getBuildService(componentImpl.services.buildServiceRegistry),
        buildMapping
    )

    @get:Nested
    val compileClasspath: ArtifactCollections = ArtifactCollections(
        variantDependencies,
        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
    )

    /** The full runtime graphs for more complex dependency models */
    @get:Nested
    @get:Optional
    val runtimeClasspath: ArtifactCollections? = if (runtimeType == RuntimeType.FULL) {
        ArtifactCollections(
            variantDependencies,
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
        )
    } else null

    /** The partial graphs for level 1 dependencies */
    @get:Nested
    val level1RuntimeArtifactCollections: Level1RuntimeArtifactCollections = Level1RuntimeArtifactCollections(variantDependencies)

    @get:Internal
    // This contains the list of all the lint jar provided by the runtime dependencies.
    // We'll match this to the component identifier of each artifact to find the lint.jar
    // that is coming via AARs.
    val runtimeLintJars: ArtifactCollection =
        variantDependencies.getArtifactCollectionForToolingModel(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            AndroidArtifacts.ArtifactScope.ALL,
            AndroidArtifacts.ArtifactType.LINT
        )

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val runtimeLintJarsFileCollection: FileCollection
        get() = runtimeLintJars.artifactFiles

    @get:Internal
    // Similar to runtimeLintJars, but for compile dependencies; there will be overlap between the
    // two in most cases, but we need compileLintJars to support compileOnly dependencies.
    val compileLintJars: ArtifactCollection =
        variantDependencies.getArtifactCollectionForToolingModel(
            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
            AndroidArtifacts.ArtifactScope.ALL,
            AndroidArtifacts.ArtifactType.LINT
        )

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val compileLintJarsFileCollection: FileCollection
        get() = compileLintJars.artifactFiles
}

// This is the partial set of file collections used by the Level1 model builder
class Level1RuntimeArtifactCollections(variantDependencies: VariantDependencies) {
    @get:Internal
    val runtimeArtifacts: ArtifactCollection =
        variantDependencies.getArtifactCollectionForToolingModel(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            AndroidArtifacts.ArtifactScope.ALL, AndroidArtifacts.ArtifactType.AAR_OR_JAR
        )

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val runtimeArtifactsFileCollection: FileCollection
        get() = runtimeArtifacts.artifactFiles

    /** See [ArtifactCollections.projectJars]. */
    @get:Internal
    val runtimeProjectJars = variantDependencies.getArtifactCollectionForToolingModel(
        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
        AndroidArtifacts.ArtifactScope.PROJECT, AndroidArtifacts.ArtifactType.JAR
    )

    @get:Internal
    val runtimeExternalJars = variantDependencies.getArtifactCollectionForToolingModel(
        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
        AndroidArtifacts.ArtifactScope.EXTERNAL, AndroidArtifacts.ArtifactType.PROCESSED_JAR
    )

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val runtimeExternalJarsFileCollection: FileCollection
        get() = runtimeExternalJars.artifactFiles
}

class ArtifactCollections(
    variantDependencies: VariantDependencies,
    @get:Internal
    val consumedConfigType: AndroidArtifacts.ConsumedConfigType
) {
    constructor(
        componentImpl: ComponentImpl,
        consumedConfigType: AndroidArtifacts.ConsumedConfigType
    ) : this(
        componentImpl.variantDependencies, consumedConfigType
    )

    /**
     * A collection containing 'all' artifacts, i.e. jar and AARs from subprojects, repositories
     * and files.
     *
     * This will give the following mapping:
     * * Java library project → Untransformed jar output
     * * Android library project → *jar* output, aar is not published between projects.
     *   This could be a separate type in the future if it was desired not to publish the jar from
     *   android-library projects.
     * * Remote jar → Untransformed jar
     * * Remote aar → Untransformed aar
     * * Local jar → untransformed jar
     * * Local aar → untransformed aar
     * * Jar wrapped as a project → untransformed aar
     * * aar wrapped as a project → untransformed aar
     *
     * Using an artifact view as that contains local dependencies, unlike
     * `configuration.incoming.resolutionResult` which only contains project and \[repository\]
     * module dependencies.
     *
     * This captures dependencies without transforming them using `AttributeCompatibilityRule`s.
     **/
    @get:Internal
    val all: ArtifactCollection = variantDependencies.getArtifactCollectionForToolingModel(
        consumedConfigType,
        AndroidArtifacts.ArtifactScope.ALL,
        AndroidArtifacts.ArtifactType.AAR_OR_JAR
    )

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val allFileCollection: FileCollection
        get() = all.artifactFiles

    @get:Internal
    val manifests: ArtifactCollection = variantDependencies.getArtifactCollectionForToolingModel(
        consumedConfigType,
        AndroidArtifacts.ArtifactScope.ALL,
        AndroidArtifacts.ArtifactType.MANIFEST
    )

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val manifestsFileCollection: FileCollection
        get() = manifests.artifactFiles

    // We still need to understand wrapped jars and aars. The former is difficult (TBD), but
    // the latter can be done by querying for EXPLODED_AAR. If a sub-project is in this list,
    // then we need to override the type to be external, rather than sub-project.
    // This is why we query for Scope.ALL
    // But we also simply need the exploded AARs for external Android dependencies so that
    // Studio can access the content.
    @get:Internal
    val explodedAars: ArtifactCollection = variantDependencies.getArtifactCollectionForToolingModel(
        consumedConfigType,
        AndroidArtifacts.ArtifactScope.ALL,
        AndroidArtifacts.ArtifactType.EXPLODED_AAR
    )

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val explodedAarFileCollection: FileCollection
        get() = explodedAars.artifactFiles

    /**
     * For project jars, query for JAR instead of PROCESSED_JAR for two reasons:
     *  - Performance: Project jars are currently considered already processed (unlike external
     *    jars).
     *  - Workaround for a Gradle issue: Gradle may throw FileNotFoundException if a project jar has
     *    not been built yet; this issue does not affect external jars (see bug 110054209).
     */
    @get:Internal
    val projectJars: ArtifactCollection = variantDependencies.getArtifactCollectionForToolingModel(
        consumedConfigType,
        AndroidArtifacts.ArtifactScope.PROJECT,
        AndroidArtifacts.ArtifactType.JAR
    )

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val projectJarsFileCollection: FileCollection
        get() = projectJars.artifactFiles

    @get:Internal
    val allCollections: Collection<ArtifactCollection>
        get() = listOf(
            all,
            manifests,
            explodedAars,
            projectJars
        )
}

/**
 * Returns a set of ResolvedArtifact where the [ResolvedArtifact.dependencyType] and
 * [ResolvedArtifact.isWrappedModule] fields have been setup properly.
 *
 * @param componentImpl the variant to get the artifacts from
 * @param consumedConfigType the type of the dependency to resolve (compile vs runtime)
 * @param dependencyFailureHandler handler for dependency resolution errors
 * @param buildMapping a build mapping from build name to root dir.
 */
fun getAllArtifacts(
    componentImpl: ComponentImpl,
    consumedConfigType: AndroidArtifacts.ConsumedConfigType,
    dependencyFailureHandler: DependencyFailureHandler?,
    buildMapping: ImmutableMap<String, String>
): Set<ResolvedArtifact> {
    val collections = ArtifactCollections(componentImpl, consumedConfigType)
    val mavenCoordinatesCache =
        getBuildService<MavenCoordinatesCacheBuildService>(
            componentImpl.services.buildServiceRegistry
        ).get()
    return getAllArtifacts(
        collections,
        dependencyFailureHandler,
        buildMapping,
        componentImpl.services.projectInfo.getProject().path,
        componentImpl.name,
        mavenCoordinatesCache
    )
}

fun getAllArtifacts(
    inputs: ArtifactCollectionsInputs,
    consumedConfigType: AndroidArtifacts.ConsumedConfigType,
    dependencyFailureHandler: DependencyFailureHandler?
): Set<ResolvedArtifact> {
    val collections = if (consumedConfigType == AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH) {
        inputs.compileClasspath
    } else {
        inputs.runtimeClasspath!!
    }
    return getAllArtifacts(
        collections,
        dependencyFailureHandler,
        inputs.buildMapping,
        inputs.projectPath,
        inputs.variantName,
        inputs.mavenCoordinatesCache.get()
    )
}

fun getAllArtifacts(
    collections: ArtifactCollections,
    dependencyFailureHandler: DependencyFailureHandler?,
    buildMapping: ImmutableMap<String, String>,
    projectPath: String,
    variantName: String,
    mavenCoordinatesCache: MavenCoordinatesCacheBuildService
): Set<ResolvedArtifact> {

    // FIXME change the way we compare dependencies b/64387392

    // we need to figure out the following:
    // - Is it an external dependency or a sub-project?
    // - Is it an android or a java dependency

    // All artifacts: see comment on collections.all
    val incomingArtifacts = collections.all

    // Then we can query for MANIFEST that will give us only the Android project so that we
    // can detect JAVA vs ANDROID.
    val manifests = collections.manifests.asMultiMap()

    val explodedAars = collections.explodedAars.asMultiMap()

    /** See [ArtifactCollections.projectJars]. */
    val projectJars = collections.projectJars.asMultiMap()

    // collect dependency resolution failures
    if (dependencyFailureHandler != null) {
        val failures = incomingArtifacts.failures
        // compute the name of the configuration
        dependencyFailureHandler.addErrors(
            projectPath
                    + "@"
                    + variantName
                    + "/"
                    + collections.consumedConfigType.getName(),
            failures
        )
    }

    // build a list of wrapped AAR, and a map of all the exploded-aar artifacts
    val aarWrappedAsProjects = explodedAars.keySet().filterIsInstance<ProjectComponentIdentifier>()

    // build a list of android dependencies based on them publishing a MANIFEST element

    // build the final list, using the main list augmented with data from the previous lists.
    val resolvedArtifactResults = incomingArtifacts.artifacts

    // use a linked hash set to keep the artifact order.
    val artifacts =
        Sets.newLinkedHashSetWithExpectedSize<ResolvedArtifact>(resolvedArtifactResults.size)

    for (resolvedComponentResult in resolvedArtifactResults) {
        val componentIdentifier = resolvedComponentResult.id.componentIdentifier

        // check if this is a wrapped module
        val isAarWrappedAsProject = aarWrappedAsProjects.contains(componentIdentifier)

        // check if this is an android external module. In this case, we want to use the exploded
        // aar as the artifact we depend on rather than just the JAR, so we swap out the
        // ResolvedArtifactResult.
        val dependencyType: ResolvedArtifact.DependencyType

        val extractedAar: Collection<ResolvedArtifactResult> = explodedAars[componentIdentifier]

        val manifest: Collection<ResolvedArtifactResult> = manifests[componentIdentifier]

        val mainArtifacts: Collection<ResolvedArtifactResult>

        val artifactType =
            resolvedComponentResult.variant.attributes.getAttribute(AndroidArtifacts.ARTIFACT_TYPE)
        when (artifactType) {
            AndroidArtifacts.ArtifactType.AAR.type -> {
                // This only happens for external dependencies - local android libraries do not
                // publish the AAR between projects.
                dependencyType = ResolvedArtifact.DependencyType.ANDROID
                mainArtifacts = listOf(resolvedComponentResult)
            }
            AndroidArtifacts.ArtifactType.JAR.type ->
                if (manifest.isNotEmpty()) {
                    dependencyType = ResolvedArtifact.DependencyType.ANDROID
                    mainArtifacts = manifest
                } else {
                    dependencyType = ResolvedArtifact.DependencyType.JAVA
                    val projectJar = projectJars[componentIdentifier]
                    mainArtifacts = if (projectJar.isNotEmpty()) {
                        projectJar
                    } else {
                        // Note use this component directly to handle classified artifacts
                        // This is tested by AppWithClassifierDepTest.
                        listOf<ResolvedArtifactResult>(resolvedComponentResult)
                    }
                }
            else -> throw IllegalStateException("Internal error: Artifact type $artifactType not expected, only jar or aar are handled.")

        }

        check(mainArtifacts.isNotEmpty()) {
            """Internal Error: No artifact found for artifactType '$componentIdentifier'
            | context: $projectPath ${variantName}
            | manifests = $manifests
            | explodedAars = $explodedAars
            | projectJars = $projectJars
        """.trimMargin()
        }


        for (mainArtifact in mainArtifacts) {
            artifacts.add(
                ResolvedArtifact(
                    mainArtifact,
                    extractedAar.firstOrNull(),
                    dependencyType,
                    isAarWrappedAsProject,
                    buildMapping,
                    mavenCoordinatesCache
                )
            )
        }
    }

    return artifacts
}

/**
 * This is a multi map to handle when there are multiple jars with the same component id.
 *
 * e.g. see `AppWithClassifierDepTest`
 */
fun ArtifactCollection.asMultiMap(): ImmutableMultimap<ComponentIdentifier, ResolvedArtifactResult> {
    return ImmutableMultimap.builder<ComponentIdentifier, ResolvedArtifactResult>()
        .also { builder ->
            for (artifact in artifacts) {
                builder.put(artifact.id.componentIdentifier, artifact)
            }
        }.build()
}
