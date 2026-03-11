/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.build.gradle.internal

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.TestVariantBuilder
import com.android.build.gradle.internal.component.*
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask
import com.android.build.gradle.internal.tasks.SigningConfigVersionsWriterTask
import com.android.build.gradle.internal.tasks.creationconfig.forTestComponent
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.tasks.factory.TaskManagerConfig
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.android.build.gradle.internal.test.SeparateTestModuleTestData
import com.android.build.gradle.internal.variant.ComponentInfo
import com.android.build.gradle.tasks.CheckTestedAppObfuscation
import com.android.build.gradle.tasks.ManifestProcessorTask
import com.android.build.gradle.tasks.ProcessTestManifest
import com.android.builder.core.ComponentType
import com.google.common.base.Preconditions
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

/** TaskManager for standalone test application that lives in a separate module from the tested application. */
class TestApplicationTaskManager(
  project: Project,
  variants: MutableCollection<out ComponentInfo<TestVariantBuilder, TestVariantCreationConfig>>,
  testComponents: MutableCollection<out TestComponentCreationConfig?>,
  testFixturesComponents: MutableCollection<out TestFixturesCreationConfig?>,
  globalConfig: GlobalTaskCreationConfig,
  localConfig: TaskManagerConfig,
) :
  AbstractAppTaskManager<TestVariantBuilder, TestVariantCreationConfig>(
    project,
    variants,
    testComponents,
    testFixturesComponents,
    globalConfig,
    localConfig,
  ) {
  private fun getTestData(testVariantProperties: TestVariantCreationConfig): SeparateTestModuleTestData {
    val testingApk: Provider<Directory> = testVariantProperties.artifacts.get(SingleArtifact.APK)

    return SeparateTestModuleTestData(
      testVariantProperties.namespace,
      testVariantProperties,
      testingApk,
      testVariantProperties.testedApks,
      testVariantProperties.services.projectOptions.getExtraInstrumentationTestRunnerArgs(),
    )
  }

  override fun doCreateTasksForVariant(variantInfo: ComponentInfo<TestVariantBuilder, TestVariantCreationConfig>) {
    createCommonTasks(variantInfo)

    val testVariantProperties = variantInfo.variant
    val testData = getTestData(testVariantProperties)
    configureTestData(testVariantProperties, testData)

    // create tasks to validate signing and produce signing config versions file.
    createValidateSigningTask(testVariantProperties)
    taskFactory.register<SigningConfigVersionsWriterTask>(SigningConfigVersionsWriterTask.CreationAction(testVariantProperties))

    // create the test connected check task.
    val instrumentTestTask: TaskProvider<DeviceProviderInstrumentTestTask> =
      taskFactory.register<DeviceProviderInstrumentTestTask>(
        object : DeviceProviderInstrumentTestTask.CreationAction(testVariantProperties, testData) {
          override val name: String
            get() = super.name + ComponentType.ANDROID_TEST_SUFFIX
        }
      )

    taskFactory.configure(CONNECTED_ANDROID_TEST, Action { task: Task? -> task!!.dependsOn(instrumentTestTask) })

    createTestDevicesForVariant(testVariantProperties, testData, testVariantProperties.name, ComponentType.ANDROID_TEST_SUFFIX)
  }

  override fun maybeCreateJavaCodeShrinkerTask(creationConfig: ConsumableCreationConfig) {
    if (creationConfig.optimizationCreationConfig.minifiedEnabled) {
      doCreateJavaCodeShrinkerTask(creationConfig, true)
    } else {
      val checkObfuscation: TaskProvider<CheckTestedAppObfuscation> =
        taskFactory.register<CheckTestedAppObfuscation>(
          CheckTestedAppObfuscation.CreationAction(creationConfig as TestVariantCreationConfig)
        )
      Preconditions.checkNotNull(creationConfig.taskContainer.javacTask)
      creationConfig.taskContainer.javacTask.dependsOn<DefaultTask>(checkObfuscation)
    }
  }

  /** Creates the merge manifests task. */
  override fun createMergeManifestTasks(creationConfig: ApkCreationConfig): TaskProvider<out ManifestProcessorTask> {
    val taskConfig = forTestComponent(creationConfig as TestVariantCreationConfig)

    return taskFactory.register(ProcessTestManifest.CreationAction(taskConfig))
  }
}
