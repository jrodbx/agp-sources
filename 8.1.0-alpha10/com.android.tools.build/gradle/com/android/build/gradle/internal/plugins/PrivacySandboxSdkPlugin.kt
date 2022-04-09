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
import com.android.build.api.dsl.PrivacySandboxSdkExtension
import com.android.build.gradle.internal.dsl.InternalPrivacySandboxSdkExtension
import com.android.build.gradle.internal.dsl.PrivacySandboxSdkExtensionImpl
import com.android.build.gradle.internal.fusedlibrary.SegregatingConstraintHandler
import com.android.build.gradle.internal.fusedlibrary.configureTransforms
import com.android.build.gradle.internal.fusedlibrary.createTasks
import com.android.build.gradle.internal.fusedlibrary.getDslServices
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScopeImpl
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.res.PrivacySandboxSdkLinkAndroidResourcesTask
import com.android.build.gradle.internal.services.Aapt2DaemonBuildService
import com.android.build.gradle.internal.services.Aapt2ThreadPoolBuildService
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.SymbolTableBuildService
import com.android.build.gradle.internal.services.TaskCreationServicesImpl
import com.android.build.gradle.internal.services.VersionedSdkLoaderService
import com.android.build.gradle.internal.tasks.AppMetadataTask
import com.android.build.gradle.internal.tasks.SignAsbTask
import com.android.build.gradle.internal.tasks.MergeJavaResourceTask
import com.android.build.gradle.internal.tasks.PerModuleBundleTask
import com.android.build.gradle.internal.tasks.ValidateSigningTask
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfigImpl
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.FusedLibraryMergeArtifactTask
import com.android.build.gradle.tasks.FusedLibraryMergeClasses
import com.android.build.gradle.tasks.GeneratePrivacySandboxAsar
import com.android.build.gradle.tasks.PackagePrivacySandboxSdkBundle
import com.android.build.gradle.tasks.PrivacySandboxSdkDexTask
import com.android.build.gradle.tasks.PrivacySandboxSdkGenerateRPackageDexTask
import com.android.build.gradle.tasks.PrivacySandboxSdkGenerateJarStubsTask
import com.android.build.gradle.tasks.PrivacySandboxSdkGenerateRClassTask
import com.android.build.gradle.tasks.PrivacySandboxSdkManifestGeneratorTask
import com.android.build.gradle.tasks.PrivacySandboxSdkManifestMergerTask
import com.android.build.gradle.tasks.PrivacySandboxSdkMergeDexTask
import com.android.build.gradle.tasks.PrivacySandboxSdkMergeResourcesTask
import com.android.repository.Revision
import com.google.wireless.android.sdk.stats.GradleBuildProject
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Usage
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JvmEcosystemPlugin
import org.gradle.build.event.BuildEventsListenerRegistry
import javax.inject.Inject

class PrivacySandboxSdkPlugin @Inject constructor(
        val softwareComponentFactory: SoftwareComponentFactory,
        listenerRegistry: BuildEventsListenerRegistry,
) : AndroidPluginBaseServices(listenerRegistry), Plugin<Project> {

    val dslServices: DslServices by lazy(LazyThreadSafetyMode.NONE) {
        withProject("dslServices") { project ->
            getDslServices(project, projectServices)
        }
    }

    private val versionedSdkLoaderService: VersionedSdkLoaderService by lazy(LazyThreadSafetyMode.NONE) {
        withProject("versionedSdkLoaderService") { project ->
            VersionedSdkLoaderService(
                    dslServices,
                    project,
                    { variantScope.compileSdkVersion },
                    {
                        Revision.parseRevision(extension.buildToolsVersion,
                                Revision.Precision.MICRO)
                    },
            )
        }
    }

    // so far, there is only one variant.
    private val variantScope: PrivacySandboxSdkVariantScope by lazy {
        withProject("variantScope") { project ->
            PrivacySandboxSdkVariantScopeImpl(
                    project,
                    TaskCreationServicesImpl(projectServices),
                    dslServices,
                    { extension },
            ) {
                BootClasspathConfigImpl(
                        project,
                        projectServices,
                        versionedSdkLoaderService,
                        null,
                        false
                )
            }
        }
    }

    private val extension: PrivacySandboxSdkExtension by lazy(LazyThreadSafetyMode.NONE)
    {
        withProject("extension") { project ->
            instantiateExtension(project)
        }
    }

    override fun configureProject(project: Project) {
        // workaround for https://github.com/gradle/gradle/issues/20145
        project.plugins.apply(JvmEcosystemPlugin::class.java)

        val projectOptions = projectServices.projectOptions
        Aapt2ThreadPoolBuildService.RegistrationAction(project, projectOptions).execute()
        Aapt2DaemonBuildService.RegistrationAction(project, projectOptions).execute()
        SymbolTableBuildService.RegistrationAction(project).execute()
    }

    override fun configureExtension(project: Project) {
        extension
    }

    override fun apply(project: Project) {
        super.basePluginApply(project)
        if (!projectServices.projectOptions[BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT]) {
            throw GradleException(
                    "Privacy Sandbox SDK support is experimental, and must be explicitly enabled.\n" +
                            "To enable support, add\n" +
                            "    ${BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT.propertyName}=true\n" +
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
                publications: List<Pair<Artifact.Single<RegularFile>, AndroidArtifacts.ArtifactType>>
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
                    val bundleTaskProvider = publication.first.let {
                        artifacts
                            .getArtifactContainer(it)
                            .getTaskProviders()
                            .last()
                    }
                    variants.create(publication.second.type) { variant ->
                        variant.artifact(bundleTaskProvider) { artifact ->
                            artifact.type = publication.second.type
                        }
                    }
                }

            }
        }
        project.configurations.create("apiElements") { apiElements ->
            configureElements(
                    apiElements,
                    Usage.JAVA_API,
                    variantScope.artifacts,
                    listOf(
                        PrivacySandboxSdkInternalArtifactType.ASAR to
                                AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_ARCHIVE,
                        PrivacySandboxSdkInternalArtifactType.STUB_JAR to
                                AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_INTERFACE_DESCRIPTOR,
                    )
            )

            apiElements.extendsFrom(includedApiUnmerged)
        }
        // this is the outgoing configuration for JAVA_RUNTIME scoped declarations, it will contain
        // this module and all transitive non merged dependencies
        project.configurations.create("runtimeElements") { runtimeElements ->
            configureElements(
                    runtimeElements,
                    Usage.JAVA_RUNTIME,
                    variantScope.artifacts,
                    listOf(
                        PrivacySandboxSdkInternalArtifactType.ASAR to
                                AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_ARCHIVE,
                        PrivacySandboxSdkInternalArtifactType.STUB_JAR to
                                AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_INTERFACE_DESCRIPTOR,
                    )
            )

            runtimeElements.extendsFrom(includeRuntimeUnmerged)
        }
        val configurationsToAdd = listOf(includeApiClasspath, includeRuntimeClasspath)
        configurationsToAdd.forEach { configuration ->
            variantScope.incomingConfigurations.addConfiguration(configuration)
        }
    }

    private fun instantiateExtension(project: Project): PrivacySandboxSdkExtension {

        val sdkLibraryExtensionImpl = dslServices.newDecoratedInstance(
                PrivacySandboxSdkExtensionImpl::class.java,
                dslServices,
        )

        abstract class Extension(
                val publicExtensionImpl: PrivacySandboxSdkExtensionImpl,
        ): InternalPrivacySandboxSdkExtension by publicExtensionImpl

        return project.extensions.create(
                PrivacySandboxSdkExtension::class.java,
                "android",
                Extension::class.java,
                sdkLibraryExtensionImpl
        )
    }

    override fun createTasks(project: Project) {
        configureTransforms(project, projectServices)
        createTasks(
                project,
                variantScope.artifacts,
                PrivacySandboxSdkInternalArtifactType.ASAR,
                listOf(
                        AppMetadataTask.PrivacySandboxSdkCreationAction(variantScope),
                        SignAsbTask.CreationActionPrivacySandboxSdk(variantScope),
                        FusedLibraryMergeClasses.PrivacySandboxSdkCreationAction(variantScope),
                        GeneratePrivacySandboxAsar.CreationAction(variantScope),
                        MergeJavaResourceTask.PrivacySandboxSdkCreationAction(variantScope),
                        PrivacySandboxSdkGenerateJarStubsTask.CreationAction(variantScope),
                        PrivacySandboxSdkMergeResourcesTask.CreationAction(variantScope),
                        PrivacySandboxSdkManifestGeneratorTask.CreationAction(variantScope),
                        PrivacySandboxSdkManifestMergerTask.CreationAction(variantScope),
                        PrivacySandboxSdkLinkAndroidResourcesTask.CreationAction(variantScope),
                        PrivacySandboxSdkDexTask.CreationAction(variantScope),
                        PrivacySandboxSdkMergeDexTask.CreationAction(variantScope),
                        PrivacySandboxSdkGenerateRPackageDexTask.CreationAction(variantScope),
                        PrivacySandboxSdkGenerateRClassTask.CreationAction(variantScope),
                        PerModuleBundleTask.PrivacySandboxSdkCreationAction(variantScope),
                        PackagePrivacySandboxSdkBundle.CreationAction(variantScope),
                        ValidateSigningTask.PrivacySandboxSdkCreationAction(variantScope),
                ) + FusedLibraryMergeArtifactTask.getCreationActions(variantScope)
        )
    }

    override fun getAnalyticsPluginType(): GradleBuildProject.PluginType =
            GradleBuildProject.PluginType.PRIVACY_SANDBOX_SDK
}
