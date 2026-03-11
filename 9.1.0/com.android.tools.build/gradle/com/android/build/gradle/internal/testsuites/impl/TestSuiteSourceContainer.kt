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
import com.android.build.api.dsl.AgpTestSuiteDependencies
import com.android.build.api.variant.TestSuiteSourceSet
import com.android.build.api.variant.TestSuiteSourceType
import com.android.build.gradle.internal.ApkTestSuiteTaskManager
import com.android.build.gradle.internal.HostJarTestSuiteTaskManager
import com.android.build.gradle.internal.api.HostJarTestSuiteSourceSet
import com.android.build.gradle.internal.api.TestApkTestSuiteSourceSet
import com.android.build.gradle.internal.component.TestSuiteCreationConfig
import com.android.build.gradle.internal.dependency.TestSuiteSourceClasspath
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.tasks.factory.TaskFactoryImpl
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

/**
 * Each test suite source type will be processed in isolation, most likely using a [com.android.build.api.artifact.Artifacts] instance to
 * store intermediate files, etc... Each source type has its own set of dependencies which are independent from other sources on the same
 * test suite.
 *
 * The TestSuiteSourceContainer represent the isolated container for sources and their derivatives (like compileClasspath) for a particular
 * test suite.
 */
class TestSuiteSourceContainer(
  project: Project,
  private val targetVariantName: String,
  private val testSuiteName: String,
  internal val source: TestSuiteSourceSet,
  override val dependencies: AgpTestSuiteDependencies,
  internal val suiteSourceClasspath: TestSuiteSourceClasspath,
) : TestSuiteSourceSet {

  override fun getName(): String = testSuiteName

  /** Returns a unique name for this source container within the test suite. */
  val identifier = "$testSuiteName${targetVariantName.capitalizeFirstChar()}"

  override val type: TestSuiteSourceType
    get() = source.type

  val artifacts = ArtifactsImpl(project, identifier)

  /**
   * Creates all the test source processing tasks and return the [TaskProvider] that can be used as a dependent of the
   * [com.android.build.gradle.tasks.TestSuiteTestTask] for successful execution.
   *
   * @return the top level or lifecycle task for this [source] to be processed entirely.
   */
  fun createTasks(taskCreationServices: TaskCreationServices, creationConfig: TestSuiteCreationConfig): TaskProvider<out Task>? {
    return when (source.type) {
      TestSuiteSourceType.ASSETS -> {
        // nothing to do for assets based source folder so far.
        null
      }
      TestSuiteSourceType.HOST_JAR -> {
        HostJarTestSuiteTaskManager().createTasks(this, source as HostJarTestSuiteSourceSet, taskFactory, taskCreationServices)
      }
      TestSuiteSourceType.TEST_APK -> {
        ApkTestSuiteTaskManager().createTasks(this, source as TestApkTestSuiteSourceSet, taskFactory, creationConfig)
      }
      else -> {
        throw RuntimeException("Unhandled test suite source set $source of type ${source.type}")
      }
    }
  }

  private val taskFactory = TaskFactoryImpl(project.tasks)
}
