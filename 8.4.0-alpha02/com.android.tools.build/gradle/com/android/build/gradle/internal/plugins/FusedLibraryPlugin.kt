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

package com.android.build.gradle.internal.plugins

import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.attributes.BuildTypeAttr
import com.android.build.api.dsl.FusedLibraryExtension
import com.android.build.gradle.internal.dsl.FusedLibraryExtensionImpl
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryVariantScope
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryVariantScopeImpl
import com.android.build.gradle.internal.fusedlibrary.SegregatingConstraintHandler
import com.android.build.gradle.internal.fusedlibrary.configureTransforms
import com.android.build.gradle.internal.fusedlibrary.createTasks
import com.android.build.gradle.internal.fusedlibrary.getDslServices
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.tasks.MergeJavaResourceTask
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.FusedLibraryBundleAar
import com.android.build.gradle.tasks.FusedLibraryBundleClasses
import com.android.build.gradle.tasks.FusedLibraryClassesRewriteTask
import com.android.build.gradle.tasks.FusedLibraryManifestMergerTask
import com.android.build.gradle.tasks.FusedLibraryMergeArtifactTask
import com.android.build.gradle.tasks.FusedLibraryMergeClasses
import com.android.build.gradle.tasks.FusedLibraryMergeResourcesTask
import com.google.wireless.android.sdk.stats.GradleBuildProject
import groovy.namespace.QName
import groovy.util.Node
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.file.RegularFile
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import javax.inject.Inject

@Suppress("UnstableApiUsage")
class FusedLibraryPlugin @Inject constructor(
        private val softwareComponentFactory: SoftwareComponentFactory,
        listenerRegistry: BuildEventsListenerRegistry,
) : AndroidPluginBaseServices(listenerRegistry), Plugin<Project> {

    val dslServices: DslServices by lazy(LazyThreadSafetyMode.NONE) {
        withProject("dslServices") { project ->
            getDslServices(project, projectServices)
        }
    }

    private val variantScope: FusedLibraryVariantScope by lazy(LazyThreadSafetyMode.NONE) {
        withProject("variantScope") { project ->
            FusedLibraryVariantScopeImpl(
                    project
            ) { extension }
        }
    }

    private val extension: FusedLibraryExtension by lazy(LazyThreadSafetyMode.NONE) {
        withProject("extension") { project ->
            instantiateExtension(project)
        }
    }

    override fun configureProject(project: Project) {
    }

    override fun configureExtension(project: Project) {
        extension
    }

    private fun instantiateExtension(project: Project): FusedLibraryExtension {

        val fusedLibraryExtensionImpl = dslServices.newDecoratedInstance(
                FusedLibraryExtensionImpl::class.java,
                dslServices,
        )

        abstract class Extension(
                val publicExtensionImpl: FusedLibraryExtensionImpl,
        ): FusedLibraryExtension by publicExtensionImpl

        return project.extensions.create(
                FusedLibraryExtension::class.java,
                "android",
                Extension::class.java,
                fusedLibraryExtensionImpl
        )

    }

    private fun maybePublishToMaven(
            project: Project,
            includeApiElements: Configuration,
            includeRuntimeElements: Configuration,
            includeRuntimeUnmerged: Configuration
    ) {
        val bundleTaskProvider = variantScope
                .artifacts
                .getArtifactContainer(FusedLibraryInternalArtifactType.BUNDLED_LIBRARY)
                .getTaskProviders()
                .last()

        val apiPublication = project.configurations.create("apiPublication").also {
            it.isCanBeConsumed = false
            it.isCanBeResolved = false
            it.isVisible = false
            it.attributes.attribute(
                    Usage.USAGE_ATTRIBUTE,
                    project.objects.named(Usage::class.java, Usage.JAVA_API)
            )
            it.attributes.attribute(
                    Bundling.BUNDLING_ATTRIBUTE,
                    project.objects.named(Bundling::class.java, Bundling.EXTERNAL)
            )
            it.attributes.attribute(
                    Category.CATEGORY_ATTRIBUTE,
                    project.objects.named(Category::class.java, Category.LIBRARY)
            )
            it.attributes.attribute(
                    LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    project.objects.named(
                            LibraryElements::class.java,
                            AndroidArtifacts.ArtifactType.AAR.type
                    )
            )
            it.attributes.attribute(
                    BuildTypeAttr.ATTRIBUTE,
                    project.objects.named(BuildTypeAttr::class.java, "debug")
            )
            it.extendsFrom(includeApiElements)
            variantScope.outgoingConfigurations.addConfiguration(it)
            it.outgoing.artifact(bundleTaskProvider) { artifact ->
                artifact.type = AndroidArtifacts.ArtifactType.AAR.type
            }
        }

        val runtimePublication = project.configurations.create("runtimePublication").also {
            it.isCanBeConsumed = false
            it.isCanBeResolved = false
            it.isVisible = false
            it.attributes.attribute(
                    Usage.USAGE_ATTRIBUTE,
                    project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME)
            )
            it.attributes.attribute(
                    Bundling.BUNDLING_ATTRIBUTE,
                    project.objects.named(Bundling::class.java, Bundling.EXTERNAL)
            )
            it.attributes.attribute(
                    Category.CATEGORY_ATTRIBUTE,
                    project.objects.named(Category::class.java, Category.LIBRARY)
            )
            it.attributes.attribute(
                    LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    project.objects.named(
                            LibraryElements::class.java,
                            AndroidArtifacts.ArtifactType.AAR.type
                    )
            )
            it.attributes.attribute(
                    BuildTypeAttr.ATTRIBUTE,
                    project.objects.named(BuildTypeAttr::class.java, "debug")
            )
            it.extendsFrom(includeRuntimeElements)
            variantScope.outgoingConfigurations.addConfiguration(it)
            it.outgoing.artifact(bundleTaskProvider) { artifact ->
                artifact.type = AndroidArtifacts.ArtifactType.AAR.type
            }
        }

        // create an adhoc component, this will be used for publication
        val adhocComponent = softwareComponentFactory.adhoc("fusedLibraryComponent")
        // add it to the list of components that this project declares
        project.components.add(adhocComponent)

        adhocComponent.addVariantsFromConfiguration(apiPublication) {
            it.mapToMavenScope("compile")
        }
        adhocComponent.addVariantsFromConfiguration(runtimePublication) {
            it.mapToMavenScope("runtime")
        }

        project.afterEvaluate {
            project.extensions.findByType(PublishingExtension::class.java)?.also {
                component(
                        it.publications.create("maven", MavenPublication::class.java)
                                .also { mavenPublication ->
                                    mavenPublication.from(adhocComponent)
                                }, includeRuntimeUnmerged.incoming.artifacts
                )
            }
        }
    }

    fun component(publication: MavenPublication, unmergedArtifacts: ArtifactCollection) {
        publication.pom { pom: MavenPom ->
            pom.withXml { xml ->
                val dependenciesNode = xml.asNode().let {
                    it.children().firstOrNull { node ->
                        ((node as Node).name() as QName).qualifiedName == "dependencies"
                    } ?: it.appendNode("dependencies")
                } as Node

                unmergedArtifacts.forEach { artifact ->
                    if (artifact.id is ModuleComponentArtifactIdentifier) {
                        when (val moduleIdentifier = artifact.id.componentIdentifier) {
                            is ModuleComponentIdentifier -> {
                                val dependencyNode = dependenciesNode.appendNode("dependency")
                                dependencyNode.appendNode("groupId", moduleIdentifier.group)
                                dependencyNode.appendNode("artifactId", moduleIdentifier.module)
                                dependencyNode.appendNode("version", moduleIdentifier.version)
                                dependencyNode.appendNode("scope", "runtime")
                            }
                            is ProjectComponentIdentifier -> println("Project : ${moduleIdentifier.projectPath}")
                            is LibraryBinaryIdentifier -> println("Library : ${moduleIdentifier.projectPath}")
                            else -> println("Unknown dependency ${moduleIdentifier.javaClass} : $artifact")
                        }
                    } else {
                        println("Unknown module ${artifact.id.javaClass} : ${artifact.id}")
                    }
                }
            }
        }
    }

    override fun createTasks(project: Project) {
        configureTransforms(project, projectServices)
        createTasks(
                project,
                variantScope.artifacts,
                FusedLibraryInternalArtifactType.BUNDLED_LIBRARY,
                listOf(
                        FusedLibraryClassesRewriteTask.CreationAction(variantScope),
                        FusedLibraryManifestMergerTask.CreationAction(variantScope),
                        FusedLibraryMergeResourcesTask.CreationAction(variantScope),
                        FusedLibraryMergeClasses.FusedLibraryCreationAction(variantScope),
                        FusedLibraryBundleClasses.CreationAction(variantScope),
                        FusedLibraryBundleAar.CreationAction(variantScope),
                        MergeJavaResourceTask.FusedLibraryCreationAction(variantScope)
                ) + FusedLibraryMergeArtifactTask.getCreationActions(variantScope),
        )
    }

    override fun getAnalyticsPluginType(): GradleBuildProject.PluginType =
            GradleBuildProject.PluginType.FUSED_LIBRARIES

    override fun apply(project: Project) {
        super.basePluginApply(project)

        if (!projectServices.projectOptions[BooleanOption.FUSED_LIBRARY_SUPPORT]) {
            throw GradleException(
                    "Fused Library Plugin is in development and is not yet supported.\n" +
                            "To enable support for internal testing only, add\n" +
                            "    ${BooleanOption.FUSED_LIBRARY_SUPPORT.propertyName}=true\n" +
                            "to your project's gradle.properties file."
            )
        }

        // so far by default, we consume and publish only 'debug' variant

        // 'include' is the configuration that users will use to indicate which dependencies should
        // be fused.
        val includeConfigurations = project.configurations.create("include").also {
            it.isCanBeConsumed = false
            val buildType: BuildTypeAttr = project.objects.named(BuildTypeAttr::class.java, "debug")
            it.attributes.attribute(
                    BuildTypeAttr.ATTRIBUTE,
                    buildType,
            )
        }
        // This is the internal configuration that will be used to feed tasks that require access
        // to the resolved 'include' dependency. It is for JAVA_API usage which mean all transitive
        // dependencies that are implementation() scoped will not be included.
        val includeApiClasspath = project.configurations.create("includeApiClasspath").also {
            it.isCanBeConsumed = false
            it.attributes.attribute(
                    Usage.USAGE_ATTRIBUTE,
                    project.objects.named(Usage::class.java, Usage.JAVA_API)
            )
            val buildType: BuildTypeAttr = project.objects.named(BuildTypeAttr::class.java, "debug")
            it.attributes.attribute(
                    BuildTypeAttr.ATTRIBUTE,
                    buildType,
            )
            it.extendsFrom(includeConfigurations)
        }
        // This is the configuration that will contain all the JAVA_API dependencies that are not
        // fused in the resulting aar library.
        val includedApiUnmerged = project.configurations.create("includeApiUnmerged").also {
            it.isCanBeConsumed = false
            it.isCanBeResolved = true
            it.incoming.beforeResolve(
                    SegregatingConstraintHandler(
                            includeApiClasspath,
                            it,
                            variantScope.mergeSpec,
                            project,
                    )
            )
        }
        // This is the internal configuration that will be used to feed tasks that require access
        // to the resolved 'include' dependency. It is for JAVA_RUNTIME usage which mean all transitive
        // dependencies that are implementation() scoped will  be included.
        val includeRuntimeClasspath =
                project.configurations.create("includeRuntimeClasspath").also {
                    it.isCanBeConsumed = false
                    it.isCanBeResolved = true

                    it.attributes.attribute(
                            Usage.USAGE_ATTRIBUTE,
                            project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME)
                    )
                    val buildType: BuildTypeAttr =
                            project.objects.named(BuildTypeAttr::class.java, "debug")
                    it.attributes.attribute(
                            BuildTypeAttr.ATTRIBUTE,
                            buildType,
                    )

                    it.extendsFrom(includeConfigurations)
                }
        // This is the configuration that will contain all the JAVA_RUNTIME dependencies that are
        // not fused in the resulting aar library.
        val includeRuntimeUnmerged = project.configurations.create("includeRuntimeUnmerged").also {
            it.isCanBeConsumed = false
            it.isCanBeResolved = true
            it.incoming.beforeResolve(
                    SegregatingConstraintHandler(
                            includeConfigurations,
                            it,
                            variantScope.mergeSpec,
                            project,
                    )
            )
        }
        // this is the outgoing configuration for JAVA_API scoped declarations, it will contain
        // this module and all transitive non merged dependencies
        fun configureElements(
                elements: Configuration,
                usage: String,
                artifacts: ArtifactsImpl,
                publicationArtifact: Artifact.Single<RegularFile>,
                publicationArtifactType: AndroidArtifacts.ArtifactType
        ) {
            // we are only interested in the last provider in the chain of transformers for this bundle.
            // Obviously, this is theoretical at this point since there is no variant API to replace
            // artifacts, there is always only one.
            val bundleTaskProvider = publicationArtifact.let {
                artifacts
                        .getArtifactContainer(it)
                        .getTaskProviders()
                        .last()
            }
            elements.attributes.attribute(
                    Usage.USAGE_ATTRIBUTE,
                    project.objects.named(Usage::class.java, usage)
            )
            elements.isCanBeResolved = false
            elements.isCanBeConsumed = true
            elements.isTransitive = true

            elements.outgoing.variants { variants ->
                variants.create(publicationArtifactType.type) { variant ->
                    variant.artifact(bundleTaskProvider) { artifact ->
                        artifact.type = publicationArtifactType.type
                    }
                }
            }
        }

        val includeApiElements =
                project.configurations.create("apiElements") { apiElements ->
                    configureElements(
                            apiElements,
                            Usage.JAVA_API,
                            variantScope.artifacts,
                            FusedLibraryInternalArtifactType.BUNDLED_LIBRARY,
                            AndroidArtifacts.ArtifactType.AAR)

                    apiElements.extendsFrom(includedApiUnmerged)
                }
        // this is the outgoing configuration for JAVA_RUNTIME scoped declarations, it will contain
        // this module and all transitive non merged dependencies
        val includeRuntimeElements =
                project.configurations.create("runtimeElements") { runtimeElements ->
                    configureElements(
                            runtimeElements,
                            Usage.JAVA_RUNTIME,
                            variantScope.artifacts,
                            FusedLibraryInternalArtifactType.BUNDLED_LIBRARY,
                            AndroidArtifacts.ArtifactType.AAR)
                    runtimeElements.extendsFrom(includeRuntimeUnmerged)
                }

        val configurationsToAdd = listOf(includeApiClasspath, includeRuntimeClasspath)
        configurationsToAdd.forEach { configuration ->
                variantScope.incomingConfigurations.addConfiguration(configuration)
        }
        maybePublishToMaven(
                project,
                includeApiElements,
                includeRuntimeElements,
                includeRuntimeUnmerged
        )
    }
}
