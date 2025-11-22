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
import com.android.build.api.dsl.PrivacySandboxSdkExtension
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.crash.afterEvaluate
import com.android.build.gradle.internal.dependency.configureKotlinPlatformAttribute
import com.android.build.gradle.internal.dsl.InternalPrivacySandboxSdkExtension
import com.android.build.gradle.internal.dsl.PrivacySandboxSdkExtensionImpl
import com.android.build.gradle.internal.fusedlibrary.configureElements
import com.android.build.gradle.internal.fusedlibrary.configureTransformsForFusedLibrary
import com.android.build.gradle.internal.fusedlibrary.createTasks
import com.android.build.gradle.internal.fusedlibrary.getDslServices
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.lint.AndroidLintCopyReportTask
import com.android.build.gradle.internal.lint.AndroidLintTask
import com.android.build.gradle.internal.lint.AndroidLintTextOutputTask
import com.android.build.gradle.internal.lint.LintModelWriterTask
import com.android.build.gradle.internal.lint.LintTaskManager.Companion.needsCopyReportTask
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScopeImpl
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.services.Aapt2DaemonBuildService
import com.android.build.gradle.internal.services.Aapt2ThreadPoolBuildService
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.R8D8ThreadPoolBuildService
import com.android.build.gradle.internal.services.R8MaxParallelTasksBuildService
import com.android.build.gradle.internal.services.SymbolTableBuildService
import com.android.build.gradle.internal.services.VersionedSdkLoaderService
import com.android.build.gradle.internal.tasks.BaseTask
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfigImpl
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.tasks.factory.TaskFactoryImpl
import com.android.build.gradle.internal.utils.createTargetSdkVersion
import com.android.build.gradle.options.BooleanOption
import com.android.builder.errors.IssueReporter
import com.android.repository.Revision
import com.google.wireless.android.sdk.stats.GradleBuildProject
import java.util.Locale
import javax.inject.Inject
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.configuration.BuildFeatures
import org.gradle.api.plugins.JvmEcosystemPlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.build.event.BuildEventsListenerRegistry

class PrivacySandboxSdkPlugin @Inject constructor(
        val softwareComponentFactory: SoftwareComponentFactory,
        listenerRegistry: BuildEventsListenerRegistry,
        private val buildFeatures: BuildFeatures,
) : AndroidPluginBaseServices(listenerRegistry, buildFeatures), Plugin<Project> {

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
                    dslServices,
                    projectServices,
                    { extension },
                    {
                        BootClasspathConfigImpl(
                                project,
                                projectServices,
                                versionedSdkLoaderService,
                                libraryRequests = listOf(),
                                isJava8Compatible = { true },
                                returnDefaultValuesForMockableJar = { false },
                                forUnitTest = false
                        )
                    })
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

        R8D8ThreadPoolBuildService.RegistrationAction(project, projectOptions).execute()
        R8MaxParallelTasksBuildService.RegistrationAction(project, projectOptions).execute()
    }

    override fun configureExtension(project: Project) {
        extension
    }

    override fun apply(project: Project) {
        throw GradleException(
            "Privacy Sandbox SDK Plugin has been phased out.\n" +
                    "Check https://privacysandbox.com/news/update-on-plans-for-privacy-sandbox-technologies for full details"
        )
    }

    private fun applyPrivacySandboxConfigurations(project: Project) {
        // so far by default, we consume and publish only 'release' variant
        val buildType: BuildTypeAttr = project.objects.named(BuildTypeAttr::class.java, "release")

        val jvmEnvironment: TargetJvmEnvironment =
            project.objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.ANDROID)
        // 'include' is the configuration that users will use to indicate which dependencies should
        // be fused.
        val includeConfigurations = project.configurations.register("include") {
            it.isCanBeConsumed = false
            it.attributes.attribute(
                    BuildTypeAttr.ATTRIBUTE,
                    buildType,
            )
        }

        // Required and optional configurations must be used for declaring SDK dependencies of all SDKs
        // on the classpath (inc. transitive SDKs dependencies). Required and optional SDK dependency
        // states will become encoded in the ASB's SdkBundleConfig.pb.

        val requiredSdkConfiguration = project.configurations.register("requiredSdk") {
            it.isCanBeConsumed = false
            it.isTransitive = false
            it.attributes.attribute(BuildTypeAttr.ATTRIBUTE, buildType)
        }

        val optionalSdkConfiguration = project.configurations.register("optionalSdk") {
            it.isCanBeConsumed = false
            it.isTransitive = false
            it.attributes.attribute(BuildTypeAttr.ATTRIBUTE, buildType)
        }

        // This is the internal configuration that will be used to feed tasks that require access
        // to the resolved 'include' dependency. It is for JAVA_API usage which mean all transitive
        // dependencies that are implementation() scoped will not be included.
        val includeApiClasspath = project.configurations.register("includeApiClasspath") {
            it.isCanBeConsumed = false
            it.attributes.attribute(
                    Usage.USAGE_ATTRIBUTE,
                    project.objects.named(Usage::class.java, Usage.JAVA_API)
            )
            it.attributes.attribute(
                    BuildTypeAttr.ATTRIBUTE,
                    buildType,
            )
            it.attributes.attribute(
                TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                jvmEnvironment
            )
            it.extendsFrom(includeConfigurations.get())
        }
        // This is the internal configuration that will be used to feed tasks that require access
        // to the resolved 'include' dependency. It is for JAVA_RUNTIME usage which mean all transitive
        // dependencies that are implementation() scoped will  be included.
        val includeRuntimeClasspath =
            project.configurations.register("includeRuntimeClasspath") {
                it.isCanBeConsumed = false
                it.isCanBeResolved = true

                it.attributes.attribute(
                    Usage.USAGE_ATTRIBUTE,
                    project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME)
                )
                it.attributes.attribute(
                    BuildTypeAttr.ATTRIBUTE,
                    buildType,
                )
                it.attributes.attribute(
                    TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                    jvmEnvironment
                )

                it.extendsFrom(includeConfigurations.get())
                it.extendsFrom(requiredSdkConfiguration.get())
                it.extendsFrom(optionalSdkConfiguration.get())
            }

        val includeLintChecksClasspath = project.configurations.register("includeLintChecksClasspath") {
            it.isCanBeConsumed = false

            it.attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME)
            )
            it.attributes.attribute(
                BuildTypeAttr.ATTRIBUTE,
                buildType,
            )
            it.attributes.attribute(
                TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                jvmEnvironment
            )

            it.extendsFrom(includeApiClasspath.get(), includeRuntimeClasspath.get())

        }

        if (!projectServices.projectOptions[BooleanOption.DISABLE_KOTLIN_ATTRIBUTE_SETUP]) {
            configureKotlinPlatformAttribute(
                listOf(includeApiClasspath.get(), includeRuntimeClasspath.get(), includeLintChecksClasspath.get()),
                project
            )
        }

        fun configurePrivacySandboxElements(configuration: Configuration, usage: String) {
            configureElements(
                    project,
                    configuration,
                    usage,
                    variantScope.artifacts,
                    mapOf(
                            PrivacySandboxSdkInternalArtifactType.ASAR to
                                    AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_ARCHIVE,
                            PrivacySandboxSdkInternalArtifactType.STUB_JAR to
                                    AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_INTERFACE_DESCRIPTOR,
                    )
            )
        }
        // this is the outgoing configuration for JAVA_API scoped declarations
        project.configurations.register("apiElements") { apiElements ->
            configurePrivacySandboxElements(apiElements, Usage.JAVA_API)
            apiElements.extendsFrom(requiredSdkConfiguration.get())
        }
        // this is the outgoing configuration for JAVA_RUNTIME scoped declarations
        project.configurations.register("runtimeElements") { runtimeElements ->
            configurePrivacySandboxElements(runtimeElements, Usage.JAVA_RUNTIME)
            runtimeElements.extendsFrom(requiredSdkConfiguration.get())
        }
        val incomingConfigurationsToAdd = listOf(includeApiClasspath.get(), includeRuntimeClasspath.get(), includeLintChecksClasspath.get())
        variantScope.incomingConfigurations.addAll(incomingConfigurationsToAdd)
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
        project.afterEvaluate(
            afterEvaluate {
                configureTransforms(project)
            }
        )
    }
    private fun configureTransforms(project: Project) {
        configureTransformsForFusedLibrary(
            project,
            projectServices
        )
    }

    override fun getAnalyticsPluginType(): GradleBuildProject.PluginType =
            GradleBuildProject.PluginType.PRIVACY_SANDBOX_SDK
}
