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

package com.android.build.gradle.internal.coverage.renderer.xmlparser

import com.android.build.gradle.internal.coverage.renderer.builders.ClassReportBuilder
import com.android.build.gradle.internal.coverage.renderer.builders.CoverageReportBuilder
import com.android.build.gradle.internal.coverage.renderer.builders.LineCoverageBuilder
import com.android.build.gradle.internal.coverage.renderer.builders.MethodReportBuilder
import com.android.build.gradle.internal.coverage.renderer.builders.ModuleReportBuilder
import com.android.build.gradle.internal.coverage.renderer.builders.PackageReportBuilder
import com.android.build.gradle.internal.coverage.renderer.builders.SourceFileReportBuilder
import com.android.build.gradle.internal.coverage.renderer.builders.SourceFileReportsBuilder
import com.android.build.gradle.internal.coverage.renderer.builders.TestSuiteFileCoverageBuilder
import com.android.build.gradle.internal.coverage.renderer.builders.TestSuiteReportCoverageBuilder
import com.android.build.gradle.internal.coverage.renderer.builders.VariantFileCoverageBuilder
import com.android.build.gradle.internal.coverage.renderer.data.CoverageInfo
import com.android.build.gradle.internal.coverage.renderer.data.VariantCoverage
import com.android.build.gradle.internal.coverage.renderer.data.VariantLineNumber
import com.android.build.gradle.internal.coverage.renderer.data.VariantSourceFilePath
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.ATTR_COVERED_BRANCHES
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.ATTR_COVERED_INSTRUCTIONS
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.ATTR_DESC
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.ATTR_LINE
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.ATTR_LINE_NUMBER
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.ATTR_MISSED_BRANCHES
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.ATTR_MISSED_INSTRUCTIONS
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.ATTR_NAME
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.ATTR_PATH
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.ATTR_SOURCE_FILENAME
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.COUNTER_TYPE_BRANCH
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.COUNTER_TYPE_INSTRUCTION
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.COUNTER_TYPE_LINE
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.KEY_MODULE_NAME
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.KEY_TEST_SUITE_NAME
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.KEY_VARIANT_NAME
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.TAG_CLASS
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.TAG_COUNTER
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.TAG_FILE
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.TAG_LINE
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.TAG_METHOD
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.TAG_PACKAGE
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.TAG_PROPERTIES
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.TAG_SOURCES
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.TAG_SOURCE_FILE
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.VALUE_AGGREGATED
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.VALUE_DEFAULT
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.VALUE_UNKNOWN
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.calculatePercent
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.elements
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.findProperty
import com.android.build.gradle.internal.coverage.renderer.xmlparser.utils.parseSingleCounter
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Parses a JaCoCo XML report and transforms its data into a structured, mutable builder model.
 *
 * This object is designed to parse multiple XML files, accumulating the results into the collections provided to the [transform] method.
 */
object XMLTransformer {

  /** A context object to hold metadata about the current report being parsed. */
  private data class ReportContext(val moduleName: String, val variantName: String, val testSuiteName: String)

  /**
   * Parses a single JaCoCo XML file and populates the given mutable builder collections.
   *
   * @param xmlFile The JaCoCo XML report file to parse.
   * @param projectBaseDir The base directory of the project, used to calculate relative paths.
   * @param coverageBuilder The main builder for the entire coverage report.
   * @param sourceFileReportsBuilder The top-level builder for all source file reports.
   */
  fun transform(
    xmlFile: File,
    projectBaseDir: File,
    coverageBuilder: CoverageReportBuilder,
    sourceFileReportsBuilder: SourceFileReportsBuilder,
  ) {
    if (!xmlFile.exists()) return

    val factory = DocumentBuilderFactory.newInstance()
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    factory.isXIncludeAware = false
    factory.isExpandEntityReferences = false

    val doc = factory.newDocumentBuilder().parse(xmlFile)
    val rootElement = doc.documentElement.apply { normalize() }

    val context = parseReportContext(rootElement)
    if (context.testSuiteName != VALUE_AGGREGATED) coverageBuilder.allTestSuiteNames.add(context.testSuiteName + context.moduleName)

    val moduleReportBuilder = coverageBuilder.moduleReportBuilders.getOrPut(context.moduleName) { ModuleReportBuilder(context.moduleName) }
    val overallCoverage = parseCoverageCounters(rootElement)
    updateAggregatedCoverages(context, overallCoverage, coverageBuilder.aggregatedVariantCoverages)

    moduleReportBuilder.testSuiteCoverages
      .getOrPut(context.testSuiteName) { TestSuiteReportCoverageBuilder(context.testSuiteName) }
      .variantCoverages
      .add(VariantCoverage(context.variantName, overallCoverage.instruction, overallCoverage.branch))

    val sourceFileLocations = parseSourceFileLocations(doc.documentElement)

    for (packageNode in rootElement.getElementsByTagName(TAG_PACKAGE).elements) {
      parsePackage(packageNode, context, projectBaseDir, moduleReportBuilder, sourceFileReportsBuilder, sourceFileLocations)
    }
  }

  private fun parseReportContext(rootElement: Element): ReportContext {
    val propertiesNode = rootElement.getElementsByTagName(TAG_PROPERTIES).item(0) as? Element
    return ReportContext(
      moduleName = findProperty(propertiesNode, KEY_MODULE_NAME)?.takeIf { it.isNotEmpty() } ?: VALUE_DEFAULT,
      variantName = findProperty(propertiesNode, KEY_VARIANT_NAME) ?: VALUE_UNKNOWN,
      testSuiteName = findProperty(propertiesNode, KEY_TEST_SUITE_NAME) ?: VALUE_AGGREGATED,
    )
  }

  private fun updateAggregatedCoverages(
    context: ReportContext,
    overallCoverage: AllCounters,
    aggregatedVariantCoverages: MutableMap<String, VariantCoverage>,
  ) {
    val projectCoverage =
      aggregatedVariantCoverages.getOrPut(context.variantName) {
        VariantCoverage(context.variantName, CoverageInfo(0, 0, 0), CoverageInfo(0, 0, 0))
      }
    val newCovered = projectCoverage.instruction.covered + overallCoverage.instruction.covered
    val newTotal = projectCoverage.instruction.total + overallCoverage.instruction.total
    aggregatedVariantCoverages[context.variantName] =
      projectCoverage.copy(instruction = CoverageInfo(calculatePercent(newCovered, newTotal), newCovered, newTotal))
  }

  private fun parsePackage(
    packageNode: Element,
    context: ReportContext,
    projectBaseDir: File,
    moduleReportBuilder: ModuleReportBuilder,
    sourceFileReportsBuilder: SourceFileReportsBuilder,
    sourceFileLocations: List<String>,
  ) {
    val packageName = packageNode.getAttribute(ATTR_NAME).replace('/', '.').takeIf { it.isNotEmpty() } ?: VALUE_DEFAULT
    val packageCounters = parseCoverageCounters(packageNode)
    val packageVariantCoverage = VariantCoverage(context.variantName, packageCounters.instruction, packageCounters.branch)

    val packageReportBuilder = moduleReportBuilder.packages.getOrPut(packageName) { PackageReportBuilder(packageName, context.moduleName) }

    packageReportBuilder.testSuiteCoverages
      .getOrPut(context.testSuiteName) { TestSuiteReportCoverageBuilder(context.testSuiteName) }
      .variantCoverages
      .add(packageVariantCoverage)

    val sourceFileNameToPath = mutableMapOf<String, String>()

    for (classNode in packageNode.getElementsByTagName(TAG_CLASS).elements) {
      parseClass(classNode, context, packageName, packageReportBuilder)
    }

    for (sourceFileNode in packageNode.getElementsByTagName(TAG_SOURCE_FILE).elements) {
      parseSourceFile(
        sourceFileNode,
        context,
        packageName,
        projectBaseDir,
        sourceFileLocations,
        sourceFileReportsBuilder,
        sourceFileNameToPath,
      )
    }

    // After all source files in the package are parsed and their paths resolved,
    // update the class builders with the correct source file paths.
    updateSourceFilePathsInClassBuilders(context.variantName, sourceFileNameToPath, packageReportBuilder)
  }

  private fun parseClass(classNode: Element, context: ReportContext, packageName: String, packageReportBuilder: PackageReportBuilder) {
    val className = classNode.getAttribute(ATTR_NAME).substringAfterLast('/').takeIf { it.isNotEmpty() } ?: VALUE_DEFAULT
    val sourceFileName = classNode.getAttribute(ATTR_SOURCE_FILENAME)
    val classCounters = parseCoverageCounters(classNode)
    val classVariantCoverage = VariantCoverage(context.variantName, classCounters.instruction, classCounters.branch)

    val classReportBuilder = packageReportBuilder.classes.getOrPut(className) { ClassReportBuilder(className, packageName, sourceFileName) }

    classReportBuilder.testSuiteCoverages
      .getOrPut(context.testSuiteName) { TestSuiteReportCoverageBuilder(context.testSuiteName) }
      .variantCoverages
      .add(classVariantCoverage)

    for (methodNode in classNode.getElementsByTagName(TAG_METHOD).elements) {
      parseMethod(methodNode, context.variantName, classReportBuilder)
    }
  }

  private fun parseMethod(methodNode: Element, variantName: String, classReportBuilder: ClassReportBuilder) {
    val methodName = (methodNode.getAttribute(ATTR_NAME) + methodNode.getAttribute(ATTR_DESC)).takeIf { it.isNotEmpty() } ?: VALUE_DEFAULT
    val methodLine = methodNode.getAttribute(ATTR_LINE).toIntOrNull() ?: 0

    classReportBuilder.methods
      .getOrPut(methodName) { MethodReportBuilder(methodName) }
      .variantLineNumbers
      .add(VariantLineNumber(variantName, methodLine))
  }

  private fun parseSourceFile(
    sourceFileNode: Element,
    context: ReportContext,
    packageName: String,
    projectBaseDir: File,
    sourceRoots: List<String>,
    sourceFileReportsBuilder: SourceFileReportsBuilder,
    sourceFileNameToPath: MutableMap<String, String>,
  ) {
    val sourceFileName = sourceFileNode.getAttribute(ATTR_NAME)
    val packagePath = if (packageName == VALUE_DEFAULT) "" else packageName.replace('.', '/') + "/"

    val sourceFileObject = locateSourceFile(sourceRoots, projectBaseDir, packagePath, sourceFileName) ?: return

    val relativePath = sourceFileObject.relativeTo(projectBaseDir).path.replace(File.separatorChar, '/')
    val pathPrefixToPackage =
      if (packagePath.isNotEmpty()) {
        relativePath.substringBefore(packagePath)
      } else {
        // Handle default package case
        val lastSlashIndex = relativePath.lastIndexOf('/')
        if (lastSlashIndex == -1) "" else relativePath.take(lastSlashIndex + 1)
      }
    val packageFlattenedPath = "${pathPrefixToPackage}${packageName}/${sourceFileName}"

    sourceFileNameToPath[sourceFileName] = packageFlattenedPath

    val sourceFileBuilder =
      sourceFileReportsBuilder.sourceFileBuilders.getOrPut(relativePath) { SourceFileReportBuilder(packageFlattenedPath) }
    val variantBuilder = sourceFileBuilder.variantFileCoverageBuilders.getOrPut(context.variantName) { VariantFileCoverageBuilder() }

    val sourceFileCounters = parseCoverageCounters(sourceFileNode)
    val testSuiteBuilder =
      variantBuilder.testSuiteFileCoverageBuilders.getOrPut(context.testSuiteName) {
        TestSuiteFileCoverageBuilder(fileCoverage = sourceFileCounters.line)
      }

    sourceFileNode.getElementsByTagName(TAG_LINE).elements.forEach { lineNode ->
      val coveredInstructions = lineNode.getAttribute(ATTR_COVERED_INSTRUCTIONS).toInt()
      val missedInstructions = lineNode.getAttribute(ATTR_MISSED_INSTRUCTIONS).toInt()
      val coveredBranches = lineNode.getAttribute(ATTR_COVERED_BRANCHES).toInt()
      val missedBranches = lineNode.getAttribute(ATTR_MISSED_BRANCHES).toInt()
      val lineNumber = lineNode.getAttribute(ATTR_LINE_NUMBER).toInt()

      testSuiteBuilder.lineCoverageBuilders[lineNumber] =
        LineCoverageBuilder(
          instructionCoverageInfo =
            CoverageInfo(
              percent = calculatePercent(coveredInstructions, coveredInstructions + missedInstructions),
              covered = coveredInstructions,
              total = coveredInstructions + missedInstructions,
            ),
          branchCoverageInfo =
            CoverageInfo(
              percent = calculatePercent(coveredBranches, coveredBranches + missedBranches),
              covered = coveredBranches,
              total = coveredBranches + missedBranches,
            ),
        )
    }
  }

  private fun updateSourceFilePathsInClassBuilders(
    variantName: String,
    sourceFileNameToPath: Map<String, String>,
    packageReportBuilder: PackageReportBuilder,
  ) {
    packageReportBuilder.classes.values.forEach { classBuilder ->
      val path = sourceFileNameToPath[classBuilder.sourceFileName]
      if (path != null) {
        classBuilder.variantSourceFilePaths.add(VariantSourceFilePath(variantName, path))
      }
    }
  }

  private data class AllCounters(val instruction: CoverageInfo, val branch: CoverageInfo, val line: CoverageInfo)

  private fun parseCoverageCounters(node: Element): AllCounters {
    val counters = node.childNodes.elements.filter { it.tagName == TAG_COUNTER }
    return AllCounters(
      instruction = parseSingleCounter(counters, COUNTER_TYPE_INSTRUCTION),
      branch = parseSingleCounter(counters, COUNTER_TYPE_BRANCH),
      line = parseSingleCounter(counters, COUNTER_TYPE_LINE),
    )
  }

  private fun parseSourceFileLocations(rootElement: Element): List<String> {
    val sourcesNode = rootElement.getElementsByTagName(TAG_SOURCES).item(0) as? Element
    return sourcesNode?.getElementsByTagName(TAG_FILE)?.elements?.map { it.getAttribute(ATTR_PATH) } ?: emptyList()
  }

  private fun locateSourceFile(sourceRoots: List<String>, projectBaseDir: File, packagePath: String, sourceFileName: String): File? {
    return sourceRoots.firstNotNullOfOrNull { root ->
      val absoluteSourceRoot = File(projectBaseDir, root)

      val potentialFile = File(absoluteSourceRoot, packagePath + sourceFileName)
      if (potentialFile.isFile) potentialFile else null
    }
  }
}
