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

package com.android.build.gradle.internal.coverage.tasks

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.internal.coverage.generateReport
import com.android.build.gradle.internal.coverage.report.ReportType
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import org.gradle.workers.ClassLoaderWorkerSpec
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.w3c.dom.Node
import org.xml.sax.SAXException

/**
 * For generating and collecting jacoco xml coverage reports for unit tests, instrumentation tests and test suites for current module and
 * dependent modules.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class CodeCoverageCollectionTask : NonIncrementalTask() {

  @get:OutputDirectory abstract val reportOutputDir: DirectoryProperty

  @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) @get:Optional abstract val unitTestCoverageFile: ConfigurableFileCollection

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  @get:Optional
  abstract val connectedTestCoverageDirectory: ConfigurableFileCollection

  @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val classFileCollection: ConfigurableFileCollection

  @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val sources: ListProperty<Provider<List<ConfigurableFileTree>>>

  @get:InputFiles
  @get:Optional
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val dependentModuleCoverageData: ConfigurableFileCollection

  @get:Classpath abstract val jacocoClasspath: ConfigurableFileCollection

  @get:Internal abstract val projectName: Property<String>

  @get:Internal abstract val projectRoot: DirectoryProperty

  override fun doTaskAction() {
    val sourceFolders: List<File> =
      sources.get().map { it.get().map(ConfigurableFileTree::getDir) }.flatten().distinctBy { it.absolutePath }

    // TODO: Add test suites implementation for XML reports generation.

    // ClassLoaderIsolation is used to ensure that the Jacoco classes and dependencies
    // are loaded in a separate classloader. This prevents potential conflicts with
    // other versions of Jacoco or related libraries that might be present in the
    // Gradle build environment or in the user's project.
    workerExecutor
      .classLoaderIsolation { classpath: ClassLoaderWorkerSpec -> classpath.classpath.from(jacocoClasspath.files) }
      .submit(CodeCoverageCollectionWorkerAction::class.java) {
        it.reportOutputDir.set(reportOutputDir)
        it.unitTestCoverageFile.setFrom(unitTestCoverageFile)
        it.connectedTestCoverageDirectory.setFrom(connectedTestCoverageDirectory)
        it.classFolders.setFrom(classFileCollection)
        it.sourceFolders.setFrom(sourceFolders)
        it.dependentModuleCoverageData.setFrom(dependentModuleCoverageData)
        it.variantName.set(variantName)
        it.projectName.set(projectName)
        it.projectRoot.set(projectRoot)
      }
  }

  abstract class BaseCoverageCollectionCreationAction(
    val jacocoAntConfiguration: Configuration,
    creationConfig: CodeCoverageReportCreationConfig,
  ) : VariantTaskCreationAction<CodeCoverageCollectionTask, CodeCoverageReportCreationConfig>(creationConfig) {

    override val type: Class<CodeCoverageCollectionTask>
      get() = CodeCoverageCollectionTask::class.java

    override fun configure(task: CodeCoverageCollectionTask) {
      super.configure(task)

      task.projectName.set(creationConfig.services.projectInfo.path)
      task.projectRoot.set(task.project.rootDir)
      task.jacocoClasspath.setFrom(jacocoAntConfiguration)

      creationConfig.unitTestCoverageFile?.let { task.unitTestCoverageFile.fromDisallowChanges(it) }

      creationConfig.connectedTestCoverageDirectory?.let { task.connectedTestCoverageDirectory.fromDisallowChanges(it) }

      creationConfig.java { javaSources -> task.sources.addAll(javaSources.getAsFileTrees()) }
      creationConfig.kotlin { kotlinSources -> task.sources.addAll(kotlinSources.getAsFileTrees()) }
      task.sources.disallowChanges()
      task.classFileCollection.fromDisallowChanges(
        creationConfig.artifacts
          .forScope(ScopedArtifacts.Scope.PROJECT)
          .getScopedArtifactsContainer(ScopedArtifact.CLASSES)
          .finalScopedContent
      )
    }
  }

  class CoverageCollectionCreationAction(jacocoAntConfiguration: Configuration, creationConfig: CodeCoverageReportCreationConfig) :
    BaseCoverageCollectionCreationAction(jacocoAntConfiguration, creationConfig) {

    override val name: String
      get() = computeTaskName("collect", "Coverage")

    override fun handleProvider(taskProvider: TaskProvider<CodeCoverageCollectionTask>) {
      super.handleProvider(taskProvider)

      creationConfig.artifacts
        .setInitialProvider(taskProvider, CodeCoverageCollectionTask::reportOutputDir)
        .on(InternalArtifactType.VARIANT_CODE_COVERAGE_DATA)

      creationConfig.globalArtifacts
        .use(taskProvider)
        .wiredWith(CodeCoverageCollectionTask::reportOutputDir)
        .toAppendTo(InternalMultipleArtifactType.CODE_COVERAGE_DATA)
    }
  }

  class AggregatedCoverageCollectionCreationAction(
    jacocoAntConfiguration: Configuration,
    creationConfig: CodeCoverageReportCreationConfig,
  ) : BaseCoverageCollectionCreationAction(jacocoAntConfiguration, creationConfig) {

    override val name: String
      get() = computeTaskName("collect", "AggregatedCoverage")

    override fun configure(task: CodeCoverageCollectionTask) {
      super.configure(task)

      task.dependentModuleCoverageData.from(creationConfig.dependantModulesReports)
    }

    override fun handleProvider(taskProvider: TaskProvider<CodeCoverageCollectionTask>) {
      super.handleProvider(taskProvider)

      creationConfig.globalArtifacts
        .use(taskProvider)
        .wiredWith(CodeCoverageCollectionTask::reportOutputDir)
        .toAppendTo(InternalMultipleArtifactType.AGGREGATED_CODE_COVERAGE_DATA)
    }
  }

  interface CodeCoverageWorkParameters : WorkParameters {
    val reportOutputDir: DirectoryProperty
    val unitTestCoverageFile: ConfigurableFileCollection
    val connectedTestCoverageDirectory: ConfigurableFileCollection
    val classFolders: ConfigurableFileCollection
    val sourceFolders: ConfigurableFileCollection
    val dependentModuleCoverageData: ConfigurableFileCollection
    val variantName: Property<String>
    val projectName: Property<String>
    val projectRoot: DirectoryProperty
  }

  abstract class CodeCoverageCollectionWorkerAction : WorkAction<CodeCoverageWorkParameters> {

    override fun execute() {

      try {
        val usedXmlFileNames = mutableSetOf<String>()
        val formattedName = formatProjectName(parameters.projectName.get())

        val generateXmlReport = { coverageFiles: Collection<File>, testSuiteName: String ->
          if (coverageFiles.isNotEmpty()) {
            val baseReportName = "${parameters.variantName.get()}${formattedName}${testSuiteName}"
            var xmlReportFileName = "${baseReportName}XmlReport"

            if (usedXmlFileNames.contains(xmlReportFileName)) {
              // In case of a collision of XML resolved names, adding timestamp for differentiation.
              xmlReportFileName = "${xmlReportFileName}_${System.currentTimeMillis()}"
            }
            usedXmlFileNames.add(xmlReportFileName)

            generateReport(
              coverageFiles = coverageFiles,
              reportDir = parameters.reportOutputDir.asFile.get(),
              classFolders = parameters.classFolders.files,
              sourceFolders = parameters.sourceFolders.files,
              tabWidth = 4,
              reportName = baseReportName,
              logger = logger,
              reportTypes = listOf(ReportType.XML),
              xmlReportName = xmlReportFileName,
            )

            val xmlFile = File(parameters.reportOutputDir.asFile.get().absolutePath, "${xmlReportFileName}.xml")
            val rootDir = parameters.projectRoot.get().asFile

            injectMetadataInXmlReport(
              xmlFile,
              mapOf(
                "moduleName" to parameters.projectName.get(),
                "testSuiteName" to testSuiteName,
                "testedVariantName" to parameters.variantName.get(),
              ),
              sourceFolders = parameters.sourceFolders.files.map { it.relativeTo(rootDir).path },
            )
          }
        }

        val unitTestCoverageFile = parameters.unitTestCoverageFile.files.filter { it.exists() }
        generateXmlReport(unitTestCoverageFile, "UnitTest")

        val connectedTestCoverageFile = parameters.connectedTestCoverageDirectory.asFileTree.files.filter(File::isFile)
        generateXmlReport(connectedTestCoverageFile, "AndroidTest")

        val mergedCoverageFiles = connectedTestCoverageFile + unitTestCoverageFile
        generateXmlReport(mergedCoverageFiles, "Aggregated")

        parameters.dependentModuleCoverageData.asFileTree.forEach { xmlFile ->
          val targetFile = parameters.reportOutputDir.asFile.get().resolve(xmlFile.name)
          xmlFile.copyTo(targetFile, overwrite = true)
        }
      } catch (e: Exception) {
        throw GradleException("Unable to generate Jacoco XML report", e)
      }
    }

    companion object {
      val logger = Logging.getLogger(CodeCoverageCollectionWorkerAction::class.java)

      /**
       * Formats a Gradle project name like ":app" or ":core:datastore" into a capitalized, CamelCase string like "App" or "CoreDatastore".
       *
       * @param projectName The Gradle project path.
       * @return The formatted name.
       */
      fun formatProjectName(projectName: String): String {
        return projectName.split(':').filter { it.isNotEmpty() }.joinToString("") { part -> part.replaceFirstChar { it.uppercase() } }
      }

      /**
       * Injects metadata into the generated Jacoco XML report.
       *
       * This function adds custom properties and source folder information to the XML report generated by Jacoco. The properties are added
       * under a `<properties>` tag, and the source folders are added under a `<sources>` tag.
       *
       * The following properties are added:
       * - "moduleName": The name of the Gradle module.
       * - "testSuiteName": The name of the test suite (e.g., "UnitTest", "AndroidTest", "Aggregated").
       * - "testedVariantName": The name of the Android variant being tested.
       *
       * @param xmlFile The Jacoco XML report file.
       * @param properties A map of key-value pairs to be added as properties.
       * @param sourceFolders A list of source folder paths to be added.
       */
      fun injectMetadataInXmlReport(xmlFile: File, properties: Map<String, String>, sourceFolders: List<String>) {
        try {
          val docFactory = DocumentBuilderFactory.newInstance()
          docFactory.isValidating = false
          docFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
          docFactory.isIgnoringElementContentWhitespace = true

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

          val sourcesList = document.getElementsByTagName("sources")

          val sourcesNode: Node
          if (sourcesList.length == 0) {
            sourcesNode = document.createElement("sources")
            document.documentElement.appendChild(sourcesNode)
          } else {
            sourcesNode = sourcesList.item(0)
          }
          sourceFolders.forEach { folderPath ->
            val fileElement = document.createElement("file")
            fileElement.setAttribute("path", folderPath)
            sourcesNode.appendChild(fileElement)
          }

          val transformerFactory = TransformerFactory.newInstance()
          val transformer = transformerFactory.newTransformer()

          val source = DOMSource(document)
          val result = StreamResult(xmlFile)

          transformer.transform(source, result)
        } catch (e: ParserConfigurationException) {
          throw Exception("Error configuring XML parser", e)
        } catch (e: SAXException) {
          throw Exception("Error parsing XML Report", e)
        } catch (e: IOException) {
          throw Exception("Error reading or writing XML Report", e)
        } catch (e: TransformerException) {
          throw Exception("Error transforming XML Report", e)
        }
      }
    }
  }
}
