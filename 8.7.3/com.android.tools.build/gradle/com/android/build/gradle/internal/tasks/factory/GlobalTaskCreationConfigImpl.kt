/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.Bundle
import com.android.build.api.dsl.CompileOptions
import com.android.build.api.dsl.ComposeOptions
import com.android.build.api.dsl.DataBinding
import com.android.build.api.dsl.ExternalNativeBuild
import com.android.build.api.dsl.Installation
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.Lint
import com.android.build.api.dsl.Prefab
import com.android.build.api.dsl.Splits
import com.android.build.api.dsl.TestCoverage
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.attribution.BuildAnalyzerIssueReporter
import com.android.build.gradle.internal.core.SettingsOptions
import com.android.build.gradle.internal.core.dsl.features.DeviceTestOptionsDslInfo
import com.android.build.gradle.internal.core.dsl.features.UnitTestOptionsDslInfo
import com.android.build.gradle.internal.core.dsl.impl.features.DeviceTestOptionsDslInfoImpl
import com.android.build.gradle.internal.core.dsl.impl.features.UnitTestOptionsDslInfoImpl
import com.android.build.gradle.internal.dsl.CommonExtensionImpl
import com.android.build.gradle.internal.dsl.LanguageSplitOptions
import com.android.build.gradle.internal.instrumentation.ASM_API_VERSION_FOR_INSTRUMENTATION
import com.android.build.gradle.internal.lint.getLocalCustomLintChecks
import com.android.build.gradle.internal.publishing.AarOrJarTypeToConsume
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.getAarOrJarTypeToConsume
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.BaseServices
import com.android.build.gradle.internal.services.VersionedSdkLoaderService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.testing.ManagedDeviceRegistry
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.StringOption
import com.android.builder.core.LibraryRequest
import com.android.builder.testing.api.DeviceProvider
import com.android.builder.testing.api.TestServer
import com.android.repository.Revision
import com.android.utils.HelpfulEnumConverter
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions

class GlobalTaskCreationConfigImpl(
    project: Project,
    private val oldExtension: BaseExtension,
    private val extension: CommonExtensionImpl<*, *, *, *, *, *>,
    override val services: BaseServices,
    private val versionedSdkLoaderService: VersionedSdkLoaderService,
    bootClasspathConfig: BootClasspathConfigImpl,
    override val lintPublish: Configuration,
    override val lintChecks: Configuration,
    private val androidJar: Configuration,
    override val fakeDependency: Configuration,
    override val settingsOptions: SettingsOptions,
    override val managedDeviceRegistry: ManagedDeviceRegistry,
) : GlobalTaskCreationConfig, BootClasspathConfig by bootClasspathConfig {

    companion object {
        @JvmStatic
        fun String.toExecutionEnum(): com.android.builder.model.TestOptions.Execution? {
            val converter = HelpfulEnumConverter(
                com.android.builder.model.TestOptions.Execution::class.java
            )
            return converter.convert(this)
        }
    }

    init {
        bootClasspathConfig.androidJar = androidJar
    }

    // DSL elements

    override val compileSdkHashString: String
        get() = extension.compileSdkVersion ?: throw RuntimeException("compileSdk is not specified!")

    override val buildToolsRevision: Revision by lazy {
        Revision.parseRevision(extension.buildToolsVersion, Revision.Precision.MICRO)
    }

    override val ndkVersion: String
        get() = extension.ndkVersion

    override val ndkPath: String?
        get() = extension.ndkPath

    override val productFlavorCount: Int
        get() = extension.productFlavors.size

    override val productFlavorDimensionCount: Int
        get() = extension.flavorDimensions.size

    override val assetPacks: Set<String>
        get() = (extension as? ApplicationExtension)?.assetPacks ?: setOf()

    override val dynamicFeatures: Set<String>
        get() = (extension as? ApplicationExtension)?.dynamicFeatures ?: setOf()

    override val hasDynamicFeatures: Boolean
        get() = dynamicFeatures.isNotEmpty()

    override val aidlPackagedList: Collection<String>?
        get() {
            val libExt = (extension as? LibraryExtension)
                ?: throw RuntimeException("calling aidlPackagedList on non Library variant")

            return libExt.aidlPackagedList
        }

    override val bundleOptions: Bundle
        get() = (extension as? ApplicationExtension)?.bundle
            ?: throw RuntimeException("calling BundleOptions on non Application variant")

    override val compileOptions: CompileOptions
        get() = extension.compileOptions

    override val compileOptionsIncremental: Boolean?
        get() = oldExtension.compileOptions.incremental

    override val composeOptions: ComposeOptions
        get() = extension.composeOptions

    override val dataBinding: DataBinding
        get() = extension.dataBinding

    override val deviceProviders: List<DeviceProvider>
        get() = oldExtension.deviceProviders

    override val externalNativeBuild: ExternalNativeBuild
        get() = extension.externalNativeBuild

    override val installationOptions: Installation
        get() = (extension as? ApplicationExtension)?.installation
            ?: extension.installation

    override val libraryRequests: Collection<LibraryRequest>
        get() = extension.libraryRequests

    override val lintOptions: Lint
        get() = extension.lint

    override val resourcePrefix: String?
        get() = extension.resourcePrefix

    override val splits: Splits
        get() = extension.splits

    override val prefab: Set<Prefab>
        get() = (extension as? LibraryExtension)?.prefab
            ?: throw RuntimeException("calling prefab on non Library variant")

    override val testCoverage: TestCoverage
        get() = extension.testCoverage

    override val androidTestOptions: DeviceTestOptionsDslInfo
        get() = DeviceTestOptionsDslInfoImpl(extension)

    override val unitTestOptions: UnitTestOptionsDslInfo
        get() = UnitTestOptionsDslInfoImpl(extension)


    override val testServers: List<TestServer>
        get() = oldExtension.testServers

    override val kotlinOptions: KotlinJvmOptions?
        get() = (oldExtension as ExtensionAware).extensions.findByName("kotlinOptions") as? KotlinJvmOptions

    override val namespacedAndroidResources: Boolean
        get() = extension.androidResources.namespaced

    override val testOptionExecutionEnum: com.android.builder.model.TestOptions.Execution? by lazy {
        androidTestOptions.execution.toExecutionEnum()
    }

    override val prefabOrEmpty: Set<Prefab>
        get() = (extension as? LibraryExtension)?.prefab ?: setOf()

    override val hasNoBuildTypeMinified: Boolean
        get() = extension.buildTypes.none { it.isMinifyEnabled }

    override val legacyLanguageSplitOptions: LanguageSplitOptions
        get() = oldExtension.splits.language
    override val manifestArtifactType: InternalArtifactType<Directory>
        get() = if (services.projectOptions[BooleanOption.IDE_DEPLOY_AS_INSTANT_APP])
            InternalArtifactType.INSTANT_APP_MANIFEST
        else
            InternalArtifactType.PACKAGED_MANIFESTS

    override val publishConsumerProguardRules: Boolean
        get() = true

    // Internal Objects

    override val globalArtifacts: ArtifactsImpl = ArtifactsImpl(project, "global")

    override val createdBy: String = "Android Gradle ${Version.ANDROID_GRADLE_PLUGIN_VERSION}"

    override val asmApiVersion = ASM_API_VERSION_FOR_INSTRUMENTATION

    // Utility methods

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

    override val localCustomLintChecks: FileCollection by lazy {
        getLocalCustomLintChecks(lintChecks)
    }

    override val versionedSdkLoader: Provider<SdkComponentsBuildService.VersionedSdkLoader>
        get() = versionedSdkLoaderService.versionedSdkLoader

    override val versionedNdkHandler: SdkComponentsBuildService.VersionedNdkHandler by lazy {
        getBuildService(services.buildServiceRegistry, SdkComponentsBuildService::class.java)
            .get()
            .versionedNdkHandler(ndkVersion, ndkPath)
    }

    override val buildAnalyzerIssueReporter: BuildAnalyzerIssueReporter? =
        services.projectOptions.get(StringOption.IDE_ATTRIBUTION_FILE_LOCATION)?.let {
            BuildAnalyzerIssueReporter(
                services.projectOptions,
                services.buildServiceRegistry
            )
        }

    override val targetDeployApiFromIDE: Int? =
        services.projectOptions.get(IntegerOption.IDE_TARGET_DEVICE_API)

    override val taskNames: GlobalTaskNames = GlobalTaskNamesImpl

    override val aarOrJarTypeToConsume: AarOrJarTypeToConsume
        get() = getAarOrJarTypeToConsume(services.projectOptions, namespacedAndroidResources)

    override val avoidTaskRegistration: Boolean = services.projectOptions.run {
        get(BooleanOption.IDE_AVOID_TASK_REGISTRATION) &&
                (get(BooleanOption.IDE_BUILD_MODEL_ONLY) || get(BooleanOption.IDE_BUILD_MODEL_ONLY_V2)
                        )
    }
}
