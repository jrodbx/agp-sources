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

package com.android.build.gradle.internal.coverage

import com.android.build.gradle.internal.coverage.report.ReportType
import com.android.build.gradle.internal.coverage.report.createHtmlReportVisitor
import com.android.build.gradle.internal.coverage.report.createXmlReportVisitor
import com.google.common.io.Closeables
import org.gradle.api.logging.Logger
import org.jacoco.core.analysis.Analyzer
import org.jacoco.core.analysis.CoverageBuilder
import org.jacoco.core.tools.ExecFileLoader
import org.jacoco.report.DirectorySourceFileLocator
import org.jacoco.report.FileMultiReportOutput
import org.jacoco.report.IReportVisitor
import org.jacoco.report.MultiReportVisitor
import org.jacoco.report.MultiSourceFileLocator
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

@Throws(IOException::class)
@JvmOverloads
internal fun generateReport(
    coverageFiles: Collection<File>,
    reportDir: File,
    classFolders: Collection<File>,
    sourceFolders: Collection<File>,
    tabWidth: Int,
    reportName: String,
    logger: Logger,
    reportTypes: List<ReportType> = listOf(ReportType.HTML, ReportType.XML),
    xmlReportName: String = "report",
) {
    // Load data
    val loader = ExecFileLoader()
    for (coverageFile in coverageFiles) {
        loader.load(coverageFile)
    }
    val sessionInfoStore = loader.sessionInfoStore
    val executionDataStore = loader.executionDataStore

    val output = FileMultiReportOutput(reportDir)
    var xmlReportOutput: OutputStream? = null

    try {
        val visitor: IReportVisitor =
            MultiReportVisitor(reportTypes.map {
                when(it) {
                    ReportType.HTML -> createHtmlReportVisitor(output)
                    ReportType.XML -> {
                        xmlReportOutput = output.createFile("${xmlReportName}.xml")
                        createXmlReportVisitor(xmlReportOutput)
                    }
                }
            })

        // Generate report
        visitor.visitInfo(sessionInfoStore.infos, executionDataStore.contents)
        val builder = CoverageBuilder()
        val analyzer = Analyzer(executionDataStore, builder)
        analyzeAll(analyzer, classFolders)
        val locator = MultiSourceFileLocator(0)
        for (folder in sourceFolders) {
            locator.add(DirectorySourceFileLocator(folder, "UTF-8", tabWidth))
        }
        val bundle = builder.getBundle(reportName)
        visitor.visitBundle(bundle, locator)
        visitor.visitEnd()
    } finally {
        try {
            xmlReportOutput?.close()
        } catch (e: IOException) {
            logger.error("Could not close xml report file", e)
        }
    }
}

@Throws(IOException::class)
private fun analyzeAll(
    analyzer: Analyzer, classFolders: Collection<File>
) {
    for (folder in classFolders) {
        analyze(analyzer, folder, classFolders)
    }
}

/**
 * Analyzes code coverage on file if it's a class file, or recursively analyzes descendants
 * if file is a folder.
 *
 * @param analyzer Jacoco Analyzer
 * @param file a file or folder
 * @param originalClassFolders the original collection of class folders to be analyzed;
 * e.g., this.classFileCollection.getFiles(). This parameter is included to avoid
 * redundant computation in the case when one of the original class folders is a
 * descendant of another.
 */
@Throws(IOException::class)
private fun analyze(
    analyzer: Analyzer,
    file: File,
    originalClassFolders: Collection<File>
) {
    if (file.isDirectory) {
        val files = file.listFiles()
        if (files != null) {
            for (f in files) {
                // check that f is not in originalClassFolders to avoid redundant
                // computation
                if (!originalClassFolders.contains(f)) {
                    analyze(analyzer, f, originalClassFolders)
                }
            }
        }
    } else {
        val name = file.name
        if (! name.endsWith(".class")
            || name == "R.class" || name.startsWith("R$")
            || name == "Manifest.class" || name.startsWith("Manifest$")
            || name == "BuildConfig.class"
        ) {
            return
        }
        val `in`: InputStream = FileInputStream(file)
        try {
            analyzer.analyzeClass(`in`, file.absolutePath)
        } finally {
            Closeables.closeQuietly(`in`)
        }
    }
}
