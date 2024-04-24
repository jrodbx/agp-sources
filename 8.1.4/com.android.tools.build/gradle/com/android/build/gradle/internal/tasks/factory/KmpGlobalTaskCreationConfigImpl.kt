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

package com.android.build.gradle.internal.tasks.factory

import com.android.Version
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.dsl.Bundle
import com.android.build.api.dsl.CompileOptions
import com.android.build.api.dsl.ComposeOptions
import com.android.build.api.dsl.DataBinding
import com.android.build.api.dsl.ExternalNativeBuild
import com.android.build.api.dsl.Installation
import com.android.build.api.dsl.Lint
import com.android.build.api.dsl.Prefab
import com.android.build.api.dsl.Splits
import com.android.build.api.dsl.TestOptions
import com.android.build.gradle.internal.KotlinMultiplatformCompileOptionsImpl
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.attribution.BuildAnalyzerIssueReporter
import com.android.build.gradle.internal.core.SettingsOptions
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidExtensionImpl
import com.android.build.gradle.internal.dsl.LanguageSplitOptions
import com.android.build.gradle.internal.instrumentation.ASM_API_VERSION_FOR_INSTRUMENTATION
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.BaseServices
import com.android.build.gradle.internal.services.FakeDependencyJarBuildService
import com.android.build.gradle.internal.services.VersionedSdkLoaderService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfigImpl.Companion.toExecutionEnum
import com.android.build.gradle.internal.testing.ManagedDeviceRegistry
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.StringOption
import com.android.builder.core.LibraryRequest
import com.android.builder.testing.api.DeviceProvider
import com.android.builder.testing.api.TestServer
import com.android.repository.Revision
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider

class KmpGlobalTaskCreationConfigImpl(
    project: Project,
    private val extension: KotlinMultiplatformAndroidExtensionImpl,
    private val versionedSdkLoaderService: VersionedSdkLoaderService,
    bootClasspathConfig: BootClasspathConfigImpl,
    compileSdkVersionProvider: () -> String,
    buildToolsVersionProvider: () -> Revision,
    private val androidJar: Configuration,
    override val services: BaseServices,
    override val settingsOptions: SettingsOptions
): GlobalTaskCreationConfig, BootClasspathConfig by bootClasspathConfig {

    init {
        bootClasspathConfig.androidJar = androidJar
    }

    override val compileSdkHashString: String by lazy {
        compileSdkVersionProvider.invoke()
    }
    override val buildToolsRevision: Revision by lazy {
        buildToolsVersionProvider.invoke()
    }
    override val versionedSdkLoader: Provider<SdkComponentsBuildService.VersionedSdkLoader>
        get() = versionedSdkLoaderService.versionedSdkLoader

    override val asmApiVersion = ASM_API_VERSION_FOR_INSTRUMENTATION

    override val createdBy: String = "Android Gradle ${Version.ANDROID_GRADLE_PLUGIN_VERSION}"

    override val globalArtifacts: ArtifactsImpl = ArtifactsImpl(project, "global")

    override val namespacedAndroidResources: Boolean
        get() = false

    override val platformAttrs: FileCollection by lazy {
        val attributes =
            Action { container: AttributeContainer ->
                container.attribute(
                    AndroidArtifacts.ARTIFACT_TYPE,
                    AndroidArtifacts.TYPE_PLATFORM_ATTR
                )
            }
        androidJar
            .incoming
            .artifactView { config -> config.attributes(attributes) }
            .artifacts
            .artifactFiles
    }

    override val testOptions: TestOptions
        get() = extension.testOptions
    override val libraryRequests: Collection<LibraryRequest>
        get() = extension.libraryRequests

    override val buildAnalyzerIssueReporter: BuildAnalyzerIssueReporter? =
        services.projectOptions.get(StringOption.IDE_ATTRIBUTION_FILE_LOCATION)?.let {
            BuildAnalyzerIssueReporter(services.projectOptions, services.buildServiceRegistry)
        }

    override val compileOptions: CompileOptions = KotlinMultiplatformCompileOptionsImpl(
        extension
    )

    override val manifestArtifactType: InternalArtifactType<Directory>
        get() = if (services.projectOptions[BooleanOption.IDE_DEPLOY_AS_INSTANT_APP])
            InternalArtifactType.INSTANT_APP_MANIFEST
        else
            InternalArtifactType.PACKAGED_MANIFESTS

    override val testOptionExecutionEnum: com.android.builder.model.TestOptions.Execution? by lazy {
        testOptions.execution.toExecutionEnum()
    }

    override val installationOptions: Installation
        get() = extension.installation

    override val deviceProviders: List<DeviceProvider>
        get() = emptyList()

    override val testServers: List<TestServer>
        get() = emptyList()

    override val productFlavorCount: Int
        get() = 0
    override val productFlavorDimensionCount: Int
        get() = 0

    override val managedDeviceRegistry = ManagedDeviceRegistry(testOptions)
    override val lintChecks = createCustomLintChecksConfig(project)
    override val fakeDependency = createFakeDependencyConfig(project)

    private fun createCustomLintChecksConfig(project: Project): Configuration {
        val lintChecks = project.configurations.maybeCreate(VariantDependencies.CONFIG_NAME_LINTCHECKS)
        lintChecks.isVisible = false
        lintChecks.description = "Configuration to apply external lint check jar"
        lintChecks.isCanBeConsumed = false
        return lintChecks
    }

    private fun createFakeDependencyConfig(project: Project): Configuration {
        val fakeJarService = getBuildService(
            project.gradle.sharedServices,
            FakeDependencyJarBuildService::class.java,
        ).get()

        val fakeDependency = project.dependencies.create(project.files(fakeJarService.lazyCachedFakeJar))
        return project.configurations.detachedConfiguration(fakeDependency)
    }

    override val targetDeployApiFromIDE: Int? =
        services.projectOptions.get(IntegerOption.IDE_TARGET_DEVICE_API)

    override val testCoverage = extension.testCoverage

    // Unsupported properties
    // TODO: Refactor the parent interface so that we don't have to override these values to avoid
    //  accidental calls.
    override val ndkVersion: String?
        get() = null
    override val ndkPath: String?
        get() = null
    override val aidlPackagedList: Collection<String>?
        get() = null
    override val compileOptionsIncremental: Boolean?
        get() = null
    override val resourcePrefix: String?
        get() = null
    override val hasNoBuildTypeMinified: Boolean
        get() = true
    override val hasDynamicFeatures: Boolean
        get() = false
    override val assetPacks: Set<String>
        get() = emptySet()
    override val dynamicFeatures: Set<String>
        get() = emptySet()
    override val prefab: Set<Prefab>
        get() = emptySet()
    override val prefabOrEmpty: Set<Prefab>
        get() = emptySet()

    override val splits: Splits
        get() = throw IllegalAccessException("Not supported for kmp")
    override val legacyLanguageSplitOptions: LanguageSplitOptions
        get() = throw IllegalAccessException("Not supported for kmp")
    override val localCustomLintChecks: FileCollection
        get() = throw IllegalAccessException("Not supported for kmp")
    override val versionedNdkHandler: SdkComponentsBuildService.VersionedNdkHandler
        get() = throw IllegalAccessException("Not supported for kmp")
    override val lintPublish: Configuration
        get() = throw IllegalAccessException("Not supported for kmp")
    override val externalNativeBuild: ExternalNativeBuild
        get() = throw IllegalAccessException("Not supported for kmp")
    override val lintOptions: Lint
        get() = throw IllegalAccessException("Not supported for kmp")
    override val bundleOptions: Bundle
        get() = throw IllegalAccessException("Not supported for kmp")
    override val composeOptions: ComposeOptions
        get() = throw IllegalAccessException("Not supported for kmp")
    override val dataBinding: DataBinding
        get() = throw IllegalAccessException("Not supported for kmp")
}
