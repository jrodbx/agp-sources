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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.tasks.TestSuiteTestTask.Companion.TEST_RESULT_METADATA_FILE
import com.android.build.gradle.tasks.TestSuiteTestTask.Companion.TEST_RESULT_METADATA_MODULE_KEY
import com.android.build.gradle.tasks.TestSuiteTestTask.Companion.TEST_RESULT_METADATA_SUITE_KEY
import com.android.build.gradle.tasks.TestSuiteTestTask.Companion.TEST_RESULT_METADATA_TARGET_KEY
import com.android.build.gradle.tasks.TestSuiteTestTask.Companion.TEST_RESULT_METADATA_VARIANT_KEY
import com.android.buildanalyzer.common.TaskCategory
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.w3c.dom.Node

@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class TestResultsCollectionTask : NonIncrementalTask() {

  @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val testResults: ListProperty<Directory>

  @get:InputFiles
  @get:Optional
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val dependentModuleTestResults: ConfigurableFileCollection

  @get:OutputDirectory abstract val outputDir: DirectoryProperty

  override fun doTaskAction() {
    val outputDir = this.outputDir.get().asFile

    fun processXml(directory: File) {
      if (directory.exists()) {
        val metadataFiles = directory.listFiles { file -> file.name == TEST_RESULT_METADATA_FILE }
        if (metadataFiles != null) {
          check(metadataFiles.isNotEmpty()) { "No metadata.txt found in ${directory.path} for test results XML processing" }
          val metadata = parseMetadata(metadataFiles[0])

          val metadataBytes = metadataFiles[0].readBytes()
          val digest = MessageDigest.getInstance("MD5").digest(metadataBytes)
          val hashString = BigInteger(1, digest).toString(16).padStart(32, '0')

          directory
            .listFiles { file -> file.extension == "xml" }
            ?.forEach { xmlFile ->
              val newFileName = "${xmlFile.nameWithoutExtension}_${hashString}.xml"
              val targetFile = outputDir.resolve(newFileName)
              injectProperties(xmlFile, metadata, targetFile)
            }
        }
      }
    }

    testResults.get().forEach { directory -> processXml(directory.asFile) }

    dependentModuleTestResults.asFileTree.forEach { xmlFile ->
      val targetFile = outputDir.resolve(xmlFile.name)
      xmlFile.copyTo(targetFile, overwrite = true)
    }
  }

  class AggregatedTestResultsCollectionCreationAction(creationConfig: VariantCreationConfig) :
    BaseTestResultsCollectionCreationAction(creationConfig) {

    override val name = computeTaskName("aggregatedTestResultsCollection")

    override fun configure(task: TestResultsCollectionTask) {
      super.configure(task)

      task.dependentModuleTestResults.from(
        creationConfig.variantDependencies.getArtifactFileCollection(
          AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
          AndroidArtifacts.ArtifactScope.PROJECT,
          AndroidArtifacts.ArtifactType.TEST_RESULTS,
        )
      )
    }

    override fun handleProvider(taskProvider: TaskProvider<TestResultsCollectionTask>) {
      super.handleProvider(taskProvider)
      creationConfig.global.globalArtifacts
        .use(taskProvider)
        .wiredWith(TestResultsCollectionTask::outputDir)
        .toAppendTo(InternalMultipleArtifactType.ALL_PROJECT_TEST_RESULTS)
    }
  }

  class TestResultsCollectionCreationAction(creationConfig: VariantCreationConfig) :
    BaseTestResultsCollectionCreationAction(creationConfig) {

    override val name = computeTaskName("testResultsCollection")

    override fun handleProvider(taskProvider: TaskProvider<TestResultsCollectionTask>) {
      super.handleProvider(taskProvider)

      creationConfig.artifacts
        .setInitialProvider(taskProvider, TestResultsCollectionTask::outputDir)
        .on(InternalArtifactType.VARIANT_TEST_RESULTS)

      creationConfig.global.globalArtifacts
        .use(taskProvider)
        .wiredWith(TestResultsCollectionTask::outputDir)
        .toAppendTo(InternalMultipleArtifactType.PROJECT_LEVEL_TEST_RESULTS)
    }
  }

  abstract class BaseTestResultsCollectionCreationAction(creationConfig: VariantCreationConfig) :
    VariantTaskCreationAction<TestResultsCollectionTask, VariantCreationConfig>(creationConfig) {
    override val type = TestResultsCollectionTask::class.java

    override fun configure(task: TestResultsCollectionTask) {
      super.configure(task)

      task.testResults.set(creationConfig.artifacts.getAll(InternalMultipleArtifactType.TEST_SUITE_RESULTS))
    }
  }

  fun parseMetadata(metadataFile: File): Map<String, String> {
    val metadata =
      metadataFile
        .readLines()
        .mapNotNull { line ->
          val parts = line.split("=", limit = 2)
          if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
        }
        .toMap()

    return mapOf(
      TEST_RESULT_METADATA_MODULE_KEY to (metadata[TEST_RESULT_METADATA_MODULE_KEY] ?: "unknown_module"),
      TEST_RESULT_METADATA_VARIANT_KEY to (metadata[TEST_RESULT_METADATA_VARIANT_KEY] ?: "unknown_variant"),
      TEST_RESULT_METADATA_SUITE_KEY to (metadata[TEST_RESULT_METADATA_SUITE_KEY] ?: "unknown_suite"),
      TEST_RESULT_METADATA_TARGET_KEY to (metadata[TEST_RESULT_METADATA_TARGET_KEY] ?: "unknown_target"),
    )
  }

  fun injectProperties(xmlFile: File, properties: Map<String, String>, targetFile: File) {
    try {
      val docFactory = DocumentBuilderFactory.newInstance()
      val docBuilder = docFactory.newDocumentBuilder()
      val document = docBuilder.parse(xmlFile)

      val propertiesList = document.getElementsByTagName("properties")

      val propertiesNode: Node
      if (propertiesList.length == 0) {
        propertiesNode = document.createElement("properties")
        document.documentElement.appendChild(propertiesNode)
      } else {
        propertiesNode = propertiesList.item(0)
      }
      properties.forEach { (key, value) ->
        val propertyElement = document.createElement("property")
        propertyElement.setAttribute("name", key)
        propertyElement.setAttribute("value", value)
        propertiesNode.appendChild(propertyElement)
      }

      val transformerFactory = TransformerFactory.newInstance()
      val transformer = transformerFactory.newTransformer()
      transformer.setOutputProperty(OutputKeys.INDENT, "yes")

      val source = DOMSource(document)
      targetFile.parentFile.mkdirs()
      val result = StreamResult(targetFile)

      transformer.transform(source, result)
    } catch (e: Exception) {
      logger.warn("Failed to inject properties into XML test results: ${e.message}")
      xmlFile.copyTo(targetFile, overwrite = true)
    }
  }
}
