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

import com.android.build.api.attributes.BuildTypeAttr
import com.android.build.api.dsl.FusedLibraryExtension
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.dsl.FusedLibraryExtensionImpl
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryVariantScope
import com.android.build.gradle.internal.fusedlibrary.SegregatingConstraintHandler
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.services.DslServicesImpl
import com.android.build.gradle.internal.tasks.factory.TaskFactoryImpl
import com.android.build.gradle.tasks.FusedLibraryBundleAar
import com.android.build.gradle.tasks.FusedLibraryBundleClasses
import com.android.build.gradle.tasks.FusedLibraryMergeClasses
import com.android.build.gradle.tasks.FusedLibraryClassesRewriteTask
import com.google.wireless.android.sdk.stats.GradleBuildProject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.Usage
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.provider.Provider
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
): AndroidPluginBaseServices(listenerRegistry), Plugin<Project> {

    // so far, there is only one variant.
    private val variantScope by lazy {
        withProject("variantScope") { project ->
            FusedLibraryVariantScope(
                project
            ) { extension }
        }
    }

    private val extension: FusedLibraryExtension by lazy {
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

        val sdkComponentsBuildService: Provider<SdkComponentsBuildService> =
            SdkComponentsBuildService.RegistrationAction(project, projectServices.projectOptions)
                .execute()

        val dslServices = DslServicesImpl(
            projectServices,
            sdkComponentsBuildService
        )

        val fusedLibraryExtensionImpl= dslServices.newDecoratedInstance(
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

    override fun createTasks(project: Project) {
        TaskFactoryImpl(project.tasks).let { taskFactory ->
            listOf(
                    FusedLibraryClassesRewriteTask.CreateAction::class.java,
                    FusedLibraryMergeClasses.CreationAction::class.java,
                    FusedLibraryBundleClasses.CreationAction::class.java,
                    FusedLibraryBundleAar.CreationAction::class.java,
            ).forEach { creationAction ->
                taskFactory.register(
                    creationAction
                        .getConstructor(FusedLibraryVariantScope::class.java)
                        .newInstance(variantScope)
                )
            }
        }

        // create anchor tasks

        project.tasks.register("assemble") {
            it.dependsOn(variantScope.artifacts.get(FusedLibraryInternalArtifactType.BUNDLED_Library))
        }
    }

    override fun getAnalyticsPluginType(): GradleBuildProject.PluginType  =
        GradleBuildProject.PluginType.FUSED_LIBRARIES

    override fun apply(project: Project) {
        super.basePluginApply(project)

        // create an adhoc component, this will be used for publication
        val adhocComponent = softwareComponentFactory.adhoc("fusedLibraryComponent")
        // add it to the list of components that this project declares
        project.components.add(adhocComponent)

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
            it.isCanBeConsumed = true
            initConfiguration(it)
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
        variantScope.incomingConfigurations.addConfiguration(includeApiClasspath)

        // This is the configuration that will contain all the JAVA_API dependencies that are not
        // fused in the resulting aar library.
        val includedApiUnmerged = project.configurations.create("includeApiUnmerged").also {
            it.isCanBeConsumed = true
            it.isCanBeResolved = true
            initConfiguration(it)
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
        val includeRuntimeClasspath = project.configurations.create("includeRuntimeClasspath").also {
            it.isCanBeConsumed = true

            initConfiguration(it)
            it.attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME)
            )
            val buildType: BuildTypeAttr = project.objects.named(BuildTypeAttr::class.java, "debug")
            it.attributes.attribute(
                BuildTypeAttr.ATTRIBUTE,
                buildType,
            )

            it.extendsFrom(includeConfigurations)
        }
        variantScope.incomingConfigurations.addConfiguration(includeRuntimeClasspath)

        // This is the configuration that will contain all the JAVA_RUNTIME dependencies that are
        // not fused in the resulting aar library.
        val includeRuntimeUnmerged = project.configurations.create("includeRuntimeUnmerged").also {
            it.isCanBeConsumed = true
            it.isCanBeResolved = true
            initConfiguration(it)
            it.incoming.beforeResolve(
                SegregatingConstraintHandler(
                    includeConfigurations,
                    it,
                    variantScope.mergeSpec,
                    project,
                )
            )
        }

        // we are only interested in the last provider in the chain of transformers for this bundle.
        // Obviously, this is theoretical at this point since there is no variant API to replace
        // artifacts, there is always only one.
        val bundleTaskProvider =
            variantScope
                .artifacts
                .getArtifactContainer(FusedLibraryInternalArtifactType.BUNDLED_Library)
                .getTaskProviders()
                .last()

        // this is the outgoing configuration for JAVA_API scoped declarations, it will contain
        // this module and all transitive non merged dependencies
        val includeApiElements = project.configurations.create("apiElements") {
            it.isCanBeConsumed = true
            it.isCanBeResolved = false
            it.isTransitive = true
            initConfiguration(it)
            it.attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                project.objects.named(Usage::class.java, Usage.JAVA_API)
            )
            it.extendsFrom(includedApiUnmerged)
            it.outgoing.artifact(bundleTaskProvider)
        }

        // this is the outgoing configuration for JAVA_RUNTIME scoped declarations, it will contain
        // this module and all transitive non merged dependencies
        val includeRuntimeElements = project.configurations.create("runtimeElements") {
            it.isCanBeConsumed = true
            it.isCanBeResolved = false
            it.isTransitive = true
            initConfiguration( it)
            it.attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME)
            )
            it.extendsFrom(includeRuntimeUnmerged)
            it.outgoing.artifact(bundleTaskProvider)
        }

        adhocComponent.addVariantsFromConfiguration(includeApiElements) {
            it.mapToMavenScope("compile")
        }
        adhocComponent.addVariantsFromConfiguration(includeRuntimeElements) {
            it.mapToMavenScope("runtime")
        }

        project.afterEvaluate {
            project.extensions.findByType(PublishingExtension::class.java)?.also {
                component(
                    it.publications.create("maven", MavenPublication::class.java).also { mavenPublication ->
                        mavenPublication.from(adhocComponent)
                    }, includeRuntimeUnmerged.incoming.artifacts
                )
            }
        }
    }

    private fun initConfiguration(configuration: Configuration) {
        configuration.attributes.attribute(
            AndroidArtifacts.ARTIFACT_TYPE,
            AndroidArtifacts.ArtifactType.CLASSES_JAR.type
        )
        configuration.attributes.attribute(
            AndroidArtifacts.ARTIFACT_TYPE,
            AndroidArtifacts.ArtifactType.JAR.type
        )
    }

    fun component(
        publication: MavenPublication,
        unmergedArtifacts: ArtifactCollection,
    ) {

        publication.pom {  pom: MavenPom ->
            pom.withXml { xml ->
                val dependenciesNode = xml.asNode().appendNode("dependencies")

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

}
