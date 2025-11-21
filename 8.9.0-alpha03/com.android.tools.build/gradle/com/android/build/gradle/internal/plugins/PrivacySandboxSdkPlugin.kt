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
import com.android.build.gradle.internal.res.PrivacySandboxSdkLinkAndroidResourcesTask
import com.android.build.gradle.internal.services.Aapt2DaemonBuildService
import com.android.build.gradle.internal.services.Aapt2ThreadPoolBuildService
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.R8ParallelBuildService
import com.android.build.gradle.internal.services.SymbolTableBuildService
import com.android.build.gradle.internal.services.VersionedSdkLoaderService
import com.android.build.gradle.internal.tasks.AppMetadataTask
import com.android.build.gradle.internal.tasks.BaseTask
import com.android.build.gradle.internal.tasks.GeneratePrivacySandboxProguardRulesTask
import com.android.build.gradle.internal.tasks.MergeJavaResourceTask
import com.android.build.gradle.internal.tasks.PerModuleBundleTask
import com.android.build.gradle.internal.tasks.R8Task
import com.android.build.gradle.internal.tasks.SignAsbTask
import com.android.build.gradle.internal.tasks.ValidateSigningTask
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfigImpl
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.tasks.factory.TaskFactoryImpl
import com.android.build.gradle.internal.utils.createTargetSdkVersion
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.tasks.FusedLibraryMergeArtifactTask
import com.android.build.gradle.tasks.FusedLibraryMergeClasses
import com.android.build.gradle.tasks.GeneratePrivacySandboxAsar
import com.android.build.gradle.tasks.PackagePrivacySandboxSdkBundle
import com.android.build.gradle.tasks.PrivacySandboxSdkGenerateJarStubsTask
import com.android.build.gradle.tasks.PrivacySandboxSdkGenerateRClassTask
import com.android.build.gradle.tasks.PrivacySandboxSdkManifestGeneratorTask
import com.android.build.gradle.tasks.PrivacySandboxSdkManifestMergerTask
import com.android.build.gradle.tasks.PrivacySandboxSdkMergeResourcesTask
import com.android.build.gradle.tasks.PrivacySandboxValidateConfigurationTask
import com.android.builder.errors.IssueReporter
import com.android.repository.Revision
import com.google.wireless.android.sdk.stats.GradleBuildProject
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
import java.util.Locale
import javax.inject.Inject

class PrivacySandboxSdkPlugin @Inject constructor(
        val softwareComponentFactory: SoftwareComponentFactory,
        listenerRegistry: BuildEventsListenerRegistry,
        private val buildFeatures: BuildFeatures,
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
                    },
                BasePlugin.createCustomLintChecksConfig(project).takeIf { lintEnabled })
        }
    }

    private val extension: PrivacySandboxSdkExtension by lazy(LazyThreadSafetyMode.NONE)
    {
        withProject("extension") { project ->
            instantiateExtension(project)
        }
    }

    private val lintEnabled: Boolean by lazy {
        projectServices.projectOptions[BooleanOption.PRIVACY_SANDBOX_SDK_ENABLE_LINT]
    }

    override fun configureProject(project: Project) {
        // workaround for https://github.com/gradle/gradle/issues/20145
        project.plugins.apply(JvmEcosystemPlugin::class.java)

        val projectOptions = projectServices.projectOptions
        Aapt2ThreadPoolBuildService.RegistrationAction(project, projectOptions).execute()
        Aapt2DaemonBuildService.RegistrationAction(project, projectOptions).execute()
        SymbolTableBuildService.RegistrationAction(project).execute()

        R8ParallelBuildService.RegistrationAction(
            project,
            projectOptions.get(IntegerOption.R8_MAX_WORKERS)
        ).execute()
    }

    override fun configureExtension(project: Project) {
        extension
    }

    override fun apply(project: Project) {
        super.basePluginApply(project, buildFeatures)
        if (projectServices.projectOptions.let {
                    !it[BooleanOption.PRIVACY_SANDBOX_SDK_PLUGIN_SUPPORT] && !it[BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT] }) {
            throw GradleException(
                    "Privacy Sandbox SDK Plugin support must be explicitly enabled.\n" +
                            "To enable support, add\n" +
                            "    ${BooleanOption.PRIVACY_SANDBOX_SDK_PLUGIN_SUPPORT.propertyName}=true\n" +
                            "to your project's gradle.properties file."
            )
        }

        applyPrivacySandboxConfigurations(project)
    }

    private fun applyPrivacySandboxConfigurations(project: Project) {
        // so far by default, we consume and publish only 'debug' variant
        val buildType: BuildTypeAttr = project.objects.named(BuildTypeAttr::class.java, "debug")

        val jvmEnvironment: TargetJvmEnvironment =
            project.objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.ANDROID)
        // 'include' is the configuration that users will use to indicate which dependencies should
        // be fused.
        val includeConfigurations = project.configurations.create("include").also {
            it.isCanBeConsumed = false
            it.attributes.attribute(
                    BuildTypeAttr.ATTRIBUTE,
                    buildType,
            )
        }

        // Required and optional configurations must be used for declaring SDK dependencies of all SDKs
        // on the classpath (inc. transitive SDKs dependencies). Required and optional SDK dependency
        // states will become encoded in the ASB's SdkBundleConfig.pb.

        val requiredSdkConfiguration = project.configurations.create("requiredSdk").also {
            it.isCanBeConsumed = false
            it.isTransitive = false
            it.attributes.attribute(BuildTypeAttr.ATTRIBUTE, buildType)
        }

        val optionalSdkConfiguration = project.configurations.create("optionalSdk").also {
            it.isCanBeConsumed = false
            it.isTransitive = false
            it.attributes.attribute(BuildTypeAttr.ATTRIBUTE, buildType)
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
            it.attributes.attribute(
                    BuildTypeAttr.ATTRIBUTE,
                    buildType,
            )
            it.attributes.attribute(
                TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                jvmEnvironment
            )
            it.extendsFrom(includeConfigurations)
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
                it.attributes.attribute(
                    BuildTypeAttr.ATTRIBUTE,
                    buildType,
                )
                it.attributes.attribute(
                    TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                    jvmEnvironment
                )

                it.extendsFrom(includeConfigurations)
                it.extendsFrom(requiredSdkConfiguration)
                it.extendsFrom(optionalSdkConfiguration)
            }

        if (!projectServices.projectOptions[BooleanOption.DISABLE_KOTLIN_ATTRIBUTE_SETUP]) {
            configureKotlinPlatformAttribute(
                listOf(includeApiClasspath, includeRuntimeClasspath),
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
        project.configurations.create("apiElements") { apiElements ->
            configurePrivacySandboxElements(apiElements, Usage.JAVA_API)
            apiElements.extendsFrom(requiredSdkConfiguration)
        }
        // this is the outgoing configuration for JAVA_RUNTIME scoped declarations
        project.configurations.create("runtimeElements") { runtimeElements ->
            configurePrivacySandboxElements(runtimeElements, Usage.JAVA_RUNTIME)
            runtimeElements.extendsFrom(requiredSdkConfiguration)
        }
        val incomingConfigurationsToAdd = listOf(includeApiClasspath, includeRuntimeClasspath)
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
        createTasks(
                project,
                variantScope.artifacts,
                PrivacySandboxSdkInternalArtifactType.ASAR,
                listOf<TaskCreationAction<out BaseTask>>(
                        AppMetadataTask.PrivacySandboxSdkCreationAction(variantScope),
                        SignAsbTask.CreationActionPrivacySandboxSdk(variantScope),
                        FusedLibraryMergeClasses.PrivacySandboxSdkCreationAction(variantScope),
                        GeneratePrivacySandboxAsar.CreationAction(variantScope),
                        MergeJavaResourceTask.PrivacySandboxSdkCreationAction(variantScope),
                        PrivacySandboxValidateConfigurationTask.CreationAction(variantScope),
                        PrivacySandboxSdkGenerateJarStubsTask.CreationAction(variantScope),
                        PrivacySandboxSdkMergeResourcesTask.CreationAction(variantScope),
                        PrivacySandboxSdkManifestGeneratorTask.CreationAction(variantScope),
                        PrivacySandboxSdkManifestMergerTask.CreationAction(variantScope),
                        PrivacySandboxSdkLinkAndroidResourcesTask.CreationAction(variantScope),
                        R8Task.PrivacySandboxSdkCreationAction(variantScope, false),
                        PrivacySandboxSdkGenerateRClassTask.CreationAction(variantScope),
                        GeneratePrivacySandboxProguardRulesTask.CreationAction(variantScope),
                        PerModuleBundleTask.PrivacySandboxSdkCreationAction(variantScope),
                        PackagePrivacySandboxSdkBundle.CreationAction(variantScope),
                        ValidateSigningTask.PrivacySandboxSdkCreationAction(variantScope),
                ) + FusedLibraryMergeArtifactTask.getCreationActions(variantScope)
        )
        if (lintEnabled) {
            createLintTasks(project)
        }
    }

    private fun createLintTasks(project: Project) {
        runConfigurationValidation()
        // Map of task path to the providers for tasks that that task subsumes,
        // and therefore should be disabled if both are in the task graph.
        // e.g. Running `lintRelease` should cause `lintVitalRelease` to be skipped,
        val variantLintTaskToLintVitalTask = mutableMapOf<String, TaskProvider<out Task>>()

        val needsCopyReportTask = needsCopyReportTask(variantScope.lintOptions)
        val taskFactory = TaskFactoryImpl(project.tasks)
        val variantLintTextOutputTask = taskFactory.register(AndroidLintTextOutputTask.PrivacySandboxSdkLintTextOutputTaskCreationAction(variantScope))
        taskFactory.register(LintModelWriterTask.PrivacySandboxCreationAction(variantScope,
            fatalOnly = false,
            projectServices.projectOptions))

        val updateLintBaselineTask =
            taskFactory.register(AndroidLintTask.PrivacySandboxSdkUpdateBaselineCreationAction(variantScope))
        val variantLintTask =
            taskFactory.register(AndroidLintTask.PrivacySandboxSdkReportingCreationAction(variantScope))
                .also { it.configure { task -> task.mustRunAfter(updateLintBaselineTask) } }

        if (needsCopyReportTask) {
            val copyLintReportTask =
                taskFactory.register(AndroidLintCopyReportTask.PrivacySandboxCreationAction(variantScope))
            variantLintTextOutputTask.configure {
                it.finalizedBy(copyLintReportTask)
            }
        }
        taskFactory.register(AndroidLintAnalysisTask.PrivacySandboxCreationAction(variantScope, projectServices.projectOptions))
        taskFactory.register(AndroidLintAnalysisTask.PrivacySandboxLintVitalCreationAction(variantScope, projectServices.projectOptions))

        val lintVitalTask =
            taskFactory.register(AndroidLintTask.PrivacySandboxLintVitalCreationAction(variantScope))
                .also { it.configure { task -> task.mustRunAfter(updateLintBaselineTask) } }
        val lintVitalTextOutputTask =
            taskFactory.register(
                AndroidLintTextOutputTask.PrivacySandboxSdkLintVitalCreationAction(variantScope)
            )
        taskFactory.configure("assemble") { it.dependsOn(lintVitalTextOutputTask) }
        fun getTaskPath(taskName: String) = TaskManager.getTaskPath(project, taskName)
        // If lint is being run, we do not need to run lint vital.
        variantLintTaskToLintVitalTask[getTaskPath(variantLintTask.name)] = lintVitalTask
        variantLintTextOutputTask.let {
            variantLintTaskToLintVitalTask[getTaskPath(it.name)] = lintVitalTextOutputTask
        }
        taskFactory.register(AndroidLintTask.PrivacySandboxSdkFixCreationAction(variantScope))
            .also { it.configure { task -> task.mustRunAfter(updateLintBaselineTask) } }
        val lintTaskPath = getTaskPath("lint")
        project.gradle.taskGraph.whenReady {
            variantLintTaskToLintVitalTask.forEach { (taskPath, taskToDisable) ->
                if (it.hasTask(taskPath)) {
                    taskToDisable.configure { it.enabled = false }
                }
            }
            if (it.hasTask(lintTaskPath)) {
                variantLintTaskToLintVitalTask.forEach { (_, lintVitalTask) ->
                    lintVitalTask.configure { it.enabled = false }
                }
            }
        }
    }

    private fun configureTransforms(project: Project) {
        com.android.build.gradle.internal.fusedlibrary.configureTransformsForFusedLibrary(
            project,
            projectServices
        )
            .configurePrivacySandboxSdkConsumerTransforms(
                variantScope.compileSdkVersion,
                Revision.parseRevision(
                    extension.buildToolsVersion,
                    Revision.Precision.MICRO
                ),
                BootClasspathConfigImpl(
                    project,
                    projectServices,
                    versionedSdkLoaderService,
                    emptyList(),
                    { true },
                    { false },
                    false
                ),
            )
    }

    private fun runConfigurationValidation() {
        val lintTargetSdkVersion = variantScope.lintOptions.run { createTargetSdkVersion(targetSdk, targetSdkPreview) }
        if (lintTargetSdkVersion != null) {
            val variantTargetSdkVersion = variantScope.targetSdkVersion
            if (lintTargetSdkVersion.apiLevel < variantTargetSdkVersion.apiLevel) {
                variantScope.services.issueReporter.reportError(IssueReporter.Type.GENERIC, String.format(
                    Locale.US,
                    """
                        lint.targetSdk (${lintTargetSdkVersion.apiLevel}) for Privacy Sandbox Sdk is smaller than android.targetSdk (${variantTargetSdkVersion.apiLevel}).
                        Please change the values such that lint.targetSdk is greater than or equal to android.targetSdk.
                    """.trimIndent()
                ))
            }
        }
    }

    override fun getAnalyticsPluginType(): GradleBuildProject.PluginType =
            GradleBuildProject.PluginType.PRIVACY_SANDBOX_SDK
}
