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

package com.android.build.gradle.internal.test.report

import com.android.build.gradle.internal.LoggerWrapper
import com.google.common.annotations.VisibleForTesting
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Type
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException

/**
 * Aggregates test results from multiple XML report streams.
 *
 * The `getReport()` method converts this internal map structure into the final list-based [RootReport] data model for serialization.
 */
class XMLReportAggregator(private val files: List<File>) {

  @VisibleForTesting fun getInputFiles(): List<File> = files

  private val logger = LoggerWrapper.getLogger(XMLReportAggregator::class.java)

  // Global set of all unique variant names encountered.
  private val rootReportBuilder = RootReportBuilder()

  /** Generates the final [RootReport] by processing all input files. */
  fun generateReport(): RootReport {
    getInputFiles().forEach { file -> processXmlForAggregation(file) }
    return getReport()
  }

  /** Generates the RootReport and writes it to the specified output directory along with the necessary JSON, JS, and HTML resources. */
  fun writeReport(outputDir: File) {
    val finalReport = generateReport()
    val gson = GsonBuilder().setPrettyPrinting().registerTypeAdapter(Function::class.java, FunctionAdapter()).create()
    val jsonString = gson.toJson(finalReport)

    if (!outputDir.exists()) {
      outputDir.mkdirs()
    }

    File(outputDir, "data.js").writeText("const TEST_DATA_SOURCE = $jsonString")

    val resources = listOf("index.html", "script.js", "styles.css")
    resources.forEach { fileName ->
      XMLReportAggregator::class.java.getResourceAsStream("/com/android/build/gradle/internal/test/report/renderer/$fileName")?.use {
        inputStream ->
        File(outputDir, fileName).outputStream().use { outputStream -> inputStream.copyTo(outputStream) }
      }
    }
  }

  private class FunctionAdapter : JsonSerializer<Function> {
    override fun serialize(src: Function, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
      val jsonObject = JsonObject()
      jsonObject.addProperty("name", src.name)
      src.results.forEach { (variant, result) ->
        if (result.stackTrace == null) {
          jsonObject.addProperty(variant, result.status)
        } else {
          val resultObj = JsonObject()
          resultObj.addProperty("status", result.status)
          resultObj.addProperty("stackTrace", result.stackTrace)
          jsonObject.add(variant, resultObj)
        }
      }
      return jsonObject
    }
  }

  /**
   * Processes all XML files in the given directory for aggregation.
   *
   * @param outputDir the directory containing XML report files
   */
  private fun processXmlForAggregation(outputDir: File) {
    if (!outputDir.exists()) {
      logger.warning("Test result output directory '${outputDir.absolutePath}' does not exist. Skipping aggregation for this directory.")
      return
    }

    outputDir
      .listFiles()
      ?.filter {
        it.isFile &&
          // We only want files with the "xml" extension
          it.extension == EXT_XML
      }
      ?.forEach { xmlFile ->
        logger.verbose("Found XML file: ${xmlFile.name}")
        try {
          xmlFile.inputStream().use { inputStream -> processXmlStream(inputStream, xmlFile.name) }
        } catch (e: IOException) {
          logger.error(e, "Error reading file ${xmlFile.name}")
        } catch (e: Exception) {
          logger.error(e, "Error processing file ${xmlFile.name}")
        }
      }
  }

  /**
   * Parses a single XML stream and adds the results to the aggregated structure.
   *
   * @param inputStream the XML stream to parse
   * @param streamName optional name for the stream (used for logging)
   */
  private fun processXmlStream(inputStream: InputStream, streamName: String? = null) {
    var variantName: String? = null
    val factory = XMLInputFactory.newInstance()
    factory.setProperty(XMLInputFactory.IS_COALESCING, true)
    // Security: Disable DTDs and external entities to prevent XXE attacks
    try {
      factory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
      factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    } catch (e: IllegalArgumentException) {
      logger.error(e, "Could not set some security properties on XMLInputFactory: ${e.message}")
    }

    try {
      inputStream.use { stream ->
        val reader = factory.createXMLStreamReader(stream)
        var modulePath: String? = null
        var testSuiteName: String? = null

        // Variables for the current test case being processed
        var currentClassname: String? = null
        var currentTestcaseName: String? = null
        var currentStatus = STATUS_PASS // Default status

        // Buffer to accumulate text content (stack trace)
        var failureBuffer: StringBuilder? = null

        while (reader.hasNext()) {
          when (reader.next()) {
            XMLStreamConstants.START_ELEMENT -> {
              when (reader.localName) {
                TAG_PROPERTY -> {
                  val name = reader.getAttributeValue(null, ATTR_NAME)
                  val value = reader.getAttributeValue(null, ATTR_VALUE)
                  if (name == KEY_MODULE_PATH) modulePath = value
                  if (name == KEY_TEST_SUITE_NAME) testSuiteName = value
                  if (name == KEY_TESTED_VARIANT_NAME) {
                    rootReportBuilder.addVariant(value)
                    variantName = value
                  }
                }
                TAG_TESTCASE -> {
                  currentClassname = reader.getAttributeValue(null, ATTR_CLASSNAME)
                  currentTestcaseName = reader.getAttributeValue(null, ATTR_NAME)
                  currentStatus = STATUS_PASS // Reset status for this new test
                  failureBuffer = null // Reset failure buffer
                }
                TAG_SKIPPED -> {
                  currentStatus = STATUS_SKIPPED
                }
                TAG_FAILURE,
                TAG_ERROR -> {
                  currentStatus = STATUS_FAIL
                  // Initialize buffer to capture stack trace text
                  failureBuffer = StringBuilder()
                }
              }
            }
            XMLStreamConstants.CHARACTERS -> {
              // If we are inside a failure/error tag, append text to buffer
              if (failureBuffer != null) {
                failureBuffer.append(reader.text)
              }
            }
            XMLStreamConstants.END_ELEMENT -> {
              if (reader.localName == TAG_TESTCASE) {
                if (
                  modulePath != null &&
                    testSuiteName != null &&
                    currentClassname != null &&
                    currentTestcaseName != null &&
                    variantName != null
                ) {

                  // Extract and clean stack trace
                  val stackTrace = failureBuffer?.toString()?.trim()?.takeIf { it.isNotEmpty() }

                  addTestResult(
                    modulePath,
                    testSuiteName,
                    currentClassname,
                    currentTestcaseName,
                    variantName,
                    TestResults(currentStatus, stackTrace),
                  )
                }
                // Clear testcase-specific data
                currentClassname = null
                currentTestcaseName = null
                failureBuffer = null
              }
            }
          }
        }
        reader.close()
      }
    } catch (e: XMLStreamException) {
      val location = streamName ?: "stream"
      logger.error(e, "Error parsing XML for variant '$variantName' in $location")
    } catch (e: Exception) {
      val location = streamName ?: "stream"
      logger.error(e, "An unexpected error occurred for variant '$variantName' in $location")
    }
  }

  /** Converts the internal aggregated structure into the final [RootReport] data model, ready for JSON serialization. */
  private fun getReport(): RootReport {
    return rootReportBuilder.build()
  }

  private class RootReportBuilder {
    private val variants = HashSet<String>()
    private val moduleBuilders = mutableMapOf<String, ModuleBuilder>()

    fun addVariant(variant: String) {
      variants.add(variant)
    }

    fun getOrAddModule(name: String) = moduleBuilders.getOrPut(name) { ModuleBuilder(name) }

    fun build() = RootReport(variants = variants.sorted(), modules = moduleBuilders.values.map { it.build() }.sortedBy { it.name })
  }

  private class ModuleBuilder(val name: String) {
    val testSuites = mutableMapOf<String, TestSuiteBuilder>()

    fun getOrAddTestSuite(name: String) = testSuites.getOrPut(name) { TestSuiteBuilder(name) }

    fun build() = Module(name = name, testSuites = testSuites.values.map { it.build() }.sortedBy { it.name })
  }

  private class TestSuiteBuilder(val name: String) {
    val packages = mutableMapOf<String, PackageBuilder>()

    fun getOrAddPackage(name: String) = packages.getOrPut(name) { PackageBuilder(name) }

    fun build() = TestSuite(name = name, packages = packages.values.map { it.build() }.sortedBy { it.name })
  }

  private class PackageBuilder(val name: String) {
    val classes = mutableMapOf<String, ClassBuilder>()

    fun getOrAddClass(name: String) = classes.getOrPut(name) { ClassBuilder(name) }

    fun build() = Package(name = name, classes = classes.values.map { it.build() }.sortedBy { it.name })
  }

  private class ClassBuilder(val name: String) {
    val functions = mutableMapOf<String, FunctionBuilder>()

    fun getOrAddFunction(name: String) = functions.getOrPut(name) { FunctionBuilder(name) }

    fun build() = ClassType(name = name, functions = functions.values.map { it.build() }.sortedBy { it.name })
  }

  private class FunctionBuilder(val name: String) {
    val results = mutableMapOf<String, TestResults>()

    fun addResult(variant: String, result: TestResults) {
      results[variant] = result
    }

    fun build() = Function(name = name, results = results.toMap())
  }

  /** Safely adds a test result to the nested structure. */
  private fun addTestResult(
    modulePath: String,
    testSuiteName: String,
    classname: String,
    testcaseName: String,
    variantName: String,
    result: TestResults,
  ) {
    try {
      val packageName = classname.substringBeforeLast('.', "")
      val className = classname.substringAfterLast('.')

      rootReportBuilder
        .getOrAddModule(modulePath)
        .getOrAddTestSuite(testSuiteName)
        .getOrAddPackage(packageName)
        .getOrAddClass(className)
        .getOrAddFunction(testcaseName)
        .addResult(variantName, result)
    } catch (e: Exception) {
      logger.error(e, "Error processing test case: $classname.$testcaseName")
    }
  }
}
