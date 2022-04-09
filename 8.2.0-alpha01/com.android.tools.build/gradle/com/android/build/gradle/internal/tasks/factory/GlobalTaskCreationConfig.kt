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
import com.android.build.api.dsl.TestCoverage
import com.android.build.api.dsl.TestOptions
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.attribution.BuildAnalyzerIssueReporter
import com.android.build.gradle.internal.core.SettingsOptions
import com.android.build.gradle.internal.dsl.LanguageSplitOptions
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.BaseServices
import com.android.build.gradle.internal.testing.ManagedDeviceRegistry
import com.android.builder.core.LibraryRequest
import com.android.builder.testing.api.DeviceProvider
import com.android.builder.testing.api.TestServer
import com.android.repository.Revision
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider

/**
 * Creation config for global tasks that are not variant-based.
 *
 * This gives access to a few select objects that may be useful.
 *
 * IMPORTANT: it must not give access to the whole extension as it is too dangerous. We need to
 * control that is accessible to global task (DSL elements that are global) and what isn't (DSL
 * elements that are configurable per-variant). Giving access directly to the DSL removes this
 * safety net and reduce maintainability in the future when things become configurable per-variant.
 */
interface GlobalTaskCreationConfig: BootClasspathConfig {

    // Global DSL Elements

    val compileSdkHashString: String
    val buildToolsRevision: Revision
    val ndkVersion: String?
    val ndkPath: String?

    val productFlavorCount: Int
    val productFlavorDimensionCount: Int

    val assetPacks: Set<String>

    val dynamicFeatures: Set<String>
    val hasDynamicFeatures: Boolean
        get() = dynamicFeatures.isNotEmpty()

    val aidlPackagedList: Collection<String>?
    val bundleOptions: Bundle
    val compileOptions: CompileOptions
    val compileOptionsIncremental: Boolean?
    val composeOptions: ComposeOptions
    val dataBinding: DataBinding
    val deviceProviders: List<DeviceProvider>
    val externalNativeBuild: ExternalNativeBuild
    val installationOptions: Installation
    val libraryRequests: Collection<LibraryRequest>
    val lintOptions: Lint
    val prefab: Set<Prefab>
    val resourcePrefix: String?
    val splits: Splits
    val testCoverage: TestCoverage
    val testOptions: TestOptions
    val testServers: List<TestServer>

    // processed access to some DSL values

    val namespacedAndroidResources: Boolean
    val testOptionExecutionEnum: com.android.builder.model.TestOptions.Execution?
    val legacyLanguageSplitOptions: LanguageSplitOptions

    /** the same as [prefab] but returns an empty set on unsupported variants */
    val prefabOrEmpty: Set<Prefab>

    val hasNoBuildTypeMinified: Boolean

    val manifestArtifactType: InternalArtifactType<Directory>

    val publishConsumerProguardRules: Boolean

    // Internal Objects

    val globalArtifacts: ArtifactsImpl
    val services: BaseServices

    val createdBy: String

    val asmApiVersion: Int

    /**
     * Queries the given configuration for platform attributes from the jar(s) in it.
     *
     * This extract platform attributes from the jars via an Artifact Transform. This is meant to
     * process android.jar
     */
    val platformAttrs: FileCollection

    val localCustomLintChecks: FileCollection

    val versionedSdkLoader: Provider<SdkComponentsBuildService.VersionedSdkLoader>

    val versionedNdkHandler: SdkComponentsBuildService.VersionedNdkHandler

    val managedDeviceRegistry: ManagedDeviceRegistry

    // configurations that may need to be accessible
    val lintPublish: Configuration
    val lintChecks: Configuration
    //  configuration with an empty jar as input, to allow deriving of things depending
    //  on AGP itself in a cacheable way by custom Gradle artifact transforms
    val fakeDependency: Configuration

    // Options from the settings plugin
    val settingsOptions: SettingsOptions

    val buildAnalyzerIssueReporter: BuildAnalyzerIssueReporter?

    /**
     * Returns the API to which device/emulator we're deploying via the IDE or null if not.
     * Can be used to optimize some build steps when deploying via the IDE.
     *
     * This has no relation with targetSdkVersion from build.gradle/manifest.
     */
    val targetDeployApiFromIDE: Int?
}
