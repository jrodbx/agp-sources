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

import com.android.SdkConstants
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.artifact.impl.InternalScopedArtifacts
import com.android.build.api.attributes.BuildTypeAttr
import com.android.build.api.dsl.FusedLibraryExtension
import com.android.build.gradle.internal.dsl.FusedLibraryExtensionImpl
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryConstants
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryGlobalScope
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryGlobalScopeImpl
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.android.build.gradle.internal.fusedlibrary.configureElements
import com.android.build.gradle.internal.fusedlibrary.configureTransformsForFusedLibrary
import com.android.build.gradle.internal.fusedlibrary.createTasks
import com.android.build.gradle.internal.fusedlibrary.getDslServices
import com.android.build.gradle.internal.fusedlibrary.getFusedLibraryDependencyModuleVersionIdentifiers
import com.android.build.gradle.internal.fusedlibrary.toDependenciesProvider
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.services.Aapt2DaemonBuildService
import com.android.build.gradle.internal.services.Aapt2ThreadPoolBuildService
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.SymbolTableBuildService
import com.android.build.gradle.internal.tasks.MergeJavaResourceTask
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.FusedLibraryBundleAar
import com.android.build.gradle.tasks.FusedLibraryBundleClasses
import com.android.build.gradle.tasks.FusedLibraryClassesRewriteTask
import com.android.build.gradle.tasks.FusedLibraryDependencyValidationTask
import com.android.build.gradle.tasks.FusedLibraryManifestMergerTask
import com.android.build.gradle.tasks.FusedLibraryMergeArtifactTask
import com.android.build.gradle.tasks.FusedLibraryMergeClasses
import com.android.build.gradle.tasks.FusedLibraryMergeResourceCompileSymbolsTask
import com.android.build.gradle.tasks.FusedLibraryMergeResourcesTask
import com.android.build.gradle.tasks.FusedLibraryReportTask
import com.android.builder.errors.IssueReporter
import com.google.wireless.android.sdk.stats.GradleBuildProject
import groovy.namespace.QName
import groovy.util.Node
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.configuration.BuildFeatures
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.build.event.BuildEventsListenerRegistry
import javax.inject.Inject

@Suppress("UnstableApiUsage")
class FusedLibraryPlugin @Inject constructor(
        private val softwareComponentFactory: SoftwareComponentFactory,
        listenerRegistry: BuildEventsListenerRegistry,
        private val buildFeatures: BuildFeatures
) : AndroidPluginBaseServices(listenerRegistry, buildFeatures), Plugin<Project> {

    val dslServices: DslServices by lazy(LazyThreadSafetyMode.NONE) {
        withProject("dslServices") { project ->
            getDslServices(project, projectServices)
        }
    }

    private val variantScope: FusedLibraryGlobalScope by lazy(LazyThreadSafetyMode.NONE) {
        withProject("variantScope") { project ->
            FusedLibraryGlobalScopeImpl(
                    project,
                    projectServices,
                    { extension }
            )
        }
    }

    private val extension: FusedLibraryExtension by lazy(LazyThreadSafetyMode.NONE) {
        withProject("extension") { project ->
            instantiateExtension(project)
        }
    }

    override fun configureProject(project: Project) {
        Aapt2DaemonBuildService
            .RegistrationAction(project, projectServices.projectOptions).execute()
        Aapt2ThreadPoolBuildService
            .RegistrationAction(project, projectServices.projectOptions).execute()
        SymbolTableBuildService.RegistrationAction(project).execute()
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
                FusedLibraryConstants.EXTENSION_NAME,
                Extension::class.java,
                fusedLibraryExtensionImpl
        )

    }

    private fun maybePublishToMaven(
        project: Project,
        fusedAarRuntimeDependenciesComponentIdProvider: Provider<Set<ModuleVersionIdentifier>>,
        fusedAarRuntimeDependenciesProvider: Provider<List<Dependency>>,
    ) {
        val bundleTaskProvider = variantScope
                .artifacts
                .getArtifactContainer(FusedLibraryInternalArtifactType.BUNDLED_LIBRARY)
                .getFinalProvider()

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
            it.dependencies.addAllLater(fusedAarRuntimeDependenciesProvider)
            it.outgoing.artifact(bundleTaskProvider) { artifact ->
                artifact.type = AndroidArtifacts.ArtifactType.AAR.type
                artifact.extension = SdkConstants.EXT_AAR
            }
        }

        // create an adhoc component, this will be used for publication
        val adhocComponent = softwareComponentFactory.adhoc("fusedLibraryComponent")
        // add it to the list of components that this project declares
        project.components.add(adhocComponent)

        adhocComponent.addVariantsFromConfiguration(runtimePublication) {
            it.mapToMavenScope("runtime")
        }

        project.afterEvaluate {
            project.extensions.findByType(PublishingExtension::class.java)?.also {
                component(
                    it.publications.create("maven", MavenPublication::class.java)
                        .also { mavenPublication ->
                            mavenPublication.from(adhocComponent)
                        },
                    fusedAarRuntimeDependenciesComponentIdProvider,
                )
                val publishPlugin = "maven-publish"
                project.pluginManager.withPlugin(publishPlugin) {
                    val publicationTasks = setOf(
                        "publish",
                        "generatePomFileForMavenPublication",
                        "generateMetadataFileForMavenPublication"
                    )
                    project.tasks.named { it in publicationTasks }.forEach {
                        it.dependsOn(FusedLibraryConstants.VALIDATE_DEPENDENCIES_TASK_NAME)
                    }
                }
            }
        }
    }

    fun component(
        publication: MavenPublication,
        runtimeDependenciesProvider: Provider<Set<ModuleVersionIdentifier>>,
    ) {
        publication.pom { pom: MavenPom ->
            pom.withXml { xml ->
                val dependenciesNode = xml.asNode().let {
                    it.children().removeIf { node ->
                        ((node as Node).name() as QName).qualifiedName == "dependencies"
                    }
                    it.appendNode("dependencies")
                } as Node

                runtimeDependenciesProvider.get().forEach { dependency ->
                    val dependencyNode = dependenciesNode.appendNode("dependency")
                    dependencyNode.appendNode("groupId", dependency.group)
                    dependencyNode.appendNode("artifactId", dependency.name)
                    dependencyNode.appendNode("version", dependency.version)
                    dependencyNode.appendNode("scope", "runtime")
                }
            }
        }
    }

    override fun createTasks(project: Project) {
        configureTransformsForFusedLibrary(project, projectServices)
        createTasks(
                project,
                variantScope.artifacts,
                FusedLibraryInternalArtifactType.BUNDLED_LIBRARY,
            listOf<TaskCreationAction<out DefaultTask>>(
                        FusedLibraryClassesRewriteTask.CreationAction(variantScope),
                        FusedLibraryManifestMergerTask.CreationAction(variantScope),
                        FusedLibraryMergeResourcesTask.CreationAction(variantScope),
                        FusedLibraryMergeClasses.FusedLibraryCreationAction(variantScope),
                        FusedLibraryBundleClasses.CreationAction(variantScope),
                        FusedLibraryBundleAar.CreationAction(variantScope),
                        MergeJavaResourceTask.FusedLibraryCreationAction(variantScope),
                        FusedLibraryMergeResourceCompileSymbolsTask.CreationAction(variantScope),
                        FusedLibraryReportTask.CreationAction(variantScope),
                        FusedLibraryDependencyValidationTask.CreationAction(variantScope)
                ) + FusedLibraryMergeArtifactTask.getCreationActions(variantScope),
        )
    }

    override fun getAnalyticsPluginType(): GradleBuildProject.PluginType =
            GradleBuildProject.PluginType.FUSED_LIBRARY

    override fun apply(project: Project) {
        super.basePluginApply(project, buildFeatures)

        val unstableNotice =
            "*Important* Fused Library Plugin is currently in an early testing phase. Artifacts published by the\n" +
                    "plugin and plugin behaviour may not be stable at this time. Take caution before distributing\n" +
                    "published artifacts created by the plugin; there is no guarantee of correctness.\n" +
                    "As an early adopter, please be aware that there may be frequent breaking changes that may require\n" +
                    "you to make changes to your project.\n"
        if (projectServices.projectOptions[BooleanOption.FUSED_LIBRARY_SUPPORT]) {
            syncIssueReporter.reportWarning(IssueReporter.Type.GENERIC, unstableNotice)
        }
        else {
            syncIssueReporter.reportError(IssueReporter.Type.GENERIC,
                unstableNotice +
                        "If you still wish to use the plugin, acknowledge the warning by " +
                        "setting `${BooleanOption.FUSED_LIBRARY_SUPPORT.propertyName}=true` to gradle.properties"
            )
        }

        // so far by default, we consume and publish only 'debug' variant

        // 'include' is the configuration that users will use to indicate which dependencies should
        // be fused.
        val include = project.configurations.create(FusedLibraryConstants.INCLUDE_CONFIGURATION_NAME).also { include ->
            include.description =
                "Used for declaring dependencies that should be packaged in the fused artifact."
            include.isCanBeConsumed = false
            val buildType: BuildTypeAttr = project.objects.named(BuildTypeAttr::class.java, "debug")
            include.attributes.attribute(
                BuildTypeAttr.ATTRIBUTE,
                buildType,
            )
        }

        // Platform dependencies must be resolved in a transitive configuration in order to
        // resolve versioned module dependencies from constraints. Since we never want to include
        // transitive dependencies in the fused library artifacts, this configuration should never
        // be extended or resolved from other configurations. It is only used to ensure consistent
        // version resolution of non-transitive dependencies.
        fun getIncludeTransitiveResolved(name: String, usage: String): Configuration =
            project.configurations.create(name)
                .also { includePlatform ->
                    includePlatform.description =
                        "Used for resolving transitive dependency version constraints of include dependencies."
                    includePlatform.isCanBeDeclared = false
                    includePlatform.isCanBeConsumed = false
                    includePlatform.isCanBeResolved = true
                    includePlatform.isTransitive = true
                    val buildType: BuildTypeAttr =
                        project.objects.named(BuildTypeAttr::class.java, "debug")
                    includePlatform.attributes.attribute(
                        BuildTypeAttr.ATTRIBUTE,
                        buildType,
                    )
                    includePlatform.attributes.attribute(
                        Usage.USAGE_ATTRIBUTE,
                        project.objects.named(Usage::class.java, usage)
                    )
                    includePlatform.extendsFrom(include)
                }

        val includeTransitiveApiResolved = getIncludeTransitiveResolved("includeTransitiveResolvedApi",Usage.JAVA_API)
        val includeTransitiveRuntimeResolved = getIncludeTransitiveResolved("includeTransitiveResolvedRuntime", Usage.JAVA_RUNTIME)

        // This is the internal configuration that will be used to feed tasks that require access
        // to the resolved 'include' dependency.
        val fusedApi =
            project.configurations.create(FusedLibraryConstants.FUSED_API_CONFIGURATION_NAME)
                .also { apiClasspath ->
                apiClasspath.isCanBeConsumed = false
                apiClasspath.isCanBeResolved = true
                apiClasspath.isTransitive = false

                apiClasspath.attributes.attribute(
                    Usage.USAGE_ATTRIBUTE,
                    project.objects.named(Usage::class.java, Usage.JAVA_API)
                )
                val buildType: BuildTypeAttr =
                    project.objects.named(BuildTypeAttr::class.java, "debug")
                apiClasspath.attributes.attribute(
                    BuildTypeAttr.ATTRIBUTE,
                    buildType,
                )
                apiClasspath.shouldResolveConsistentlyWith(includeTransitiveApiResolved)
                apiClasspath.extendsFrom(include)
            }

        // This is the internal configuration that will be used to feed tasks that require access
        // to the resolved 'include' dependency.
        val fusedRuntime =
            project.configurations.create(FusedLibraryConstants.FUSED_RUNTIME_CONFIGURATION_NAME)
                .also { runtimeClasspath ->
                runtimeClasspath.isCanBeConsumed = false
                runtimeClasspath.isCanBeResolved = true
                runtimeClasspath.isTransitive = false

                runtimeClasspath.attributes.attribute(
                    Usage.USAGE_ATTRIBUTE,
                    project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME)
                )
                val buildType: BuildTypeAttr =
                    project.objects.named(BuildTypeAttr::class.java, "debug")
                runtimeClasspath.attributes.attribute(
                    BuildTypeAttr.ATTRIBUTE,
                    buildType,
                )
                runtimeClasspath.shouldResolveConsistentlyWith(includeTransitiveRuntimeResolved)
                runtimeClasspath.extendsFrom(include)
            }

        // This is the configuration that will contain all the JAVA_RUNTIME dependencies that are
        // not fused in the resulting aar library.
        project.configurations.create("runtimeElements") { runtimeElements ->

            configureElements(
                project,
                runtimeElements,
                Usage.JAVA_RUNTIME,
                variantScope.artifacts,
                mapOf(
                FusedLibraryInternalArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME to
                        AndroidArtifacts.ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME)
            )
            runtimeElements.extendsFrom(include)
        }

        val resolvableConfigurations = listOf(fusedApi, fusedRuntime)
        variantScope.incomingConfigurations.addAll(resolvableConfigurations)

        val dependenciesModuleVersionIds =
            getFusedLibraryDependencyModuleVersionIdentifiers(includeTransitiveRuntimeResolved)
        val dependenciesProvider = dependenciesModuleVersionIds.toDependenciesProvider(project)

        maybePublishToMaven(
            project,
            dependenciesModuleVersionIds,
            dependenciesProvider,
        )

        variantScope.artifacts.forScope(
            InternalScopedArtifacts.InternalScope.LOCAL_DEPS
        ).setInitialContent(
            ScopedArtifact.CLASSES,
            variantScope.getLocalJars()
        )
    }
}
