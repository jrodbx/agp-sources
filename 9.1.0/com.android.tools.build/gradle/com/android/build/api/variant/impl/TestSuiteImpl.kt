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

package com.android.build.api.variant.impl

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.impl.computeTaskName
import com.android.build.api.component.impl.features.DexingImpl
import com.android.build.api.dsl.DefaultConfig
import com.android.build.api.dsl.TestTaskContext
import com.android.build.api.variant.JUnitEngineSpec
import com.android.build.api.variant.TestSuite
import com.android.build.api.variant.TestSuiteSourceSet
import com.android.build.api.variant.VariantBuilder
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.TestSuiteCreationConfig
import com.android.build.gradle.internal.component.TestSuiteTargetCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.core.dsl.ApplicationVariantDslInfo
import com.android.build.gradle.internal.core.dsl.VariantDslInfo
import com.android.build.gradle.internal.core.dsl.impl.getDefaultInstrumentationTestRunner
import com.android.build.gradle.internal.core.dsl.impl.getInstrumentationRunner
import com.android.build.gradle.internal.manifest.ManifestDataProvider
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.testsuites.impl.JUnitEngineSpecForVariantBuilder
import com.android.build.gradle.internal.testsuites.impl.TestSuiteBuilderImpl
import com.android.build.gradle.internal.variant.VariantComponentInfo
import com.android.builder.dexing.DexingType
import java.io.File
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test

/** Implementation of [TestSuite] for test suites declared via the DSL. */
class TestSuiteImpl
internal constructor(
  testSuiteBuilder: TestSuiteBuilderImpl,
  override val sourceContainers: Collection<TestSuiteSourceContainer>,
  val testedVariantComponent: VariantComponentInfo<VariantBuilder, VariantDslInfo, VariantCreationConfig>,
  override val global: GlobalTaskCreationConfig,
  override val variantServices: VariantServices,
  override val services: TaskCreationServices,
  override val artifacts: ArtifactsImpl,
  val defaultConfig: DefaultConfig,
  override val manifestDataProviderBuilder: (File) -> ManifestDataProvider,
) : TestSuite, TestSuiteCreationConfig {

  private val _name = testSuiteBuilder.name

  //
  // Public APIs
  //
  override fun getName() = _name

  override val junitEngineSpec: JUnitEngineSpec =
    JUnitEngineSpecImplForVariant(
      testSuiteBuilder.junitEngineSpec as JUnitEngineSpecForVariantBuilder,
      { variantServices.mapPropertyOf(String::class.java, String::class.java, mapOf()) },
    )
  override val testedVariant: VariantCreationConfig
    get() = testedVariantComponent.variant

  override val targets: Map<String, TestSuiteTargetCreationConfig> =
    testSuiteBuilder.targets.mapValues { entry ->
      TestSuiteTargetImpl(
        entry.value,
        computeTaskName(
          testedVariant.name,
          "test${_name.capitalizeFirstChar()}${entry.value.uniqueName().capitalizeFirstChar()}",
          "TestSuite",
        ),
      )
    }

  override val sources: Collection<TestSuiteSourceSet>
    get() = sourceContainers.map { it.source }

  @Synchronized
  override fun configureTestTasks(action: Test.(context: TestTaskContext) -> Unit) {
    testTaskConfigActions.add(action)
  }

  override val codeCoverage: Property<Boolean> = variantServices.propertyOf(Boolean::class.java, testSuiteBuilder.codeCoverage)

  override fun instrumentationRunner(source: TestSuiteSourceSet.TestApk): Provider<String> {
    val dslInfo = testedVariantComponent.variantDslInfo
    val variant = testedVariantComponent.variant
    return if (dslInfo is ApplicationVariantDslInfo && variant is ApplicationCreationConfig) {
      getInstrumentationRunner(
        dslInfo.productFlavorList,
        defaultConfig,
        manifestDataProviderBuilder(source.manifestFile),
        DexingImpl(variant, true, dslInfo.dexingDslInfo.multiDexKeepProguard, dslInfo.dexingDslInfo.multiDexKeepFile, variantServices)
          .dexingType,
        variantServices,
      )
    } else {
      getDefaultInstrumentationTestRunner(variantServices, DexingType.MONO_DEX)
    }
  }

  /** Internal APIs */
  private val testTaskConfigActions =
    mutableListOf<Test.(TestTaskContext) -> Unit>().also { it.addAll(testSuiteBuilder.testSuite.testTaskConfigActions) }

  @Synchronized
  override fun runTestTaskConfigurationActions(context: TestTaskContext, testTaskProvider: TaskProvider<out Test>) {
    testTaskConfigActions.forEach { testTaskProvider.configure { testTask -> it(testTask, context) } }
  }
}
