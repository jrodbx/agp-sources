/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.build.gradle.internal.coverage

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.core.BuilderConstants
import com.android.utils.usLocaleCapitalize
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import org.gradle.workers.ClassLoaderWorkerSpec
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters

/**
 * For generating host test coverage reports using jacoco. Provides separate CreateActions for generating host test and connected test
 * reports.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class JacocoReportTask : NonIncrementalTask() {

  @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) abstract val coverageFiles: ConfigurableFileCollection

  @get:Input abstract val reportName: Property<String>

  @get:Classpath abstract val classFileCollection: ConfigurableFileCollection

  @get:Classpath abstract val jacocoClasspath: ConfigurableFileCollection

  @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val sources: ListProperty<Provider<List<ConfigurableFileTree>>>

  @get:Internal abstract val tabWidth: Property<Int>

  @get:OutputDirectory abstract val outputReportDir: DirectoryProperty

  override fun doTaskAction() {
    val jacocoCoverageFiles = coverageFiles.asFileTree.files.filter(File::isFile)
    if (jacocoCoverageFiles.none()) {
      throw IOException(
        "Test coverage report requested, but no tests were run. " + "Task '${name}' failed because no coverage data was found."
      )
    }

    // Jacoco requires source set directory roots rather than source files to produce
    // source code highlighting in reports.
    val sourceFolders: List<File> =
      sources.get().map { it.get().map(ConfigurableFileTree::getDir) }.flatten().distinctBy { it.absolutePath }

    workerExecutor
      .classLoaderIsolation { classpath: ClassLoaderWorkerSpec -> classpath.classpath.from(jacocoClasspath.files) }
      .submit(JacocoReportWorkerAction::class.java) {
        it.coverageFiles.setFrom(jacocoCoverageFiles)
        it.reportDir.set(outputReportDir)
        it.classFolders.setFrom(classFileCollection)
        it.sourceFolders.setFrom(sourceFolders)
        it.tabWidth.set(tabWidth)
        it.reportName.set(reportName)
      }
  }

  abstract class BaseCreationAction(
    testComponentProperties: TestComponentCreationConfig,
    protected open val jacocoAntConfiguration: Configuration? = null,
    private val coverageReportSubDirName: String = "",
  ) : VariantTaskCreationAction<JacocoReportTask, TestComponentCreationConfig>(testComponentProperties) {

    override val name: String
      get() = computeTaskName("create", "CoverageReport")

    override val type: Class<JacocoReportTask>
      get() = JacocoReportTask::class.java

    override fun handleProvider(taskProvider: TaskProvider<JacocoReportTask>) {
      super.handleProvider(taskProvider)
      creationConfig.taskContainer.coverageReportTask = taskProvider
    }

    override fun configure(task: JacocoReportTask) {
      super.configure(task)
      task.jacocoClasspath.setFrom(jacocoAntConfiguration)
      if (coverageReportSubDirName.isNotBlank()) {
        task.outputReportDir.set(creationConfig.paths.coverageReportDir.map { it.dir(coverageReportSubDirName) })
      } else {
        task.outputReportDir.set(creationConfig.paths.coverageReportDir)
      }
      task.outputReportDir.disallowChanges()
      task.reportName.setDisallowChanges(creationConfig.mainVariant.name)
      task.tabWidth.setDisallowChanges(4)
      creationConfig.mainVariant.sources.java { javaSources -> task.sources.addAll(javaSources.getAsFileTrees()) }
      creationConfig.mainVariant.sources.kotlin { kotlinSources -> task.sources.addAll(kotlinSources.getAsFileTrees()) }
      task.sources.disallowChanges()
      task.classFileCollection.fromDisallowChanges(
        creationConfig.mainVariant.artifacts
          .forScope(ScopedArtifacts.Scope.PROJECT)
          .getScopedArtifactsContainer(ScopedArtifact.CLASSES)
          .finalScopedContent
      )
    }
  }

  internal class CreateActionHostTest(
    testComponentProperties: TestComponentCreationConfig,
    override val jacocoAntConfiguration: Configuration? = null,
    private val testTaskName: String,
    private val internalArtifactType: InternalArtifactType<RegularFile>,
  ) : BaseCreationAction(testComponentProperties, jacocoAntConfiguration) {

    override fun configure(task: JacocoReportTask) {
      super.configure(task)
      val testName = if (creationConfig.componentType.isForScreenshotPreview) "screenshot" else "unit"
      task.description = "Generates a Jacoco code coverage report from $testName tests."
      task.coverageFiles.from(creationConfig.artifacts.get(internalArtifactType))
      task.coverageFiles.disallowChanges()
      /** Jacoco coverage files are generated from [AndroidUnitTest] */
      task.dependsOn("${testTaskName}${creationConfig.name.usLocaleCapitalize()}")
    }
  }

  class CreationActionConnectedTest(
    testComponentProperties: TestComponentCreationConfig,
    override val jacocoAntConfiguration: Configuration,
  ) : BaseCreationAction(testComponentProperties, jacocoAntConfiguration, BuilderConstants.CONNECTED) {

    override fun configure(task: JacocoReportTask) {
      super.configure(task)
      task.description = "Creates JaCoCo test coverage report from data gathered on the device."
      task.coverageFiles.from(creationConfig.artifacts.get(InternalArtifactType.CODE_COVERAGE))
      task.coverageFiles.disallowChanges()
    }
  }

  class CreationActionManagedDeviceTest(
    testComponentProperties: TestComponentCreationConfig,
    override val jacocoAntConfiguration: Configuration,
  ) : BaseCreationAction(testComponentProperties, jacocoAntConfiguration, BuilderConstants.MANAGED_DEVICE) {

    override val name: String
      get() = computeTaskName("createManagedDevice", "CoverageReport")

    override fun configure(task: JacocoReportTask) {
      super.configure(task)
      task.description = "Creates JaCoCo test coverage report from data gathered on the Gradle managed device."
      task.coverageFiles.from(creationConfig.artifacts.get(InternalArtifactType.MANAGED_DEVICE_CODE_COVERAGE))
      task.coverageFiles.disallowChanges()
    }
  }

  interface JacocoWorkParameters : WorkParameters {
    val coverageFiles: ConfigurableFileCollection
    val reportDir: DirectoryProperty
    val classFolders: ConfigurableFileCollection
    val sourceFolders: ConfigurableFileCollection
    val tabWidth: Property<Int>
    val reportName: Property<String>
  }

  abstract class JacocoReportWorkerAction : WorkAction<JacocoWorkParameters> {

    override fun execute() {
      try {
        generateReport(
          parameters.coverageFiles.files,
          parameters.reportDir.asFile.get(),
          parameters.classFolders.files,
          parameters.sourceFolders.files,
          parameters.tabWidth.get(),
          parameters.reportName.get(),
          logger,
        )
      } catch (e: IOException) {
        throw UncheckedIOException("Unable to generate Jacoco report", e)
      }
      val reportLocation = parameters.reportDir.locationOnly.get().file("index.html").asFile.toURI()
      logger.lifecycle("View coverage report at $reportLocation")
    }

    companion object {
      val logger = Logging.getLogger(JacocoReportWorkerAction::class.java)
    }
  }
}
