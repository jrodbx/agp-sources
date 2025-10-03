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

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.artifact.impl.InternalScopedArtifacts
import com.android.build.api.attributes.BuildTypeAttr
import com.android.build.api.dsl.FusedLibraryExtension
import com.android.build.gradle.internal.dependency.configureKotlinPlatformAttribute
import com.android.build.gradle.internal.dsl.FusedLibraryExtensionImpl
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryConstants
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryGlobalScope
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryGlobalScopeImpl
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryTargetJvmEnvironmentCompatibilityRule
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
import org.gradle.api.attributes.Bundling.BUNDLING_ATTRIBUTE
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.DocsType
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.configuration.BuildFeatures
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.build.event.BuildEventsListenerRegistry
import org.jdom2.DocType
import shadow.bundletool.com.android.SdkConstants
import shadow.bundletool.com.android.tools.r8.internal.tR
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
        val sourcesJarProvider = variantScope
            .artifacts
            .getArtifactContainer(FusedLibraryInternalArtifactType.MERGED_SOURCES_JAR)
            .getFinalProvider()

        val runtimePublication = if (bundleTaskProvider != null) {
            project.configurations.register("runtimePublication") {
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

                it.dependencies.addAllLater(fusedAarRuntimeDependenciesProvider)
                it.outgoing.artifact(bundleTaskProvider) { artifact ->
                    artifact.type = AndroidArtifacts.ArtifactType.AAR.type
                    artifact.extension = SdkConstants.EXT_AAR
                }
            }
        } else null

        val runtimeSourcePublication = if (sourcesJarProvider != null) {
            project.configurations.register("runtimeSourcePublication") {
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
                    project.objects.named(Category::class.java, Category.DOCUMENTATION)
                )
                it.attributes.attribute(
                    DocsType.DOCS_TYPE_ATTRIBUTE,
                    project.objects.named(DocsType::class.java, DocsType.SOURCES)
                )
                it.attributes.attribute(
                    LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    project.objects.named(
                        LibraryElements::class.java,
                        AndroidArtifacts.ArtifactType.JAR.type
                    )
                )

                it.outgoing.artifact(sourcesJarProvider) {
                    it.type = AndroidArtifacts.ArtifactType.SOURCES_JAR.type
                    it.extension = SdkConstants.EXT_JAR
                    it.classifier = "sources"
                }
            }
        } else null

        // create an adhoc component, this will be used for publication
        val adhocComponent = softwareComponentFactory.adhoc(
            FusedLibraryConstants.FUSED_LIBRARY_PUBLICATION_COMPONENT_NAME)
        // add it to the list of components that this project declares
        project.components.add(adhocComponent)

        listOfNotNull(
            runtimePublication,
            runtimeSourcePublication
        ).forEach { outgoingRuntimeConfigurationProvider ->
            adhocComponent.addVariantsFromConfiguration(outgoingRuntimeConfigurationProvider.get()) {
                it.mapToMavenScope("runtime")
            }
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
                        FusedLibraryMergeClasses.FusedLibraryCreationAction(
                            variantScope,
                            AndroidArtifacts.ArtifactType.CLASSES_JAR,
                            FusedLibraryInternalArtifactType.MERGED_CLASSES,
                            false
                        ),
                        FusedLibraryMergeClasses.FusedLibraryCreationAction(
                            variantScope,
                            AndroidArtifacts.ArtifactType.LINT,
                            FusedLibraryInternalArtifactType.MERGED_PUBLISHED_LINT_CLASSES,
                            true
                        ),
                        FusedLibraryBundleClasses.CreationActionClassesJar(variantScope),
                        FusedLibraryBundleClasses.CreationActionLintJar(variantScope),
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
        super.applyBaseServices(project, buildFeatures)

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

        // 'include' is the configuration that users will use to indicate which dependencies should
        // be fused.
        val include = project.configurations.register(FusedLibraryConstants.INCLUDE_CONFIGURATION_NAME) { include ->
            include.description =
                "Used for declaring dependencies that should be packaged in the fused artifact."
            include.isCanBeConsumed = false
        }

        // Platform dependencies must be resolved in a transitive configuration in order to
        // resolve versioned module dependencies from constraints. Since we never want to include
        // transitive dependencies in the fused library artifacts, this configuration should never
        // be extended or resolved from other configurations. It is only used to ensure consistent
        // version resolution of non-transitive dependencies.
        fun getIncludeTransitiveResolved(name: String, usage: String): Configuration =
            project.configurations.register(name) { includePlatform ->
                    includePlatform.description =
                        "Used for resolving transitive dependency version constraints of include dependencies."
                    includePlatform.isCanBeDeclared = false
                    includePlatform.isCanBeConsumed = false
                    includePlatform.isCanBeResolved = true
                    includePlatform.isTransitive = true
                    includePlatform.attributes.attribute(
                        Usage.USAGE_ATTRIBUTE,
                        project.objects.named(Usage::class.java, usage)
                    )
                    includePlatform.extendsFrom(include.get())
                }.get()

        val includeTransitiveApiResolved = getIncludeTransitiveResolved("includeTransitiveResolvedApi",Usage.JAVA_API)
        val includeTransitiveRuntimeResolved = getIncludeTransitiveResolved("includeTransitiveResolvedRuntime", Usage.JAVA_RUNTIME)

        // This is the internal configuration that will be used to feed tasks that require access
        // to the resolved 'include' dependency.
        val fusedApi =
            project.configurations.register(FusedLibraryConstants.FUSED_API_CONFIGURATION_NAME) { apiClasspath ->
                apiClasspath.isCanBeConsumed = false
                apiClasspath.isCanBeResolved = true
                apiClasspath.isTransitive = false

                apiClasspath.attributes.attribute(
                    Usage.USAGE_ATTRIBUTE,
                    project.objects.named(Usage::class.java, Usage.JAVA_API)
                )
                apiClasspath.shouldResolveConsistentlyWith(includeTransitiveApiResolved)
                apiClasspath.extendsFrom(include.get())
            }

        // This is the internal configuration that will be used to feed tasks that require access
        // to the resolved 'include' dependency.
        val fusedRuntime =
            project.configurations.register(FusedLibraryConstants.FUSED_RUNTIME_CONFIGURATION_NAME) { runtimeClasspath ->
                runtimeClasspath.isCanBeConsumed = false
                runtimeClasspath.isCanBeResolved = true
                runtimeClasspath.isTransitive = false

                runtimeClasspath.attributes.attribute(
                    Usage.USAGE_ATTRIBUTE,
                    project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME)
                )
                runtimeClasspath.shouldResolveConsistentlyWith(includeTransitiveRuntimeResolved)
                runtimeClasspath.extendsFrom(include.get())
            }

        val sourcesConfiguration =
            project.configurations.register(FusedLibraryConstants.FUSED_SOURCES_CONFIGURATION_NAME) { sourcesConfig ->
                sourcesConfig.isCanBeConsumed = false
                sourcesConfig.isCanBeResolved = true
                sourcesConfig.isTransitive = false

                sourcesConfig.attributes.attribute(
                    CATEGORY_ATTRIBUTE,
                    project.objects.named(Category::class.java, Category.DOCUMENTATION)
                )
                sourcesConfig.attributes.attribute(
                    DocsType.DOCS_TYPE_ATTRIBUTE,
                    project.objects.named(DocsType::class.java, DocsType.SOURCES)
                )
                sourcesConfig.extendsFrom(include.get())
            }

        val consumerConfigurations: List<Configuration> = listOf(
            include.get(),
            includeTransitiveApiResolved,
            includeTransitiveRuntimeResolved,
            fusedApi.get(),
            fusedRuntime.get(),
            sourcesConfiguration.get()
        )
        applyCommonConsumptionAttributes(project, consumerConfigurations)

        if (projectServices.projectOptions[BooleanOption.FUSED_LIBRARY_PUBLICATION_ONLY_MODE]) {
            val publicationOnlyModeWarning =
                """Fused Library Plugin is using Publication Only Mode.

                    Depending on Fused Library projects from other projects is not fully supported.

                    Local Fused Library projects will not be able to be added as project dependencies to other modules.
                    If you wish to depend on a fused library, consider publishing as an external library using
                    maven-publish plugin and using the ${FusedLibraryConstants.FUSED_LIBRARY_PUBLICATION_COMPONENT_NAME} component.
                    """
            syncIssueReporter.reportWarning(IssueReporter.Type.GENERIC, publicationOnlyModeWarning)
        } else {
            // This is the configuration that will contain all the JAVA_RUNTIME dependencies that are
            // not fused in the resulting aar library.
            project.configurations.register("runtimeElements") { runtimeElements ->

                configureElements(
                    project,
                    runtimeElements,
                    Usage.JAVA_RUNTIME,
                    variantScope.artifacts,
                    mapOf(
                        FusedLibraryInternalArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME to
                                AndroidArtifacts.ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME
                    )
                )
                runtimeElements.extendsFrom(include.get())
            }
        }

        val resolvableConfigurations = listOf(fusedApi.get(), fusedRuntime.get())
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

    private fun applyCommonConsumptionAttributes(
        project: Project,
        configurations: List<Configuration>
    ) {
        val consumptionBuildType: BuildTypeAttr =
            project.objects.named(BuildTypeAttr::class.java, "release")
        val jvmEnvironment: TargetJvmEnvironment =
            project.objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.ANDROID)

        configurations.forEach {
            it.attributes.attribute(
                BuildTypeAttr.ATTRIBUTE,
                consumptionBuildType,
            )
            it.attributes.attribute(
                TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                jvmEnvironment
            )
        }
        project.dependencies.attributesSchema.attribute(
            TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE).also {
            it.compatibilityRules.add(FusedLibraryTargetJvmEnvironmentCompatibilityRule::class.java)
        }
        configureKotlinPlatformAttribute(configurations, project)
    }
}
