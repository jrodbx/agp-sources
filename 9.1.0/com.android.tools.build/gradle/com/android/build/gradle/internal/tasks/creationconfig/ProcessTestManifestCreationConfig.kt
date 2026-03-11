/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks.creationconfig

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.variant.TestSuiteSourceSet
import com.android.build.api.variant.impl.TestSuiteSourceContainer
import com.android.build.api.variant.impl.getApiString
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.DeviceTestCreationConfig
import com.android.build.gradle.internal.component.HostTestCreationConfig
import com.android.build.gradle.internal.component.InstrumentedTestCreationConfig
import com.android.build.gradle.internal.component.TargetSdkAwareConfig
import com.android.build.gradle.internal.component.TaskCreationConfig
import com.android.build.gradle.internal.component.TestCreationConfig
import com.android.build.gradle.internal.component.TestSuiteCreationConfig
import com.android.build.gradle.internal.component.TestVariantCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.utils.parseTargetHash
import com.android.build.gradle.internal.variant.VariantPathHelper
import java.io.File
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider

/**
 * Configuration needed to create the [com.android.build.gradle.tasks.ProcessTestManifest], implementations should be partially delegated to
 * [com.android.build.gradle.internal.component.ComponentCreationConfig].
 */
interface ProcessTestManifestCreationConfig : TaskCreationConfig {
  val baseName: String
  val dirName: String
  val paths: VariantPathHelper

  // should be taken from main variant if it's unit test with APK as tested code
  val applicationId: Provider<String>
  val testedApplicationId: Provider<String>
  val namespace: Provider<String>
  val instrumentationRunner: Provider<String>

  val debuggable: Boolean

  val compileSdk: Int?

  val manifestFile: File
  val manifestOverlayFiles: Provider<List<File>>

  // manifests for main variant that is APK for HostedTests
  val mainManifestFile: File?
  val mainManifestOverlayFiles: Provider<List<File>>?

  // artifacts for main variant that is APK for HostedTests
  val testedApkVariantArtifacts: ArtifactsImpl?

  val minSdk: String
  val targetSdkVersion: String

  val handleProfiling: Provider<Boolean>
  val functionalTest: Provider<Boolean>
  val testLabel: Provider<String>

  val manifests: ArtifactCollection?
  val navigationJsons: FileCollection?

  val useLegacyPackaging: Provider<Boolean>

  val placeholderValues: MapProperty<String, String>
}

fun <T : Any> TaskCreationConfig.emptyProvider(): Provider<T> = this.services.provider { null }

/**
 * Create test manifest config for configurations like:
 * - unit test fot library
 * - unit test for application
 * - application instrumented test
 */
fun forTestComponent(creationConfig: TestCreationConfig): ProcessTestManifestCreationConfig {
  val isHostTestWithTestedApk = creationConfig is HostTestCreationConfig && creationConfig.mainVariant.componentType.isApk
  return if (isHostTestWithTestedApk) {
    object : TestComponentProcessTestManifestCreationConfig(creationConfig) {
      override val applicationId: Provider<String>
        get() = creationConfig.mainVariant.applicationId

      override val testedApplicationId: Provider<String>
        get() = creationConfig.mainVariant.applicationId

      override val namespace: Provider<String>
        get() = creationConfig.mainVariant.namespace

      override val instrumentationRunner: Provider<String>
        get() = creationConfig.emptyProvider()

      override val mainManifestFile: File
        get() = creationConfig.mainVariant.sources.manifestFile

      override val mainManifestOverlayFiles: Provider<List<File>>
        get() = creationConfig.mainVariant.sources.manifestOverlayFiles

      override val testedApkVariantArtifacts: ArtifactsImpl
        get() = creationConfig.mainVariant.artifacts
    }
  } else if (creationConfig is InstrumentedTestCreationConfig) {
    object : TestComponentProcessTestManifestCreationConfig(creationConfig) {
      override val handleProfiling: Provider<Boolean>
        get() = creationConfig.handleProfiling

      override val functionalTest: Provider<Boolean>
        get() = creationConfig.functionalTest

      override val testLabel: Provider<String>
        get() = creationConfig.testLabel
    }
  } else {
    TestComponentProcessTestManifestCreationConfig(creationConfig)
  }
}

// Special implementation for APK testSuites
// returns null if variant is not APK or Library
fun forTestSuite(
  creationConfig: TestSuiteCreationConfig,
  sourceContainer: TestSuiteSourceContainer,
  source: TestSuiteSourceSet.TestApk,
): ProcessTestManifestCreationConfig? {
  return (creationConfig.testedVariant as? TargetSdkAwareConfig)?.let { variant ->
    object : TestSuiteProcessTestManifestCreationConfig(creationConfig, sourceContainer, source) {
      override val targetSdkVersion: String = variant.targetSdk.getApiString()
    }
  }
}

/** Config for library unit test config (no instrumentation, not an apk related unit test) */
abstract class BaseProcessTestManifestCreationConfig(val creationConfig: ComponentCreationConfig) : ProcessTestManifestCreationConfig {
  override val baseName: String
    get() = creationConfig.baseName

  override val dirName: String
    get() = creationConfig.dirName

  override val paths: VariantPathHelper
    get() = creationConfig.paths

  override val applicationId: Provider<String>
    get() = creationConfig.applicationId

  override val namespace: Provider<String>
    get() = creationConfig.namespace

  override val testedApkVariantArtifacts: ArtifactsImpl?
    get() = null

  // return non-empty values for instrumented test
  override val handleProfiling: Provider<Boolean>
    get() = creationConfig.emptyProvider()

  override val functionalTest: Provider<Boolean>
    get() = creationConfig.emptyProvider()

  override val testLabel: Provider<String>
    get() = creationConfig.emptyProvider()

  override val debuggable: Boolean
    get() = creationConfig.debuggable

  override val compileSdk: Int?
    get() = parseTargetHash(creationConfig.global.compileSdkHashString).apiLevel

  override val manifestFile: File
    get() = creationConfig.sources.manifestFile

  override val manifestOverlayFiles: Provider<List<File>>
    get() = creationConfig.sources.manifestOverlayFiles

  override val mainManifestFile: File?
    get() = null

  override val mainManifestOverlayFiles: Provider<List<File>>?
    get() = null

  override val minSdk: String
    get() = creationConfig.minSdk.getApiString()

  override val manifests: ArtifactCollection?
    get() =
      creationConfig.variantDependencies.getArtifactCollection(
        ConsumedConfigType.RUNTIME_CLASSPATH,
        ArtifactScope.ALL,
        AndroidArtifacts.ArtifactType.MANIFEST,
      )

  override val navigationJsons: FileCollection?
    get() =
      creationConfig.variantDependencies.getArtifactFileCollection(
        ConsumedConfigType.RUNTIME_CLASSPATH,
        ArtifactScope.ALL,
        AndroidArtifacts.ArtifactType.NAVIGATION_JSON,
      )

  override val useLegacyPackaging: Provider<Boolean>
    get() =
      if (creationConfig is DeviceTestCreationConfig || creationConfig is TestVariantCreationConfig)
        creationConfig.packaging.jniLibs.useLegacyPackaging
      else creationConfig.services.provider { null }

  override val placeholderValues: MapProperty<String, String>
    get() =
      creationConfig.manifestPlaceholdersCreationConfig?.placeholders
        ?: creationConfig.services.mapProperty(String::class.java, String::class.java)

  // avoid delegate mechanism as it will fail with overriding name for testSuites and
  // computeTaskNameInternal
  override val name: String
    get() = creationConfig.name

  override val services: TaskCreationServices
    get() = creationConfig.services

  override val taskContainer: MutableTaskContainer
    get() = creationConfig.taskContainer

  override val artifacts: ArtifactsImpl
    get() = creationConfig.artifacts
}

/** Non-abstract class that init all values with TestCreationConfig */
open class TestComponentProcessTestManifestCreationConfig(val testCreationConfig: TestCreationConfig) :
  BaseProcessTestManifestCreationConfig(testCreationConfig) {

  override val testedApplicationId: Provider<String>
    get() = testCreationConfig.testedApplicationId

  override val instrumentationRunner: Provider<String>
    get() = testCreationConfig.instrumentationRunner

  override val targetSdkVersion: String
    get() = testCreationConfig.targetSdkVersion.getApiString()
}

abstract class TestSuiteProcessTestManifestCreationConfig(
  val testSuiteCreationConfig: TestSuiteCreationConfig,
  val sourceContainer: TestSuiteSourceContainer,
  val source: TestSuiteSourceSet.TestApk,
) : BaseProcessTestManifestCreationConfig(testSuiteCreationConfig.testedVariant) {
  override val name
    get() = sourceContainer.identifier

  override val testedApplicationId: Provider<String>
    get() = testSuiteCreationConfig.testedVariant.applicationId

  override val instrumentationRunner: Provider<String>
    get() = testSuiteCreationConfig.instrumentationRunner(source)

  override val placeholderValues: MapProperty<String, String>
    get() = creationConfig.services.mapProperty(String::class.java, String::class.java)

  override val manifestFile: File
    get() = source.manifestFile

  override val manifestOverlayFiles: Provider<List<File>>
    get() = testSuiteCreationConfig.testedVariant.emptyProvider()

  override val testedApkVariantArtifacts: ArtifactsImpl
    get() = creationConfig.artifacts
}
